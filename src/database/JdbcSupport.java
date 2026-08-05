package database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcSupport {
    private JdbcSupport() {
    }

    public static int requireGeneratedIntKey(PreparedStatement statement, String recordName)
            throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated key returned for " + recordName);
            }
            return keys.getInt(1);
        }
    }
}
