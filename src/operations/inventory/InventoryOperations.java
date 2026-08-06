package operations.inventory;

import common.OperationResult;
import database.TransactionManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class InventoryOperations {
    private final TransactionManager transactions;

    public InventoryOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<List<ReservedSeatAvailability>> getReservedInventory(int performanceId) {
        if (performanceId <= 0) {
            return OperationResult.invalidInput("Performance ID must be positive.");
        }

        return transactions.execute(connection -> {
            OperationResult<Void> saleable = checkSaleablePerformance(connection, performanceId);
            if (!saleable.isSuccess()) {
                return copyFailure(saleable);
            }

            String sql = """
                    SELECT ps.performance_seat_id, ps.section_name, ps.row_name,
                           ps.seat_number, ps.blocked_status, sta.tier_code, pt.price,
                           t.ticket_id
                    FROM PerformanceSeats ps
                    JOIN SectionTierAssignment sta
                      ON sta.performance_id = ps.performance_id
                     AND sta.venue_id = ps.venue_id
                     AND sta.section_name = ps.section_name
                    JOIN PriceTier pt
                      ON pt.performance_id = sta.performance_id
                     AND pt.tier_code = sta.tier_code
                    LEFT JOIN Tickets t
                      ON t.active_reserved_seat_ref = ps.performance_seat_id
                    WHERE ps.performance_id = ?
                    ORDER BY ps.section_name, ps.row_name, ps.seat_number
                    """;
            List<ReservedSeatAvailability> seats = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, performanceId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        seats.add(new ReservedSeatAvailability(
                                rows.getInt("performance_seat_id"),
                                rows.getString("section_name"),
                                rows.getString("row_name"),
                                rows.getInt("seat_number"),
                                rows.getString("tier_code"),
                                rows.getBigDecimal("price"),
                                InventoryMath.reservedState(
                                        rows.getBoolean("blocked_status"),
                                        rows.getObject("ticket_id") != null
                                )
                        ));
                    }
                }
            }
            return OperationResult.success("Reserved inventory loaded.", List.copyOf(seats));
        });
    }

    public OperationResult<List<GeneralAdmissionAvailability>> getGeneralAdmissionInventory(
            int performanceId
    ) {
        if (performanceId <= 0) {
            return OperationResult.invalidInput("Performance ID must be positive.");
        }

        return transactions.execute(connection -> {
            OperationResult<Void> saleable = checkSaleablePerformance(connection, performanceId);
            if (!saleable.isSuccess()) {
                return copyFailure(saleable);
            }

            String sql = """
                    SELECT gac.ga_capacity_id, gac.section_name, gac.total_capacity,
                           gac.remaining_capacity, sta.tier_code, pt.price
                    FROM GeneralAdmissionCapacity gac
                    JOIN SectionTierAssignment sta
                      ON sta.performance_id = gac.performance_id
                     AND sta.venue_id = gac.venue_id
                     AND sta.section_name = gac.section_name
                    JOIN PriceTier pt
                      ON pt.performance_id = sta.performance_id
                     AND pt.tier_code = sta.tier_code
                    WHERE gac.performance_id = ?
                    ORDER BY gac.section_name
                    """;
            List<GeneralAdmissionAvailability> sections = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, performanceId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        int total = rows.getInt("total_capacity");
                        int remaining = rows.getInt("remaining_capacity");
                        sections.add(new GeneralAdmissionAvailability(
                                rows.getInt("ga_capacity_id"),
                                rows.getString("section_name"),
                                rows.getString("tier_code"),
                                rows.getBigDecimal("price"),
                                total,
                                InventoryMath.soldQuantity(total, remaining),
                                remaining
                        ));
                    }
                }
            }
            return OperationResult.success("General-admission inventory loaded.", List.copyOf(sections));
        });
    }

    public OperationResult<Void> blockSeat(
            int performanceId,
            int performanceSeatId
    ) {
        return changeSeatBlock(performanceId, performanceSeatId, true);
    }

    public OperationResult<Void> unblockSeat(
            int performanceId,
            int performanceSeatId
    ) {
        return changeSeatBlock(performanceId, performanceSeatId, false);
    }

    private OperationResult<Void> changeSeatBlock(
            int performanceId,
            int performanceSeatId,
            boolean targetBlocked
    ) {
        if (performanceId <= 0 || performanceSeatId <= 0) {
            return OperationResult.invalidInput(
                    "Performance and seat IDs must be positive."
            );
        }

        return transactions.execute(connection -> {
            String sql = """
                    SELECT ps.blocked_status, p.status, p.date_time
                    FROM PerformanceSeats ps
                    JOIN Performance p ON p.performance_id = ps.performance_id
                    WHERE ps.performance_id = ? AND ps.performance_seat_id = ?
                    FOR UPDATE
                    """;
            boolean currentlyBlocked;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, performanceId);
                statement.setInt(2, performanceSeatId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound(
                                "Reserved seat inventory was not found for this performance."
                        );
                    }
                    if (!"scheduled".equals(rows.getString("status"))
                            || !rows.getTimestamp("date_time").toLocalDateTime()
                            .isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                        return OperationResult.conflict(
                                "Seats can be blocked only for a scheduled future performance."
                        );
                    }
                    currentlyBlocked = rows.getBoolean("blocked_status");
                }
            }

            boolean sold = activeTicketExists(connection, performanceSeatId);
            String rejection = validateSeatChange(currentlyBlocked, sold, targetBlocked);
            if (rejection != null) {
                return OperationResult.conflict(rejection);
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE PerformanceSeats SET blocked_status = ? "
                            + "WHERE performance_id = ? AND performance_seat_id = ?"
            )) {
                statement.setBoolean(1, targetBlocked);
                statement.setInt(2, performanceId);
                statement.setInt(3, performanceSeatId);
                statement.executeUpdate();
            }
            return OperationResult.success(
                    targetBlocked ? "Seat blocked." : "Seat unblocked."
            );
        });
    }

    public static String validateSeatChange(
            boolean currentlyBlocked,
            boolean sold,
            boolean targetBlocked
    ) {
        if (sold) {
            return targetBlocked
                    ? "A sold seat cannot be blocked; it can be freed only through cancellation."
                    : "A sold seat cannot be unblocked; it can be freed only through cancellation.";
        }
        if (targetBlocked && currentlyBlocked) {
            return "The seat is already blocked.";
        }
        if (!targetBlocked && !currentlyBlocked) {
            return "The seat is not blocked.";
        }
        return null;
    }

    private boolean activeTicketExists(java.sql.Connection connection, int performanceSeatId)
            throws SQLException {
        String sql = "SELECT 1 FROM Tickets WHERE active_reserved_seat_ref = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceSeatId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private OperationResult<Void> checkSaleablePerformance(
            java.sql.Connection connection,
            int performanceId
    ) throws SQLException {
        String sql = "SELECT status, date_time FROM Performance WHERE performance_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return OperationResult.notFound("Performance not found.");
                }
                if (!"scheduled".equals(rows.getString("status"))) {
                    return OperationResult.conflict("Only scheduled performances have saleable inventory.");
                }
                Timestamp dateTime = rows.getTimestamp("date_time");
                if (dateTime.toLocalDateTime().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                    return OperationResult.conflict("Past performances do not have saleable inventory.");
                }
                return OperationResult.success("Performance is saleable.");
            }
        }
    }

    private <T> OperationResult<T> copyFailure(OperationResult<?> result) {
        return switch (result.getStatus()) {
            case INVALID_INPUT -> OperationResult.invalidInput(result.getMessage());
            case NOT_FOUND -> OperationResult.notFound(result.getMessage());
            case FORBIDDEN -> OperationResult.forbidden(result.getMessage());
            case CONFLICT -> OperationResult.conflict(result.getMessage());
            case DATABASE_FAILURE -> OperationResult.databaseFailure(result.getMessage());
            case SUCCESS -> throw new IllegalArgumentException("Cannot copy a successful result as failure");
        };
    }
}
