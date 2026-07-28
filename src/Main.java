import database.DatabaseConfig;
import database.DatabaseConnection;
import ui.TerminalApplication;

import java.sql.SQLException;
import java.util.Scanner;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        DatabaseConfig config = DatabaseConfig.defaults();

        try (DatabaseConnection database = new DatabaseConnection(config);
             Scanner input = new Scanner(System.in)) {
            connectAtStartup(database);
            // start the terminal
            TerminalApplication application = new TerminalApplication(
                    input,
                    database
            );
            application.run();
        }
    }

    private static void connectAtStartup(DatabaseConnection database) {
        System.out.println("Connecting to " + database.getConfig().describe() + "...");

        try {
            database.connect();
            System.out.println("MySQL connection established.");
        } catch (SQLException exception) {
            System.out.println("MySQL is currently unavailable; MyTix will start in offline mode.");
            System.out.println("Reason: " + database.getLastError());
            System.out.println("Use menu option 10 to retry after checking the database settings.");
        }
    }
}
