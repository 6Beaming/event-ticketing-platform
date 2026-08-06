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


 /*************************************************************************************************************
 REPORT-3
 *************************************************************************************************************/
public OperationResult<List<OrganizerRevenueReport>> report3a() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT o.user_id AS organizer_id,
                       u.name AS organizer_name,
                       SUM(t.face_value) AS gross_revenue
                FROM Tickets t
                JOIN Performance p 
                    ON p.performance_id = t.performance_id
                JOIN Event e
                    ON e.event_id = p.event_id
                JOIN Organizer o
                    ON o.user_id = e.organizer_id
                JOIN Users u
                    ON u.user_id = o.user_id
                WHERE t.status = 'active'
                GROUP BY o.user_id, u.name
                ORDER BY gross_revenue DESC
                """;


        List<OrganizerRevenueReport> reports = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new OrganizerRevenueReport(
                            rows.getInt("organizer_id"),
                            rows.getString("organizer_name"),
                            null,
                            null,
                            rows.getBigDecimal("gross_revenue")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Organizer revenue ranking generated.",
                List.copyOf(reports)
        );

    });
}

public OperationResult<List<OrganizerRevenueReport>> report3b() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT o.user_id AS organizer_id,
                       u.name AS organizer_name,
                       v.country,
                       SUM(t.face_value) AS gross_revenue
                FROM Tickets t
                JOIN Performance p
                    ON p.performance_id = t.performance_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                JOIN Event e
                    ON e.event_id = p.event_id
                JOIN Organizer o
                    ON o.user_id = e.organizer_id
                JOIN Users u
                    ON u.user_id = o.user_id
                WHERE t.status = 'active'
                GROUP BY o.user_id, u.name, v.country
                ORDER BY v.country, gross_revenue DESC
                """;


        List<OrganizerRevenueReport> reports = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new OrganizerRevenueReport(
                            rows.getInt("organizer_id"),
                            rows.getString("organizer_name"),
                            rows.getString("country"),
                            null,
                            rows.getBigDecimal("gross_revenue")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Organizer revenue by country generated.",
                List.copyOf(reports)
        );

    });
}

public OperationResult<List<OrganizerRevenueReport>> report3c() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT o.user_id AS organizer_id,
                       u.name AS organizer_name,
                       v.city,
                       SUM(t.face_value) AS gross_revenue
                FROM Tickets t
                JOIN Performance p
                    ON p.performance_id = t.performance_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                JOIN Event e
                    ON e.event_id = p.event_id
                JOIN Organizer o
                    ON o.user_id = e.organizer_id
                JOIN Users u
                    ON u.user_id = o.user_id
                WHERE t.status = 'active'
                GROUP BY o.user_id, u.name, v.city
                ORDER BY v.city, gross_revenue DESC
                """;


        List<OrganizerRevenueReport> reports = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new OrganizerRevenueReport(
                            rows.getInt("organizer_id"),
                            rows.getString("organizer_name"),
                            null,
                            rows.getString("city"),
                            rows.getBigDecimal("gross_revenue")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Organizer revenue by city generated.",
                List.copyOf(reports)
        );

    });
}



 /*************************************************************************************************************
 REPORT-4
 *************************************************************************************************************/


public OperationResult<List<ScalperDetectionReport>> report4(
        LocalDateTime oneYearAgo
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT purchases.customer_id,
                       u.name,
                       v.city,
                       purchases.num_purchased,
                       COALESCE(listed.num_listed, 0) AS num_listed
                FROM (
                    SELECT tr.customer_id,
                           p.venue_id,
                           COUNT(*) AS num_purchased
                    FROM Tickets t
                    JOIN Transactions tr
                        ON tr.transaction_id = t.purchase_id
                    JOIN Performance p
                        ON p.performance_id = t.performance_id
                    WHERE tr.transaction_type = 'purchase'
                      AND tr.transaction_date >= ?
                    GROUP BY tr.customer_id, p.venue_id
                ) AS purchases
                JOIN Venue v
                    ON v.venue_id = purchases.venue_id
                JOIN Users u
                    ON u.user_id = purchases.customer_id
                LEFT JOIN (
                    SELECT o.customer_id,
                           p.venue_id,
                           COUNT(*) AS num_listed
                    FROM ResaleListing rl
                    JOIN TicketOwnership o
                        ON o.ownership_id = rl.seller_ownership_id
                    JOIN Tickets t
                        ON t.ticket_id = o.ticket_id
                    JOIN Performance p
                        ON p.performance_id = t.performance_id
                    WHERE rl.listed_date >= ?
                    GROUP BY o.customer_id, p.venue_id
                ) AS listed
                    ON listed.customer_id = purchases.customer_id
                   AND listed.venue_id = purchases.venue_id
                WHERE purchases.num_purchased >= 10
                  AND COALESCE(listed.num_listed, 0) > purchases.num_purchased / 2
                ORDER BY v.city, num_listed DESC
                """;


        List<ScalperDetectionReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(oneYearAgo)
            );

            statement.setTimestamp(
                    2,
                    Timestamp.valueOf(oneYearAgo)
            );


            try (ResultSet rows = statement.executeQuery()) {


                while (rows.next()) {

                    reports.add(new ScalperDetectionReport(
                            rows.getInt("customer_id"),
                            rows.getString("name"),
                            rows.getString("city"),
                            rows.getInt("num_purchased"),
                            rows.getInt("num_listed")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Scalper detection report generated.",
                List.copyOf(reports)
        );

    });
}



 /*************************************************************************************************************
 REPORT-5
 *************************************************************************************************************/



public OperationResult<List<CustomerOrderRankingReport>> report5a(
        LocalDateTime startDate,
        LocalDateTime endDate
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT tr.customer_id,
                       u.name,
                       COUNT(*) AS num_orders
                FROM Transactions tr
                JOIN Users u
                    ON u.user_id = tr.customer_id
                WHERE tr.transaction_type = 'purchase'
                  AND tr.transaction_date BETWEEN ? AND ?
                GROUP BY tr.customer_id, u.name
                ORDER BY num_orders DESC
                """;


        List<CustomerOrderRankingReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(startDate)
            );

            statement.setTimestamp(
                    2,
                    Timestamp.valueOf(endDate)
            );


            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new CustomerOrderRankingReport(
                            rows.getInt("customer_id"),
                            rows.getString("name"),
                            null,
                            rows.getInt("num_orders")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Customer order ranking generated.",
                List.copyOf(reports)
        );

    });
}


public OperationResult<List<CustomerOrderRankingReport>> report5b(
        LocalDateTime oneYearAgo
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT tr.customer_id,
                       u.name,
                       v.city,
                       COUNT(*) AS num_orders
                FROM Transactions tr
                JOIN Users u
                    ON u.user_id = tr.customer_id
                JOIN Performance p
                    ON p.performance_id = tr.performance_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                WHERE tr.transaction_type = 'purchase'
                  AND tr.transaction_date >= ?
                GROUP BY tr.customer_id, u.name, v.city
                HAVING COUNT(*) >= 2
                ORDER BY v.city, num_orders DESC
                """;


        List<CustomerOrderRankingReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(oneYearAgo)
            );


            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new CustomerOrderRankingReport(
                            rows.getInt("customer_id"),
                            rows.getString("name"),
                            rows.getString("city"),
                            rows.getInt("num_orders")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Customer city order ranking generated.",
                List.copyOf(reports)
        );

    });
}


 /*************************************************************************************************************
 REPORT-6
 *************************************************************************************************************/
public OperationResult<List<CancellationReport>> report6a(
        LocalDateTime oneYearAgo
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT o.customer_id,
                       u.name,
                       COUNT(*) AS num_cancelled_tickets
                FROM TicketCancellation tc
                JOIN TicketOwnership o
                    ON o.ownership_id = tc.ownership_id
                JOIN Users u
                    ON u.user_id = o.customer_id
                WHERE tc.cancellation_date >= ?
                GROUP BY o.customer_id, u.name
                ORDER BY num_cancelled_tickets DESC
                """;


        List<CancellationReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(oneYearAgo)
            );


            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new CancellationReport(
                            rows.getInt("customer_id"),
                            rows.getString("name"),
                            rows.getInt("num_cancelled_tickets")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Cancelled ticket report generated.",
                List.copyOf(reports)
        );

    });
}
public OperationResult<List<CancellationReport>> report6b(
        LocalDateTime oneYearAgo
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT e.organizer_id,
                       u.name,
                       COUNT(*) AS num_cancelled_performances
                FROM Performance p
                JOIN Event e
                    ON e.event_id = p.event_id
                JOIN Users u
                    ON u.user_id = e.organizer_id
                WHERE p.status = 'cancelled'
                  AND p.cancellation_date >= ?
                GROUP BY e.organizer_id, u.name
                ORDER BY num_cancelled_performances DESC
                """;


        List<CancellationReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(oneYearAgo)
            );


            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new CancellationReport(
                            rows.getInt("organizer_id"),
                            rows.getString("name"),
                            rows.getInt("num_cancelled_performances")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Cancelled performance report generated.",
                List.copyOf(reports)
        );

    });
}



 /*************************************************************************************************************
 REPORT-8
 *************************************************************************************************************/
public OperationResult<List<ResaleReport>> report8a() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT e.event_id,
                       e.title,
                       COUNT(*) AS num_completed_resales,
                       ROUND(
                           AVG((rl.listing_price - t.face_value)
                           / t.face_value), 4
                       ) AS avg_markup_pct,
                       ROUND(
                           SUM(
                               CASE
                                   WHEN rl.listing_price = rl.cap_price_at_listing
                                   THEN 1
                                   ELSE 0
                               END
                           ) / COUNT(*), 4
                       ) AS pct_at_cap
                FROM ResaleListing rl
                JOIN Tickets t
                    ON t.ticket_id = rl.ticket_id
                JOIN Performance p
                    ON p.performance_id = t.performance_id
                JOIN Event e
                    ON e.event_id = p.event_id
                WHERE rl.status = 'sold'
                GROUP BY e.event_id, e.title
                """;


        List<ResaleReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new ResaleReport(
                            rows.getInt("event_id"),
                            rows.getString("title"),
                            rows.getInt("num_completed_resales"),
                            rows.getBigDecimal("avg_markup_pct"),
                            rows.getBigDecimal("pct_at_cap")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Event resale report generated.",
                List.copyOf(reports)
        );

    });
}

public OperationResult<List<ResaleReport>> report8b(
        LocalDateTime startDate,
        LocalDateTime endDate
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT e.event_id,
                       e.title,
                       COUNT(*) AS resale_volume
                FROM ResaleListing rl
                JOIN Tickets t
                    ON t.ticket_id = rl.ticket_id
                JOIN Performance p
                    ON p.performance_id = t.performance_id
                JOIN Event e
                    ON e.event_id = p.event_id
                WHERE rl.status = 'sold'
                  AND rl.listed_date BETWEEN ? AND ?
                GROUP BY e.event_id, e.title
                ORDER BY resale_volume DESC
                LIMIT 10
                """;


        List<ResaleReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(startDate)
            );

            statement.setTimestamp(
                    2,
                    Timestamp.valueOf(endDate)
            );


            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new ResaleReport(
                            rows.getInt("event_id"),
                            rows.getString("title"),
                            rows.getInt("resale_volume"),
                            null,
                            null
                    ));
                }
            }
        }


        return OperationResult.success(
                "Top resale volume report generated.",
                List.copyOf(reports)
        );

    });
}


 /*************************************************************************************************************
 REPORT-9
 *************************************************************************************************************/

public OperationResult<List<EventNounPhraseReport>> report9() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT e.event_id,
                       e.title,
                       r.comment_text
                FROM Reviews r
                JOIN Performance p
                    ON p.performance_id = r.performance_id
                JOIN Event e
                    ON e.event_id = p.event_id
                ORDER BY e.event_id
                """;


        List<EventCommentReport> comments =
                new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet rows =
                     statement.executeQuery()) {


            while (rows.next()) {

                comments.add(
                        new EventCommentReport(
                                rows.getInt("event_id"),
                                rows.getString("title"),
                                rows.getString("comment_text")
                        )
                );
            }
        }


        NounPhraseAnalyzer analyzer =
                new NounPhraseAnalyzer();


        return OperationResult.success(
                "Event word cloud report generated.",
                analyzer.analyze(comments)
        );

    });
}


/*************************************************************************************************************
 REPORT-7
 *************************************************************************************************************/

public OperationResult<List<SellThroughReport>> report7a() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT p.performance_id,
                       e.title,
                       v.city,
                       sellable.capacity,
                       COALESCE(sold.num_sold, 0) AS num_sold,
                       ROUND(
                           COALESCE(sold.num_sold, 0) / sellable.capacity,
                           4
                       ) AS sell_through_rate
                FROM Performance p
                JOIN Event e
                    ON e.event_id = p.event_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                JOIN (
                    SELECT p2.performance_id,
                           COALESCE(seats.n, 0)
                           + COALESCE(ga.total_capacity, 0) AS capacity
                    FROM Performance p2
                    LEFT JOIN (
                        SELECT performance_id,
                               COUNT(*) AS n
                        FROM PerformanceSeats
                        WHERE blocked_status = FALSE
                        GROUP BY performance_id
                    ) seats
                        ON seats.performance_id = p2.performance_id
                    LEFT JOIN GeneralAdmissionCapacity ga
                        ON ga.performance_id = p2.performance_id
                ) sellable
                    ON sellable.performance_id = p.performance_id
                LEFT JOIN (
                    SELECT performance_id,
                           COUNT(*) AS num_sold
                    FROM Tickets
                    WHERE status = 'active'
                    GROUP BY performance_id
                ) sold
                    ON sold.performance_id = p.performance_id
                WHERE sellable.capacity > 0
                """;


        List<SellThroughReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet rows = statement.executeQuery()) {


            while (rows.next()) {

                reports.add(new SellThroughReport(
                        rows.getInt("performance_id"),
                        rows.getString("title"),
                        rows.getString("city"),
                        rows.getInt("capacity"),
                        rows.getInt("num_sold"),
                        rows.getBigDecimal("sell_through_rate")
                ));
            }
        }


        return OperationResult.success(
                "Performance sell-through report generated.",
                List.copyOf(reports)
        );

    });
}


public OperationResult<List<SellThroughTierReport>> report7b() {

    return transactions.execute(connection -> {

        String sql = """
                SELECT p.performance_id,
                       pt.tier_code,
                       tier_cap.capacity,
                       COALESCE(tier_sold.num_sold, 0) AS num_sold,
                       ROUND(
                           COALESCE(tier_sold.num_sold, 0)
                           / tier_cap.capacity,
                           4
                       ) AS sell_through_rate
                FROM Performance p
                JOIN PriceTier pt
                    ON pt.performance_id = p.performance_id
                JOIN (
                    SELECT sta.performance_id,
                           sta.tier_code,
                           SUM(
                               CASE
                                   WHEN s.section_type = 'reserved'
                                   THEN seat_ct.n
                                   ELSE ga.total_capacity
                               END
                           ) AS capacity
                    FROM SectionTierAssignment sta
                    JOIN Section s
                        ON s.venue_id = sta.venue_id
                       AND s.section_name = sta.section_name
                    LEFT JOIN (
                        SELECT performance_id,
                               venue_id,
                               section_name,
                               COUNT(*) AS n
                        FROM PerformanceSeats
                        WHERE blocked_status = FALSE
                        GROUP BY performance_id,
                                 venue_id,
                                 section_name
                    ) seat_ct
                        ON seat_ct.performance_id = sta.performance_id
                       AND seat_ct.venue_id = sta.venue_id
                       AND seat_ct.section_name = sta.section_name
                    LEFT JOIN GeneralAdmissionCapacity ga
                        ON ga.performance_id = sta.performance_id
                       AND ga.venue_id = sta.venue_id
                       AND ga.section_name = sta.section_name
                    GROUP BY sta.performance_id,
                             sta.tier_code
                ) tier_cap
                    ON tier_cap.performance_id = pt.performance_id
                   AND tier_cap.tier_code = pt.tier_code
                LEFT JOIN (
                    SELECT performance_id,
                           tier_code,
                           COUNT(*) AS num_sold
                    FROM Tickets
                    WHERE status = 'active'
                    GROUP BY performance_id,
                             tier_code
                ) tier_sold
                    ON tier_sold.performance_id = pt.performance_id
                   AND tier_sold.tier_code = pt.tier_code
                """;


        List<SellThroughTierReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql);

             ResultSet rows = statement.executeQuery()) {


            while (rows.next()) {

                reports.add(new SellThroughTierReport(
                        rows.getInt("performance_id"),
                        rows.getString("tier_code"),
                        rows.getInt("capacity"),
                        rows.getInt("num_sold"),
                        rows.getBigDecimal("sell_through_rate")
                ));
            }
        }


        return OperationResult.success(
                "Tier sell-through report generated.",
                List.copyOf(reports)
        );

    });
}


public OperationResult<List<SellThroughBucketReport>> report7c(
        int year,
        int month
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT v.city,
                       p.performance_id,
                       e.title,
                       ROUND(
                           COALESCE(sold.num_sold, 0)
                           / sellable.capacity,
                           4
                       ) AS sell_through_rate,
                       CASE
                           WHEN COALESCE(sold.num_sold, 0)
                                >= sellable.capacity
                           THEN 'sold_out'
                           WHEN COALESCE(sold.num_sold, 0)
                                < sellable.capacity * 0.25
                           THEN 'under_quarter'
                       END AS bucket
                FROM Performance p
                JOIN Event e
                    ON e.event_id = p.event_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                JOIN (
                    SELECT p2.performance_id,
                           COALESCE(seats.n, 0)
                           + COALESCE(ga.total_capacity, 0) AS capacity
                    FROM Performance p2
                    LEFT JOIN (
                        SELECT performance_id,
                               COUNT(*) AS n
                        FROM PerformanceSeats
                        WHERE blocked_status = FALSE
                        GROUP BY performance_id
                    ) seats
                        ON seats.performance_id = p2.performance_id
                    LEFT JOIN GeneralAdmissionCapacity ga
                        ON ga.performance_id = p2.performance_id
                ) sellable
                    ON sellable.performance_id = p.performance_id
                LEFT JOIN (
                    SELECT performance_id,
                           COUNT(*) AS num_sold
                    FROM Tickets
                    WHERE status = 'active'
                    GROUP BY performance_id
                ) sold
                    ON sold.performance_id = p.performance_id
                WHERE sellable.capacity > 0
                  AND YEAR(p.date_time) = ?
                  AND MONTH(p.date_time) = ?
                HAVING bucket IS NOT NULL
                ORDER BY v.city
                """;


        List<SellThroughBucketReport> reports = new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setInt(1, year);
            statement.setInt(2, month);


            try (ResultSet rows = statement.executeQuery()) {

                while (rows.next()) {

                    reports.add(new SellThroughBucketReport(
                            rows.getString("city"),
                            rows.getInt("performance_id"),
                            rows.getString("title"),
                            rows.getBigDecimal("sell_through_rate"),
                            rows.getString("bucket")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Monthly sell-through bucket report generated.",
                List.copyOf(reports)
        );

    });
}
}