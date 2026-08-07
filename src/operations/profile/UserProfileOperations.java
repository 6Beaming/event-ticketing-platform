package operations.profile;

import common.OperationResult;
import database.JdbcSupport;
import database.TransactionManager;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UserProfileOperations {
    private static final LocalDate ANONYMIZED_DATE = LocalDate.of(1970, 1, 1);

    private final TransactionManager transactions;

    public UserProfileOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<Integer> createCustomer(ProfileInput profile, PaymentInput payment) {
        Optional<String> profileError = ProfileValidator.validateProfile(profile, todayUtc());
        if (profileError.isPresent()) {
            return OperationResult.invalidInput(profileError.get());
        }
        Optional<String> paymentError = ProfileValidator.validatePayment(payment);
        if (paymentError.isPresent()) {
            return OperationResult.invalidInput(paymentError.get());
        }

        return transactions.execute(connection -> {
            if (emailExists(connection, profile.getEmail())) {
                return OperationResult.conflict("An account already uses that email address.");
            }
            int userId = insertUser(connection, profile, "customer");
            insertSubtype(connection, "Customer", userId, "customer");
            insertPayment(connection, userId, payment);
            return OperationResult.success("Customer profile created.", userId);
        });
    }

    public OperationResult<Integer> createOrganizer(ProfileInput profile) {
        Optional<String> profileError = ProfileValidator.validateProfile(profile, todayUtc());
        if (profileError.isPresent()) {
            return OperationResult.invalidInput(profileError.get());
        }

        return transactions.execute(connection -> {
            if (emailExists(connection, profile.getEmail())) {
                return OperationResult.conflict("An account already uses that email address.");
            }
            int userId = insertUser(connection, profile, "organizer");
            insertSubtype(connection, "Organizer", userId, "organizer");
            return OperationResult.success("Organizer profile created.", userId);
        });
    }

    public OperationResult<Void> checkEmailAvailability(String email) {
        Optional<String> emailError = ProfileValidator.validateEmail(email);
        if (emailError.isPresent()) {
            return OperationResult.invalidInput(emailError.get());
        }

        return transactions.execute(connection -> {
            if (emailExists(connection, email)) {
                return OperationResult.conflict("An account already uses that email address.");
            }
            return OperationResult.success("Email address is available.");
        });
    }

    public OperationResult<CustomerProfile> getCustomerProfile(int customerId) {
        if (customerId <= 0) {
            return OperationResult.invalidInput("Customer ID must be positive.");
        }

        return transactions.execute(connection -> {
            String sql = """
                    SELECT u.user_id, u.name, u.address, u.email, u.date_of_birth,
                           u.account_status, p.card_number, p.card_holder_name,
                           p.expiry_date, p.billing_zip
                    FROM Users u
                    JOIN Customer c ON c.user_id = u.user_id
                    LEFT JOIN PaymentInfo p ON p.customer_id = c.user_id
                    WHERE u.user_id = ?
                    ORDER BY p.payment_info_id
                    LIMIT 1
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Customer profile not found.");
                    }
                    Date expiryDate = rows.getDate("expiry_date");
                    CustomerProfile customer = new CustomerProfile(
                            rows.getInt("user_id"),
                            rows.getString("name"),
                            rows.getString("address"),
                            rows.getString("email"),
                            rows.getDate("date_of_birth").toLocalDate(),
                            rows.getString("account_status"),
                            maskCard(rows.getString("card_number")),
                            rows.getString("card_holder_name"),
                            expiryDate == null ? null : expiryDate.toLocalDate(),
                            rows.getString("billing_zip")
                    );
                    return OperationResult.success("Customer profile found.", customer);
                }
            }
        });
    }

    public OperationResult<List<CustomerOrderHistoryEntry>> getCustomerOrderHistory(
            int customerId
    ) {
        if (customerId <= 0) {
            return OperationResult.invalidInput("Customer ID must be positive.");
        }

        return transactions.execute(connection -> {
            if (!customerExists(connection, customerId)) {
                return OperationResult.notFound("Customer not found.");
            }

            String sql = """
                    SELECT tr.transaction_id, tr.transaction_type, tr.transaction_date,
                           tr.payment_card_number,
                           t.ticket_id, t.performance_id, t.tier_code,
                           t.status AS ticket_status,
                           e.title AS event_title, p.date_time AS performance_date_time,
                           p.status AS performance_status,
                           v.name AS venue_name, v.city AS venue_city,
                           COALESCE(rl.listing_price, t.face_value) AS purchase_price,
                           COALESCE(ps.section_name, gac.section_name) AS section_name,
                           ps.row_name, ps.seat_number,
                           own.acquired_at, own.ended_at AS ownership_ended_at,
                           tc.cancellation_date,
                           COALESCE(ref.amount, 0) AS refund_amount
                    FROM Transactions tr
                    JOIN TicketOwnership own
                      ON own.acquired_transaction_id = tr.transaction_id
                     AND own.customer_id = tr.customer_id
                    JOIN Tickets t ON t.ticket_id = own.ticket_id
                    JOIN Performance p ON p.performance_id = t.performance_id
                    JOIN Event e ON e.event_id = p.event_id
                    JOIN Venue v ON v.venue_id = p.venue_id
                    LEFT JOIN ResaleListing rl
                      ON rl.listing_id = own.acquired_listing_id
                    LEFT JOIN PerformanceSeats ps
                      ON ps.performance_seat_id = t.performance_seats_ref
                     AND ps.performance_id = t.performance_id
                    LEFT JOIN GeneralAdmissionCapacity gac
                      ON gac.ga_capacity_id = t.general_seats_ref
                     AND gac.performance_id = t.performance_id
                    LEFT JOIN TicketCancellation tc
                      ON tc.ownership_id = own.ownership_id
                    LEFT JOIN Refund ref ON ref.cancellation_id = tc.cancellation_id
                    WHERE tr.customer_id = ?
                    ORDER BY tr.transaction_date DESC, tr.transaction_id DESC, t.ticket_id
                    """;
            List<CustomerOrderHistoryEntry> history = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        history.add(new CustomerOrderHistoryEntry(
                                rows.getInt("transaction_id"),
                                rows.getString("transaction_type"),
                                rows.getTimestamp("transaction_date").toLocalDateTime(),
                                maskCard(rows.getString("payment_card_number")),
                                rows.getInt("ticket_id"),
                                rows.getInt("performance_id"),
                                rows.getString("event_title"),
                                rows.getTimestamp("performance_date_time").toLocalDateTime(),
                                rows.getString("performance_status"),
                                rows.getString("venue_name"),
                                rows.getString("venue_city"),
                                rows.getString("tier_code"),
                                rows.getBigDecimal("purchase_price"),
                                rows.getString("ticket_status"),
                                rows.getString("section_name"),
                                rows.getString("row_name"),
                                nullableInt(rows, "seat_number"),
                                rows.getTimestamp("acquired_at").toLocalDateTime(),
                                nullableDateTime(rows, "ownership_ended_at"),
                                nullableDateTime(rows, "cancellation_date"),
                                rows.getBigDecimal("refund_amount")
                        ));
                    }
                }
            }
            return OperationResult.success("Customer order and ticket history retrieved.", history);
        });
    }

    public OperationResult<Void> deactivateUser(int userId) {
        if (userId <= 0) {
            return OperationResult.invalidInput("User ID must be positive.");
        }

        return transactions.execute(connection -> {
            String status = lockUserStatus(connection, userId);
            if (status == null) {
                return OperationResult.notFound("User profile not found.");
            }
            if ("deleted".equals(status)) {
                return OperationResult.conflict("User profile is already deactivated.");
            }

            anonymizeUser(connection, userId);
            anonymizePayments(connection, userId);
            return OperationResult.success("User profile deactivated; historical records were retained.");
        });
    }

    private int insertUser(Connection connection, ProfileInput profile, String role)
            throws SQLException {
        String sql = """
                INSERT INTO Users (name, address, email, date_of_birth, user_role)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, profile.getName().trim());
            statement.setString(2, profile.getAddress().trim());
            statement.setString(3, profile.getEmail().trim());
            statement.setDate(4, Date.valueOf(profile.getDateOfBirth()));
            statement.setString(5, role);
            statement.executeUpdate();
            return JdbcSupport.requireGeneratedIntKey(statement, role + " user");
        }
    }

    private boolean emailExists(Connection connection, String email) throws SQLException {
        String sql = "SELECT 1 FROM Users WHERE email = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean customerExists(Connection connection, int customerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM Customer WHERE user_id = ?"
        )) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void insertSubtype(Connection connection, String table, int userId, String role)
            throws SQLException {
        if (!"Customer".equals(table) && !"Organizer".equals(table)) {
            throw new SQLException("Unsupported user subtype");
        }
        String sql = "INSERT INTO " + table + " (user_id, user_role) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, role);
            statement.executeUpdate();
        }
    }

    private void insertPayment(Connection connection, int customerId, PaymentInput payment)
            throws SQLException {
        String sql = """
                INSERT INTO PaymentInfo
                    (customer_id, card_number, card_holder_name, expiry_date, billing_zip)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setString(2, payment.getCardNumber().trim());
            statement.setString(3, payment.getCardHolderName().trim());
            statement.setDate(4, Date.valueOf(payment.getExpiryDate()));
            statement.setString(5, payment.getBillingZip().trim());
            statement.executeUpdate();
        }
    }

    private String lockUserStatus(Connection connection, int userId) throws SQLException {
        String sql = "SELECT account_status FROM Users WHERE user_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString("account_status") : null;
            }
        }
    }

    private void anonymizeUser(Connection connection, int userId) throws SQLException {
        String sql = """
                UPDATE Users
                SET name = ?, address = 'Deleted', email = ?, date_of_birth = ?,
                    account_status = 'deleted', deleted_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "Deleted User " + userId);
            statement.setString(2, "deleted-" + userId + "@mytix.invalid");
            statement.setDate(3, Date.valueOf(ANONYMIZED_DATE));
            statement.setInt(4, userId);
            statement.executeUpdate();
        }
    }

    private void anonymizePayments(Connection connection, int userId) throws SQLException {
        String sql = """
                UPDATE PaymentInfo
                SET card_number = CONCAT('deleted-', payment_info_id),
                    card_holder_name = 'Deleted', expiry_date = ?, billing_zip = 'Deleted'
                WHERE customer_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(ANONYMIZED_DATE));
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }

    private String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return "Not available";
        }
        int visibleStart = Math.max(0, cardNumber.length() - 4);
        return "****" + cardNumber.substring(visibleStart);
    }

    private Integer nullableInt(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private java.time.LocalDateTime nullableDateTime(ResultSet rows, String column)
            throws SQLException {
        Timestamp value = rows.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
