package operations.review;

import common.OperationResult;
import database.TransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ReviewOperations {
    public static final int RECENT_ATTENDANCE_DAYS = 365;

    private final TransactionManager transactions;

    public ReviewOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<Void> submitReview(
            int customerId,
            int performanceId,
            int eventRating,
            int venueRating,
            String comment
    ) {
        String validationError = validateReview(
                customerId,
                performanceId,
                eventRating,
                venueRating,
                comment
        );
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        return transactions.execute(connection -> {
            if (!lockActiveCustomer(connection, customerId)) {
                return OperationResult.notFound("Active customer not found.");
            }

            String performanceSql = """
                    SELECT status,
                           date_time < UTC_TIMESTAMP() AS has_occurred,
                           date_time >= DATE_SUB(
                               UTC_TIMESTAMP(), INTERVAL 365 DAY
                           ) AS is_recent
                    FROM Performance
                    WHERE performance_id = ?
                    FOR UPDATE
                    """;
            try (PreparedStatement statement = connection.prepareStatement(performanceSql)) {
                statement.setInt(1, performanceId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Performance not found.");
                    }
                    if (!"completed".equals(rows.getString("status"))
                            || !rows.getBoolean("has_occurred")) {
                        return OperationResult.conflict(
                                "Reviews can be submitted only after a completed performance."
                        );
                    }
                    if (!rows.getBoolean("is_recent")) {
                        return OperationResult.conflict(
                                "The performance is outside the documented one-year review window."
                        );
                    }
                }
            }

            if (reviewExists(connection, customerId, performanceId)) {
                return OperationResult.conflict(
                        "A customer can review an attended performance only once."
                );
            }
            if (!heldActiveTicketAtPerformance(connection, customerId, performanceId)) {
                return OperationResult.forbidden(
                        "The customer did not hold a non-cancelled ticket when this performance occurred."
                );
            }

            String insertSql = """
                    INSERT INTO Reviews
                        (customer_id, performance_id, comment_text,
                         event_rating, venue_rating)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                statement.setInt(1, customerId);
                statement.setInt(2, performanceId);
                statement.setString(3, comment.trim());
                statement.setInt(4, eventRating);
                statement.setInt(5, venueRating);
                statement.executeUpdate();
            }
            return OperationResult.success("Attendance review submitted.");
        });
    }

    public static String validateReview(
            int customerId,
            int performanceId,
            int eventRating,
            int venueRating,
            String comment
    ) {
        if (customerId <= 0 || performanceId <= 0) {
            return "Customer and performance IDs must be positive.";
        }
        if (eventRating < 1 || eventRating > 5 || venueRating < 1 || venueRating > 5) {
            return "Event and venue ratings must each be from 1 to 5.";
        }
        if (comment == null || comment.trim().isEmpty()) {
            return "Review comment is required.";
        }
        return null;
    }

    private boolean lockActiveCustomer(Connection connection, int customerId) throws SQLException {
        String sql = """
                SELECT c.user_id
                FROM Customer c
                JOIN Users u ON u.user_id = c.user_id
                WHERE c.user_id = ? AND u.account_status = 'active'
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean reviewExists(Connection connection, int customerId, int performanceId)
            throws SQLException {
        String sql = """
                SELECT 1
                FROM Reviews
                WHERE customer_id = ? AND performance_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setInt(2, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean heldActiveTicketAtPerformance(
            Connection connection,
            int customerId,
            int performanceId
    ) throws SQLException {
        String sql = """
                SELECT own.ownership_id
                FROM Tickets t
                JOIN Performance p ON p.performance_id = t.performance_id
                JOIN TicketOwnership own ON own.ticket_id = t.ticket_id
                WHERE t.performance_id = ?
                  AND t.status = 'active'
                  AND own.customer_id = ?
                  AND own.acquired_at <= p.date_time
                  AND (own.ended_at IS NULL OR own.ended_at >= p.date_time)
                ORDER BY own.ownership_id
                LIMIT 1
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            statement.setInt(2, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
