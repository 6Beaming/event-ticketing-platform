package operations.inventory;

import common.OperationResult;
import database.TransactionManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class InventoryOperations {
    private static final String SEAT_BLOCKING_PERFORMANCE_CONFLICT =
            "Seats can be blocked only for a scheduled future performance.";

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
            if (!performanceExists(connection, performanceId)) {
                return OperationResult.notFound("Performance not found.");
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
            if (!performanceExists(connection, performanceId)) {
                return OperationResult.notFound("Performance not found.");
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
            String rowName,
            int seatNumber
    ) {
        return changeSeatBlock(performanceId, rowName, seatNumber, true);
    }

    public OperationResult<Void> unblockSeat(
            int performanceId,
            String rowName,
            int seatNumber
    ) {
        return changeSeatBlock(performanceId, rowName, seatNumber, false);
    }

    public OperationResult<Void> checkPerformanceForSeatBlocking(int performanceId) {
        if (performanceId <= 0) {
            return OperationResult.invalidInput("Performance ID must be positive.");
        }

        return transactions.execute(
                connection -> checkPerformanceForSeatBlocking(
                        connection,
                        performanceId,
                        false
                )
        );
    }

    private OperationResult<Void> changeSeatBlock(
            int performanceId,
            String rowName,
            int seatNumber,
            boolean targetBlocked
    ) {
        if (performanceId <= 0 || rowName == null || rowName.isBlank() || seatNumber <= 0) {
            return OperationResult.invalidInput(
                    "Performance ID, row, and a positive seat number are required."
            );
        }

        return transactions.execute(connection -> {
            OperationResult<Void> performanceCheck = checkPerformanceForSeatBlocking(
                    connection,
                    performanceId,
                    true
            );
            if (!performanceCheck.isSuccess()) {
                return performanceCheck;
            }

            String sql = """
                    SELECT ps.performance_seat_id, ps.blocked_status
                    FROM PerformanceSeats ps
                    WHERE ps.performance_id = ?
                      AND ps.row_name = ?
                      AND ps.seat_number = ?
                    FOR UPDATE
                    """;
            int performanceSeatId;
            boolean currentlyBlocked;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, performanceId);
                statement.setString(2, rowName.trim());
                statement.setInt(3, seatNumber);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound(
                                "Reserved row " + rowName.trim() + ", seat " + seatNumber
                                        + " was not found for this performance."
                        );
                    }
                    performanceSeatId = rows.getInt("performance_seat_id");
                    currentlyBlocked = rows.getBoolean("blocked_status");
                    if (rows.next()) {
                        return OperationResult.conflict(
                                "Row " + rowName.trim() + ", seat " + seatNumber
                                        + " exists in more than one reserved section."
                        );
                    }
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

    private OperationResult<Void> checkPerformanceForSeatBlocking(
            java.sql.Connection connection,
            int performanceId,
            boolean lockForUpdate
    ) throws SQLException {
        String sql = """
                SELECT status, date_time
                FROM Performance
                WHERE performance_id = ?
                """ + (lockForUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return OperationResult.notFound("Performance not found.");
                }
                if (!"scheduled".equals(rows.getString("status"))
                        || !rows.getTimestamp("date_time").toLocalDateTime()
                        .isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    return OperationResult.conflict(SEAT_BLOCKING_PERFORMANCE_CONFLICT);
                }
                return OperationResult.success("Performance is open for seat blocking.");
            }
        }
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

    private boolean performanceExists(
            java.sql.Connection connection,
            int performanceId
    ) throws SQLException {
        String sql = "SELECT 1 FROM Performance WHERE performance_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
