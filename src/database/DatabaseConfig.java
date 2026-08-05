package database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class DatabaseConfig {
    private static final String CONFIG_FILE = "config.properties";
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/mytix?serverTimezone=UTC&connectTimeout=5000";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

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
        Path configPath = Paths.get(CONFIG_FILE);

        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read config.properties.", exception);
            }
        }

        return new DatabaseConfig(
                properties.getProperty("db.url", DEFAULT_URL),
                properties.getProperty("db.user", DEFAULT_USER),
                properties.getProperty("db.password", DEFAULT_PASSWORD)
        );
    }

    public static DatabaseConfig fromValues(String url, String user, String password) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Database URL is required");
        }
        if (user == null || user.trim().isEmpty()) {
            throw new IllegalArgumentException("Database user is required");
        }
        return new DatabaseConfig(url.trim(), user.trim(), password == null ? "" : password);
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
