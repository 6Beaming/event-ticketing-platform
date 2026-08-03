package ui;

import common.OperationResult;
import database.DatabaseConnection;
import database.TransactionManager;
import operations.event.ArtistBillingInput;
import operations.event.EventInput;
import operations.event.OrganizerEventOperations;
import operations.event.PerformanceInput;
import operations.inventory.GeneralAdmissionAvailability;
import operations.inventory.InventoryOperations;
import operations.inventory.ReservedSeatAvailability;
import operations.pricing.PerformancePricingOperations;
import operations.pricing.PricingSetupInput;
import operations.pricing.PricingSetupSummary;
import operations.pricing.SectionTierInput;
import operations.pricing.TierInput;
import operations.profile.CustomerProfile;
import operations.profile.PaymentInput;
import operations.profile.ProfileInput;
import operations.profile.UserProfileOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class TerminalApplication {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Scanner input;
    private final DatabaseConnection database;
    private final UserProfileOperations profiles;
    private final OrganizerEventOperations events;
    private final PerformancePricingOperations pricing;
    private final InventoryOperations inventory;
    private boolean running;

    public TerminalApplication(Scanner input, DatabaseConnection database) {
        if (input == null || database == null) {
            throw new IllegalArgumentException("Terminal input and database are required");
        }
        this.input = input;
        this.database = database;
        TransactionManager transactions = new TransactionManager(database);
        this.profiles = new UserProfileOperations(transactions);
        this.events = new OrganizerEventOperations(transactions);
        this.pricing = new PerformancePricingOperations(transactions);
        this.inventory = new InventoryOperations(transactions);
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
        System.out.println(" 3. Performance pricing and inventory");
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
            case "1" -> showProfileMenu();
            case "2" -> showEventMenu();
            case "3" -> showPricingAndInventoryMenu();
            case "4" -> showLaterModule(
                    "Ticket booking and cancellations",
                    "Reserved/GA booking and cancellation are scheduled for August 1-3."
            );
            case "5" -> showLaterModule(
                    "Ticket resale",
                    "Listing, withdrawal, purchase, and ownership transfer are scheduled for August 1-3."
            );
            case "6" -> showLaterModule(
                    "Attendance reviews",
                    "Attendance-based reviews are scheduled for August 1-3."
            );
            case "7" -> showSearches();
            case "8" -> showReports();
            case "9" -> showLaterModule(
                    "Organizer toolkit",
                    "Pricing and tier-structure suggestions are scheduled for August 4-6."
            );
            case "10" -> showDatabaseMenu();
            case "0" -> {
                running = false;
                System.out.println("Goodbye.");
            }
            default -> System.out.println("Unknown option. Enter a number from the menu.");
        }
    }

    private void showProfileMenu() {
        boolean inMenu = true;
        while (running && inMenu) {
            printHeading("User profiles");
            System.out.println("1. Create customer profile");
            System.out.println("2. Create organizer profile");
            System.out.println("3. View customer profile");
            System.out.println("4. Deactivate user profile");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::createCustomer);
                case "2" -> runOnlineAction(this::createOrganizer);
                case "3" -> runOnlineAction(this::viewCustomer);
                case "4" -> runOnlineAction(this::deactivateUser);
                case "0" -> inMenu = false;
                default -> System.out.println("Unknown profile option.");
            }
        }
    }

    private void createCustomer() {
        ProfileInput profile = readProfile();
        if (profile == null) {
            pause();
            return;
        }
        System.out.println("Use fictional payment information only.");
        String cardNumber = readLine("Fictional card number: ");
        String cardHolder = readLine("Card holder name: ");
        LocalDate expiry = readDate("Expiry date (YYYY-MM-DD): ");
        if (expiry == null) {
            pause();
            return;
        }
        String billingZip = readLine("Billing postal/ZIP code: ");

        OperationResult<Integer> result = profiles.createCustomer(
                profile,
                new PaymentInput(cardNumber, cardHolder, expiry, billingZip)
        );
        printResult(result);
        result.getValue().ifPresent(id -> System.out.println("Customer ID: " + id));
        pause();
    }

    private void createOrganizer() {
        ProfileInput profile = readProfile();
        if (profile == null) {
            pause();
            return;
        }
        OperationResult<Integer> result = profiles.createOrganizer(profile);
        printResult(result);
        result.getValue().ifPresent(id -> System.out.println("Organizer ID: " + id));
        pause();
    }

    private ProfileInput readProfile() {
        String name = readLine("Name: ");
        String address = readLine("Address: ");
        String email = readLine("Email: ");
        LocalDate dateOfBirth = readDate("Date of birth (YYYY-MM-DD): ");
        if (dateOfBirth == null) {
            return null;
        }
        return new ProfileInput(name, address, email, dateOfBirth);
    }

    private void viewCustomer() {
        Integer customerId = readPositiveInt("Customer ID: ");
        if (customerId == null) {
            pause();
            return;
        }
        OperationResult<CustomerProfile> result = profiles.getCustomerProfile(customerId);
        printResult(result);
        result.getValue().ifPresent(customer -> {
            System.out.println("ID: " + customer.getUserId());
            System.out.println("Name: " + customer.getName());
            System.out.println("Address: " + customer.getAddress());
            System.out.println("Email: " + customer.getEmail());
            System.out.println("Date of birth: " + customer.getDateOfBirth());
            System.out.println("Status: " + customer.getAccountStatus());
            System.out.println("Card: " + customer.getMaskedCardNumber());
            System.out.println("Card holder: " + nullable(customer.getCardHolderName()));
            System.out.println("Expiry: " + nullable(customer.getExpiryDate()));
            System.out.println("Billing postal/ZIP: " + nullable(customer.getBillingZip()));
        });
        pause();
    }

    private void deactivateUser() {
        Integer userId = readPositiveInt("User ID to deactivate: ");
        if (userId == null) {
            pause();
            return;
        }
        String confirmation = readLine("Type DEACTIVATE to preserve history and anonymize this profile: ");
        if (!"DEACTIVATE".equals(confirmation)) {
            System.out.println("Deactivation cancelled.");
            pause();
            return;
        }
        printResult(profiles.deactivateUser(userId));
        pause();
    }

    private void showEventMenu() {
        boolean inMenu = true;
        while (running && inMenu) {
            printHeading("Organizer events and performances");
            System.out.println("1. Create event with artist billing");
            System.out.println("2. Add performance");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::createEvent);
                case "2" -> runOnlineAction(this::addPerformance);
                case "0" -> inMenu = false;
                default -> System.out.println("Unknown event option.");
            }
        }
    }

    private void createEvent() {
        Integer organizerId = readPositiveInt("Organizer ID: ");
        Integer genreId = readPositiveInt("Genre ID: ");
        if (organizerId == null || genreId == null) {
            pause();
            return;
        }
        String title = readLine("Event title: ");
        String description = readLine("Description (optional): ");
        BigDecimal resaleCap = readDecimalWithDefault(
                "Resale cap multiplier [1.20]: ",
                new BigDecimal("1.20")
        );
        Integer artistCount = readPositiveInt("Number of artists/teams: ");
        if (resaleCap == null || artistCount == null) {
            pause();
            return;
        }

        List<ArtistBillingInput> artists = new ArrayList<>();
        for (int index = 1; index <= artistCount; index++) {
            Integer artistId = readPositiveInt("Artist/team " + index + " ID: ");
            Integer rank = readPositiveInt("Billing rank for artist/team " + index + ": ");
            if (artistId == null || rank == null) {
                pause();
                return;
            }
            artists.add(new ArtistBillingInput(artistId, rank));
        }

        OperationResult<Integer> result = events.createEvent(new EventInput(
                organizerId,
                title,
                description,
                resaleCap,
                genreId,
                artists
        ));
        printResult(result);
        result.getValue().ifPresent(id -> System.out.println("Event ID: " + id));
        pause();
    }

    private void addPerformance() {
        Integer eventId = readPositiveInt("Event ID: ");
        Integer venueId = readPositiveInt("Venue ID: ");
        LocalDateTime dateTime = readDateTime("Date and time (YYYY-MM-DD HH:mm): ");
        if (eventId == null || venueId == null || dateTime == null) {
            pause();
            return;
        }
        OperationResult<Integer> result = events.addPerformance(
                new PerformanceInput(eventId, venueId, dateTime)
        );
        printResult(result);
        result.getValue().ifPresent(id -> System.out.println("Performance ID: " + id));
        pause();
    }

    private void showPricingAndInventoryMenu() {
        boolean inMenu = true;
        while (running && inMenu) {
            printHeading("Performance pricing and inventory");
            System.out.println("1. Configure tiers and all section assignments");
            System.out.println("2. View reserved-seat inventory");
            System.out.println("3. View general-admission inventory");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::configurePricing);
                case "2" -> runOnlineAction(this::viewReservedInventory);
                case "3" -> runOnlineAction(this::viewGeneralInventory);
                case "0" -> inMenu = false;
                default -> System.out.println("Unknown pricing/inventory option.");
            }
        }
    }

    private void configurePricing() {
        Integer performanceId = readPositiveInt("Performance ID: ");
        Integer tierCount = readPositiveInt("Number of tiers (minimum 2): ");
        if (performanceId == null || tierCount == null) {
            pause();
            return;
        }
        List<TierInput> tiers = new ArrayList<>();
        for (int index = 1; index <= tierCount; index++) {
            String code = readLine("Tier " + index + " code: ");
            BigDecimal price = readDecimal("Tier " + index + " price: ");
            if (price == null) {
                pause();
                return;
            }
            tiers.add(new TierInput(code, price));
        }

        Integer assignmentCount = readPositiveInt("Number of venue sections to assign: ");
        if (assignmentCount == null) {
            pause();
            return;
        }
        List<SectionTierInput> assignments = new ArrayList<>();
        for (int index = 1; index <= assignmentCount; index++) {
            String section = readLine("Section " + index + " name: ");
            String tierCode = readLine("Tier code for " + section + ": ");
            assignments.add(new SectionTierInput(section, tierCode));
        }

        OperationResult<PricingSetupSummary> result = pricing.configurePricing(
                new PricingSetupInput(performanceId, tiers, assignments)
        );
        printResult(result);
        result.getValue().ifPresent(summary -> {
            System.out.println("Performance ID: " + summary.getPerformanceId());
            System.out.println("Tiers created: " + summary.getTierCount());
            System.out.println("Sections assigned: " + summary.getAssignedSectionCount());
        });
        pause();
    }

    private void viewReservedInventory() {
        Integer performanceId = readPositiveInt("Performance ID: ");
        if (performanceId == null) {
            pause();
            return;
        }
        OperationResult<List<ReservedSeatAvailability>> result =
                inventory.getReservedInventory(performanceId);
        printResult(result);
        result.getValue().ifPresent(seats -> {
            if (seats.isEmpty()) {
                System.out.println("No reserved seats are configured for this performance.");
                return;
            }
            System.out.printf("%-8s %-20s %-8s %-6s %-8s %-10s %-10s%n",
                    "Seat ID", "Section", "Row", "Seat", "Tier", "Price", "Status");
            for (ReservedSeatAvailability seat : seats) {
                System.out.printf("%-8d %-20s %-8s %-6d %-8s $%-9s %-10s%n",
                        seat.getPerformanceSeatId(),
                        seat.getSectionName(),
                        seat.getRowName(),
                        seat.getSeatNumber(),
                        seat.getTierCode(),
                        seat.getPrice().toPlainString(),
                        seat.getState());
            }
        });
        pause();
    }

    private void viewGeneralInventory() {
        Integer performanceId = readPositiveInt("Performance ID: ");
        if (performanceId == null) {
            pause();
            return;
        }
        OperationResult<List<GeneralAdmissionAvailability>> result =
                inventory.getGeneralAdmissionInventory(performanceId);
        printResult(result);
        result.getValue().ifPresent(sections -> {
            if (sections.isEmpty()) {
                System.out.println("No general-admission sections are configured for this performance.");
                return;
            }
            System.out.printf("%-8s %-20s %-8s %-10s %-8s %-8s %-10s%n",
                    "GA ID", "Section", "Tier", "Price", "Total", "Sold", "Remaining");
            for (GeneralAdmissionAvailability section : sections) {
                System.out.printf("%-8d %-20s %-8s $%-9s %-8d %-8d %-10d%n",
                        section.getCapacityId(),
                        section.getSectionName(),
                        section.getTierCode(),
                        section.getPrice().toPlainString(),
                        section.getTotalCapacity(),
                        section.getSoldQuantity(),
                        section.getRemainingCapacity());
            }
        });
        pause();
    }

    private void showLaterModule(String title, String message) {
        printHeading(title);
        System.out.println(message);
        pause();
    }

    private void showSearches() {
        printHeading("Required searches");
        System.out.println("Q1-Q7 implementation is scheduled for August 1-3.");
        pause();
    }

    private void showReports() {
        printHeading("Required reports");
        System.out.println("R1-R9 implementation is scheduled for August 4-6.");
        pause();
    }

    private void showDatabaseMenu() {
        printHeading("Database connection");
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
        } catch (java.sql.SQLException exception) {
            System.out.println("Connection failed: " + database.getLastError());
        }
        pause();
    }

    private void runOnlineAction(Runnable action) {
        if (!database.isConnected()) {
            System.out.println("This operation requires MySQL. Use main-menu option 10 to reconnect.");
            pause();
            return;
        }
        action.run();
    }

    private void printResult(OperationResult<?> result) {
        System.out.println(result.getStatus() + ": " + result.getMessage());
    }

    private void printHeading(String title) {
        System.out.println();
        System.out.println(title);
        System.out.println(repeat('-', title.length()));
    }

    private Integer readPositiveInt(String prompt) {
        String value = readLine(prompt);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            System.out.println("Enter a positive whole number.");
            return null;
        }
    }

    private BigDecimal readDecimal(String prompt) {
        String value = readLine(prompt);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            System.out.println("Enter a valid decimal number.");
            return null;
        }
    }

    private BigDecimal readDecimalWithDefault(String prompt, BigDecimal defaultValue) {
        String value = readLine(prompt);
        return value.isEmpty() ? defaultValue : parseDecimal(value);
    }

    private BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            System.out.println("Enter a valid decimal number.");
            return null;
        }
    }

    private LocalDate readDate(String prompt) {
        String value = readLine(prompt);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            System.out.println("Enter a date in YYYY-MM-DD format.");
            return null;
        }
    }

    private LocalDateTime readDateTime(String prompt) {
        String value = readLine(prompt);
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            System.out.println("Enter date and time in YYYY-MM-DD HH:mm format.");
            return null;
        }
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
        readLine("Press Enter to continue...");
    }

    private String nullable(Object value) {
        return value == null ? "Not available" : value.toString();
    }

    private String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(character);
        }
        return result.toString();
    }
}
