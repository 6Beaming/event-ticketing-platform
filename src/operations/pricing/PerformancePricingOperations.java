package operations.pricing;

import common.OperationResult;
import database.TransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    public OperationResult<PricingSetupSummary> configurePricing(PricingSetupInput input) {
        String validationError = PricingValidator.validateShape(input);
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        return transactions.execute(connection -> {
            Integer venueId = lockPerformanceVenue(connection, input.getPerformanceId());
            if (venueId == null) {
                return OperationResult.notFound("Performance not found.");
            }

            Set<String> venueSections = loadVenueSections(connection, venueId);
            String coverageError = validateCoverage(input, venueSections);
            if (coverageError != null) {
                return OperationResult.invalidInput(coverageError);
            }

            insertTiers(connection, input);
            insertAssignments(connection, input, venueId);
            PricingSetupSummary summary = new PricingSetupSummary(
                    input.getPerformanceId(),
                    input.getTiers().size(),
                    input.getAssignments().size()
            );
            return OperationResult.success("Performance pricing configured.", summary);
        });
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

    private Integer lockPerformanceVenue(Connection connection, int performanceId)
            throws SQLException {
        String sql = "SELECT venue_id FROM Performance WHERE performance_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt("venue_id") : null;
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

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
