package toolkit;

import common.OperationResult;
import database.TransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ToolkitOperations {
    private static final int MIN_COMPARABLES = 3;
    private static final double FALLBACK_CAPACITY_TOLERANCE = 0.50;
    private static final int FALLBACK_LOOKBACK_MONTHS = 36;

    private final TransactionManager transactions;

    public ToolkitOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<PricingRecommendation> recommendPricing(
            PricingRecommendationInput input
    ) {
        String validationError = validateRecommendationInput(input);
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        return transactions.execute(connection -> {
            if (!genreExists(connection, input.getGenreId())) {
                return OperationResult.notFound(
                        "Genre " + input.getGenreId() + " does not exist."
                );
            }

            List<ComparablePerformance> primary = findComparables(
                    connection,
                    input,
                    true,
                    input.getCapacityTolerancePct(),
                    input.getLookbackMonths(),
                    input.getMaxComparables(),
                    "Primary match"
            );

            LinkedHashMap<Integer, ComparablePerformance> selected = new LinkedHashMap<>();
            primary.forEach(row -> selected.put(row.getPerformanceId(), row));

            boolean fallbackUsed = primary.size() < MIN_COMPARABLES;
            if (fallbackUsed && selected.size() < input.getMaxComparables()) {
                List<ComparablePerformance> expanded = findComparables(
                        connection,
                        input,
                        false,
                        Math.max(input.getCapacityTolerancePct(), FALLBACK_CAPACITY_TOLERANCE),
                        Math.max(input.getLookbackMonths(), FALLBACK_LOOKBACK_MONTHS),
                        input.getMaxComparables(),
                        "Expanded fallback"
                );
                for (ComparablePerformance comparable : expanded) {
                    if (selected.size() >= input.getMaxComparables()) {
                        break;
                    }
                    selected.putIfAbsent(comparable.getPerformanceId(), comparable);
                }
            }

            List<ComparablePerformance> comparables = new ArrayList<>(selected.values());
            if (comparables.isEmpty()) {
                return OperationResult.success(
                        "No usable historical performances were found; a rule-based fallback was used.",
                        defaultRecommendation(
                                "No completed same-genre or same-segment performances were available."
                        )
                );
            }

            List<Integer> performanceIds = comparables.stream()
                    .map(ComparablePerformance::getPerformanceId)
                    .toList();
            List<TierMetric> metrics = getTierMetrics(connection, performanceIds);
            if (metrics.isEmpty()) {
                return OperationResult.success(
                        "Comparable performances lacked complete tier data; a rule-based fallback was used.",
                        defaultRecommendation(
                                "Historical matches existed, but none had usable tier capacity and sales data."
                        )
                );
            }

            String strategy = describeStrategy(input, primary.size(), fallbackUsed, comparables.size());
            PricingRecommendation recommendation = generateRecommendation(
                    metrics,
                    comparables,
                    fallbackUsed,
                    strategy
            );
            if (recommendation.getTiers().isEmpty()) {
                return OperationResult.success(
                        "Comparable tier structures were incomplete; a rule-based fallback was used.",
                        defaultRecommendation(
                                "Historical tier rows could not form a complete recommendation."
                        )
                );
            }

            String message = fallbackUsed
                    ? "Pricing recommendation generated with the expanded comparable fallback."
                    : "Pricing recommendation generated from primary comparable performances.";
            return OperationResult.success(message, recommendation);
        });
    }

    private static String validateRecommendationInput(PricingRecommendationInput input) {
        if (input == null) {
            return "Pricing recommendation input is required.";
        }
        if (input.getGenreId() <= 0) {
            return "Genre ID must be positive.";
        }
        if (input.getCity() == null || input.getCity().isBlank()) {
            return "City is required.";
        }
        if (input.getVenueCapacity() <= 0) {
            return "Venue capacity must be positive.";
        }
        if (input.getCapacityTolerancePct() <= 0
                || input.getCapacityTolerancePct() > 1) {
            return "Capacity tolerance must be greater than 0 and at most 1.";
        }
        if (input.getLookbackMonths() <= 0) {
            return "Lookback months must be positive.";
        }
        if (input.getMaxComparables() <= 0) {
            return "Maximum comparables must be positive.";
        }
        return null;
    }

    private boolean genreExists(Connection connection, int genreId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM Genre WHERE genre_id = ?"
        )) {
            statement.setInt(1, genreId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private List<ComparablePerformance> findComparables(
            Connection connection,
            PricingRecommendationInput input,
            boolean requireSameCity,
            double capacityTolerance,
            int lookbackMonths,
            int limit,
            String selectionStage
    ) throws SQLException {
        String sql = """
                WITH venue_capacity AS (
                    SELECT sec.venue_id,
                           SUM(CASE
                               WHEN sec.section_type = 'reserved' THEN COALESCE(seat_counts.n, 0)
                               ELSE COALESCE(sec.standing_capacity, 0)
                           END) AS capacity
                    FROM Section sec
                    LEFT JOIN (
                        SELECT venue_id, section_name, COUNT(*) AS n
                        FROM Seats
                        GROUP BY venue_id, section_name
                    ) seat_counts
                      ON seat_counts.venue_id = sec.venue_id
                     AND seat_counts.section_name = sec.section_name
                    GROUP BY sec.venue_id
                )
                SELECT p.performance_id,
                       e.title,
                       g.genre_id,
                       g.genre_name,
                       sg.segment_name,
                       v.venue_id,
                       v.name AS venue_name,
                       v.city,
                       vc.capacity AS venue_capacity,
                       p.date_time,
                       ROW_NUMBER() OVER (
                           ORDER BY CASE WHEN g.genre_id = ? THEN 1 ELSE 2 END,
                                    ABS(vc.capacity - ?),
                                    p.date_time DESC,
                                    p.performance_id
                       ) AS match_rank
                FROM Performance p
                JOIN Event e ON e.event_id = p.event_id
                JOIN Genre g ON g.genre_id = e.genre_id
                JOIN Segment sg ON sg.segment_id = g.segment_id
                JOIN Venue v ON v.venue_id = p.venue_id
                JOIN venue_capacity vc ON vc.venue_id = v.venue_id
                WHERE p.status = 'completed'
                  AND p.date_time < UTC_TIMESTAMP()
                  AND (
                        g.genre_id = ?
                        OR sg.segment_id = (
                            SELECT segment_id FROM Genre WHERE genre_id = ?
                        )
                      )
                  AND (? = FALSE OR v.city = ?)
                  AND vc.capacity BETWEEN ? AND ?
                  AND p.date_time >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL ? MONTH)
                ORDER BY match_rank
                LIMIT ?
                """;

        int lowerCapacity = Math.max(
                1,
                (int) Math.floor(input.getVenueCapacity() * (1 - capacityTolerance))
        );
        int upperCapacity = Math.max(
                lowerCapacity,
                (int) Math.ceil(input.getVenueCapacity() * (1 + capacityTolerance))
        );

        List<ComparablePerformance> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, input.getGenreId());
            statement.setInt(2, input.getVenueCapacity());
            statement.setInt(3, input.getGenreId());
            statement.setInt(4, input.getGenreId());
            statement.setBoolean(5, requireSameCity);
            statement.setString(6, input.getCity().trim());
            statement.setInt(7, lowerCapacity);
            statement.setInt(8, upperCapacity);
            statement.setInt(9, lookbackMonths);
            statement.setInt(10, limit);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int comparableCapacity = result.getInt("venue_capacity");
                    double capacityDifference = Math.abs(
                            comparableCapacity - input.getVenueCapacity()
                    ) * 100.0 / input.getVenueCapacity();
                    boolean exactGenre = result.getInt("genre_id") == input.getGenreId();
                    boolean sameCity = result.getString("city")
                            .equalsIgnoreCase(input.getCity().trim());
                    LocalDateTime completedAt = result.getTimestamp("date_time")
                            .toLocalDateTime();
                    String reason = selectionStage
                            + ": " + (exactGenre ? "exact genre" : "same segment")
                            + ", " + (sameCity ? "same city" : "expanded city range")
                            + ", capacity " + String.format("%.1f", capacityDifference)
                            + "% from target, completed " + completedAt.toLocalDate();

                    results.add(new ComparablePerformance(
                            result.getInt("performance_id"),
                            result.getString("title"),
                            result.getString("genre_name"),
                            result.getString("segment_name"),
                            result.getInt("venue_id"),
                            result.getString("venue_name"),
                            result.getString("city"),
                            comparableCapacity,
                            completedAt,
                            result.getInt("match_rank"),
                            reason
                    ));
                }
            }
        }
        return results;
    }

    private List<TierMetric> getTierMetrics(
            Connection connection,
            List<Integer> performanceIds
    ) throws SQLException {
        if (performanceIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(
                ",",
                Collections.nCopies(performanceIds.size(), "?")
        );
        String sql = """
                WITH section_stats AS (
                    SELECT sta.performance_id,
                           sta.venue_id,
                           sta.section_name,
                           sta.tier_code,
                           s.section_type,
                           CASE
                               WHEN s.section_type = 'reserved' THEN COALESCE(rc.n, 0)
                               ELSE COALESCE(ga.total_capacity, 0)
                           END AS section_capacity,
                           CASE
                               WHEN s.section_type = 'reserved' THEN COALESCE(rs.n, 0)
                               ELSE COALESCE(gs.n, 0)
                           END AS section_sold,
                           CASE
                               WHEN s.section_type = 'reserved' THEN COALESCE(rs.revenue, 0)
                               ELSE COALESCE(gs.revenue, 0)
                           END AS section_revenue
                    FROM SectionTierAssignment sta
                    JOIN Section s
                      ON s.venue_id = sta.venue_id
                     AND s.section_name = sta.section_name
                    LEFT JOIN (
                        SELECT performance_id, venue_id, section_name, COUNT(*) AS n
                        FROM PerformanceSeats
                        WHERE blocked_status = FALSE
                        GROUP BY performance_id, venue_id, section_name
                    ) rc
                      ON rc.performance_id = sta.performance_id
                     AND rc.venue_id = sta.venue_id
                     AND rc.section_name = sta.section_name
                    LEFT JOIN GeneralAdmissionCapacity ga
                      ON ga.performance_id = sta.performance_id
                     AND ga.venue_id = sta.venue_id
                     AND ga.section_name = sta.section_name
                    LEFT JOIN (
                        SELECT t.performance_id,
                               ps.venue_id,
                               ps.section_name,
                               COUNT(*) AS n,
                               SUM(t.face_value) AS revenue
                        FROM Tickets t
                        JOIN PerformanceSeats ps
                          ON ps.performance_seat_id = t.performance_seats_ref
                         AND ps.performance_id = t.performance_id
                        WHERE t.status = 'active'
                        GROUP BY t.performance_id, ps.venue_id, ps.section_name
                    ) rs
                      ON rs.performance_id = sta.performance_id
                     AND rs.venue_id = sta.venue_id
                     AND rs.section_name = sta.section_name
                    LEFT JOIN (
                        SELECT t.performance_id,
                               gac.venue_id,
                               gac.section_name,
                               COUNT(*) AS n,
                               SUM(t.face_value) AS revenue
                        FROM Tickets t
                        JOIN GeneralAdmissionCapacity gac
                          ON gac.ga_capacity_id = t.general_seats_ref
                         AND gac.performance_id = t.performance_id
                        WHERE t.status = 'active'
                        GROUP BY t.performance_id, gac.venue_id, gac.section_name
                    ) gs
                      ON gs.performance_id = sta.performance_id
                     AND gs.venue_id = sta.venue_id
                     AND gs.section_name = sta.section_name
                    WHERE sta.performance_id IN (%s)
                ),
                tier_totals AS (
                    SELECT ss.performance_id,
                           ss.tier_code,
                           pt.price,
                           SUM(ss.section_capacity) AS tier_capacity,
                           SUM(ss.section_sold) AS tier_sold,
                           SUM(ss.section_revenue) AS tier_revenue
                    FROM section_stats ss
                    JOIN PriceTier pt
                      ON pt.performance_id = ss.performance_id
                     AND pt.tier_code = ss.tier_code
                    GROUP BY ss.performance_id, ss.tier_code, pt.price
                )
                SELECT tt.performance_id,
                       tt.price,
                       tt.tier_capacity,
                       tt.tier_sold,
                       tt.tier_revenue,
                       ROUND(
                           tt.tier_sold / NULLIF(tt.tier_capacity, 0) * 100,
                           2
                       ) AS sell_through_pct,
                       ROUND(
                           tt.tier_capacity
                           / NULLIF(SUM(tt.tier_capacity) OVER (
                               PARTITION BY tt.performance_id
                           ), 0) * 100,
                           2
                       ) AS pct_of_venue_capacity,
                       ROW_NUMBER() OVER (
                           PARTITION BY tt.performance_id
                           ORDER BY tt.price ASC, tt.tier_code ASC
                       ) AS tier_rank
                FROM tier_totals tt
                ORDER BY tt.performance_id, tier_rank
                """.formatted(placeholders);

        List<TierMetric> metrics = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Integer performanceId : performanceIds) {
                statement.setInt(index++, performanceId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    metrics.add(new TierMetric(
                            result.getInt("performance_id"),
                            result.getInt("tier_rank"),
                            result.getBigDecimal("price"),
                            result.getInt("tier_capacity"),
                            result.getInt("tier_sold"),
                            result.getBigDecimal("tier_revenue"),
                            result.getDouble("sell_through_pct"),
                            result.getDouble("pct_of_venue_capacity")
                    ));
                }
            }
        }
        return metrics;
    }

    private PricingRecommendation generateRecommendation(
            List<TierMetric> metrics,
            List<ComparablePerformance> comparables,
            boolean fallbackUsed,
            String strategyDescription
    ) {
        int tierCount = determineTierCount(metrics);
        if (tierCount <= 0) {
            return new PricingRecommendation(
                    0,
                    List.of(),
                    0,
                    BigDecimal.ZERO,
                    List.of(),
                    fallbackUsed,
                    strategyDescription
            );
        }

        Map<Integer, Long> rowsPerPerformance = metrics.stream().collect(
                Collectors.groupingBy(TierMetric::getPerformanceId, Collectors.counting())
        );
        Set<Integer> usablePerformanceIds = rowsPerPerformance.entrySet().stream()
                .filter(entry -> entry.getValue() == tierCount)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TierMetric> usableMetrics = metrics.stream()
                .filter(metric -> usablePerformanceIds.contains(metric.getPerformanceId()))
                .toList();
        List<ComparablePerformance> usableComparables = comparables.stream()
                .filter(row -> usablePerformanceIds.contains(row.getPerformanceId()))
                .toList();

        List<Double> rawCapacityShares = new ArrayList<>();
        List<BigDecimal> averagePrices = new ArrayList<>();
        for (int rank = 1; rank <= tierCount; rank += 1) {
            int currentRank = rank;
            List<TierMetric> tierRows = usableMetrics.stream()
                    .filter(metric -> metric.getTierRank() == currentRank)
                    .toList();
            if (tierRows.isEmpty()) {
                return new PricingRecommendation(
                        0,
                        List.of(),
                        0,
                        BigDecimal.ZERO,
                        List.of(),
                        fallbackUsed,
                        strategyDescription
                );
            }

            rawCapacityShares.add(tierRows.stream()
                    .mapToDouble(TierMetric::getCapacityPct)
                    .average()
                    .orElse(0));
            averagePrices.add(tierRows.stream()
                    .map(TierMetric::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(tierRows.size()), 2, RoundingMode.HALF_UP));
        }

        List<BigDecimal> normalizedShares = normalizeCapacityShares(rawCapacityShares);
        List<TierRecommendation> tiers = new ArrayList<>();
        for (int index = 0; index < tierCount; index += 1) {
            tiers.add(new TierRecommendation(
                    index + 1,
                    normalizedShares.get(index),
                    averagePrices.get(index)
            ));
        }

        BigDecimal averageHistoricalRevenue = averageHistoricalRevenue(
                usableMetrics,
                usablePerformanceIds
        );
        return new PricingRecommendation(
                tierCount,
                tiers,
                usableComparables.size(),
                averageHistoricalRevenue,
                usableComparables,
                fallbackUsed,
                strategyDescription
        );
    }

    private int determineTierCount(List<TierMetric> metrics) {
        Map<Integer, Long> tierCounts = metrics.stream().collect(
                Collectors.groupingBy(TierMetric::getPerformanceId, Collectors.counting())
        );
        Map<Long, Long> frequencies = tierCounts.values().stream().collect(
                Collectors.groupingBy(value -> value, Collectors.counting())
        );

        long selectedTierCount = 0;
        long selectedFrequency = -1;
        List<Long> sortedTierCounts = frequencies.keySet().stream().sorted().toList();
        for (Long candidate : sortedTierCounts) {
            long frequency = frequencies.get(candidate);
            if (frequency > selectedFrequency) {
                selectedFrequency = frequency;
                selectedTierCount = candidate;
            }
        }
        return Math.toIntExact(selectedTierCount);
    }

    private List<BigDecimal> normalizeCapacityShares(List<Double> rawShares) {
        if (rawShares.isEmpty()) {
            return List.of();
        }

        BigDecimal total = rawShares.stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> normalized = new ArrayList<>();
        if (total.signum() == 0) {
            BigDecimal equalShare = new BigDecimal("100.0")
                    .divide(BigDecimal.valueOf(rawShares.size()), 1, RoundingMode.HALF_UP);
            for (int index = 0; index < rawShares.size(); index += 1) {
                normalized.add(equalShare);
            }
        } else {
            for (Double rawShare : rawShares) {
                normalized.add(BigDecimal.valueOf(rawShare)
                        .multiply(new BigDecimal("100.0"))
                        .divide(total, 1, RoundingMode.HALF_UP));
            }
        }

        BigDecimal roundedTotal = normalized.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal adjustment = new BigDecimal("100.0").subtract(roundedTotal);
        int largestIndex = 0;
        for (int index = 1; index < rawShares.size(); index += 1) {
            if (rawShares.get(index) > rawShares.get(largestIndex)) {
                largestIndex = index;
            }
        }
        normalized.set(
                largestIndex,
                normalized.get(largestIndex).add(adjustment).setScale(1, RoundingMode.HALF_UP)
        );
        return List.copyOf(normalized);
    }

    private BigDecimal averageHistoricalRevenue(
            List<TierMetric> metrics,
            Set<Integer> performanceIds
    ) {
        if (performanceIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Integer, BigDecimal> revenueByPerformance = new LinkedHashMap<>();
        for (TierMetric metric : metrics) {
            revenueByPerformance.merge(
                    metric.getPerformanceId(),
                    metric.getTierRevenue(),
                    BigDecimal::add
            );
        }
        return revenueByPerformance.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(revenueByPerformance.size()), 2, RoundingMode.HALF_UP);
    }

    private PricingRecommendation defaultRecommendation(String reason) {
        return new PricingRecommendation(
                3,
                List.of(
                        new TierRecommendation(1, new BigDecimal("50.0"), new BigDecimal("60.00")),
                        new TierRecommendation(2, new BigDecimal("30.0"), new BigDecimal("100.00")),
                        new TierRecommendation(3, new BigDecimal("20.0"), new BigDecimal("150.00"))
                ),
                0,
                BigDecimal.ZERO,
                List.of(),
                true,
                reason + " Default value/mid/premium tiers use 50%/30%/20% of capacity "
                        + "at $60/$100/$150."
        );
    }

    private String describeStrategy(
            PricingRecommendationInput input,
            int primaryCount,
            boolean fallbackUsed,
            int totalCount
    ) {
        String primary = "Completed performances from the previous "
                + input.getLookbackMonths() + " months in " + input.getCity().trim()
                + ", exact genre first and then the same segment, with venue capacity within +/-"
                + BigDecimal.valueOf(input.getCapacityTolerancePct() * 100)
                        .stripTrailingZeros().toPlainString()
                + "%.";
        if (!fallbackUsed) {
            return primary;
        }
        return primary + " Only " + primaryCount + " primary match(es) were found, so the pool "
                + "was expanded to any city, the previous "
                + Math.max(input.getLookbackMonths(), FALLBACK_LOOKBACK_MONTHS)
                + " months, and +/-"
                + BigDecimal.valueOf(
                        Math.max(input.getCapacityTolerancePct(), FALLBACK_CAPACITY_TOLERANCE) * 100
                ).stripTrailingZeros().toPlainString()
                + "% capacity; " + totalCount + " match(es) were available after expansion."
                + (totalCount < MIN_COMPARABLES
                    ? " Fewer than three matches means this recommendation is low confidence."
                    : "");
    }

    public OperationResult<RevenueImpactEstimate> estimateRevenueImpact(
            RevenueImpactInput input
    ) {
        String validationError = validateRevenueImpactInput(input);
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        return transactions.execute(connection -> {
            List<TierMetric> metrics = getTierMetrics(
                    connection,
                    input.getComparablePerformanceIds()
            );
            if (metrics.isEmpty()) {
                return OperationResult.notFound("No comparable tier data is available.");
            }

            RevenueProjection current = projectRevenue(
                    metrics,
                    input.getCurrentPrice(),
                    input.getBandWidth()
            );
            RevenueProjection proposed = projectRevenue(
                    metrics,
                    input.getProposedPrice(),
                    input.getBandWidth()
            );
            if (current == null || proposed == null) {
                return OperationResult.notFound(
                        "Comparable tiers were not found near both prices; increase the price band."
                );
            }

            BigDecimal change = proposed.expectedRevenue
                    .subtract(current.expectedRevenue)
                    .setScale(2, RoundingMode.HALF_UP);
            return OperationResult.success(
                    "Revenue change estimated from historical tiers near both prices.",
                    new RevenueImpactEstimate(
                            current.expectedSellThroughPct,
                            proposed.expectedSellThroughPct,
                            current.expectedRevenue,
                            proposed.expectedRevenue,
                            change,
                            current.sampleSize,
                            proposed.sampleSize
                    )
            );
        });
    }

    private static String validateRevenueImpactInput(RevenueImpactInput input) {
        if (input == null) {
            return "Revenue-impact input is required.";
        }
        if (input.getComparablePerformanceIds() == null
                || input.getComparablePerformanceIds().isEmpty()) {
            return "At least one comparable performance is required.";
        }
        if (input.getCurrentPrice() == null || input.getCurrentPrice().signum() <= 0) {
            return "Current price must be positive.";
        }
        if (input.getProposedPrice() == null || input.getProposedPrice().signum() <= 0) {
            return "Proposed price must be positive.";
        }
        if (input.getBandWidth() == null || input.getBandWidth().signum() <= 0) {
            return "Price band must be positive.";
        }
        return null;
    }

    private RevenueProjection projectRevenue(
            List<TierMetric> metrics,
            BigDecimal targetPrice,
            BigDecimal bandWidth
    ) {
        BigDecimal lowerBound = targetPrice.subtract(bandWidth);
        BigDecimal upperBound = targetPrice.add(bandWidth);
        List<TierMetric> matching = metrics.stream()
                .filter(metric -> metric.getPrice().compareTo(lowerBound) >= 0
                        && metric.getPrice().compareTo(upperBound) <= 0)
                .toList();
        if (matching.isEmpty()) {
            return null;
        }

        double averageSellThrough = matching.stream()
                .mapToDouble(TierMetric::getSellThroughPct)
                .average()
                .orElse(0);
        double averageCapacity = matching.stream()
                .mapToInt(TierMetric::getTierCapacity)
                .average()
                .orElse(0);
        BigDecimal expectedRevenue = targetPrice
                .multiply(BigDecimal.valueOf(averageCapacity))
                .multiply(BigDecimal.valueOf(averageSellThrough))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new RevenueProjection(
                averageSellThrough,
                expectedRevenue,
                matching.size()
        );
    }

    private static final class RevenueProjection {
        private final double expectedSellThroughPct;
        private final BigDecimal expectedRevenue;
        private final int sampleSize;

        private RevenueProjection(
                double expectedSellThroughPct,
                BigDecimal expectedRevenue,
                int sampleSize
        ) {
            this.expectedSellThroughPct = expectedSellThroughPct;
            this.expectedRevenue = expectedRevenue;
            this.sampleSize = sampleSize;
        }
    }
}
