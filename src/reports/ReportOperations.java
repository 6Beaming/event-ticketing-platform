package reports;

import common.OperationResult;
import database.TransactionManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ReportOperations {

    private final TransactionManager transactions;

    public ReportOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }


   public OperationResult<List<TicketRevenueReport>> report1() {

        return transactions.execute(connection -> {

            String sql = """
                    -- SQL goes here
                    """;

            List<TicketRevenueReport> reports = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {

                try (ResultSet rows = statement.executeQuery()) {

                    while (rows.next()) {

                        reports.add(new TicketRevenueReport(
                                rows.getString("city"),
                                rows.getInt("tickets_sold"),
                                rows.getBigDecimal("gross_revenue")
                        ));
                    }
                }
            }

            return OperationResult.success(

                reports.add(new TicketRevenueReport(
                        "Toronto",
                        500,
                        new BigDecimal("25000.00")
                ));

                reports.add(new TicketRevenueReport(
                        "Ottawa",
                        200,
                        new BigDecimal("10000.00")
                ));

                return OperationResult.success(
                        "Ticket revenue report generated.",
                        List.copyOf(reports)
                );
        
    }

//     public OperationResult<List<TicketRevenueReport>> report1() {

//     return transactions.execute(connection -> {

//         List<TicketRevenueReport> reports = new ArrayList<>();

//         reports.add(new TicketRevenueReport(
//                 "Toronto",
//                 500,
//                 new BigDecimal("25000.00")
//         ));

//         reports.add(new TicketRevenueReport(
//                 "Ottawa",
//                 200,
//                 new BigDecimal("10000.00")
//         ));

//         return OperationResult.success(
//                 "Ticket revenue report generated.",
//                 List.copyOf(reports)
//         );
//     });
// }
}