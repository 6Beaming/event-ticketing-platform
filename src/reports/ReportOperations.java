package reports;

import common.OperationResult;
import database.TransactionManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import java.sql.PreparedStatement;
import java.sql.ResultSet;


public final class ReportOperations {

    private final TransactionManager transactions;

    public ReportOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

 /*************************************************************************************************************
 REPORT-1
 *************************************************************************************************************/
 
public OperationResult<List<TicketRevenueReport>> report1a(
        LocalDateTime startDate,
        LocalDateTime endDate
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT v.city,
                       COUNT(*) AS tickets_sold,
                       SUM(t.face_value) AS gross_revenue
                FROM Tickets t
                JOIN Performance p
                    ON p.performance_id = t.performance_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                WHERE t.status = 'active'
                  AND p.date_time BETWEEN ? AND ?
                GROUP BY v.city
                ORDER BY gross_revenue DESC
                """;

        List<TicketRevenueReport> reports = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, startDate);
            statement.setObject(2, endDate);

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new TicketRevenueReport(
                    rows.getString("city"),
                    null,
                    rows.getInt("tickets_sold"),
                    rows.getBigDecimal("gross_revenue")
            ));
                }
            }
        }

        return OperationResult.success(
                "R1a ticket revenue report generated.",
                List.copyOf(reports)
        );
    });
}


public OperationResult<List<TicketRevenueReport>> report1b(
        String city,
        LocalDateTime startDate,
        LocalDateTime endDate
) {

    if (city == null || city.isBlank()) {
        return OperationResult.invalidInput(
                "City is required."
        );
    }

    return transactions.execute(connection -> {

        String sql = """
                SELECT v.city,
                       v.name AS venue_name,
                       COUNT(*) AS tickets_sold,
                       SUM(t.face_value) AS gross_revenue
                FROM Tickets t
                JOIN Performance p
                    ON p.performance_id = t.performance_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                WHERE t.status = 'active'
                  AND v.city = ?
                  AND p.date_time BETWEEN ? AND ?
                GROUP BY v.city, v.name
                ORDER BY gross_revenue DESC
                """;

        List<TicketRevenueReport> reports = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, city);
            statement.setObject(2, startDate);
            statement.setObject(3, endDate);

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new TicketRevenueReport(
        rows.getString("city"),
        rows.getString("venue_name"),
        rows.getInt("tickets_sold"),
        rows.getBigDecimal("gross_revenue")
));
                }
            }
        }

        return OperationResult.success(
                "R1b ticket revenue report generated.",
                List.copyOf(reports)
        );
    });
}



 /*************************************************************************************************************
 REPORT-2
 *************************************************************************************************************/

public OperationResult<List<EventPerformanceReport>> report2a() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT sg.segment_name,
                       g.genre_name,
                       COUNT(DISTINCT e.event_id) AS num_events,
                       COUNT(DISTINCT p.performance_id) AS num_performances
                FROM Event e
                JOIN Genre g
                  ON g.genre_id = e.genre_id
                JOIN Segment sg
                  ON sg.segment_id = g.segment_id
                LEFT JOIN Performance p
                  ON p.event_id = e.event_id
                GROUP BY sg.segment_name, g.genre_name
                ORDER BY sg.segment_name, g.genre_name
                """;


        List<EventPerformanceReport> reports = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(
                            new EventPerformanceReport(
                                    rows.getString("segment_name"),
                                    rows.getString("genre_name"),
                                    null,
                                    null,
                                    null,
                                    rows.getInt("num_events"),
                                    rows.getInt("num_performances")
                            )
                    );
                }
            }
        }


        return OperationResult.success(
                "Event and performance report generated.",
                List.copyOf(reports)
        );

    });
}

public OperationResult<List<EventPerformanceReport>> report2b() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT v.country,
                       COUNT(DISTINCT e.event_id) AS num_events,
                       COUNT(DISTINCT p.performance_id) AS num_performances
                FROM Performance p
                JOIN Event e
                  ON e.event_id = p.event_id
                JOIN Venue v
                  ON v.venue_id = p.venue_id
                GROUP BY v.country
                ORDER BY v.country
                """;


        List<EventPerformanceReport> reports = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new EventPerformanceReport(
                            null,
                            null,
                            rows.getString("country"),
                            null,
                            null,
                            rows.getInt("num_events"),
                            rows.getInt("num_performances")
                    ));
                }
            }
        }

        return OperationResult.success(
                "Country report generated.",
                List.copyOf(reports)
        );

    });
}


public OperationResult<List<EventPerformanceReport>> report2c() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT v.country,
                       v.city,
                       COUNT(DISTINCT e.event_id) AS num_events,
                       COUNT(DISTINCT p.performance_id) AS num_performances
                FROM Performance p
                JOIN Event e
                  ON e.event_id = p.event_id
                JOIN Venue v
                  ON v.venue_id = p.venue_id
                GROUP BY v.country, v.city
                ORDER BY v.country, v.city
                """;


        List<EventPerformanceReport> reports = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new EventPerformanceReport(
                            null,
                            null,
                            rows.getString("country"),
                            rows.getString("city"),
                            null,
                            rows.getInt("num_events"),
                            rows.getInt("num_performances")
                    ));
                }
            }
        }

        return OperationResult.success(
                "Country and city report generated.",
                List.copyOf(reports)
        );

    });
}


public OperationResult<List<EventPerformanceReport>> report2d() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT v.country,
                       v.city,
                       v.name AS venue_name,
                       COUNT(DISTINCT e.event_id) AS num_events,
                       COUNT(DISTINCT p.performance_id) AS num_performances
                FROM Performance p
                JOIN Event e
                  ON e.event_id = p.event_id
                JOIN Venue v
                  ON v.venue_id = p.venue_id
                GROUP BY v.country, v.city, v.name
                ORDER BY v.country, v.city, v.name
                """;


        List<EventPerformanceReport> reports = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new EventPerformanceReport(
                            null,
                            null,
                            rows.getString("country"),
                            rows.getString("city"),
                            rows.getString("venue_name"),
                            rows.getInt("num_events"),
                            rows.getInt("num_performances")
                    ));
                }
            }
        }

        return OperationResult.success(
                "Country, city and venue report generated.",
                List.copyOf(reports)
        );

    });
}

}