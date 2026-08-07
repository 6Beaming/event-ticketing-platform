package operations.booking;

import common.OperationResult;
import database.JdbcSupport;
import database.TransactionManager;
import operations.restriction.CustomerRestrictionGuard;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BookingOperations {
    private final TransactionManager transactions;
    private final CustomerRestrictionGuard restrictions;

    public BookingOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
        this.restrictions = new CustomerRestrictionGuard();
    }

    public OperationResult<BookingSummary> bookReservedSeats(
            int customerId,
            int performanceId,
            List<Integer> requestedSeatIds
    ) {
        String validationError = validateReservedRequest(
                customerId,
                performanceId,
                requestedSeatIds
        );
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        List<Integer> sortedSeatIds = requestedSeatIds.stream().sorted().toList();
        return transactions.execute(connection -> {
            PaymentSnapshot payment = lockCustomerPayment(connection, customerId);
            if (payment == null) {
                return OperationResult.notFound(
                        "An active customer with saved payment information was not found."
                );
            }
            OperationResult<Void> restriction = restrictions.checkCustomerAllowed(
                    connection,
                    customerId,
                    LocalDateTime.now(ZoneOffset.UTC).minusYears(1)
            );
            if (!restriction.isSuccess()) {
                return copyFailure(restriction);
            }
            OperationResult<Void> performance = lockSaleablePerformance(connection, performanceId);
            if (!performance.isSuccess()) {
                return copyFailure(performance);
            }

            List<ReservedSelection> selections = lockReservedSelections(
                    connection,
                    performanceId,
                    sortedSeatIds
            );
            if (selections.size() != sortedSeatIds.size()) {
                return OperationResult.notFound(
                        "One or more requested seats do not belong to this performance."
                );
            }
            for (ReservedSelection selection : selections) {
                if (selection.blocked) {
                    return OperationResult.conflict(
                            "Row " + selection.rowName + ", seat " + selection.seatNumber
                                    + " is blocked. No tickets were booked."
                    );
                }
            }
            Integer soldSeatId = findSoldReservedSeat(connection, sortedSeatIds);
            if (soldSeatId != null) {
                ReservedSelection soldSelection = selections.stream()
                        .filter(selection -> selection.performanceSeatId == soldSeatId)
                        .findFirst()
                        .orElseThrow();
                return OperationResult.conflict(
                        "Row " + soldSelection.rowName + ", seat "
                                + soldSelection.seatNumber
                                + " is already sold. No tickets were booked."
                );
            }

            int transactionId = insertPurchaseTransaction(
                    connection,
                    customerId,
                    performanceId,
                    payment
            );
            List<Integer> ticketIds = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (ReservedSelection selection : selections) {
                int ticketId = insertTicket(
                        connection,
                        transactionId,
                        performanceId,
                        selection.tierCode,
                        selection.performanceSeatId,
                        null,
                        selection.price
                );
                insertInitialOwnership(connection, ticketId, customerId, transactionId);
                ticketIds.add(ticketId);
                total = total.add(selection.price);
            }
            return OperationResult.success(
                    "Reserved seats booked atomically.",
                    new BookingSummary(transactionId, ticketIds, total)
            );
        });
    }

    public OperationResult<BookingSummary> bookGeneralAdmission(
            int customerId,
            int performanceId,
            String sectionName,
            int quantity
    ) {
        String validationError = validateGeneralRequest(
                customerId,
                performanceId,
                sectionName,
                quantity
        );
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        return transactions.execute(connection -> {
            PaymentSnapshot payment = lockCustomerPayment(connection, customerId);
            if (payment == null) {
                return OperationResult.notFound(
                        "An active customer with saved payment information was not found."
                );
            }
            OperationResult<Void> restriction = restrictions.checkCustomerAllowed(
                    connection,
                    customerId,
                    LocalDateTime.now(ZoneOffset.UTC).minusYears(1)
            );
            if (!restriction.isSuccess()) {
                return copyFailure(restriction);
            }
            OperationResult<Void> performance = lockSaleablePerformance(connection, performanceId);
            if (!performance.isSuccess()) {
                return copyFailure(performance);
            }

            GeneralSelection selection = lockGeneralSelection(
                    connection,
                    performanceId,
                    sectionName.trim()
            );
            if (selection == null) {
                return OperationResult.notFound(
                        "General-admission inventory was not found for this performance and section."
                );
            }
            if (selection.remainingCapacity < quantity) {
                return OperationResult.conflict(
                        "Only " + selection.remainingCapacity
                                + " general-admission tickets remain. No tickets were booked."
                );
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE GeneralAdmissionCapacity
                    SET remaining_capacity = remaining_capacity - ?
                    WHERE ga_capacity_id = ? AND performance_id = ?
                    """)) {
                statement.setInt(1, quantity);
                statement.setInt(2, selection.capacityId);
                statement.setInt(3, performanceId);
                statement.executeUpdate();
            }

            int transactionId = insertPurchaseTransaction(
                    connection,
                    customerId,
                    performanceId,
                    payment
            );
            List<Integer> ticketIds = new ArrayList<>();
            for (int index = 0; index < quantity; index++) {
                int ticketId = insertTicket(
                        connection,
                        transactionId,
                        performanceId,
                        selection.tierCode,
                        null,
                        selection.capacityId,
                        selection.price
                );
                insertInitialOwnership(connection, ticketId, customerId, transactionId);
                ticketIds.add(ticketId);
            }
            return OperationResult.success(
                    "General-admission tickets booked atomically.",
                    new BookingSummary(
                            transactionId,
                            ticketIds,
                            selection.price.multiply(BigDecimal.valueOf(quantity))
                    )
            );
        });
    }

    public OperationResult<Void> checkPerformanceForBooking(int performanceId) {
        if (performanceId <= 0) {
            return OperationResult.invalidInput("Performance ID must be positive.");
        }
        return transactions.execute(
                connection -> checkSaleablePerformance(connection, performanceId, false)
        );
    }

    public OperationResult<Void> checkCustomerForBooking(int customerId) {
        if (customerId <= 0) {
            return OperationResult.invalidInput("Customer ID must be positive.");
        }
        return transactions.execute(connection -> {
            PaymentSnapshot payment = lockCustomerPayment(connection, customerId);
            if (payment == null) {
                return OperationResult.notFound(
                        "An active customer with saved payment information was not found."
                );
            }
            return restrictions.checkCustomerAllowed(
                    connection,
                    customerId,
                    LocalDateTime.now(ZoneOffset.UTC).minusYears(1)
            );
        });
    }

    public static String validateReservedRequest(
            int customerId,
            int performanceId,
            List<Integer> requestedSeatIds
    ) {
        if (customerId <= 0 || performanceId <= 0) {
            return "Customer and performance IDs must be positive.";
        }
        if (requestedSeatIds == null || requestedSeatIds.isEmpty()) {
            return "At least one reserved seat ID is required.";
        }
        Set<Integer> unique = new HashSet<>();
        for (Integer seatId : requestedSeatIds) {
            if (seatId == null || seatId <= 0) {
                return "Reserved seat IDs must be positive.";
            }
            if (!unique.add(seatId)) {
                return "A reserved seat can be requested only once per booking.";
            }
        }
        return null;
    }

    public static String validateGeneralRequest(
            int customerId,
            int performanceId,
            String sectionName,
            int quantity
    ) {
        if (customerId <= 0 || performanceId <= 0) {
            return "Customer and performance IDs must be positive.";
        }
        if (sectionName == null || sectionName.trim().isEmpty()) {
            return "General-admission section name is required.";
        }
        if (quantity <= 0) {
            return "General-admission quantity must be positive.";
        }
        return null;
    }

    private PaymentSnapshot lockCustomerPayment(Connection connection, int customerId)
            throws SQLException {
        String sql = """
                SELECT p.payment_info_id, p.card_number, p.card_holder_name,
                       p.expiry_date, p.billing_zip
                FROM Customer c
                JOIN Users u ON u.user_id = c.user_id
                JOIN PaymentInfo p ON p.customer_id = c.user_id
                WHERE c.user_id = ? AND u.account_status = 'active'
                ORDER BY p.payment_info_id
                LIMIT 1
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new PaymentSnapshot(
                        rows.getInt("payment_info_id"),
                        rows.getString("card_number"),
                        rows.getString("card_holder_name"),
                        rows.getDate("expiry_date"),
                        rows.getString("billing_zip")
                );
            }
        }
    }

    private OperationResult<Void> lockSaleablePerformance(
            Connection connection,
            int performanceId
    ) throws SQLException {
        return checkSaleablePerformance(connection, performanceId, true);
    }

    private OperationResult<Void> checkSaleablePerformance(
            Connection connection,
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
                if (!"scheduled".equals(rows.getString("status"))) {
                    return OperationResult.conflict(
                            "Tickets can be booked only for a scheduled performance."
                    );
                }
                Timestamp dateTime = rows.getTimestamp("date_time");
                if (!dateTime.toLocalDateTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    return OperationResult.conflict("Tickets cannot be booked for a past performance.");
                }
                return OperationResult.success("Performance is saleable.");
            }
        }
    }

    private List<ReservedSelection> lockReservedSelections(
            Connection connection,
            int performanceId,
            List<Integer> seatIds
    ) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(seatIds.size(), "?"));
        String sql = """
                SELECT ps.performance_seat_id, ps.row_name, ps.seat_number, ps.blocked_status,
                       sta.tier_code, pt.price
                FROM PerformanceSeats ps
                JOIN SectionTierAssignment sta
                  ON sta.performance_id = ps.performance_id
                 AND sta.venue_id = ps.venue_id
                 AND sta.section_name = ps.section_name
                JOIN PriceTier pt
                  ON pt.performance_id = sta.performance_id
                 AND pt.tier_code = sta.tier_code
                WHERE ps.performance_id = ?
                  AND ps.performance_seat_id IN (%s)
                ORDER BY ps.performance_seat_id
                FOR UPDATE
                """.formatted(placeholders);
        List<ReservedSelection> selections = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            for (int index = 0; index < seatIds.size(); index++) {
                statement.setInt(index + 2, seatIds.get(index));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    selections.add(new ReservedSelection(
                            rows.getInt("performance_seat_id"),
                            rows.getString("row_name"),
                            rows.getInt("seat_number"),
                            rows.getBoolean("blocked_status"),
                            rows.getString("tier_code"),
                            rows.getBigDecimal("price")
                    ));
                }
            }
        }
        return selections;
    }

    private Integer findSoldReservedSeat(Connection connection, List<Integer> seatIds)
            throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(seatIds.size(), "?"));
        String sql = """
                SELECT active_reserved_seat_ref
                FROM Tickets
                WHERE active_reserved_seat_ref IN (%s)
                ORDER BY active_reserved_seat_ref
                FOR UPDATE
                """.formatted(placeholders);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < seatIds.size(); index++) {
                statement.setInt(index + 1, seatIds.get(index));
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt("active_reserved_seat_ref") : null;
            }
        }
    }

    private GeneralSelection lockGeneralSelection(
            Connection connection,
            int performanceId,
            String sectionName
    ) throws SQLException {
        String sql = """
                SELECT gac.ga_capacity_id, gac.remaining_capacity,
                       sta.tier_code, pt.price
                FROM GeneralAdmissionCapacity gac
                JOIN SectionTierAssignment sta
                  ON sta.performance_id = gac.performance_id
                 AND sta.venue_id = gac.venue_id
                 AND sta.section_name = gac.section_name
                JOIN PriceTier pt
                  ON pt.performance_id = sta.performance_id
                 AND pt.tier_code = sta.tier_code
                WHERE gac.performance_id = ? AND gac.section_name = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            statement.setString(2, sectionName);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new GeneralSelection(
                        rows.getInt("ga_capacity_id"),
                        rows.getInt("remaining_capacity"),
                        rows.getString("tier_code"),
                        rows.getBigDecimal("price")
                );
            }
        }
    }

    private int insertPurchaseTransaction(
            Connection connection,
            int customerId,
            int performanceId,
            PaymentSnapshot payment
    ) throws SQLException {
        String sql = """
                INSERT INTO Transactions
                    (customer_id, payment_info_id, payment_card_number,
                     payment_card_holder_name, payment_expiry_date, payment_billing_zip,
                     transaction_type, performance_id)
                VALUES (?, ?, ?, ?, ?, ?, 'purchase', ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setInt(1, customerId);
            statement.setInt(2, payment.paymentInfoId);
            statement.setString(3, payment.cardNumber);
            statement.setString(4, payment.cardHolderName);
            statement.setDate(5, payment.expiryDate);
            statement.setString(6, payment.billingZip);
            statement.setInt(7, performanceId);
            statement.executeUpdate();
            return JdbcSupport.requireGeneratedIntKey(statement, "purchase transaction");
        }
    }

    private int insertTicket(
            Connection connection,
            int transactionId,
            int performanceId,
            String tierCode,
            Integer performanceSeatId,
            Integer generalCapacityId,
            BigDecimal faceValue
    ) throws SQLException {
        String sql = """
                INSERT INTO Tickets
                    (purchase_id, performance_id, tier_code, performance_seats_ref,
                     general_seats_ref, face_value)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setInt(1, transactionId);
            statement.setInt(2, performanceId);
            statement.setString(3, tierCode);
            if (performanceSeatId == null) {
                statement.setNull(4, java.sql.Types.INTEGER);
            } else {
                statement.setInt(4, performanceSeatId);
            }
            if (generalCapacityId == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, generalCapacityId);
            }
            statement.setBigDecimal(6, faceValue);
            statement.executeUpdate();
            return JdbcSupport.requireGeneratedIntKey(statement, "ticket");
        }
    }

    private void insertInitialOwnership(
            Connection connection,
            int ticketId,
            int customerId,
            int transactionId
    ) throws SQLException {
        String sql = """
                INSERT INTO TicketOwnership
                    (ticket_id, customer_id, acquired_transaction_id)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            statement.setInt(2, customerId);
            statement.setInt(3, transactionId);
            statement.executeUpdate();
        }
    }

    private <T> OperationResult<T> copyFailure(OperationResult<?> result) {
        return switch (result.getStatus()) {
            case INVALID_INPUT -> OperationResult.invalidInput(result.getMessage());
            case NOT_FOUND -> OperationResult.notFound(result.getMessage());
            case FORBIDDEN -> OperationResult.forbidden(result.getMessage());
            case CONFLICT -> OperationResult.conflict(result.getMessage());
            case DATABASE_FAILURE -> OperationResult.databaseFailure(result.getMessage());
            case SUCCESS -> throw new IllegalArgumentException("Cannot copy a successful result");
        };
    }

    private static final class PaymentSnapshot {
        private final int paymentInfoId;
        private final String cardNumber;
        private final String cardHolderName;
        private final Date expiryDate;
        private final String billingZip;

        private PaymentSnapshot(
                int paymentInfoId,
                String cardNumber,
                String cardHolderName,
                Date expiryDate,
                String billingZip
        ) {
            this.paymentInfoId = paymentInfoId;
            this.cardNumber = cardNumber;
            this.cardHolderName = cardHolderName;
            this.expiryDate = expiryDate;
            this.billingZip = billingZip;
        }
    }

    private static final class ReservedSelection {
        private final int performanceSeatId;
        private final String rowName;
        private final int seatNumber;
        private final boolean blocked;
        private final String tierCode;
        private final BigDecimal price;

        private ReservedSelection(
                int performanceSeatId,
                String rowName,
                int seatNumber,
                boolean blocked,
                String tierCode,
                BigDecimal price
        ) {
            this.performanceSeatId = performanceSeatId;
            this.rowName = rowName;
            this.seatNumber = seatNumber;
            this.blocked = blocked;
            this.tierCode = tierCode;
            this.price = price;
        }
    }

    private static final class GeneralSelection {
        private final int capacityId;
        private final int remainingCapacity;
        private final String tierCode;
        private final BigDecimal price;

        private GeneralSelection(
                int capacityId,
                int remainingCapacity,
                String tierCode,
                BigDecimal price
        ) {
            this.capacityId = capacityId;
            this.remainingCapacity = remainingCapacity;
            this.tierCode = tierCode;
            this.price = price;
        }
    }
}
