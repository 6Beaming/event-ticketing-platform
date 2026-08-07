package operations.validation;

import common.OperationResult;
import database.TransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Read-only existence and relationship checks used while collecting terminal input.
 * The operation itself must still repeat its transactional checks before changing data.
 */
public final class OperationInputChecks {
    private final TransactionManager transactions;

    public OperationInputChecks(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<Void> checkActiveCustomer(int customerId) {
        if (customerId <= 0) {
            return OperationResult.invalidInput("Customer ID must be positive.");
        }
        return transactions.execute(connection -> {
            String sql = """
                    SELECT 1
                    FROM Customer c
                    JOIN Users u ON u.user_id = c.user_id
                    WHERE c.user_id = ? AND u.account_status = 'active'
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? OperationResult.success("Active customer found.")
                            : OperationResult.notFound("Active customer not found.");
                }
            }
        });
    }

    public OperationResult<Void> checkCustomer(int customerId) {
        if (customerId <= 0) {
            return OperationResult.invalidInput("Customer ID must be positive.");
        }
        return transactions.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM Customer WHERE user_id = ?"
            )) {
                statement.setInt(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? OperationResult.success("Customer found.")
                            : OperationResult.notFound("Customer not found.");
                }
            }
        });
    }

    public OperationResult<Void> checkOrganizer(int organizerId) {
        if (organizerId <= 0) {
            return OperationResult.invalidInput("Organizer ID must be positive.");
        }
        return transactions.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM Organizer WHERE user_id = ?"
            )) {
                statement.setInt(1, organizerId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? OperationResult.success("Organizer found.")
                            : OperationResult.notFound("Organizer not found.");
                }
            }
        });
    }

    public OperationResult<Void> checkActiveUser(int userId) {
        if (userId <= 0) {
            return OperationResult.invalidInput("User ID must be positive.");
        }
        return transactions.execute(connection -> {
            String sql = "SELECT 1 FROM Users WHERE user_id = ? AND account_status = 'active'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? OperationResult.success("Active user found.")
                            : OperationResult.notFound("Active user not found.");
                }
            }
        });
    }

    public OperationResult<Void> checkPerformance(int performanceId) {
        return checkRecord(
                performanceId,
                "SELECT 1 FROM Performance WHERE performance_id = ?",
                "Performance found.",
                "Performance not found."
        );
    }

    public OperationResult<Void> checkOwnedPerformance(int organizerId, int performanceId) {
        if (organizerId <= 0 || performanceId <= 0) {
            return OperationResult.invalidInput("Organizer and performance IDs must be positive.");
        }
        return transactions.execute(connection -> {
            String sql = """
                    SELECT e.organizer_id
                    FROM Performance p
                    JOIN Event e ON e.event_id = p.event_id
                    WHERE p.performance_id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, performanceId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Performance not found.");
                    }
                    return rows.getInt("organizer_id") == organizerId
                            ? OperationResult.success("Organizer manages the performance.")
                            : OperationResult.forbidden(
                                    "Only the event organizer can manage this performance."
                            );
                }
            }
        });
    }

    public OperationResult<Void> checkTicket(int ticketId) {
        return checkRecord(
                ticketId,
                "SELECT 1 FROM Tickets WHERE ticket_id = ?",
                "Ticket found.",
                "Ticket not found."
        );
    }

    public OperationResult<Void> checkTicketForResale(int sellerId, int ticketId) {
        if (sellerId <= 0 || ticketId <= 0) {
            return OperationResult.invalidInput("Seller and ticket IDs must be positive.");
        }
        return transactions.execute(connection -> {
            String sql = """
                    SELECT t.status AS ticket_status,
                           p.status AS performance_status,
                           (p.date_time > UTC_TIMESTAMP()) AS future_performance,
                           own.ownership_id,
                           own.customer_id AS owner_customer_id,
                           EXISTS (
                               SELECT 1
                               FROM ResaleListing rl
                               WHERE rl.active_ticket_id = t.ticket_id
                           ) AS active_listing_exists
                    FROM Tickets t
                    JOIN Performance p ON p.performance_id = t.performance_id
                    LEFT JOIN TicketOwnership own ON own.current_ticket_id = t.ticket_id
                    WHERE t.ticket_id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, ticketId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Ticket not found.");
                    }
                    if (!"active".equals(rows.getString("ticket_status"))) {
                        return OperationResult.conflict(
                                "Only an active ticket can be listed for resale."
                        );
                    }
                    if (!"scheduled".equals(rows.getString("performance_status"))
                            || !rows.getBoolean("future_performance")) {
                        return OperationResult.conflict(
                                "Only a ticket for a scheduled future performance can be listed."
                        );
                    }
                    long ownershipId = rows.getLong("ownership_id");
                    if (rows.wasNull() || ownershipId <= 0) {
                        return OperationResult.conflict(
                                "Ticket has no current ownership record."
                        );
                    }
                    if (rows.getInt("owner_customer_id") != sellerId) {
                        return OperationResult.forbidden(
                                "Only the ticket's current owner can list it for resale."
                        );
                    }
                    if (rows.getBoolean("active_listing_exists")) {
                        return OperationResult.conflict(
                                "Ticket already has an active resale listing."
                        );
                    }
                    return OperationResult.success("Ticket can be listed for resale.");
                }
            }
        });
    }

    public OperationResult<Void> checkResaleListingPrice(
            int ticketId,
            BigDecimal listingPrice
    ) {
        if (ticketId <= 0) {
            return OperationResult.invalidInput("Ticket ID must be positive.");
        }
        if (listingPrice == null || listingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return OperationResult.invalidInput("Listing price must be positive.");
        }
        return transactions.execute(connection -> {
            String sql = """
                    SELECT t.face_value, e.resale_cap_pct
                    FROM Tickets t
                    JOIN Performance p ON p.performance_id = t.performance_id
                    JOIN Event e ON e.event_id = p.event_id
                    WHERE t.ticket_id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, ticketId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Ticket not found.");
                    }
                    BigDecimal capPrice = rows.getBigDecimal("face_value")
                            .multiply(rows.getBigDecimal("resale_cap_pct"))
                            .setScale(2, RoundingMode.HALF_UP);
                    if (listingPrice.compareTo(capPrice) > 0) {
                        return OperationResult.conflict(
                                "Listing price exceeds the event cap of $"
                                        + capPrice.toPlainString() + "."
                        );
                    }
                    return OperationResult.success("Listing price is within the event cap.");
                }
            }
        });
    }

    public OperationResult<Void> checkTickets(List<Integer> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return OperationResult.invalidInput("At least one ticket ID is required.");
        }
        return transactions.execute(connection -> {
            String sql = "SELECT 1 FROM Tickets WHERE ticket_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Integer ticketId : ticketIds) {
                    if (ticketId == null || ticketId <= 0) {
                        return OperationResult.invalidInput("Ticket IDs must be positive.");
                    }
                    statement.setInt(1, ticketId);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) {
                            return OperationResult.notFound("Ticket " + ticketId + " not found.");
                        }
                    }
                }
            }
            return OperationResult.success("Tickets found.");
        });
    }

    public OperationResult<Void> checkActiveResaleTicket(int ticketId) {
        return checkRecord(
                ticketId,
                "SELECT 1 FROM ResaleListing WHERE active_ticket_id = ?",
                "Active resale listing found for ticket.",
                "Ticket does not have an active resale listing."
        );
    }

    public OperationResult<Void> checkResaleListing(int listingId) {
        return checkRecord(
                listingId,
                "SELECT 1 FROM ResaleListing WHERE listing_id = ?",
                "Resale listing found.",
                "Resale listing not found."
        );
    }

    public OperationResult<Void> checkTier(int performanceId, String tierCode) {
        if (performanceId <= 0 || tierCode == null || tierCode.isBlank()) {
            return OperationResult.invalidInput("Performance ID and tier code are required.");
        }
        return transactions.execute(connection -> {
            String sql = """
                    SELECT 1
                    FROM PriceTier
                    WHERE performance_id = ? AND tier_code = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, performanceId);
                statement.setString(2, tierCode.trim());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? OperationResult.success("Tier found.")
                            : OperationResult.notFound(
                                    "Tier not found for the selected performance."
                            );
                }
            }
        });
    }

    public OperationResult<Void> checkReservedSeat(
            int performanceId,
            int performanceSeatId
    ) {
        if (performanceId <= 0 || performanceSeatId <= 0) {
            return OperationResult.invalidInput(
                    "Performance and reserved-seat inventory IDs must be positive."
            );
        }
        return transactions.execute(connection -> {
            String sql = """
                    SELECT 1
                    FROM PerformanceSeats
                    WHERE performance_id = ? AND performance_seat_id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, performanceId);
                statement.setInt(2, performanceSeatId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? OperationResult.success("Reserved seat found.")
                            : OperationResult.notFound(
                                    "Reserved seat not found for the selected performance."
                            );
                }
            }
        });
    }

    public OperationResult<Void> checkReservedSeats(
            int performanceId,
            List<Integer> performanceSeatIds
    ) {
        if (performanceSeatIds == null || performanceSeatIds.isEmpty()) {
            return OperationResult.invalidInput("At least one reserved-seat ID is required.");
        }
        for (Integer performanceSeatId : performanceSeatIds) {
            OperationResult<Void> result = checkReservedSeat(performanceId, performanceSeatId);
            if (!result.isSuccess()) {
                return result;
            }
        }
        return OperationResult.success("Reserved seats found.");
    }

    public OperationResult<Void> checkGeneralAdmissionSection(
            int performanceId,
            String sectionName
    ) {
        if (performanceId <= 0 || sectionName == null || sectionName.isBlank()) {
            return OperationResult.invalidInput(
                    "Performance ID and general-admission section are required."
            );
        }
        return transactions.execute(connection -> {
            String sql = """
                    SELECT 1
                    FROM GeneralAdmissionCapacity
                    WHERE performance_id = ? AND section_name = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, performanceId);
                statement.setString(2, sectionName.trim());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? OperationResult.success("General-admission section found.")
                            : OperationResult.notFound(
                                    "General-admission section not found for the selected performance."
                            );
                }
            }
        });
    }

    private OperationResult<Void> checkRecord(
            int id,
            String sql,
            String successMessage,
            String notFoundMessage
    ) {
        if (id <= 0) {
            return OperationResult.invalidInput("ID must be positive.");
        }
        return transactions.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? OperationResult.success(successMessage)
                            : OperationResult.notFound(notFoundMessage);
                }
            }
        });
    }
}
