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
                configuredValue("MYTIX_DB_URL", properties, "db.url", DEFAULT_URL),
                configuredValue("MYTIX_DB_USER", properties, "db.user", DEFAULT_USER),
                configuredValue("MYTIX_DB_PASSWORD", properties, "db.password", DEFAULT_PASSWORD)
        );
    }

    private static String configuredValue(
            String environmentName,
            Properties properties,
            String propertyName,
            String fallback
    ) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null) {
            return environmentValue;
        }
        return properties.getProperty(propertyName, fallback);
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
