package database;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider {
    Connection requireConnection() throws SQLException;

    void close();
}
