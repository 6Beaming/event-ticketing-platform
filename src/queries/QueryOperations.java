package queries;

import common.OperationResult;
import database.TransactionManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QueryOperations {

    private final TransactionManager transactions;

    public QueryOperations(TransactionManager transactions) {
        this.transactions = transactions;
    }

    public static String validateQuery1(
            double latitude,
            double longitude,
            double radiusKm,
            String sortBy
    ) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            return "Latitude must be between -90 and 90.";
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            return "Longitude must be between -180 and 180.";
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0) {
            return "Search distance must be greater than zero.";
        }
        if (!"distance".equals(sortBy)
                && !"price_asc".equals(sortBy)
                && !"price_desc".equals(sortBy)) {
            return "Select a supported sort option.";
        }
        return null;
    }

    public static String validateQuery2(String postalCode) {
        return isBlank(postalCode) ? "Postal code is required." : null;
    }

    public static String validateQuery3(String address) {
        return isBlank(address) ? "Address is required." : null;
    }

    public static String validateQuery6(int performanceId) {
        return performanceId <= 0 ? "Performance ID must be positive." : null;
    }

    public static String validateQuery7(int performanceId, int quantity, Double budget) {
        if (performanceId <= 0) {
            return "Performance ID must be positive.";
        }
        if (quantity <= 0) {
            return "Number of seats required must be positive.";
        }
        if (budget != null && (!Double.isFinite(budget) || budget <= 0)) {
            return "Budget must be positive when supplied.";
        }
        return null;
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
    String validationError = validateQuery1(latitude, longitude, radiusKm, sortBy);
    if (validationError != null) {
        return OperationResult.invalidInput(validationError);
    }

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
    String validationError = validateQuery2(postalCode);
    if (validationError != null) {
        return OperationResult.invalidInput(validationError);
    }

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
    String validationError = validateQuery3(address);
    if (validationError != null) {
        return OperationResult.invalidInput(validationError);
    }

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
 Date range and minimum available tickets refinement of Q1-Q3
 *************************************************************************************************************/
public OperationResult<List<DateRangePerformanceQuery>> query4(
        LocationSearchInput location,
        LocalDateTime startDate,
        LocalDateTime endDate,
        int minTickets
) {
    String validationError = validateQuery4(
            location,
            startDate,
            endDate,
            minTickets
    );
    if (validationError != null) {
        return OperationResult.invalidInput(validationError);
    }

    return transactions.execute(connection -> {
        boolean coordinateSearch =
                location.getType() == LocationSearchInput.Type.COORDINATES;

        String distanceExpression = coordinateSearch
                ? """
                  ROUND(
                      6371 * ACOS(
                          LEAST(1.0, GREATEST(-1.0,
                              COS(RADIANS(?))
                              * COS(RADIANS(v.latitude))
                              * COS(RADIANS(v.longitude) - RADIANS(?))
                              + SIN(RADIANS(?))
                              * SIN(RADIANS(v.latitude))
                          ))
                      ),
                      2
                  )
                  """
                : "NULL";

        StringBuilder sql = new StringBuilder(availabilityCtes(""));
        sql.append("""
                SELECT p.performance_id,
                       e.title,
                       v.name AS venue_name,
                       v.address,
                       v.postal_code,
                       v.city,
                       p.date_time,
                       availability.total_available,
                       availability.cheapest_available_price,
                       %s AS distance_km
                FROM Performance p
                JOIN Event e
                    ON e.event_id = p.event_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                JOIN performance_availability availability
                    ON availability.performance_id = p.performance_id
                WHERE p.status = 'scheduled'
                  AND p.date_time > NOW()
                  AND p.date_time >= ?
                  AND p.date_time < ?
                  AND availability.total_available >= ?
                """.formatted(distanceExpression));

        switch (location.getType()) {
            case COORDINATES -> sql.append("HAVING distance_km <= ?\n");
            case POSTAL_CODE -> sql.append("""
                      AND LEFT(REPLACE(v.postal_code, ' ', ''), 3)
                          = LEFT(REPLACE(?, ' ', ''), 3)
                    """);
            case ADDRESS -> sql.append("  AND v.address = ?\n");
        }

        if (coordinateSearch) {
            switch (location.getSortBy()) {
                case "price_asc" -> sql.append(
                        "ORDER BY cheapest_available_price ASC, distance_km ASC, p.date_time ASC");
                case "price_desc" -> sql.append(
                        "ORDER BY cheapest_available_price DESC, distance_km ASC, p.date_time ASC");
                default -> sql.append("ORDER BY distance_km ASC, p.date_time ASC");
            }
        } else {
            sql.append("ORDER BY p.date_time ASC");
        }

        List<DateRangePerformanceQuery> results = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {
            int parameter = 1;

            if (coordinateSearch) {
                statement.setDouble(parameter++, location.getLatitude());
                statement.setDouble(parameter++, location.getLongitude());
                statement.setDouble(parameter++, location.getLatitude());
            }

            statement.setTimestamp(parameter++, Timestamp.valueOf(startDate));
            statement.setTimestamp(parameter++, Timestamp.valueOf(endDate));
            statement.setInt(parameter++, minTickets);

            switch (location.getType()) {
                case COORDINATES ->
                        statement.setDouble(parameter, location.getRadiusKm());
                case POSTAL_CODE ->
                        statement.setString(parameter, location.getPostalCode().trim());
                case ADDRESS ->
                        statement.setString(parameter, location.getAddress().trim());
            }

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    results.add(new DateRangePerformanceQuery(
                            rows.getInt("performance_id"),
                            rows.getString("title"),
                            rows.getString("venue_name"),
                            rows.getString("address"),
                            rows.getString("postal_code"),
                            rows.getString("city"),
                            rows.getTimestamp("date_time").toLocalDateTime(),
                            rows.getInt("total_available"),
                            rows.getDouble("cheapest_available_price"),
                            rows.getObject("distance_km") == null
                                    ? null
                                    : rows.getDouble("distance_km")
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
        Double minPrice,
        Double maxPrice,
        Integer minAvailable,
        String sectionType
) {
    String normalizedCity = normalizeOptional(city);
    String normalizedSegment = normalizeOptional(segment);
    String normalizedGenre = normalizeOptional(genre);
    String normalizedSectionType = normalizeOptional(sectionType);
    if (normalizedSectionType != null) {
        normalizedSectionType = normalizedSectionType.toLowerCase(Locale.ROOT);
    }

    String validationError = validateQuery5(
            startDate,
            endDate,
            minPrice,
            maxPrice,
            minAvailable,
            normalizedSectionType
    );
    if (validationError != null) {
        return OperationResult.invalidInput(validationError);
    }

    String finalSectionType = normalizedSectionType;

    return transactions.execute(connection -> {
        String sectionFilter = finalSectionType == null
                ? ""
                : "  AND section_type = ?\n";
        StringBuilder sql = new StringBuilder(availabilityCtes(sectionFilter));
        sql.append("""
                SELECT p.performance_id,
                       e.title,
                       v.name AS venue_name,
                       v.city,
                       sg.segment_name,
                       g.genre_name,
                       p.date_time,
                       availability.cheapest_available_price,
                       availability.total_available
                FROM Performance p
                JOIN Event e
                    ON e.event_id = p.event_id
                JOIN Genre g
                    ON g.genre_id = e.genre_id
                JOIN Segment sg
                    ON sg.segment_id = g.segment_id
                JOIN Venue v
                    ON v.venue_id = p.venue_id
                JOIN performance_availability availability
                    ON availability.performance_id = p.performance_id
                WHERE p.status = 'scheduled'
                  AND p.date_time > NOW()
                """);

        if (normalizedCity != null) {
            sql.append("  AND v.city = ?\n");
        }
        if (normalizedSegment != null) {
            sql.append("  AND sg.segment_name = ?\n");
        }
        if (normalizedGenre != null) {
            sql.append("  AND g.genre_name = ?\n");
        }
        if (startDate != null) {
            sql.append("  AND p.date_time >= ?\n");
            sql.append("  AND p.date_time < ?\n");
        }
        if (minPrice != null) {
            sql.append("  AND availability.cheapest_available_price >= ?\n");
        }
        if (maxPrice != null) {
            sql.append("  AND availability.cheapest_available_price <= ?\n");
        }
        if (minAvailable != null) {
            sql.append("  AND availability.total_available >= ?\n");
        }
        sql.append("ORDER BY p.date_time ASC");

        List<FilteredPerformanceQuery> results = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {
            int parameter = 1;

            if (finalSectionType != null) {
                statement.setString(parameter++, finalSectionType);
            }
            if (normalizedCity != null) {
                statement.setString(parameter++, normalizedCity);
            }
            if (normalizedSegment != null) {
                statement.setString(parameter++, normalizedSegment);
            }
            if (normalizedGenre != null) {
                statement.setString(parameter++, normalizedGenre);
            }
            if (startDate != null) {
                statement.setTimestamp(parameter++, Timestamp.valueOf(startDate));
                statement.setTimestamp(parameter++, Timestamp.valueOf(endDate));
            }
            if (minPrice != null) {
                statement.setDouble(parameter++, minPrice);
            }
            if (maxPrice != null) {
                statement.setDouble(parameter++, maxPrice);
            }
            if (minAvailable != null) {
                statement.setInt(parameter, minAvailable);
            }

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    results.add(new FilteredPerformanceQuery(
                            rows.getInt("performance_id"),
                            rows.getString("title"),
                            rows.getString("venue_name"),
                            rows.getString("city"),
                            rows.getString("segment_name"),
                            rows.getString("genre_name"),
                            rows.getTimestamp("date_time").toLocalDateTime(),
                            rows.getDouble("cheapest_available_price"),
                            rows.getInt("total_available")
                    ));
                }
            }
        }

        return OperationResult.success(
                "Filtered performance search completed.",
                List.copyOf(results)
        );
    });
}

public static String validateQuery4(
        LocationSearchInput location,
        LocalDateTime startDate,
        LocalDateTime endDate,
        int minTickets
) {
    if (location == null || location.getType() == null) {
        return "A location search type is required.";
    }
    if (startDate == null || endDate == null || !startDate.isBefore(endDate)) {
        return "Start date/time must be before end date/time.";
    }
    if (minTickets <= 0) {
        return "Minimum available tickets must be a positive whole number.";
    }

    return switch (location.getType()) {
        case COORDINATES -> validateCoordinateLocation(location);
        case POSTAL_CODE -> isBlank(location.getPostalCode())
                ? "Postal code is required."
                : null;
        case ADDRESS -> isBlank(location.getAddress())
                ? "Address is required."
                : null;
    };
}

public static String validateQuery5(
        LocalDateTime startDate,
        LocalDateTime endDate,
        Double minPrice,
        Double maxPrice,
        Integer minAvailable,
        String sectionType
) {
    if ((startDate == null) != (endDate == null)) {
        return "Enter both start and end date/time, or leave both blank.";
    }
    if (startDate != null && !startDate.isBefore(endDate)) {
        return "Start date/time must be before end date/time.";
    }
    if (minPrice != null && (!Double.isFinite(minPrice) || minPrice < 0)) {
        return "Minimum price must be zero or greater.";
    }
    if (maxPrice != null && (!Double.isFinite(maxPrice) || maxPrice < 0)) {
        return "Maximum price must be zero or greater.";
    }
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
        return "Minimum price cannot exceed maximum price.";
    }
    if (minAvailable != null && minAvailable <= 0) {
        return "Minimum available tickets must be a positive whole number.";
    }
    if (sectionType != null
            && !sectionType.equalsIgnoreCase("reserved")
            && !sectionType.equalsIgnoreCase("general")) {
        return "Section type must be reserved or general.";
    }
    return null;
}

private static String validateCoordinateLocation(LocationSearchInput location) {
    Double latitude = location.getLatitude();
    Double longitude = location.getLongitude();
    Double radiusKm = location.getRadiusKm();
    String sortBy = location.getSortBy();

    if (latitude == null || !Double.isFinite(latitude)
            || latitude < -90 || latitude > 90) {
        return "Latitude must be between -90 and 90.";
    }
    if (longitude == null || !Double.isFinite(longitude)
            || longitude < -180 || longitude > 180) {
        return "Longitude must be between -180 and 180.";
    }
    if (radiusKm == null || !Double.isFinite(radiusKm) || radiusKm <= 0) {
        return "Search distance must be greater than zero.";
    }
    if (!"distance".equals(sortBy)
            && !"price_asc".equals(sortBy)
            && !"price_desc".equals(sortBy)) {
        return "Coordinate searches require a supported sort choice.";
    }
    return null;
}

private static String availabilityCtes(String sectionFilter) {
    return """
            WITH reserved_availability AS (
                SELECT ps.performance_id,
                       ps.venue_id,
                       ps.section_name,
                       SUM(
                           CASE
                               WHEN ps.blocked_status = FALSE
                                AND t.ticket_id IS NULL
                               THEN 1
                               ELSE 0
                           END
                       ) AS available_count
                FROM PerformanceSeats ps
                LEFT JOIN Tickets t
                    ON t.performance_id = ps.performance_id
                   AND t.performance_seats_ref = ps.performance_seat_id
                   AND t.status = 'active'
                GROUP BY ps.performance_id,
                         ps.venue_id,
                         ps.section_name
            ),
            available_sections AS (
                SELECT sta.performance_id,
                       s.section_type,
                       pt.price,
                       CASE
                           WHEN s.section_type = 'reserved'
                           THEN COALESCE(reserved.available_count, 0)
                           ELSE COALESCE(ga.remaining_capacity, 0)
                       END AS available_count
                FROM SectionTierAssignment sta
                JOIN Section s
                    ON s.venue_id = sta.venue_id
                   AND s.section_name = sta.section_name
                JOIN PriceTier pt
                    ON pt.performance_id = sta.performance_id
                   AND pt.tier_code = sta.tier_code
                LEFT JOIN reserved_availability reserved
                    ON reserved.performance_id = sta.performance_id
                   AND reserved.venue_id = sta.venue_id
                   AND reserved.section_name = sta.section_name
                LEFT JOIN GeneralAdmissionCapacity ga
                    ON ga.performance_id = sta.performance_id
                   AND ga.venue_id = sta.venue_id
                   AND ga.section_name = sta.section_name
            ),
            performance_availability AS (
                SELECT performance_id,
                       MIN(price) AS cheapest_available_price,
                       SUM(available_count) AS total_available
                FROM available_sections
                WHERE available_count > 0
            %sGROUP BY performance_id
            )
            """.formatted(sectionFilter);
}

private static String normalizeOptional(String value) {
    if (isBlank(value)) {
        return null;
    }
    return value.trim();
}

private static boolean isBlank(String value) {
    return value == null || value.isBlank();
}

/*************************************************************************************************************
 QUERY-6
 Seat map summary
 *************************************************************************************************************/
public OperationResult<List<SeatMapSummaryQuery>> query6(
        int performanceId
) {
    String validationError = validateQuery6(performanceId);
    if (validationError != null) {
        return OperationResult.invalidInput(validationError);
    }

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
    String validationError = validateQuery7(performanceId, q, budget);
    if (validationError != null) {
        return OperationResult.invalidInput(validationError);
    }

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
