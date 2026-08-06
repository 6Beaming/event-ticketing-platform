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




}
