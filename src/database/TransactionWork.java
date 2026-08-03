package database;

import common.OperationResult;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface TransactionWork<T> {
    OperationResult<T> execute(Connection connection) throws SQLException;
}
