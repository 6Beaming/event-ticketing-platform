package toolkit;

import common.OperationResult;
import database.TransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ToolkitOperations {

    private final TransactionManager transactions;


    public ToolkitOperations(
            TransactionManager transactions
    ) {
        this.transactions = transactions;
    }



    /**
     * Main organizer recommendation function.
     *
     * Flow:
     *
     * PR1 -> comparable performances
     * PR2 -> tier metrics
     * Recommendation algorithm
     */
    public OperationResult<PricingRecommendation> recommendPricing(
            PricingRecommendationInput input
    ) {

        return transactions.execute(connection -> {


            List<ComparablePerformance> comparables =
                    findComparables(
                            connection,
                            input
                    );


            if (comparables.isEmpty()) {

                return OperationResult.success(
                        "No comparable performances found.",
                        new PricingRecommendation(
                                0,
                                List.of(),
                                0,
                                BigDecimal.ZERO,
                                List.of()
                        )
                );
            }


            List<Integer> performanceIds =
                    comparables.stream()
                            .map(ComparablePerformance::getPerformanceId)
                            .toList();



            List<TierMetric> tierMetrics =
                    getTierMetrics(
                            connection,
                            performanceIds
                    );



            PricingRecommendation recommendation =
                    generateRecommendation(
                            tierMetrics,
                            comparables.size(),
                            performanceIds
                    );



            return OperationResult.success(
                    "Pricing recommendation generated.",
                    recommendation
            );

        });
    }





    /**********************************************************************
     PR1

     Finds comparable completed performances.

     Returns:
     - performance id
     - transparency information for UI

     **********************************************************************/
    private List<ComparablePerformance> findComparables(
        Connection connection,
        PricingRecommendationInput input
) throws SQLException {


    String sql = """
            WITH venue_capacity AS (
                SELECT sec.venue_id,
                       SUM(
                           CASE 
                               WHEN sec.section_type = 'reserved' 
                               THEN seat_counts.n 
                               ELSE 0 
                           END
                       )
                       +
                       SUM(
                           CASE 
                               WHEN sec.section_type = 'general'
                               THEN sec.standing_capacity
                               ELSE 0
                           END
                       ) AS capacity
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

            SELECT
                p.performance_id,
                e.title,
                g.genre_name,
                sg.segment_name,
                v.venue_id,
                v.name AS venue_name,
                v.city,
                vc.capacity AS venue_capacity,
                p.date_time,
                ROW_NUMBER() OVER (
                    ORDER BY
                        CASE
                            WHEN g.genre_id = ? THEN 1
                            ELSE 2
                        END,
                        ABS(vc.capacity - ?),
                        p.date_time DESC
                ) AS match_rank
            FROM Performance p
            JOIN Event e 
                ON e.event_id = p.event_id
            JOIN Genre g 
                ON g.genre_id = e.genre_id
            JOIN Segment sg 
                ON sg.segment_id = g.segment_id
            JOIN Venue v 
                ON v.venue_id = p.venue_id
            JOIN venue_capacity vc 
                ON vc.venue_id = v.venue_id
            WHERE p.status = 'completed'
              AND (
                    g.genre_id = ?
                    OR sg.segment_id = (
                        SELECT segment_id
                        FROM Genre
                        WHERE genre_id = ?
                    )
                  )
              AND v.city = ?
              AND vc.capacity BETWEEN ?
                                  AND ?
              AND p.date_time >= DATE_SUB(
                    NOW(),
                    INTERVAL ? MONTH
                  )
            ORDER BY match_rank
            LIMIT ?
            """;


    List<ComparablePerformance> results =
            new ArrayList<>();


    try (PreparedStatement statement =
                 connection.prepareStatement(sql)) {


        int lowerCapacity =
                (int)
                (
                    input.getVenueCapacity()
                    *
                    (1 - input.getCapacityTolerancePct())
                );


        int upperCapacity =
                (int)
                (
                    input.getVenueCapacity()
                    *
                    (1 + input.getCapacityTolerancePct())
                );



        // 1, 2: ROW_NUMBER() window ORDER BY (genre match tiebreak, then capacity closeness)
        statement.setInt(
                1,
                input.getGenreId()
        );


        statement.setInt(
                2,
                input.getVenueCapacity()
        );


        // 3, 4: WHERE genre_id = ? OR segment_id = (SELECT ... WHERE genre_id = ?)
        statement.setInt(
                3,
                input.getGenreId()
        );


        statement.setInt(
                4,
                input.getGenreId()
        );


        statement.setString(
                5,
                input.getCity()
        );


        statement.setInt(
                6,
                lowerCapacity
        );


        statement.setInt(
                7,
                upperCapacity
        );


        statement.setInt(
                8,
                input.getLookbackMonths()
        );


        statement.setInt(
                9,
                input.getMaxComparables()
        );



        try (ResultSet rs = statement.executeQuery()) {


            while (rs.next()) {

                results.add(
                        new ComparablePerformance(
                                rs.getInt(
                                        "performance_id"
                                ),
                                rs.getString(
                                        "title"
                                ),
                                rs.getString(
                                        "genre_name"
                                ),
                                rs.getString(
                                        "segment_name"
                                ),
                                rs.getInt(
                                        "venue_id"
                                ),
                                rs.getString(
                                        "venue_name"
                                ),
                                rs.getString(
                                        "city"
                                ),
                                rs.getInt(
                                        "venue_capacity"
                                ),
                                rs.getTimestamp(
                                        "date_time"
                                ).toLocalDateTime(),
                                rs.getInt(
                                        "match_rank"
                                )
                        )
                );
            }
        }
    }


    return results;
}




    /**********************************************************************
     PR2

     Gets tier-level metrics for comparable performances.

     Returns:
     - tier rank
     - price
     - capacity %
     - sell-through
     - revenue

     **********************************************************************/
    private List<TierMetric> getTierMetrics(
        Connection connection,
        List<Integer> performanceIds
) throws SQLException {


    if (performanceIds.isEmpty()) {
        return List.of();
    }


    String placeholders =
            String.join(
                    ",",
                    Collections.nCopies(
                            performanceIds.size(),
                            "?"
                    )
            );



    String sql = """
            WITH section_stats AS (
                SELECT
                    sta.performance_id,
                    sta.venue_id,
                    sta.section_name,
                    sta.tier_code,
                    s.section_type,

                    CASE
                        WHEN s.section_type = 'reserved'
                        THEN COALESCE(rc.n, 0)
                        ELSE ga.total_capacity
                    END AS section_capacity,

                    CASE
                        WHEN s.section_type = 'reserved'
                        THEN COALESCE(rs.n, 0)
                        ELSE COALESCE(gs.n, 0)
                    END AS section_sold,

                    CASE
                        WHEN s.section_type = 'reserved'
                        THEN COALESCE(rs.revenue, 0)
                        ELSE COALESCE(gs.revenue, 0)
                    END AS section_revenue

                FROM SectionTierAssignment sta

                JOIN Section s
                    ON s.venue_id = sta.venue_id
                    AND s.section_name = sta.section_name


                LEFT JOIN (
                    SELECT
                        performance_id,
                        venue_id,
                        section_name,
                        COUNT(*) AS n
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
                    SELECT
                        t.performance_id,
                        ps.venue_id,
                        ps.section_name,
                        COUNT(*) AS n,
                        SUM(t.face_value) AS revenue
                    FROM Tickets t

                    JOIN PerformanceSeats ps
                        ON ps.performance_seat_id = t.performance_seats_ref
                        AND ps.performance_id = t.performance_id

                    WHERE t.status = 'active'

                    GROUP BY
                        t.performance_id,
                        ps.venue_id,
                        ps.section_name

                ) rs
                    ON rs.performance_id = sta.performance_id
                    AND rs.venue_id = sta.venue_id
                    AND rs.section_name = sta.section_name



                LEFT JOIN (
                    SELECT
                        t.performance_id,
                        gac.venue_id,
                        gac.section_name,
                        COUNT(*) AS n,
                        SUM(t.face_value) AS revenue

                    FROM Tickets t

                    JOIN GeneralAdmissionCapacity gac
                        ON gac.ga_capacity_id = t.general_seats_ref
                        AND gac.performance_id = t.performance_id

                    WHERE t.status = 'active'

                    GROUP BY
                        t.performance_id,
                        gac.venue_id,
                        gac.section_name

                ) gs
                    ON gs.performance_id = sta.performance_id
                    AND gs.venue_id = sta.venue_id
                    AND gs.section_name = sta.section_name


                WHERE sta.performance_id IN (
                    %s
                )
            )


            SELECT
                ss.performance_id,
                pt.price,

                SUM(ss.section_capacity) AS tier_capacity,
                SUM(ss.section_sold) AS tier_sold,
                SUM(ss.section_revenue) AS tier_revenue,


                ROUND(
                    SUM(ss.section_sold)
                    /
                    NULLIF(SUM(ss.section_capacity),0)
                    * 100,
                    1
                ) AS sell_through_pct,


                ROUND(
                    SUM(ss.section_capacity)
                    /
                    SUM(
                        SUM(ss.section_capacity)
                    ) OVER (
                        PARTITION BY ss.performance_id
                    )
                    * 100,
                    1
                ) AS pct_of_venue_capacity,


                ROW_NUMBER() OVER (
                    PARTITION BY ss.performance_id
                    ORDER BY pt.price ASC
                ) AS tier_rank


            FROM section_stats ss


            JOIN PriceTier pt
                ON pt.performance_id = ss.performance_id
                AND pt.tier_code = ss.tier_code


            GROUP BY
                ss.performance_id,
                ss.tier_code,
                pt.price


            ORDER BY
                ss.performance_id,
                tier_rank;
            """
            .formatted(placeholders);



    List<TierMetric> metrics =
            new ArrayList<>();



    try (PreparedStatement statement =
                 connection.prepareStatement(sql)) {



        int index = 1;


        for (Integer id : performanceIds) {

            statement.setInt(
                    index++,
                    id
            );
        }



        try (ResultSet rs =
                     statement.executeQuery()) {


            while (rs.next()) {


                metrics.add(
                        new TierMetric(

                                rs.getInt(
                                        "performance_id"
                                ),

                                rs.getInt(
                                        "tier_rank"
                                ),

                                rs.getBigDecimal(
                                        "price"
                                ),

                                rs.getInt(
                                        "tier_capacity"
                                ),

                                rs.getInt(
                                        "tier_sold"
                                ),

                                rs.getBigDecimal(
                                        "tier_revenue"
                                ),

                                rs.getDouble(
                                        "sell_through_pct"
                                ),

                                rs.getDouble(
                                        "pct_of_venue_capacity"
                                )
                        )
                );
            }
        }
    }


    return metrics;
}





    /**********************************************************************
     Recommendation algorithm

     Uses PR2 output only.

     1. Determine common tier count
     2. Determine capacity split
     3. Determine average price

     **********************************************************************/
    private PricingRecommendation generateRecommendation(
            List<TierMetric> metrics,
            int comparablesUsed,
            List<Integer> comparablePerformanceIds
    ) {


        int tierCount =
                determineTierCount(
                        metrics
                );



        List<TierRecommendation> tiers =
                new ArrayList<>();

        BigDecimal expectedRevenue =
                BigDecimal.ZERO;



        for (int rank = 1;
             rank <= tierCount;
             rank++) {


            final int currentRank = rank;


            List<TierMetric> tierRows =
                    metrics.stream()
                            .filter(t ->
                                    t.getTierRank() == currentRank
                            )
                            .toList();



            if (tierRows.isEmpty()) {
                continue;
            }



            double averageCapacity =
                    tierRows.stream()
                            .mapToDouble(
                                    TierMetric::getCapacityPct
                            )
                            .average()
                            .orElse(0);



            BigDecimal averagePrice =
                    tierRows.stream()
                            .map(
                                TierMetric::getPrice
                            )
                            .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                            )
                            .divide(
                                BigDecimal.valueOf(
                                        tierRows.size()
                                ),
                                2,
                                RoundingMode.HALF_UP
                            );



            BigDecimal averageTierRevenue =
                    tierRows.stream()
                            .map(
                                TierMetric::getTierRevenue
                            )
                            .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                            )
                            .divide(
                                BigDecimal.valueOf(
                                        tierRows.size()
                                ),
                                2,
                                RoundingMode.HALF_UP
                            );

            expectedRevenue =
                    expectedRevenue.add(
                            averageTierRevenue
                    );



            tiers.add(
                    new TierRecommendation(
                            rank,
                            averageCapacity,
                            averagePrice
                    )
            );
        }



        return new PricingRecommendation(
                tierCount,
                tiers,
                comparablesUsed,
                expectedRevenue,
                comparablePerformanceIds
        );
    }





    /**********************************************************************
     Determines recommended number of tiers.

     Example:

     Performance A -> 3 tiers
     Performance B -> 3 tiers
     Performance C -> 4 tiers

     Result -> 3

     **********************************************************************/
    private int determineTierCount(
            List<TierMetric> metrics
    ) {


        Map<Integer, Long> counts =
                metrics.stream()
                        .collect(
                                Collectors.groupingBy(
                                        TierMetric::getPerformanceId,
                                        Collectors.counting()
                                )
                        );



        return counts.values()
                .stream()
                .collect(
                        Collectors.groupingBy(
                                x -> x,
                                Collectors.counting()
                        )
                )
                .entrySet()
                .stream()
                .max(
                        Map.Entry.comparingByValue()
                )
                .map(
                        entry ->
                                entry.getKey().intValue()
                )
                .orElse(0);
    }





    /**********************************************************************
     PR4b Extra credit

     Only called when organizer asks:
     "What happens if I change the price?"

     Approach:
     - Reuse PR2 (getTierMetrics) against the comparable performances already
       identified by recommendPricing, so this shares the same sold/capacity/
       revenue data rather than re-deriving it.
     - Narrow to tiers whose historical price fell within bandWidth of the
       proposedPrice (i.e. tiers that actually sold at roughly the price
       being proposed), since sell-through at a $20 tier tells us little
       about a $95 tier.
     - Average that band's sell-through % and capacity, then project revenue
       at the proposed price: proposedPrice * avgCapacity * avgSellThroughPct.

     Note: currentPrice is accepted for the caller's context (e.g. so the UI
     can show "from $X to $Y") but isn't used in this estimate — the estimate
     is driven entirely by historical tiers priced near the proposed price,
     not by the delta from the current price.
     **********************************************************************/
    public OperationResult<RevenueImpactEstimate> estimateRevenueImpact(
            RevenueImpactInput input
    ) {


        return transactions.execute(connection -> {


            List<TierMetric> tierMetrics =
                    getTierMetrics(
                            connection,
                            input.getComparablePerformanceIds()
                    );


            if (tierMetrics.isEmpty()) {

                return OperationResult.success(
                        "No comparable tier data available.",
                        new RevenueImpactEstimate(
                                0,
                                BigDecimal.ZERO,
                                0
                        )
                );
            }


            BigDecimal lowerBound =
                    input.getProposedPrice()
                            .subtract(input.getBandWidth());


            BigDecimal upperBound =
                    input.getProposedPrice()
                            .add(input.getBandWidth());


            List<TierMetric> matchingTiers =
                    tierMetrics.stream()
                            .filter(t ->
                                    t.getPrice().compareTo(lowerBound) >= 0
                                    && t.getPrice().compareTo(upperBound) <= 0
                            )
                            .toList();


            if (matchingTiers.isEmpty()) {

                return OperationResult.success(
                        "No comparable tiers found near the proposed price.",
                        new RevenueImpactEstimate(
                                0,
                                BigDecimal.ZERO,
                                0
                        )
                );
            }


            double averageSellThroughPct =
                    matchingTiers.stream()
                            .mapToDouble(
                                    TierMetric::getSellThroughPct
                            )
                            .average()
                            .orElse(0);


            double averageCapacity =
                    matchingTiers.stream()
                            .mapToInt(
                                    TierMetric::getTierCapacity
                            )
                            .average()
                            .orElse(0);


            BigDecimal expectedRevenue =
                    input.getProposedPrice()
                            .multiply(
                                    BigDecimal.valueOf(averageCapacity)
                            )
                            .multiply(
                                    BigDecimal.valueOf(averageSellThroughPct / 100.0)
                            )
                            .setScale(2, RoundingMode.HALF_UP);


            return OperationResult.success(
                    "Revenue impact estimated.",
                    new RevenueImpactEstimate(
                            averageSellThroughPct,
                            expectedRevenue,
                            matchingTiers.size()
                    )
            );
        });
    }

}