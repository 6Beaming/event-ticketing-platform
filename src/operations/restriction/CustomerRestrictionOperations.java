package operations.restriction;

import common.OperationResult;
import database.TransactionManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class CustomerRestrictionOperations {
    private final TransactionManager transactions;

    public CustomerRestrictionOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<Boolean> refreshPossibleScalperStatus(int customerId) {
        if (customerId <= 0) {
            return OperationResult.invalidInput("Customer ID must be positive.");
        }
        return transactions.execute(connection -> {
            String customerSql = """
                    SELECT c.user_id
                    FROM Customer c
                    JOIN Users u ON u.user_id = c.user_id
                    WHERE c.user_id = ? AND u.account_status = 'active'
                    FOR UPDATE
                    """;
            try (PreparedStatement statement = connection.prepareStatement(customerSql)) {
                statement.setInt(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Active customer not found.");
                    }
                }
            }

            String restriction = CustomerRestrictionGuard.findCurrentRestriction(
                    connection,
                    customerId
            );
            if (restriction == null) {
                return OperationResult.success(
                        "Customer does not currently meet the possible-scalper rule.",
                        false
                );
            }
            return OperationResult.success(restriction, true);
        });
    }
}
