package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Manages driver loading, connection lifecycle, validation, retry, and safe errors.
public final class DatabaseConnection implements AutoCloseable, ConnectionProvider {
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private final DatabaseConfig config;
    private Connection connection;
    private String lastError;

    public DatabaseConnection(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Database configuration is required");
        }
        this.config = config;
    }

    public synchronized Connection connect() throws SQLException {
        close();
        loadDriver();
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);

        try {
            connection = DriverManager.getConnection(
                    config.getUrl(),
                    config.getUser(),
                    config.getPassword()
            );
            lastError = null;
            return connection;
        } catch (SQLException exception) {
            lastError = formatError(exception);
            throw exception;
        }
    }

    public synchronized Connection requireConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException(
                    "MyTix is not connected to MySQL. Retry the connection from menu option 10."
            );
        }
        return connection;
    }

    public synchronized boolean isConnected() {
        if (connection == null) {
            return false;
        }

        try {
            return !connection.isClosed() && connection.isValid(2);
        } catch (SQLException exception) {
            lastError = formatError(exception);
            return false;
        }
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public synchronized String getLastError() {
        return lastError == null ? "No connection error has been recorded." : lastError;
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            lastError = formatError(exception);
        } finally {
            connection = null;
        }
    }

    private void loadDriver() throws SQLException {
        try {
            Class.forName(MYSQL_DRIVER);
        } catch (ClassNotFoundException exception) {
            lastError = "MySQL Connector/J was not found on the classpath.";
            throw new SQLException(lastError, exception);
        }
    }

    private String formatError(SQLException exception) {
        String detail = exception.getMessage();
        StringBuilder message = new StringBuilder(
                detail == null || detail.trim().isEmpty()
                        ? exception.getClass().getSimpleName()
                        : detail
        );
        if (exception.getSQLState() != null) {
            message.append(" (SQLState ").append(exception.getSQLState()).append(')');
        }
        return message.toString();
    }
}
