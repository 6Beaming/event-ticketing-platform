package operations.event;

import common.OperationResult;
import database.JdbcSupport;
import database.TransactionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OrganizerEventOperations {
    private final TransactionManager transactions;

    public OrganizerEventOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<Void> checkActiveOrganizer(int organizerId) {
        if (organizerId <= 0) {
            return OperationResult.invalidInput("Organizer ID must be positive.");
        }
        return transactions.execute(connection -> activeOrganizerExists(connection, organizerId)
                ? OperationResult.success("Active organizer found.")
                : OperationResult.notFound("Active organizer not found."));
    }

    public OperationResult<Void> checkGenre(int genreId) {
        return checkRecord(genreId, "Genre", "genre_id",
                "Genre found.", "Genre not found in the configured taxonomy.");
    }

    public OperationResult<Void> checkArtist(int artistId) {
        return checkRecord(artistId, "ArtistsTeams", "artist_id",
                "Artist or team found.", "Artist or team not found.");
    }

    public OperationResult<Void> checkEvent(int eventId) {
        return checkRecord(eventId, "Event", "event_id",
                "Event found.", "Event not found.");
    }

    public OperationResult<Void> checkVenue(int venueId) {
        return checkRecord(venueId, "Venue", "venue_id",
                "Venue found.", "Venue not found.");
    }

    public OperationResult<List<OrganizerPerformanceSalesHistory>>
            getOrganizerPerformanceSalesHistory(int organizerId) {
        if (organizerId <= 0) {
            return OperationResult.invalidInput("Organizer ID must be positive.");
        }

        return transactions.execute(connection -> {
            if (!organizerExists(connection, organizerId)) {
                return OperationResult.notFound("Organizer not found.");
            }

            String sql = """
                    SELECT e.event_id, e.title AS event_title,
                           p.performance_id, p.date_time, p.status AS performance_status,
                           v.name AS venue_name, v.city AS venue_city,
                           COALESCE(ts.original_ticket_count, 0) AS original_ticket_count,
                           COALESCE(ts.original_gross_revenue, 0) AS original_gross_revenue,
                           COALESCE(ts.active_ticket_count, 0) AS active_ticket_count,
                           COALESCE(ts.cancelled_ticket_count, 0) AS cancelled_ticket_count,
                           COALESCE(rf.refunded_amount, 0) AS refunded_amount,
                           COALESCE(rs.completed_resale_count, 0) AS completed_resale_count,
                           COALESCE(rs.resale_gross_revenue, 0) AS resale_gross_revenue,
                           sh.transaction_id, sh.transaction_type, sh.transaction_date,
                           sh.customer_id, buyer.name AS customer_name,
                           sh.ticket_id, sh.sale_price
                    FROM Event e
                    LEFT JOIN Performance p ON p.event_id = e.event_id
                    LEFT JOIN Venue v ON v.venue_id = p.venue_id
                    LEFT JOIN (
                        SELECT performance_id,
                               COUNT(*) AS original_ticket_count,
                               SUM(face_value) AS original_gross_revenue,
                               SUM(status = 'active') AS active_ticket_count,
                               SUM(status = 'cancelled') AS cancelled_ticket_count
                        FROM Tickets
                        GROUP BY performance_id
                    ) ts ON ts.performance_id = p.performance_id
                    LEFT JOIN (
                        SELECT t.performance_id, SUM(ref.amount) AS refunded_amount
                        FROM Refund ref
                        JOIN TicketCancellation tc
                          ON tc.cancellation_id = ref.cancellation_id
                        JOIN Tickets t ON t.ticket_id = tc.ticket_id
                        GROUP BY t.performance_id
                    ) rf ON rf.performance_id = p.performance_id
                    LEFT JOIN (
                        SELECT t.performance_id,
                               COUNT(*) AS completed_resale_count,
                               SUM(rl.listing_price) AS resale_gross_revenue
                        FROM ResaleListing rl
                        JOIN Tickets t ON t.ticket_id = rl.ticket_id
                        WHERE rl.status = 'sold'
                        GROUP BY t.performance_id
                    ) rs ON rs.performance_id = p.performance_id
                    LEFT JOIN (
                        SELECT t.performance_id, tr.transaction_id,
                               tr.transaction_type, tr.transaction_date,
                               tr.customer_id, t.ticket_id,
                               t.face_value AS sale_price
                        FROM Transactions tr
                        JOIN Tickets t ON t.purchase_id = tr.transaction_id
                        UNION ALL
                        SELECT t.performance_id, tr.transaction_id,
                               tr.transaction_type, tr.transaction_date,
                               tr.customer_id, t.ticket_id,
                               rl.listing_price AS sale_price
                        FROM Transactions tr
                        JOIN ResaleListing rl ON rl.listing_id = tr.listing_id
                        JOIN Tickets t ON t.ticket_id = rl.ticket_id
                    ) sh ON sh.performance_id = p.performance_id
                    LEFT JOIN Users buyer ON buyer.user_id = sh.customer_id
                    WHERE e.organizer_id = ?
                    ORDER BY e.event_id, p.date_time, p.performance_id,
                             sh.transaction_date, sh.transaction_id, sh.ticket_id
                    """;
            List<OrganizerPerformanceSalesHistory> history = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, organizerId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        history.add(new OrganizerPerformanceSalesHistory(
                                rows.getInt("event_id"),
                                rows.getString("event_title"),
                                nullableInt(rows, "performance_id"),
                                nullableDateTime(rows, "date_time"),
                                rows.getString("performance_status"),
                                rows.getString("venue_name"),
                                rows.getString("venue_city"),
                                rows.getInt("original_ticket_count"),
                                rows.getBigDecimal("original_gross_revenue"),
                                rows.getInt("active_ticket_count"),
                                rows.getInt("cancelled_ticket_count"),
                                rows.getBigDecimal("refunded_amount"),
                                rows.getInt("completed_resale_count"),
                                rows.getBigDecimal("resale_gross_revenue"),
                                nullableInt(rows, "transaction_id"),
                                rows.getString("transaction_type"),
                                nullableDateTime(rows, "transaction_date"),
                                nullableInt(rows, "customer_id"),
                                rows.getString("customer_name"),
                                nullableInt(rows, "ticket_id"),
                                rows.getBigDecimal("sale_price")
                        ));
                    }
                }
            }
            return OperationResult.success(
                    "Organizer event and performance sales history retrieved.",
                    history
            );
        });
    }

    public OperationResult<Integer> createEvent(EventInput input) {
        String validationError = validateEvent(input);
        if (validationError != null) {
            return OperationResult.invalidInput(validationError);
        }

        return transactions.execute(connection -> {
            if (!activeOrganizerExists(connection, input.getOrganizerId())) {
                return OperationResult.notFound("Active organizer not found.");
            }
            if (!recordExists(connection, "Genre", "genre_id", input.getGenreId())) {
                return OperationResult.notFound("Genre not found in the configured taxonomy.");
            }
            for (ArtistBillingInput artist : input.getArtists()) {
                if (!recordExists(connection, "ArtistsTeams", "artist_id", artist.getArtistId())) {
                    return OperationResult.notFound(
                            "Artist or team " + artist.getArtistId() + " was not found."
                    );
                }
            }

            int eventId = insertEvent(connection, input);
            insertBillingOrder(connection, eventId, input);
            return OperationResult.success("Event and billing order created.", eventId);
        });
    }

    public OperationResult<Integer> addPerformance(PerformanceInput input) {
        if (input == null) {
            return OperationResult.invalidInput("Performance information is required.");
        }
        if (input.getEventId() <= 0 || input.getVenueId() <= 0) {
            return OperationResult.invalidInput("Event and venue IDs must be positive.");
        }
        if (input.getDateTime() == null) {
            return OperationResult.invalidInput("Performance date and time are required.");
        }

        return transactions.execute(connection -> {
            if (!recordExists(connection, "Event", "event_id", input.getEventId())) {
                return OperationResult.notFound("Event not found.");
            }
            if (!recordExists(connection, "Venue", "venue_id", input.getVenueId())) {
                return OperationResult.notFound("Venue not found.");
            }

            String sql = """
                    INSERT INTO Performance (event_id, venue_id, date_time, status)
                    VALUES (?, ?, ?, 'scheduled')
                    """;
            try (PreparedStatement statement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setInt(1, input.getEventId());
                statement.setInt(2, input.getVenueId());
                statement.setTimestamp(3, Timestamp.valueOf(input.getDateTime()));
                statement.executeUpdate();
                int performanceId = JdbcSupport.requireGeneratedIntKey(statement, "performance");
                return OperationResult.success("Performance created.", performanceId);
            }
        });
    }

    public OperationResult<Void> updateResaleCap(
            int eventId,
            BigDecimal resaleCapMultiplier
    ) {
        if (eventId <= 0) {
            return OperationResult.invalidInput("Event ID must be positive.");
        }
        if (resaleCapMultiplier == null
                || resaleCapMultiplier.compareTo(BigDecimal.ONE) < 0) {
            return OperationResult.invalidInput(
                    "Resale cap multiplier must be at least 1.00."
            );
        }

        return transactions.execute(connection -> {
            String sql = "SELECT 1 FROM Event WHERE event_id = ? FOR UPDATE";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, eventId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Event not found.");
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE Event SET resale_cap_pct = ? WHERE event_id = ?"
            )) {
                statement.setBigDecimal(1, resaleCapMultiplier);
                statement.setInt(2, eventId);
                statement.executeUpdate();
            }
            return OperationResult.success("Event resale cap updated.");
        });
    }

    public static String validateEvent(EventInput input) {
        if (input == null) {
            return "Event information is required.";
        }
        if (input.getOrganizerId() <= 0 || input.getGenreId() <= 0) {
            return "Organizer and genre IDs must be positive.";
        }
        if (input.getTitle() == null || input.getTitle().trim().isEmpty()) {
            return "Event title is required.";
        }
        if (input.getResaleCapMultiplier() == null
                || input.getResaleCapMultiplier().compareTo(java.math.BigDecimal.ONE) < 0) {
            return "Resale cap multiplier must be at least 1.00.";
        }
        if (input.getArtists().isEmpty()) {
            return "At least one artist or team is required.";
        }

        Set<Integer> artistIds = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        int maximumRank = input.getArtists().size();
        for (ArtistBillingInput artist : input.getArtists()) {
            if (artist == null || artist.getArtistId() <= 0 || artist.getBillingRank() <= 0) {
                return "Artist IDs and billing ranks must be positive.";
            }
            if (artist.getBillingRank() > maximumRank) {
                return "Billing ranks cannot exceed the number of artists or teams.";
            }
            if (!artistIds.add(artist.getArtistId())) {
                return "An artist or team can appear only once in an event billing order.";
            }
            if (!ranks.add(artist.getBillingRank())) {
                return "Billing ranks must be unique within an event.";
            }
        }
        return null;
    }

    private int insertEvent(Connection connection, EventInput input) throws SQLException {
        String sql = """
                INSERT INTO Event
                    (title, description, resale_cap_pct, organizer_id, genre_id)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, input.getTitle().trim());
            statement.setString(2, blankToNull(input.getDescription()));
            statement.setBigDecimal(3, input.getResaleCapMultiplier());
            statement.setInt(4, input.getOrganizerId());
            statement.setInt(5, input.getGenreId());
            statement.executeUpdate();
            return JdbcSupport.requireGeneratedIntKey(statement, "event");
        }
    }

    private void insertBillingOrder(Connection connection, int eventId, EventInput input)
            throws SQLException {
        String sql = """
                INSERT INTO BillingOrder (event_id, artist_id, billing_rank)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ArtistBillingInput artist : input.getArtists()) {
                statement.setInt(1, eventId);
                statement.setInt(2, artist.getArtistId());
                statement.setInt(3, artist.getBillingRank());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean activeOrganizerExists(Connection connection, int organizerId)
            throws SQLException {
        String sql = """
                SELECT 1
                FROM Organizer o
                JOIN Users u ON u.user_id = o.user_id
                WHERE o.user_id = ? AND u.account_status = 'active'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, organizerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean organizerExists(Connection connection, int organizerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM Organizer WHERE user_id = ?"
        )) {
            statement.setInt(1, organizerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
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

    private OperationResult<Void> checkRecord(
            int id,
            String table,
            String column,
            String successMessage,
            String notFoundMessage
    ) {
        if (id <= 0) {
            return OperationResult.invalidInput(column.replace('_', ' ') + " must be positive.");
        }
        return transactions.execute(connection -> recordExists(connection, table, column, id)
                ? OperationResult.success(successMessage)
                : OperationResult.notFound(notFoundMessage));
    }

    private boolean recordExists(Connection connection, String table, String column, int id)
            throws SQLException {
        Set<String> supported = Set.of("Genre.genre_id", "ArtistsTeams.artist_id",
                "Event.event_id", "Venue.venue_id");
        if (!supported.contains(table + "." + column)) {
            throw new SQLException("Unsupported existence lookup");
        }
        String sql = "SELECT 1 FROM " + table + " WHERE " + column + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
