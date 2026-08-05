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
}
