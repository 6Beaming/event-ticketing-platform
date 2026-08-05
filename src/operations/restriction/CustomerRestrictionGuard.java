package operations.restriction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class CustomerRestrictionGuard {
    private CustomerRestrictionGuard() {
    }

    public static String findCurrentRestriction(Connection connection, int customerId)
            throws SQLException {
        String existing = findExistingRestriction(connection, customerId);
        if (existing != null) {
            return existing;
        }

        ScalperEvidence evidence = findScalperEvidence(connection, customerId);
        if (evidence == null) {
            return null;
        }
        String details = "Possible scalper threshold met in " + evidence.city
                + ": purchased " + evidence.purchasedCount
                + " tickets and listed " + evidence.listedCount
                + " within the rolling year.";
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO CustomerRestriction
                    (customer_id, restriction_reason, details)
                VALUES (?, 'possible_scalper', ?)
                """)) {
            statement.setInt(1, customerId);
            statement.setString(2, details);
            statement.executeUpdate();
        }
        return "Customer actions are prohibited by an active possible scalper restriction. "
                + details;
    }

    public static boolean qualifiesAsPossibleScalper(int purchasedCount, int listedCount) {
        return purchasedCount >= 10 && listedCount * 2 > purchasedCount;
    }

    public static String findExistingRestriction(Connection connection, int customerId)
            throws SQLException {
        String sql = """
                SELECT restriction_reason, details
                FROM CustomerRestriction
                WHERE customer_id = ? AND ended_at IS NULL
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                String reason = rows.getString("restriction_reason");
                String details = rows.getString("details");
                String message = "Customer actions are prohibited by an active "
                        + reason.replace('_', ' ') + " restriction.";
                return details == null || details.isBlank()
                        ? message
                        : message + " " + details;
            }
        }
    }

    private static ScalperEvidence findScalperEvidence(Connection connection, int customerId)
            throws SQLException {
        String sql = """
                SELECT purchases.city, purchases.purchased_count,
                       COALESCE(listings.listed_count, 0) AS listed_count
                FROM (
                    SELECT v.city, COUNT(DISTINCT t.ticket_id) AS purchased_count
                    FROM Transactions tr
                    JOIN Tickets t ON t.purchase_id = tr.transaction_id
                    JOIN Performance p ON p.performance_id = t.performance_id
                    JOIN Venue v ON v.venue_id = p.venue_id
                    WHERE tr.customer_id = ?
                      AND tr.transaction_type = 'purchase'
                      AND tr.transaction_date >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 YEAR)
                    GROUP BY v.city
                ) purchases
                LEFT JOIN (
                    SELECT v.city, COUNT(DISTINCT rl.ticket_id) AS listed_count
                    FROM ResaleListing rl
                    JOIN TicketOwnership seller
                      ON seller.ownership_id = rl.seller_ownership_id
                    JOIN Tickets t ON t.ticket_id = rl.ticket_id
                    JOIN Performance p ON p.performance_id = t.performance_id
                    JOIN Venue v ON v.venue_id = p.venue_id
                    WHERE seller.customer_id = ?
                      AND rl.listed_date >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 YEAR)
                    GROUP BY v.city
                ) listings ON listings.city = purchases.city
                WHERE purchases.purchased_count >= 10
                  AND COALESCE(listings.listed_count, 0) * 2 > purchases.purchased_count
                ORDER BY purchases.city
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setInt(2, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ScalperEvidence(
                        rows.getString("city"),
                        rows.getInt("purchased_count"),
                        rows.getInt("listed_count")
                );
            }
        }
    }

    private static final class ScalperEvidence {
        private final String city;
        private final int purchasedCount;
        private final int listedCount;

        private ScalperEvidence(String city, int purchasedCount, int listedCount) {
            this.city = city;
            this.purchasedCount = purchasedCount;
            this.listedCount = listedCount;
        }
    }
}
