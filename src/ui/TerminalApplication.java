package ui;

import database.DatabaseConnection;

import java.sql.SQLException;
import java.util.Scanner;

// Implement the persistent menu loop, input handling, database retry, Q1–Q7, and R1–R9 screens.
public final class TerminalApplication {
    private final Scanner input;
    private final DatabaseConnection database;
    private boolean running;

    public TerminalApplication(
            Scanner input,
            DatabaseConnection database
    ) {
        this.input = input;
        this.database = database;
    }

    public void run() {
        running = true;
        printWelcome();

        while (running) {
            printMainMenu();
            handleMainMenu(readLine("Select an option: "));
        }
    }

    private void printWelcome() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("                         MyTix");
        System.out.println("        Event ticketing database terminal foundation");
        System.out.println("============================================================");
    }

    private void printMainMenu() {
        System.out.println();
        System.out.println("Database: " + (database.isConnected() ? "CONNECTED" : "OFFLINE"));
        System.out.println();
        System.out.println(" 1. User profiles");
        System.out.println(" 2. Organizer events and performances");
        System.out.println(" 3. Pricing and seat inventory");
        System.out.println(" 4. Ticket booking and cancellations");
        System.out.println(" 5. Ticket resale");
        System.out.println(" 6. Attendance reviews");
        System.out.println(" 7. Searches (Q1-Q7)");
        System.out.println(" 8. Reports (R1-R9)");
        System.out.println(" 9. Organizer toolkit");
        System.out.println("10. Database connection");
        System.out.println(" 0. Exit");
    }

    private void handleMainMenu(String choice) {
        switch (choice) {
            case "1":
                showFoundationModule(
                        "User profiles",
                        "Create customer or organizer profiles; delete eligible users; manage fictional payment details."
                );
                break;
            case "2":
                showFoundationModule(
                        "Organizer events and performances",
                        "Create events; assign artists and billing order; add performances; set the resale cap."
                );
                break;
            case "3":
                showFoundationModule(
                        "Pricing and seat inventory",
                        "Define tiers; assign sections; update eligible prices; block or unblock available seats."
                );
                break;
            case "4":
                showFoundationModule(
                        "Ticket booking and cancellations",
                        "Book reserved or general-admission tickets; cancel eligible tickets or performances."
                );
                break;
            case "5":
                showFoundationModule(
                        "Ticket resale",
                        "List an owned ticket; withdraw a listing; purchase a listing; retain ownership history."
                );
                break;
            case "6":
                showFoundationModule(
                        "Attendance reviews",
                        "Create one eligible event and venue review for each attended performance."
                );
                break;
            case "7":
                showSearches();
                break;
            case "8":
                showReports();
                break;
            case "9":
                showFoundationModule(
                        "Organizer toolkit",
                        "Suggest price tiers, prices, and capacity shares using comparable performances."
                );
                break;
            case "10":
                showDatabaseMenu();
                break;
            case "0":
                running = false;
                System.out.println("Goodbye.");
                break;
            default:
                System.out.println("Unknown option. Enter a number from the menu.");
        }
    }

    private void showFoundationModule(String title, String plannedOperations) {
        System.out.println();
        System.out.println(title);
        System.out.println(repeat('-', title.length()));
        System.out.println(plannedOperations);
        System.out.println();
        System.out.println("Foundation status: menu route created; SQL operation not implemented yet.");
        pause();
    }

    private void showSearches() {
        System.out.println();
        System.out.println("Required searches");
        System.out.println("-----------------");
        System.out.println("Q1  Nearby upcoming performances by distance or cheapest ticket.");
        System.out.println("Q2  Upcoming performances in the same or adjacent postal codes.");
        System.out.println("Q3  Exact-address venue search with upcoming performances.");
        System.out.println("Q4  Date-range and minimum-availability refinement.");
        System.out.println("Q5  Fully combinable performance filters.");
        System.out.println("Q6  Section-level seat map and inventory summary.");
        System.out.println("Q7  Best consecutive seats for quantity and optional budget.");
        System.out.println();
        System.out.println("Foundation status: query routes mapped; SQL not implemented yet.");
        pause();
    }

    private void showReports() {
        System.out.println();
        System.out.println("Required reports");
        System.out.println("----------------");
        System.out.println("R1  Ticket sales and gross revenue by city or venue.");
        System.out.println("R2  Event and performance counts by taxonomy and location.");
        System.out.println("R3  Organizer revenue rankings.");
        System.out.println("R4  Possible-scalper detection by city.");
        System.out.println("R5  Customer order rankings.");
        System.out.println("R6  Customer and organizer cancellation rankings.");
        System.out.println("R7  Performance and tier sell-through.");
        System.out.println("R8  Resale activity and markup.");
        System.out.println("R9  Popular noun phrases from event comments.");
        System.out.println();
        System.out.println("Foundation status: report routes mapped; SQL not implemented yet.");
        pause();
    }

    private void showDatabaseMenu() {
        System.out.println();
        System.out.println("Database connection");
        System.out.println("-------------------");
        System.out.println("Target: " + database.getConfig().describe());
        System.out.println("Status: " + (database.isConnected() ? "CONNECTED" : "OFFLINE"));
        if (!database.isConnected()) {
            System.out.println("Last error: " + database.getLastError());
        }
        System.out.println();

        String choice = readLine("Enter R to reconnect, or press Enter to return: ");
        if (!"R".equalsIgnoreCase(choice)) {
            return;
        }

        try {
            database.connect();
            System.out.println("MySQL connection established.");
        } catch (SQLException exception) {
            System.out.println("Connection failed: " + database.getLastError());
        }
        pause();
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!input.hasNextLine()) {
            running = false;
            System.out.println();
            return "0";
        }
        return input.nextLine().trim();
    }

    private void pause() {
        readLine("Press Enter to return to the main menu...");
    }

    private String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(character);
        }
        return result.toString();
    }
}
