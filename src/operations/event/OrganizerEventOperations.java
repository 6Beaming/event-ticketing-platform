package operations.event;

import common.OperationResult;
import database.JdbcSupport;
import database.TransactionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashSet;
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
        for (ArtistBillingInput artist : input.getArtists()) {
            if (artist == null || artist.getArtistId() <= 0 || artist.getBillingRank() <= 0) {
                return "Artist IDs and billing ranks must be positive.";
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
