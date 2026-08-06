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


// REPORT-1
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







}