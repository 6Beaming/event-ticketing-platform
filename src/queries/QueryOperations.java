package queries;

import database.TransactionManager;

import common.OperationResult;
import database.TransactionManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class QueryOperations {

    private final TransactionManager transactions;

    public QueryOperations(TransactionManager transactions) {
        this.transactions = transactions;
    }


/*************************************************************************************************************
 QUERY-1
 Upcoming performances near location
 *************************************************************************************************************/
public OperationResult<List<UpcomingPerformanceQuery>> query1(
        double latitude,
        double longitude,
        double radiusKm,
        String sortBy
) {

    return transactions.execute(connection -> {

        String sql = """
SELECT p.performance_id, e.title, v.name AS venue_name, v.city,
       ROUND(
         6371 * ACOS(
           COS(RADIANS(?)) * COS(RADIANS(v.latitude)) * COS(RADIANS(v.longitude) - RADIANS(?))
           + SIN(RADIANS(?)) * SIN(RADIANS(v.latitude))
         ), 2
       ) AS distance_km,
       cheapest.min_price AS cheapest_available_price
FROM Performance p
JOIN Event e ON e.event_id = p.event_id
JOIN Venue v ON v.venue_id = p.venue_id
LEFT JOIN (
    SELECT pt.performance_id, MIN(pt.price) AS min_price
    FROM PriceTier pt
    JOIN SectionTierAssignment sta 
        ON sta.performance_id = pt.performance_id 
        AND sta.tier_code = pt.tier_code
    JOIN Section s 
        ON s.venue_id = sta.venue_id 
        AND s.section_name = sta.section_name
    LEFT JOIN (
        SELECT performance_id, venue_id, section_name,
               SUM(
                   CASE 
                       WHEN blocked_status = FALSE
                        AND performance_seat_id NOT IN (
                             SELECT performance_seats_ref 
                             FROM Tickets
                             WHERE status = 'active' 
                             AND performance_seats_ref IS NOT NULL
                        )
                       THEN 1 
                       ELSE 0 
                   END
               ) AS avail_count
        FROM PerformanceSeats
        GROUP BY performance_id, venue_id, section_name
    ) seat_avail 
        ON seat_avail.performance_id = sta.performance_id
        AND seat_avail.venue_id = sta.venue_id
        AND seat_avail.section_name = sta.section_name
    LEFT JOIN GeneralAdmissionCapacity ga 
        ON ga.performance_id = sta.performance_id
        AND ga.venue_id = sta.venue_id
        AND ga.section_name = sta.section_name
    WHERE (s.section_type = 'reserved' 
           AND COALESCE(seat_avail.avail_count, 0) > 0)
       OR (s.section_type = 'general'  
           AND ga.remaining_capacity > 0)
    GROUP BY pt.performance_id
) cheapest 
    ON cheapest.performance_id = p.performance_id
WHERE p.status = 'scheduled'
  AND p.date_time > NOW()
HAVING distance_km <= ?
ORDER BY
  CASE 
      WHEN ? IN ('price_asc','price_desc') 
      AND cheapest_available_price IS NULL 
      THEN 1 
      ELSE 0 
  END,
  CASE 
      WHEN ? = 'distance' 
      THEN distance_km 
  END ASC,
  CASE 
      WHEN ? = 'price_asc' 
      THEN cheapest_available_price 
  END ASC,
  CASE 
      WHEN ? = 'price_desc' 
      THEN cheapest_available_price 
  END DESC;
                """;


        List<UpcomingPerformanceQuery> results =
                new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


              statement.setDouble(1, latitude);
    statement.setDouble(2, longitude);
    statement.setDouble(3, latitude);

    statement.setDouble(4, radiusKm);

    statement.setString(5, sortBy);
    statement.setString(6, sortBy);
    statement.setString(7, sortBy);
    statement.setString(8, sortBy);



            try (ResultSet rows =
                         statement.executeQuery()) {


                while (rows.next()) {

                    results.add(new UpcomingPerformanceQuery(
                            rows.getInt("performance_id"),
                            rows.getString("title"),
                            rows.getString("venue_name"),
                            rows.getString("city"),
                            rows.getDouble("distance_km"),
                            rows.getDouble("cheapest_available_price")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Upcoming performances retrieved.",
                List.copyOf(results)
        );

    });
}

/*************************************************************************************************************
 QUERY-2
 Upcoming performances by postal code
 *************************************************************************************************************/
public OperationResult<List<PostalCodePerformanceQuery>> query2(
        String postalCode
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT p.performance_id,
                       e.title,
                       v.name AS venue_name,
                       v.postal_code,
                       v.city,
                       p.date_time

                FROM Performance p

                JOIN Event e
                    ON e.event_id = p.event_id

                JOIN Venue v
                    ON v.venue_id = p.venue_id

                WHERE p.status = 'scheduled'
                  AND p.date_time > NOW()
                  AND LEFT(v.postal_code, 3) = LEFT(?, 3)

                ORDER BY v.postal_code, p.date_time
                """;


        List<PostalCodePerformanceQuery> results =
                new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setString(
                    1,
                    postalCode
            );


            try (ResultSet rows =
                         statement.executeQuery()) {


                while (rows.next()) {

                    results.add(new PostalCodePerformanceQuery(
                            rows.getInt("performance_id"),
                            rows.getString("title"),
                            rows.getString("venue_name"),
                            rows.getString("postal_code"),
                            rows.getString("city"),
                            rows.getTimestamp("date_time")
                                 .toLocalDateTime()
                    ));
                }
            }
        }


        return OperationResult.success(
                "Postal code performance search completed.",
                List.copyOf(results)
        );

    });
}
    /*************************************************************************************************************
 QUERY-3
 Exact address search
 *************************************************************************************************************/
public OperationResult<List<AddressPerformanceQuery>> query3(
        String address
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT v.venue_id,
                       v.name,
                       v.address,
                       v.city,
                       v.country,
                       p.performance_id,
                       e.title,
                       p.date_time

                FROM Venue v

                LEFT JOIN Performance p
                    ON p.venue_id = v.venue_id
                    AND p.status = 'scheduled'
                    AND p.date_time > NOW()

                LEFT JOIN Event e
                    ON e.event_id = p.event_id

                WHERE v.address = ?

                ORDER BY p.date_time
                """;


        List<AddressPerformanceQuery> results =
                new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setString(
                    1,
                    address
            );


            try (ResultSet rows =
                         statement.executeQuery()) {


                while (rows.next()) {

                    LocalDateTime dateTime = null;

                    if (rows.getTimestamp("date_time") != null) {
                        dateTime =
                                rows.getTimestamp("date_time")
                                        .toLocalDateTime();
                    }


                    results.add(new AddressPerformanceQuery(
                            rows.getInt("venue_id"),
                            rows.getString("name"),
                            rows.getString("address"),
                            rows.getString("city"),
                            rows.getString("country"),
                            rows.getInt("performance_id"),
                            rows.getString("title"),
                            dateTime
                    ));
                }
            }
        }


        return OperationResult.success(
                "Address search completed.",
                List.copyOf(results)
        );

    });
}


/*************************************************************************************************************
 QUERY-4
 Date range and minimum available tickets refinement
 *************************************************************************************************************/
public OperationResult<List<DateRangePerformanceQuery>> query4(
        String postalCode,
        LocalDateTime startDate,
        LocalDateTime endDate,
        int minTickets
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT p.performance_id,
                       e.title,
                       v.name AS venue_name,
                       v.postal_code,
                       avail.total_available

                FROM Performance p

                JOIN Event e
                    ON e.event_id = p.event_id

                JOIN Venue v
                    ON v.venue_id = p.venue_id

                JOIN (
                    SELECT p2.performance_id,
                           COALESCE(seats.avail, 0)
                           + COALESCE(ga.avail, 0) AS total_available

                    FROM Performance p2

                    LEFT JOIN (
                        SELECT ps.performance_id,
                               SUM(
                                   CASE
                                       WHEN ps.blocked_status = FALSE
                                       AND ps.performance_seat_id NOT IN (
                                           SELECT performance_seats_ref
                                           FROM Tickets
                                           WHERE status = 'active'
                                           AND performance_seats_ref IS NOT NULL
                                       )
                                       THEN 1
                                       ELSE 0
                                   END
                               ) AS avail

                        FROM PerformanceSeats ps

                        GROUP BY ps.performance_id

                    ) seats
                        ON seats.performance_id = p2.performance_id

                    LEFT JOIN (
                        SELECT performance_id,
                               SUM(remaining_capacity) AS avail

                        FROM GeneralAdmissionCapacity

                        GROUP BY performance_id

                    ) ga
                        ON ga.performance_id = p2.performance_id

                ) avail
                    ON avail.performance_id = p.performance_id

                WHERE p.status = 'scheduled'
                  AND LEFT(v.postal_code, 3) = LEFT(?, 3)
                  AND p.date_time BETWEEN ? AND ?
                  AND avail.total_available >= ?

                ORDER BY p.date_time
                """;


        List<DateRangePerformanceQuery> results =
                new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setString(1, postalCode);

            statement.setTimestamp(
                    2,
                    Timestamp.valueOf(startDate)
            );

            statement.setTimestamp(
                    3,
                    Timestamp.valueOf(endDate)
            );

            statement.setInt(
                    4,
                    minTickets
            );


            try (ResultSet rows =
                         statement.executeQuery()) {


                while (rows.next()) {

                    results.add(new DateRangePerformanceQuery(
                            rows.getInt("performance_id"),
                            rows.getString("title"),
                            rows.getString("venue_name"),
                            rows.getString("postal_code"),
                            rows.getInt("total_available")
                    ));
                }
            }
        }


        return OperationResult.success(
                "Date range performance search completed.",
                List.copyOf(results)
        );

    });
}

/*************************************************************************************************************
 QUERY-5
 Filtered performance search
 *************************************************************************************************************/
public OperationResult<List<FilteredPerformanceQuery>> query5(
        String city,
        String segment,
        String genre,
        LocalDateTime startDate,
        LocalDateTime endDate,
        double minPrice,
        double maxPrice,
        int minAvailable,
        String sectionType
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT p.performance_id, e.title, v.city, sg.segment_name, g.genre_name,
                       cheapest.min_price AS cheapest_available_price,
                       avail.total_available
                FROM Performance p
                JOIN Event e    ON e.event_id = p.event_id
                JOIN Genre g    ON g.genre_id = e.genre_id
                JOIN Segment sg ON sg.segment_id = g.segment_id
                JOIN Venue v    ON v.venue_id = p.venue_id
                JOIN (
                    SELECT pt.performance_id, MIN(pt.price) AS min_price
                    FROM PriceTier pt
                    JOIN SectionTierAssignment sta
                        ON sta.performance_id = pt.performance_id
                       AND sta.tier_code = pt.tier_code
                    JOIN Section s
                        ON s.venue_id = sta.venue_id
                       AND s.section_name = sta.section_name
                    LEFT JOIN (
                        SELECT performance_id, venue_id, section_name,
                               SUM(
                                   CASE
                                       WHEN blocked_status = FALSE
                                        AND performance_seat_id NOT IN (
                                            SELECT performance_seats_ref
                                            FROM Tickets
                                            WHERE status = 'active'
                                              AND performance_seats_ref IS NOT NULL
                                        )
                                       THEN 1
                                       ELSE 0
                                   END
                               ) AS avail_count
                        FROM PerformanceSeats
                        GROUP BY performance_id, venue_id, section_name
                    ) seat_avail
                        ON seat_avail.performance_id = sta.performance_id
                       AND seat_avail.venue_id = sta.venue_id
                       AND seat_avail.section_name = sta.section_name
                    LEFT JOIN GeneralAdmissionCapacity ga
                        ON ga.performance_id = sta.performance_id
                       AND ga.venue_id = sta.venue_id
                       AND ga.section_name = sta.section_name
                    WHERE (? IS NULL OR s.section_type = ?)
                      AND (
                            (s.section_type = 'reserved'
                             AND COALESCE(seat_avail.avail_count, 0) > 0)
                         OR (s.section_type = 'general'
                             AND ga.remaining_capacity > 0)
                      )
                    GROUP BY pt.performance_id
                ) cheapest
                    ON cheapest.performance_id = p.performance_id
                JOIN (
                    SELECT p2.performance_id,
                           COALESCE(seats.avail, 0)
                           + COALESCE(ga.avail, 0) AS total_available
                    FROM Performance p2
                    LEFT JOIN (
                        SELECT ps.performance_id,
                               SUM(
                                   CASE
                                       WHEN ps.blocked_status = FALSE
                                        AND ps.performance_seat_id NOT IN (
                                            SELECT performance_seats_ref
                                            FROM Tickets
                                            WHERE status = 'active'
                                              AND performance_seats_ref IS NOT NULL
                                        )
                                       THEN 1
                                       ELSE 0
                                   END
                               ) AS avail
                        FROM PerformanceSeats ps
                        JOIN Section s2
                            ON s2.venue_id = ps.venue_id
                           AND s2.section_name = ps.section_name
                        WHERE (? IS NULL OR s2.section_type = ?)
                        GROUP BY ps.performance_id
                    ) seats
                        ON seats.performance_id = p2.performance_id
                    LEFT JOIN (
                        SELECT performance_id,
                               SUM(remaining_capacity) AS avail
                        FROM GeneralAdmissionCapacity
                        WHERE (? IS NULL OR section_type = ?)
                        GROUP BY performance_id
                    ) ga
                        ON ga.performance_id = p2.performance_id
                ) avail
                    ON avail.performance_id = p.performance_id
                WHERE p.status = 'scheduled'
                  AND v.city = ?
                  AND sg.segment_name = ?
                  AND g.genre_name = ?
                  AND p.date_time BETWEEN ? AND ?
                  AND cheapest.min_price BETWEEN ? AND ?
                  AND avail.total_available >= ?
                ORDER BY p.date_time
                """;


        List<FilteredPerformanceQuery> results =
                new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, sectionType);
            statement.setString(2, sectionType);

            statement.setString(3, sectionType);
            statement.setString(4, sectionType);

            statement.setString(5, sectionType);
            statement.setString(6, sectionType);

            statement.setString(7, city);
            statement.setString(8, segment);
            statement.setString(9, genre);

            statement.setTimestamp(
                    10,
                    Timestamp.valueOf(startDate)
            );

            statement.setTimestamp(
                    11,
                    Timestamp.valueOf(endDate)
            );

            statement.setDouble(12, minPrice);
            statement.setDouble(13, maxPrice);

            statement.setInt(14, minAvailable);


            try (ResultSet rows =
                         statement.executeQuery()) {

                while (rows.next()) {

                    results.add(
                            new FilteredPerformanceQuery(
                                    rows.getInt("performance_id"),
                                    rows.getString("title"),
                                    rows.getString("city"),
                                    rows.getString("segment_name"),
                                    rows.getString("genre_name"),
                                    rows.getDouble("cheapest_available_price"),
                                    rows.getInt("total_available")
                            )
                    );
                }
            }
        }


        return OperationResult.success(
                "Filtered performance search completed.",
                List.copyOf(results)
        );

    });
}

/*************************************************************************************************************
 QUERY-6
 Seat map summary
 *************************************************************************************************************/
public OperationResult<List<SeatMapSummaryQuery>> query6(
        int performanceId
) {

    return transactions.execute(connection -> {

        String sql = """
                SELECT sta.section_name,
                       sta.tier_code,
                       pt.price,
                       SUM(CASE WHEN ps.blocked_status = FALSE
                                 AND ps.performance_seat_id NOT IN (
                                     SELECT performance_seats_ref
                                     FROM Tickets
                                     WHERE status='active'
                                       AND performance_seats_ref IS NOT NULL)
                                THEN 1 ELSE 0 END) AS available,
                       SUM(CASE WHEN ps.performance_seat_id IN (
                                     SELECT performance_seats_ref
                                     FROM Tickets
                                     WHERE status='active'
                                       AND performance_seats_ref IS NOT NULL)
                                THEN 1 ELSE 0 END) AS sold,
                       SUM(CASE WHEN ps.blocked_status = TRUE
                                THEN 1 ELSE 0 END) AS blocked
                FROM SectionTierAssignment sta
                JOIN PriceTier pt
                    ON pt.performance_id = sta.performance_id
                   AND pt.tier_code = sta.tier_code
                JOIN Section s
                    ON s.venue_id = sta.venue_id
                   AND s.section_name = sta.section_name
                   AND s.section_type = 'reserved'
                JOIN PerformanceSeats ps
                    ON ps.performance_id = sta.performance_id
                   AND ps.venue_id = sta.venue_id
                   AND ps.section_name = sta.section_name
                WHERE sta.performance_id = ?
                GROUP BY sta.section_name,
                         sta.tier_code,
                         pt.price

                UNION ALL

                SELECT sta.section_name,
                       sta.tier_code,
                       pt.price,
                       ga.remaining_capacity AS available,
                       ga.total_capacity - ga.remaining_capacity AS sold,
                       0 AS blocked
                FROM SectionTierAssignment sta
                JOIN PriceTier pt
                    ON pt.performance_id = sta.performance_id
                   AND pt.tier_code = sta.tier_code
                JOIN Section s
                    ON s.venue_id = sta.venue_id
                   AND s.section_name = sta.section_name
                   AND s.section_type = 'general'
                JOIN GeneralAdmissionCapacity ga
                    ON ga.performance_id = sta.performance_id
                   AND ga.venue_id = sta.venue_id
                   AND ga.section_name = sta.section_name
                WHERE sta.performance_id = ?
                """;


        List<SeatMapSummaryQuery> results =
                new ArrayList<>();


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, performanceId);
            statement.setInt(2, performanceId);

            try (ResultSet rows =
                         statement.executeQuery()) {

                while (rows.next()) {

                    results.add(
                            new SeatMapSummaryQuery(
                                    rows.getString("section_name"),
                                    rows.getString("tier_code"),
                                    rows.getDouble("price"),
                                    rows.getInt("available"),
                                    rows.getInt("sold"),
                                    rows.getInt("blocked")
                            )
                    );
                }
            }
        }


        return OperationResult.success(
                "Seat map summary generated.",
                List.copyOf(results)
        );

    });

}
/*************************************************************************************************************
 QUERY-7
 Best available consecutive seats
 *************************************************************************************************************/
public OperationResult<BestAvailableQuery> query7(
        int performanceId,
        int q,
        Double budget
) {

    return transactions.execute(connection -> {

        String sql = """
                WITH available_seats AS (

                    SELECT ps.performance_id,
                           ps.venue_id,
                           ps.section_name,
                           ps.row_name,
                           ps.seat_number,
                           pt.price,

                           ps.seat_number - ROW_NUMBER() OVER (
                               PARTITION BY ps.venue_id,
                                            ps.section_name,
                                            ps.row_name
                               ORDER BY ps.seat_number
                           ) AS grp

                    FROM PerformanceSeats ps

                    JOIN SectionTierAssignment sta
                        ON sta.performance_id = ps.performance_id
                       AND sta.venue_id = ps.venue_id
                       AND sta.section_name = ps.section_name

                    JOIN PriceTier pt
                        ON pt.performance_id = sta.performance_id
                       AND pt.tier_code = sta.tier_code

                    WHERE ps.performance_id = ?
                      AND ps.blocked_status = FALSE
                      AND ps.performance_seat_id NOT IN (
                          SELECT performance_seats_ref
                          FROM Tickets
                          WHERE status='active'
                            AND performance_seats_ref IS NOT NULL
                      )
                ),

                runs AS (

                    SELECT venue_id,
                           section_name,
                           row_name,
                           grp,
                           price,
                           MIN(seat_number) AS start_seat,
                           COUNT(*) AS run_length,
                           COUNT(*) * price AS total_price

                    FROM available_seats

                    GROUP BY venue_id,
                             section_name,
                             row_name,
                             grp,
                             price

                    HAVING COUNT(*) >= ?
                )

                SELECT section_name,
                       row_name,
                       start_seat,
                       start_seat + ? - 1 AS end_seat,
                       ? * price AS total_price

                FROM runs

                WHERE (? IS NULL OR ? * price <= ?)

                ORDER BY total_price ASC

                LIMIT 1
                """;


        BestAvailableQuery result = null;


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {


            statement.setInt(1, performanceId);

            statement.setInt(2, q);

            statement.setInt(3, q);

            statement.setInt(4, q);


            if (budget == null) {
                statement.setNull(5, java.sql.Types.DOUBLE);
                statement.setNull(6, java.sql.Types.INTEGER);
                statement.setNull(7, java.sql.Types.DOUBLE);
            }
            else {
                statement.setDouble(5, budget);
                statement.setInt(6, q);
                statement.setDouble(7, budget);
            }


            try (ResultSet rows =
                         statement.executeQuery()) {


                if (rows.next()) {

                    result = new BestAvailableQuery(
                            rows.getString("section_name"),
                            rows.getString("row_name"),
                            rows.getInt("start_seat"),
                            rows.getInt("end_seat"),
                            rows.getDouble("total_price")
                    );
                }
            }
        }


        return OperationResult.success(
                "Best available seat search completed.",
                result
        );

    });
}
}
