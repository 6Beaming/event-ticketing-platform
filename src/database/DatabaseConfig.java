package database;

// Configuration: local mytix database with the username root and an empty password
public final class DatabaseConfig {
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
        return new DatabaseConfig(
                DEFAULT_URL,
                DEFAULT_USER,
                DEFAULT_PASSWORD
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
