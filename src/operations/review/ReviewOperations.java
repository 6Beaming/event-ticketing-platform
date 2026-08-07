package operations.review;

import common.OperationResult;
import database.TransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ReviewOperations {
    public static final int RECENT_ATTENDANCE_DAYS = 365;

    private final TransactionManager transactions;

    public ReviewOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<List<Integer>> getReviewablePerformanceIds(int customerId) {
        if (customerId <= 0) {
            return OperationResult.invalidInput("Customer ID must be positive.");
        }

        return transactions.execute(connection -> {
            if (!activeCustomerExists(connection, customerId, false)) {
                return OperationResult.notFound("Active customer not found.");
            }

            String sql = """
                    SELECT DISTINCT p.performance_id, p.date_time
                    FROM Performance p
                    JOIN Tickets t ON t.performance_id = p.performance_id
                    JOIN TicketOwnership own ON own.ticket_id = t.ticket_id
                    WHERE own.customer_id = ?
                      AND p.status = 'completed'
                      AND p.date_time < UTC_TIMESTAMP()
                      AND p.date_time >= DATE_SUB(
                          UTC_TIMESTAMP(), INTERVAL 365 DAY
                      )
                      AND t.status = 'active'
                      AND own.acquired_at <= p.date_time
                      AND (own.ended_at IS NULL OR own.ended_at >= p.date_time)
                      AND NOT EXISTS (
                          SELECT 1
                          FROM Reviews r
                          WHERE r.customer_id = own.customer_id
                            AND r.performance_id = p.performance_id
                      )
                    ORDER BY p.date_time DESC, p.performance_id
                    """;
            List<Integer> performanceIds = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        performanceIds.add(rows.getInt("performance_id"));
                    }
                }
            }
            return OperationResult.success(
                    "Performances available for review retrieved.",
                    performanceIds
            );
        });
    }

    public OperationResult<List<CustomerReview>> getCustomerReviews(int customerId) {
        if (customerId <= 0) {
            return OperationResult.invalidInput("Customer ID must be positive.");
        }

        return transactions.execute(connection -> {
            if (!activeCustomerExists(connection, customerId, false)) {
                return OperationResult.notFound("Active customer not found.");
            }

            String sql = """
                    SELECT performance_id, event_rating, venue_rating,
                           comment_text, review_date
                    FROM Reviews
                    WHERE customer_id = ?
                    ORDER BY review_date DESC, performance_id
                    """;
            List<CustomerReview> customerReviews = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        customerReviews.add(new CustomerReview(
                                rows.getInt("performance_id"),
                                rows.getInt("event_rating"),
                                rows.getInt("venue_rating"),
                                rows.getString("comment_text"),
                                rows.getTimestamp("review_date").toLocalDateTime()
                        ));
                    }
                }
            }
            return OperationResult.success("Customer reviews retrieved.", customerReviews);
        });
    }

    public OperationResult<Void> checkReviewEligibility(int customerId, int performanceId) {
        if (customerId <= 0 || performanceId <= 0) {
            return OperationResult.invalidInput("Customer and performance IDs must be positive.");
        }

        return transactions.execute(
                connection -> checkReviewEligibility(
                        connection,
                        customerId,
                        performanceId,
                        false
                )
        );
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
            OperationResult<Void> eligibility = checkReviewEligibility(
                    connection,
                    customerId,
                    performanceId,
                    true
            );
            if (!eligibility.isSuccess()) {
                return eligibility;
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

    private OperationResult<Void> checkReviewEligibility(
            Connection connection,
            int customerId,
            int performanceId,
            boolean lockRows
    ) throws SQLException {
        if (!activeCustomerExists(connection, customerId, lockRows)) {
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
                """ + (lockRows ? "FOR UPDATE" : "");
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

        if (reviewExists(connection, customerId, performanceId, lockRows)) {
            return OperationResult.conflict(
                    "This customer has already left a review for this performance."
            );
        }
        if (!heldActiveTicketAtPerformance(
                connection,
                customerId,
                performanceId,
                lockRows
        )) {
            return OperationResult.forbidden(
                    "The customer did not hold a non-cancelled ticket when this performance occurred."
            );
        }
        return OperationResult.success("Customer can review this performance.");
    }

    private boolean activeCustomerExists(
            Connection connection,
            int customerId,
            boolean lockRow
    ) throws SQLException {
        String sql = """
                SELECT c.user_id
                FROM Customer c
                JOIN Users u ON u.user_id = c.user_id
                WHERE c.user_id = ? AND u.account_status = 'active'
                """ + (lockRow ? "FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean reviewExists(
            Connection connection,
            int customerId,
            int performanceId,
            boolean lockRow
    ) throws SQLException {
        String sql = """
                SELECT 1
                FROM Reviews
                WHERE customer_id = ? AND performance_id = ?
                """ + (lockRow ? "FOR UPDATE" : "");
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
            int performanceId,
            boolean lockRow
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
                """ + (lockRow ? "FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            statement.setInt(2, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
