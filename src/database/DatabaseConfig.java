package database;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

// Configuration: local mytix database with the username root and an empty password
public final class DatabaseConfig {

    private static final String CONFIG_FILE = "config.properties";

    private final String url;
    private final String user;
    private final String password;

    private DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static DatabaseConfig defaults() {
        Properties properties = new Properties();

        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load config.properties. " +
                    "Copy config.properties.example and update your database credentials.",
                    exception
            );
        }

        return new DatabaseConfig(
                properties.getProperty("db.url"),
                properties.getProperty("db.user"),
                properties.getProperty("db.password")
        );
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String describe() {
        return url + " as user '" + user + "'";
    }
}
