package operations.pricing;

import common.OperationResult;
import database.TransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PerformancePricingOperations {
    private final TransactionManager transactions;

    public PerformancePricingOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<List<String>> getVenueSectionsForPricing(int performanceId) {
        if (performanceId <= 0) {
            return OperationResult.invalidInput("Performance ID must be positive.");
        }

        return transactions.execute(connection -> {
            PerformanceDetails performance = findPerformance(connection, performanceId, false);
            if (performance == null) {
                return OperationResult.notFound("Performance not found.");
            }
            boolean existingPricing = pricingExists(connection, performanceId);
            String replacementError = findReplacementConflict(connection, performanceId);
            if (replacementError != null) {
                return OperationResult.conflict(replacementError);
            }
            Set<String> sections = loadVenueSections(connection, performance.venueId);
            if (sections.isEmpty()) {
                return OperationResult.conflict(
                        "The performance venue has no sections to configure."
                );
            }
            String message = existingPricing
                    ? "NOTICE: Performance " + performanceId
                            + " already has tiers and section assignments. Completing this setup "
                            + "will replace the existing pricing."
                    : "Performance venue sections loaded.";
            return OperationResult.success(
                    message,
                    List.copyOf(sections)
            );
        });
    }

    public OperationResult<PricingSetupSummary> configurePricing(PricingSetupInput input) {
        String validationError = PricingValidator.validateShape(input);
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        return transactions.execute(connection -> {
            PerformanceDetails performance = findPerformance(
                    connection,
                    input.getPerformanceId(),
                    true
            );
            if (performance == null) {
                return OperationResult.notFound("Performance not found.");
            }

            boolean replacingExistingPricing = pricingExists(connection, input.getPerformanceId());
            String replacementError = findReplacementConflict(
                    connection,
                    input.getPerformanceId()
            );
            if (replacementError != null) {
                return OperationResult.conflict(replacementError);
            }

            Set<String> venueSections = loadVenueSections(connection, performance.venueId);
            String coverageError = validateCoverage(input, venueSections);
            if (coverageError != null) {
                return OperationResult.invalidInput(coverageError);
            }

            if (replacingExistingPricing) {
                deleteExistingPricing(connection, input.getPerformanceId());
            }
            insertTiers(connection, input);
            insertAssignments(connection, input, performance.venueId);
            initializePerformanceInventory(
                    connection,
                    input.getPerformanceId(),
                    performance.venueId
            );
            PricingSetupSummary summary = new PricingSetupSummary(
                    input.getPerformanceId(),
                    input.getTiers().size(),
                    input.getAssignments().size()
            );
            String message = replacingExistingPricing
                    ? "Existing performance pricing replaced."
                    : "Performance pricing configured.";
            return OperationResult.success(message, summary);
        });
    }

    public OperationResult<Void> updateTierPrice(
            int performanceId,
            String tierCode,
            java.math.BigDecimal newPrice
    ) {
        if (performanceId <= 0) {
            return OperationResult.invalidInput("Performance ID must be positive.");
        }
        if (tierCode == null || tierCode.trim().isEmpty()) {
            return OperationResult.invalidInput("Tier code is required.");
        }
        if (newPrice == null || newPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return OperationResult.invalidInput("Tier price must be positive.");
        }

        return transactions.execute(connection -> {
            PerformanceDetails performance = findPerformance(connection, performanceId, true);
            if (performance == null) {
                return OperationResult.notFound("Performance not found.");
            }

            String tierSql = """
                    SELECT price
                    FROM PriceTier
                    WHERE performance_id = ? AND tier_code = ?
                    FOR UPDATE
                    """;
            try (PreparedStatement statement = connection.prepareStatement(tierSql)) {
                statement.setInt(1, performanceId);
                statement.setString(2, tierCode.trim());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Price tier not found for this performance.");
                    }
                }
            }

            boolean futureScheduled = "scheduled".equals(performance.status)
                    && performance.dateTime.isAfter(LocalDateTime.now(ZoneOffset.UTC));
            boolean ticketsExist = ticketsExist(connection, performanceId, tierCode.trim());
            String rejection = validateTierPriceUpdate(futureScheduled, ticketsExist);
            if (rejection != null) {
                return OperationResult.conflict(rejection);
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE PriceTier SET price = ? WHERE performance_id = ? AND tier_code = ?"
            )) {
                statement.setBigDecimal(1, newPrice);
                statement.setInt(2, performanceId);
                statement.setString(3, tierCode.trim());
                statement.executeUpdate();
            }
            return OperationResult.success("Tier price updated.");
        });
    }

    public static String validateTierPriceUpdate(boolean futureScheduled, boolean ticketsExist) {
        if (!futureScheduled) {
            return "A tier price can be changed only for a scheduled future performance.";
        }
        if (ticketsExist) {
            return "The tier price cannot be changed because a ticket has already been sold "
                    + "from this tier.";
        }
        return null;
    }

    public static String validateReplacement(
            int performanceId,
            boolean existingPricing,
            boolean futureScheduled,
            boolean ticketsExist
    ) {
        if (!existingPricing) {
            return null;
        }
        if (ticketsExist) {
            return "Pricing for performance " + performanceId
                    + " cannot be replaced because some tickets have already been sold. "
                    + "Existing tiers and section assignments were kept.";
        }
        if (!futureScheduled) {
            return "Pricing for performance " + performanceId
                    + " can be replaced only while the performance is scheduled in the future. "
                    + "Existing tiers and section assignments were kept.";
        }
        return null;
    }

    public static String validateCoverage(PricingSetupInput input, Set<String> venueSections) {
        Set<String> normalizedVenueSections = new HashSet<>();
        for (String section : venueSections) {
            normalizedVenueSections.add(normalize(section));
        }

        Set<String> suppliedSections = new HashSet<>();
        for (SectionTierInput assignment : input.getAssignments()) {
            suppliedSections.add(normalize(assignment.getSectionName()));
        }

        Set<String> missing = new LinkedHashSet<>(normalizedVenueSections);
        missing.removeAll(suppliedSections);
        if (!missing.isEmpty()) {
            return "Missing tier assignments for venue sections: " + String.join(", ", missing) + ".";
        }

        Set<String> unknown = new LinkedHashSet<>(suppliedSections);
        unknown.removeAll(normalizedVenueSections);
        if (!unknown.isEmpty()) {
            return "Assignments include sections outside the performance venue: "
                    + String.join(", ", unknown) + ".";
        }
        return null;
    }

    private PerformanceDetails findPerformance(
            Connection connection,
            int performanceId,
            boolean lock
    ) throws SQLException {
        String sql = """
                SELECT p.venue_id, p.date_time, p.status
                FROM Performance p
                WHERE p.performance_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new PerformanceDetails(
                        rows.getInt("venue_id"),
                        rows.getTimestamp("date_time").toLocalDateTime(),
                        rows.getString("status")
                );
            }
        }
    }

    private Set<String> loadVenueSections(Connection connection, int venueId) throws SQLException {
        String sql = "SELECT section_name FROM Section WHERE venue_id = ? ORDER BY section_name";
        Set<String> sections = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, venueId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    sections.add(rows.getString("section_name"));
                }
            }
        }
        return sections;
    }

    private String findReplacementConflict(Connection connection, int performanceId)
            throws SQLException {
        boolean existingPricing = pricingExists(connection, performanceId);
        if (!existingPricing) {
            return null;
        }
        return validateReplacement(
                performanceId,
                true,
                isFutureScheduledPerformance(connection, performanceId),
                ticketsExist(connection, performanceId)
        );
    }

    private boolean pricingExists(Connection connection, int performanceId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM PriceTier WHERE performance_id = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private boolean ticketsExist(Connection connection, int performanceId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM Tickets WHERE performance_id = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private boolean ticketsExist(Connection connection, int performanceId, String tierCode)
            throws SQLException {
        String sql = """
                SELECT ticket_id
                FROM Tickets
                WHERE performance_id = ? AND tier_code = ?
                ORDER BY ticket_id
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            statement.setString(2, tierCode);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean isFutureScheduledPerformance(Connection connection, int performanceId)
            throws SQLException {
        String sql = """
                SELECT status = 'scheduled' AND date_time > UTC_TIMESTAMP()
                FROM Performance
                WHERE performance_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        }
    }

    private void deleteExistingPricing(Connection connection, int performanceId)
            throws SQLException {
        try (PreparedStatement assignments = connection.prepareStatement(
                "DELETE FROM SectionTierAssignment WHERE performance_id = ?"
        )) {
            assignments.setInt(1, performanceId);
            assignments.executeUpdate();
        }
        try (PreparedStatement tiers = connection.prepareStatement(
                "DELETE FROM PriceTier WHERE performance_id = ?"
        )) {
            tiers.setInt(1, performanceId);
            tiers.executeUpdate();
        }
    }

    private void insertTiers(Connection connection, PricingSetupInput input) throws SQLException {
        String sql = "INSERT INTO PriceTier (performance_id, tier_code, price) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (TierInput tier : input.getTiers()) {
                statement.setInt(1, input.getPerformanceId());
                statement.setString(2, tier.getTierCode().trim());
                statement.setBigDecimal(3, tier.getPrice());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertAssignments(Connection connection, PricingSetupInput input, int venueId)
            throws SQLException {
        String sql = """
                INSERT INTO SectionTierAssignment
                    (performance_id, venue_id, section_name, tier_code)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (SectionTierInput assignment : input.getAssignments()) {
                statement.setInt(1, input.getPerformanceId());
                statement.setInt(2, venueId);
                statement.setString(3, assignment.getSectionName().trim());
                statement.setString(4, assignment.getTierCode().trim());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void initializePerformanceInventory(
            Connection connection,
            int performanceId,
            int venueId
    ) throws SQLException {
        String reservedSql = """
                INSERT INTO PerformanceSeats
                    (performance_id, venue_id, section_name, row_name, seat_number)
                SELECT ?, s.venue_id, s.section_name, s.row_name, s.seat_number
                FROM Seats s
                WHERE s.venue_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM PerformanceSeats ps
                      WHERE ps.performance_id = ?
                        AND ps.venue_id = s.venue_id
                        AND ps.section_name = s.section_name
                        AND ps.row_name = s.row_name
                        AND ps.seat_number = s.seat_number
                  )
                """;
        try (PreparedStatement statement = connection.prepareStatement(reservedSql)) {
            statement.setInt(1, performanceId);
            statement.setInt(2, venueId);
            statement.setInt(3, performanceId);
            statement.executeUpdate();
        }

        String generalSql = """
                INSERT INTO GeneralAdmissionCapacity
                    (performance_id, venue_id, section_name, total_capacity, remaining_capacity)
                SELECT ?, s.venue_id, s.section_name, s.standing_capacity, s.standing_capacity
                FROM Section s
                WHERE s.venue_id = ? AND s.section_type = 'general'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM GeneralAdmissionCapacity gac
                      WHERE gac.performance_id = ?
                        AND gac.venue_id = s.venue_id
                        AND gac.section_name = s.section_name
                  )
                """;
        try (PreparedStatement statement = connection.prepareStatement(generalSql)) {
            statement.setInt(1, performanceId);
            statement.setInt(2, venueId);
            statement.setInt(3, performanceId);
            statement.executeUpdate();
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class PerformanceDetails {
        private final int venueId;
        private final LocalDateTime dateTime;
        private final String status;

        private PerformanceDetails(
                int venueId,
                LocalDateTime dateTime,
                String status
        ) {
            this.venueId = venueId;
            this.dateTime = dateTime;
            this.status = status;
        }
    }
}
