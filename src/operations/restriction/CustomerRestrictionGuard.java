package operations.restriction;

import common.OperationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SQL-authoritative implementation of the PDF's R4 possible-scalper policy.
 */
public final class CustomerRestrictionGuard {
    private static final String CANDIDATE_SQL = """
            WITH purchases AS (
                SELECT own.customer_id,
                       v.city,
                       COUNT(DISTINCT own.ownership_id) AS num_purchased
                FROM TicketOwnership own
                JOIN Transactions tr
                  ON tr.transaction_id = own.acquired_transaction_id
                JOIN Tickets t
                  ON t.ticket_id = own.ticket_id
                JOIN Performance p
                  ON p.performance_id = t.performance_id
                JOIN Venue v
                  ON v.venue_id = p.venue_id
                WHERE tr.transaction_type IN ('purchase', 'resale')
                  AND tr.transaction_date >= ?
                GROUP BY own.customer_id, v.city
            ),
            listed AS (
                SELECT own.customer_id,
                       v.city,
                       COUNT(DISTINCT rl.ticket_id) AS num_listed
                FROM ResaleListing rl
                JOIN TicketOwnership own
                  ON own.ownership_id = rl.seller_ownership_id
                JOIN Tickets t
                  ON t.ticket_id = rl.ticket_id
                JOIN Performance p
                  ON p.performance_id = t.performance_id
                JOIN Venue v
                  ON v.venue_id = p.venue_id
                WHERE rl.listed_date >= ?
                GROUP BY own.customer_id, v.city
            )
            SELECT purchases.customer_id,
                   u.name,
                   purchases.city,
                   purchases.num_purchased,
                   COALESCE(listed.num_listed, 0) AS num_listed
            FROM purchases
            JOIN Users u
              ON u.user_id = purchases.customer_id
            LEFT JOIN listed
              ON listed.customer_id = purchases.customer_id
             AND listed.city = purchases.city
            WHERE purchases.num_purchased >= 10
              AND COALESCE(listed.num_listed, 0) * 2 > purchases.num_purchased
            """;

    public List<ScalperCandidate> findCandidates(
            Connection connection,
            LocalDateTime oneYearAgo
    ) throws SQLException {
        return findCandidates(connection, oneYearAgo, null);
    }

    public OperationResult<Void> checkCustomerAllowed(
            Connection connection,
            int customerId,
            LocalDateTime oneYearAgo
    ) throws SQLException {
        boolean qualifies = !findCandidates(connection, oneYearAgo, customerId).isEmpty();
        ActiveRestriction restriction = lockActiveRestriction(connection, customerId);

        if (qualifies && restriction == null) {
            insertPossibleScalperRestriction(connection, customerId);
            restriction = new ActiveRestriction("possible_scalper");
        } else if (!qualifies
                && restriction != null
                && "possible_scalper".equals(restriction.reason)) {
            closePossibleScalperRestriction(connection, customerId);
            restriction = null;
        }

        if (restriction != null) {
            return OperationResult.forbidden(
                    "Customer is prohibited from ticket purchases and new resale listings "
                            + "by an active " + restriction.reason + " restriction."
            );
        }
        return OperationResult.success("Customer is allowed to continue.");
    }

    public void refreshPossibleScalperRestrictions(
            Connection connection,
            List<ScalperCandidate> candidates
    ) throws SQLException {
        Set<Integer> customerIds = candidates.stream()
                .map(ScalperCandidate::getCustomerId)
                .collect(Collectors.toSet());

        closeCustomersNoLongerQualifying(connection, customerIds);
        for (Integer customerId : customerIds) {
            if (lockActiveRestriction(connection, customerId) == null) {
                insertPossibleScalperRestriction(connection, customerId);
            }
        }
    }

    private List<ScalperCandidate> findCandidates(
            Connection connection,
            LocalDateTime oneYearAgo,
            Integer customerId
    ) throws SQLException {
        String sql = CANDIDATE_SQL
                + (customerId == null ? "" : " AND purchases.customer_id = ?\n")
                + " ORDER BY purchases.city, num_listed DESC, purchases.customer_id";
        List<ScalperCandidate> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp cutoff = Timestamp.valueOf(oneYearAgo);
            statement.setTimestamp(1, cutoff);
            statement.setTimestamp(2, cutoff);
            if (customerId != null) {
                statement.setInt(3, customerId);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    candidates.add(new ScalperCandidate(
                            rows.getInt("customer_id"),
                            rows.getString("name"),
                            rows.getString("city"),
                            rows.getInt("num_purchased"),
                            rows.getInt("num_listed")
                    ));
                }
            }
        }
        return List.copyOf(candidates);
    }

    private ActiveRestriction lockActiveRestriction(Connection connection, int customerId)
            throws SQLException {
        String sql = """
                SELECT restriction_reason
                FROM CustomerRestriction
                WHERE current_customer_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? new ActiveRestriction(rows.getString("restriction_reason"))
                        : null;
            }
        }
    }

    private void insertPossibleScalperRestriction(Connection connection, int customerId)
            throws SQLException {
        String sql = """
                INSERT INTO CustomerRestriction
                    (customer_id, restriction_reason, details, started_at)
                VALUES (?, 'possible_scalper',
                        'Automatically flagged by the rolling one-year R4 SQL rule.',
                        UTC_TIMESTAMP())
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.executeUpdate();
        }
    }

    private void closePossibleScalperRestriction(Connection connection, int customerId)
            throws SQLException {
        String sql = """
                UPDATE CustomerRestriction
                SET ended_at = UTC_TIMESTAMP()
                WHERE current_customer_id = ?
                  AND restriction_reason = 'possible_scalper'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.executeUpdate();
        }
    }

    private void closeCustomersNoLongerQualifying(
            Connection connection,
            Set<Integer> qualifyingCustomerIds
    ) throws SQLException {
        String placeholders = String.join(
                ",",
                Collections.nCopies(qualifyingCustomerIds.size(), "?")
        );
        String sql = """
                UPDATE CustomerRestriction
                SET ended_at = UTC_TIMESTAMP()
                WHERE ended_at IS NULL
                  AND restriction_reason = 'possible_scalper'
                """ + (qualifyingCustomerIds.isEmpty()
                ? ""
                : " AND customer_id NOT IN (" + placeholders + ")");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (Integer customerId : qualifyingCustomerIds) {
                statement.setInt(parameter++, customerId);
            }
            statement.executeUpdate();
        }
    }

    private static final class ActiveRestriction {
        private final String reason;

        private ActiveRestriction(String reason) {
            this.reason = reason;
        }
    }
}
