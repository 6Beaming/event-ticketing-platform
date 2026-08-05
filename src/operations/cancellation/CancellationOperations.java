package operations.cancellation;

import common.OperationResult;
import database.JdbcSupport;
import database.TransactionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CancellationOperations {
    private final TransactionManager transactions;

    public CancellationOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<CancellationSummary> cancelCustomerTickets(
            int customerId,
            List<Integer> ticketIds,
            String reason
    ) {
        String validationError = validateCustomerCancellation(customerId, ticketIds);
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        List<Integer> sortedTicketIds = ticketIds.stream().sorted().toList();
        return transactions.execute(connection -> {
            if (!lockActiveCustomer(connection, customerId)) {
                return OperationResult.notFound("Active customer not found.");
            }

            List<TicketCancellationTarget> targets = new ArrayList<>();
            for (int ticketId : sortedTicketIds) {
                TicketCancellationTarget target = lockTicket(connection, ticketId);
                if (target == null) {
                    return OperationResult.notFound(
                            "Ticket " + ticketId + " was not found. No tickets were cancelled."
                    );
                }
                if (!"active".equals(target.ticketStatus)) {
                    return OperationResult.conflict(
                            "Ticket " + ticketId + " is already cancelled. No tickets were cancelled."
                    );
                }
                if (target.ownerCustomerId != customerId) {
                    return OperationResult.forbidden(
                            "Only the customer who currently owns ticket " + ticketId
                                    + " can cancel it. No tickets were cancelled."
                    );
                }
                if (!"scheduled".equals(target.performanceStatus)) {
                    return OperationResult.conflict(
                            "Ticket " + ticketId
                                    + " is not for a scheduled performance. No tickets were cancelled."
                    );
                }
                if (!target.customerDeadlineEligible) {
                    return OperationResult.conflict(
                            "Ticket " + ticketId
                                    + " cannot be cancelled fewer than seven days before the performance."
                    );
                }
                targets.add(target);
            }

            BigDecimal total = BigDecimal.ZERO;
            for (TicketCancellationTarget target : targets) {
                cancelTicket(connection, target, customerId, blankToNull(reason));
                total = total.add(target.refundAmount);
            }
            return OperationResult.success(
                    "Customer ticket cancellation completed with full refunds.",
                    new CancellationSummary(targets.size(), total)
            );
        });
    }

    public OperationResult<CancellationSummary> cancelPerformance(
            int organizerId,
            int performanceId,
            String reason
    ) {
        if (organizerId <= 0 || performanceId <= 0) {
            return OperationResult.invalidInput("Organizer and performance IDs must be positive.");
        }

        return transactions.execute(connection -> {
            String performanceSql = """
                    SELECT p.status, e.organizer_id
                    FROM Performance p
                    JOIN Event e ON e.event_id = p.event_id
                    WHERE p.performance_id = ?
                    FOR UPDATE
                    """;
            try (PreparedStatement statement = connection.prepareStatement(performanceSql)) {
                statement.setInt(1, performanceId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Performance not found.");
                    }
                    if (rows.getInt("organizer_id") != organizerId) {
                        return OperationResult.forbidden(
                                "Only the organizer who manages the event can cancel this performance."
                        );
                    }
                    if ("cancelled".equals(rows.getString("status"))) {
                        return OperationResult.conflict("Performance is already cancelled.");
                    }
                    if ("completed".equals(rows.getString("status"))) {
                        return OperationResult.conflict("A completed performance cannot be cancelled.");
                    }
                }
            }

            List<Integer> ticketIds = lockActivePerformanceTicketIds(connection, performanceId);
            List<TicketCancellationTarget> targets = new ArrayList<>();
            for (int ticketId : ticketIds) {
                TicketCancellationTarget target = lockTicket(connection, ticketId);
                if (target == null || target.ownerCustomerId <= 0) {
                    return OperationResult.conflict(
                            "An active ticket is missing current ownership. No changes were saved."
                    );
                }
                targets.add(target);
            }

            String cancellationReason = blankToNull(reason);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE Performance
                    SET status = 'cancelled', cancellation_date = UTC_TIMESTAMP(),
                        cancellation_reason = ?, cancelled_by_organizer_id = ?
                    WHERE performance_id = ?
                    """)) {
                statement.setString(1, cancellationReason);
                statement.setInt(2, organizerId);
                statement.setInt(3, performanceId);
                statement.executeUpdate();
            }

            BigDecimal total = BigDecimal.ZERO;
            for (TicketCancellationTarget target : targets) {
                cancelTicket(connection, target, organizerId, cancellationReason);
                total = total.add(target.refundAmount);
            }
            return OperationResult.success(
                    "Performance cancelled and every active ticket refunded.",
                    new CancellationSummary(targets.size(), total)
            );
        });
    }

    public static String validateCustomerCancellation(int customerId, List<Integer> ticketIds) {
        if (customerId <= 0) {
            return "Customer ID must be positive.";
        }
        if (ticketIds == null || ticketIds.isEmpty()) {
            return "At least one ticket ID is required.";
        }
        Set<Integer> unique = new HashSet<>();
        for (Integer ticketId : ticketIds) {
            if (ticketId == null || ticketId <= 0) {
                return "Ticket IDs must be positive.";
            }
            if (!unique.add(ticketId)) {
                return "A ticket can be requested only once per cancellation."
                        ;
            }
        }
        return null;
    }

    public static boolean meetsSevenDayDeadline(
            LocalDateTime currentDateTime,
            LocalDateTime performanceDateTime
    ) {
        return currentDateTime != null
                && performanceDateTime != null
                && !performanceDateTime.isBefore(currentDateTime.plusDays(7));
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

    private TicketCancellationTarget lockTicket(Connection connection, int ticketId)
            throws SQLException {
        String sql = """
                SELECT t.ticket_id, t.status AS ticket_status, t.performance_seats_ref,
                       t.general_seats_ref, t.face_value,
                       p.performance_id, p.status AS performance_status,
                       (p.date_time >= DATE_ADD(UTC_TIMESTAMP(), INTERVAL 7 DAY))
                           AS customer_deadline_eligible,
                       own.ownership_id, own.customer_id AS owner_customer_id,
                       own.acquired_listing_id,
                       COALESCE(acquired_listing.listing_price, t.face_value) AS refund_amount
                FROM Tickets t
                JOIN Performance p ON p.performance_id = t.performance_id
                LEFT JOIN TicketOwnership own
                  ON own.current_ticket_id = t.ticket_id
                LEFT JOIN Transactions acquired_transaction
                  ON acquired_transaction.transaction_id = own.acquired_transaction_id
                LEFT JOIN ResaleListing acquired_listing
                  ON acquired_listing.listing_id = own.acquired_listing_id
                WHERE t.ticket_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new TicketCancellationTarget(
                        rows.getInt("ticket_id"),
                        rows.getString("ticket_status"),
                        rows.getInt("performance_id"),
                        rows.getString("performance_status"),
                        rows.getBoolean("customer_deadline_eligible"),
                        nullableInt(rows, "performance_seats_ref"),
                        nullableInt(rows, "general_seats_ref"),
                        nullableLong(rows, "ownership_id"),
                        nullableInt(rows, "owner_customer_id") == null
                                ? 0 : rows.getInt("owner_customer_id"),
                        rows.getBigDecimal("refund_amount")
                );
            }
        }
    }

    private List<Integer> lockActivePerformanceTicketIds(
            Connection connection,
            int performanceId
    ) throws SQLException {
        String sql = """
                SELECT ticket_id
                FROM Tickets
                WHERE performance_id = ? AND status = 'active'
                ORDER BY ticket_id
                FOR UPDATE
                """;
        List<Integer> ticketIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ticketIds.add(rows.getInt("ticket_id"));
                }
            }
        }
        return ticketIds;
    }

    private void cancelTicket(
            Connection connection,
            TicketCancellationTarget target,
            int cancelledByUserId,
            String reason
    ) throws SQLException {
        Integer activeListingId = lockActiveListing(connection, target.ticketId);
        if (activeListingId != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE ResaleListing SET status = 'withdrawn' WHERE listing_id = ?"
            )) {
                statement.setInt(1, activeListingId);
                statement.executeUpdate();
            }
        }

        if (target.performanceSeatId != null) {
            lockReservedInventory(connection, target.performanceSeatId);
        } else if (target.generalCapacityId != null) {
            releaseGeneralInventory(connection, target.generalCapacityId, target.performanceId);
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE Tickets SET status = 'cancelled' WHERE ticket_id = ?"
        )) {
            statement.setInt(1, target.ticketId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE TicketOwnership SET ended_at = UTC_TIMESTAMP() WHERE ownership_id = ?"
        )) {
            statement.setLong(1, target.ownershipId);
            statement.executeUpdate();
        }

        long cancellationId;
        String cancellationSql = """
                INSERT INTO TicketCancellation
                    (ticket_id, ownership_id, cancelled_by_user_id, reason)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                cancellationSql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setInt(1, target.ticketId);
            statement.setLong(2, target.ownershipId);
            statement.setInt(3, cancelledByUserId);
            statement.setString(4, reason);
            statement.executeUpdate();
            cancellationId = JdbcSupport.requireGeneratedIntKey(statement, "ticket cancellation");
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO Refund (cancellation_id, amount)
                VALUES (?, ?)
                """)) {
            statement.setLong(1, cancellationId);
            statement.setBigDecimal(2, target.refundAmount);
            statement.executeUpdate();
        }
    }

    private Integer lockActiveListing(Connection connection, int ticketId) throws SQLException {
        String sql = """
                SELECT listing_id
                FROM ResaleListing
                WHERE active_ticket_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt("listing_id") : null;
            }
        }
    }

    private void lockReservedInventory(Connection connection, int performanceSeatId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT performance_seat_id
                FROM PerformanceSeats
                WHERE performance_seat_id = ?
                FOR UPDATE
                """)) {
            statement.setInt(1, performanceSeatId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Reserved inventory was not found");
                }
            }
        }
    }

    private void releaseGeneralInventory(
            Connection connection,
            int capacityId,
            int performanceId
    ) throws SQLException {
        int totalCapacity;
        int remainingCapacity;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT total_capacity, remaining_capacity
                FROM GeneralAdmissionCapacity
                WHERE ga_capacity_id = ? AND performance_id = ?
                FOR UPDATE
                """)) {
            statement.setInt(1, capacityId);
            statement.setInt(2, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("General-admission inventory was not found");
                }
                totalCapacity = rows.getInt("total_capacity");
                remainingCapacity = rows.getInt("remaining_capacity");
            }
        }
        if (remainingCapacity >= totalCapacity) {
            throw new SQLException("General-admission inventory is already fully released");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE GeneralAdmissionCapacity
                SET remaining_capacity = remaining_capacity + 1
                WHERE ga_capacity_id = ? AND performance_id = ?
                """)) {
            statement.setInt(1, capacityId);
            statement.setInt(2, performanceId);
            statement.executeUpdate();
        }
    }

    private Integer nullableInt(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static final class TicketCancellationTarget {
        private final int ticketId;
        private final String ticketStatus;
        private final int performanceId;
        private final String performanceStatus;
        private final boolean customerDeadlineEligible;
        private final Integer performanceSeatId;
        private final Integer generalCapacityId;
        private final long ownershipId;
        private final int ownerCustomerId;
        private final BigDecimal refundAmount;

        private TicketCancellationTarget(
                int ticketId,
                String ticketStatus,
                int performanceId,
                String performanceStatus,
                boolean customerDeadlineEligible,
                Integer performanceSeatId,
                Integer generalCapacityId,
                Long ownershipId,
                int ownerCustomerId,
                BigDecimal refundAmount
        ) {
            this.ticketId = ticketId;
            this.ticketStatus = ticketStatus;
            this.performanceId = performanceId;
            this.performanceStatus = performanceStatus;
            this.customerDeadlineEligible = customerDeadlineEligible;
            this.performanceSeatId = performanceSeatId;
            this.generalCapacityId = generalCapacityId;
            this.ownershipId = ownershipId == null ? 0 : ownershipId;
            this.ownerCustomerId = ownerCustomerId;
            this.refundAmount = refundAmount;
        }
    }
}
