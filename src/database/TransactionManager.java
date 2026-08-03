package database;

import common.OperationResult;

import java.sql.Connection;
import java.sql.SQLException;

public final class TransactionManager {
    private final ConnectionProvider database;

    public TransactionManager(ConnectionProvider database) {
        if (database == null) {
            throw new IllegalArgumentException("Database connection manager is required");
        }
        this.database = database;
    }

    public synchronized <T> OperationResult<T> execute(TransactionWork<T> work) {
        if (work == null) {
            return OperationResult.invalidInput("Transaction work is required.");
        }

        Connection connection;
        try {
            connection = database.requireConnection();
        } catch (SQLException exception) {
            return OperationResult.databaseFailure(
                    "The database is unavailable. Reconnect and try again."
            );
        }

        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException exception) {
            return OperationResult.databaseFailure(
                    "The database transaction could not be started. No changes were saved."
            );
        }

        OperationResult<T> result;
        try {
            result = work.execute(connection);
            if (result == null) {
                throw new SQLException("Transaction work returned no result");
            }

            if (result.isSuccess()) {
                connection.commit();
            } else {
                connection.rollback();
            }
        } catch (SQLException exception) {
            rollbackQuietly(connection, exception);
            result = databaseResult(exception);
        }

        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            database.close();
            if (!result.isSuccess()) {
                return result;
            }
            return OperationResult.databaseFailure(
                    "The operation completed, but the database connection became unusable. Reconnect before continuing."
            );
        }

        return result;
    }

    private void rollbackQuietly(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private <T> OperationResult<T> databaseResult(SQLException exception) {
        String sqlState = exception.getSQLState();
        if (sqlState != null && sqlState.startsWith("23")) {
            return OperationResult.conflict(
                    "The operation conflicts with existing database data. No changes were saved."
                            + " Check unique and referenced values, then try again."
            );
        }
        return OperationResult.databaseFailure(
                "The database operation failed. No changes were saved."
                        + " Reconnect or verify the supplied IDs, then try again."
        );
    }
}
