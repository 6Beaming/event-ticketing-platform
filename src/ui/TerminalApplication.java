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
import operations.profile.ProfileValidator;
import operations.profile.UserProfileOperations;

import reports.ReportOperations;
import reports.TicketRevenueReport;
import reports.EventPerformanceReport;
import reports.OrganizerRevenueReport;
import reports.ScalperDetectionReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Function;

public final class TerminalApplication {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Scanner input;
    private final DatabaseConnection database;
    private final UserProfileOperations profiles;
    private final OrganizerEventOperations events;
    private final PerformancePricingOperations pricing;
    private final InventoryOperations inventory;
    private final ReportOperations reports;

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
        this.reports = new ReportOperations(transactions);
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
        while (running) {
            ProfileInput profile = readProfile();
            if (profile == null) {
                return;
            }
            PaymentInput payment = readPayment();
            if (payment == null) {
                return;
            }

            OperationResult<Integer> result = profiles.createCustomer(profile, payment);
            printResult(result);
            if (result.isSuccess()) {
                result.getValue().ifPresent(id -> System.out.println("Customer ID: " + id));
                pause();
                return;
            }
            if (!promptToRetry()) {
                return;
            }
        }
    }

    private PaymentInput readPayment() {
        String cardNumber = readValidatedText(
                "Card number: ",
                ProfileValidator::validateCardNumber
        );
        if (cardNumber == null) {
            return null;
        }
        String cardHolder = readValidatedText(
                "Card holder name: ",
                ProfileValidator::validateCardHolderName
        );
        if (cardHolder == null) {
            return null;
        }
        LocalDate expiry = readValidatedDate(
                "Expiry date (YYYY-MM-DD): ",
                ProfileValidator::validateExpiryDate
        );
        if (expiry == null) {
            return null;
        }
        String billingZip = readValidatedText(
                "Billing postal/ZIP code: ",
                ProfileValidator::validateBillingZip
        );
        if (billingZip == null) {
            return null;
        }
        return new PaymentInput(cardNumber, cardHolder, expiry, billingZip);
    }

    private void createOrganizer() {
        while (running) {
            ProfileInput profile = readProfile();
            if (profile == null) {
                return;
            }
            OperationResult<Integer> result = profiles.createOrganizer(profile);
            printResult(result);
            if (result.isSuccess()) {
                result.getValue().ifPresent(id -> System.out.println("Organizer ID: " + id));
                pause();
                return;
            }
            if (!promptToRetry()) {
                return;
            }
        }
    }

    private ProfileInput readProfile() {
        String name = readValidatedText("Name: ", ProfileValidator::validateName);
        if (name == null) {
            return null;
        }
        String address = readValidatedText("Address: ", ProfileValidator::validateAddress);
        if (address == null) {
            return null;
        }
        String email = readAvailableEmail();
        if (email == null) {
            return null;
        }
        LocalDate dateOfBirth = readValidatedDate(
                "Date of birth (YYYY-MM-DD): ",
                date -> ProfileValidator.validateDateOfBirth(
                        date,
                        LocalDate.now(ZoneOffset.UTC)
                )
        );
        if (dateOfBirth == null) {
            return null;
        }
        return new ProfileInput(name, address, email, dateOfBirth);
    }

    private String readAvailableEmail() {
        while (running) {
            String email = readValidatedText("Email: ", ProfileValidator::validateEmail);
            if (email == null) {
                return null;
            }

            OperationResult<Void> availability = profiles.checkEmailAvailability(email);
            if (availability.isSuccess()) {
                return email;
            }
            printResult(availability);
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private String readValidatedText(
            String prompt,
            Function<String, Optional<String>> validator
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }
            Optional<String> error = validator.apply(value);
            if (error.isEmpty()) {
                return value;
            }
            printInputError(error.get());
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private LocalDate readValidatedDate(
            String prompt,
            Function<LocalDate, Optional<String>> validator
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }
            LocalDate date;
            try {
                date = LocalDate.parse(value);
            } catch (DateTimeParseException exception) {
                printInputError("Enter a date in YYYY-MM-DD format.");
                if (!promptToRetry()) {
                    return null;
                }
                continue;
            }

            Optional<String> error = validator.apply(date);
            if (error.isEmpty()) {
                return date;
            }
            printInputError(error.get());
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
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

    private void printInputError(String message) {
        System.out.println("INVALID_INPUT: " + message);
    }

    private boolean promptToRetry() {
        while (running) {
            String choice = readLine("Try again? (y/n): ");
            if (!running) {
                return false;
            }
            if ("y".equalsIgnoreCase(choice)) {
                return true;
            }
            if ("n".equalsIgnoreCase(choice)) {
                return false;
            }
            System.out.println("Invalid input. Please type 'y' or 'n'");
        }
        return false;
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
        String confirmation = readLine("Type DEACTIVATE to make this profile anonymous while preserving the history");
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
        while (running) {
            EventInput input = readEventInput();
            if (input == null) {
                return;
            }
            OperationResult<Integer> result = events.createEvent(input);
            printResult(result);
            if (result.isSuccess()) {
                result.getValue().ifPresent(id -> System.out.println("Event ID: " + id));
                pause();
                return;
            }
            if (!promptToRetry()) {
                return;
            }
        }
    }

    private EventInput readEventInput() {
        Integer organizerId = readCheckedId("Organizer ID: ", events::checkActiveOrganizer);
        if (organizerId == null) {
            return null;
        }
        Integer genreId = readCheckedId("Genre ID: ", events::checkGenre);
        if (genreId == null) {
            return null;
        }
        String title = readValidatedText(
                "Event title: ",
                value -> value.trim().isEmpty()
                        ? Optional.of("Event title is required.")
                        : Optional.empty()
        );
        if (title == null) {
            return null;
        }
        String description = readLine("Description (optional): ");
        BigDecimal resaleCap = readDecimalWithDefaultRetry(
                "Resale cap multiplier [default 1.20]: ",
                new BigDecimal("1.20"),
                value -> value.compareTo(BigDecimal.ONE) < 0
                        ? Optional.of("Resale cap multiplier must be at least 1.00.")
                        : Optional.empty()
        );
        if (resaleCap == null) {
            return null;
        }
        Integer artistCount = readPositiveIntWithRetry("Number of artists/teams: ");
        if (artistCount == null) {
            return null;
        }

        List<ArtistBillingInput> artists = new ArrayList<>();
        Set<Integer> artistIds = new HashSet<>();
        Set<Integer> billingRanks = new HashSet<>();
        for (int index = 1; index <= artistCount; index++) {
            Integer artistId = readUniqueCheckedId(
                    "Artist/team " + index + " ID: ",
                    events::checkArtist,
                    artistIds,
                    "An artist or team can appear only once in an event billing order."
            );
            if (artistId == null) {
                return null;
            }
            Integer rank = readUniquePositiveInt(
                    "Billing rank for artist/team " + index + ": ",
                    billingRanks,
                    "Billing ranks must be unique within an event."
            );
            if (rank == null) {
                return null;
            }
            artists.add(new ArtistBillingInput(artistId, rank));
        }

        return new EventInput(
                organizerId,
                title,
                description,
                resaleCap,
                genreId,
                artists
        );
    }

    private void addPerformance() {
        while (running) {
            Integer eventId = readCheckedId("Event ID: ", events::checkEvent);
            if (eventId == null) {
                return;
            }
            Integer venueId = readCheckedId("Venue ID: ", events::checkVenue);
            if (venueId == null) {
                return;
            }
            LocalDateTime dateTime = readDateTimeWithRetry(
                    "Date and time (YYYY-MM-DD HH:mm): "
            );
            if (dateTime == null) {
                return;
            }

            OperationResult<Integer> result = events.addPerformance(
                    new PerformanceInput(eventId, venueId, dateTime)
            );
            printResult(result);
            if (result.isSuccess()) {
                result.getValue().ifPresent(id -> System.out.println("Performance ID: " + id));
                pause();
                return;
            }
            if (!promptToRetry()) {
                return;
            }
        }
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
        while (running) {
            PricingSetupInput input = readPricingSetup();
            if (input == null) {
                return;
            }
            OperationResult<PricingSetupSummary> result = pricing.configurePricing(input);
            printResult(result);
            if (result.isSuccess()) {
                result.getValue().ifPresent(summary -> {
                    System.out.println("Performance ID: " + summary.getPerformanceId());
                    System.out.println("Tiers created: " + summary.getTierCount());
                    System.out.println("Sections assigned: " + summary.getAssignedSectionCount());
                });
                pause();
                return;
            }
            if (!promptToRetry()) {
                return;
            }
        }
    }

    private PricingSetupInput readPricingSetup() {
        Integer performanceId = null;
        List<String> venueSections = null;
        while (running) {
            performanceId = readPositiveIntWithRetry("Performance ID: ");
            if (performanceId == null) {
                return null;
            }
            OperationResult<List<String>> sectionsResult =
                    pricing.getVenueSectionsForPricing(performanceId);
            if (sectionsResult.isSuccess()) {
                System.out.println(sectionsResult.getMessage());
                venueSections = sectionsResult.getValue().orElseThrow();
                break;
            }
            printResult(sectionsResult);
            if (!promptToRetry()) {
                return null;
            }
        }
        if (performanceId == null || venueSections == null) {
            return null;
        }

        System.out.println("Venue sections: " + String.join(", ", venueSections));
        int requiredAssignments = venueSections.size();
        Integer tierCount = readValidatedInteger(
                "Number of tiers (minimum 2): ",
                value -> {
                    if (value < 2) {
                        return Optional.of("A performance must have at least two price tiers.");
                    }
                    if (value > requiredAssignments) {
                        return Optional.of("The number of tiers cannot exceed the venue's "
                                + requiredAssignments + " sections because every tier must be used.");
                    }
                    return Optional.empty();
                }
        );
        if (tierCount == null) {
            return null;
        }

        List<TierInput> tiers = new ArrayList<>();
        Set<String> tierCodes = new HashSet<>();
        for (int index = 1; index <= tierCount; index++) {
            String code = readValidatedText(
                    "Tier " + index + " code: ",
                    value -> {
                        if (value.trim().isEmpty()) {
                            return Optional.of("Every tier needs a code.");
                        }
                        if (tierCodes.contains(normalize(value))) {
                            return Optional.of("Tier codes must be unique within a performance.");
                        }
                        return Optional.empty();
                    }
            );
            if (code == null) {
                return null;
            }
            tierCodes.add(normalize(code));
            BigDecimal price = readDecimalWithRetry(
                    "Tier " + index + " price: ",
                    value -> value.compareTo(BigDecimal.ZERO) <= 0
                            ? Optional.of("Every tier price must be positive.")
                            : Optional.empty()
            );
            if (price == null) {
                return null;
            }
            tiers.add(new TierInput(code, price));
        }

        Integer assignmentCount = readValidatedInteger(
                "Number of venue sections to assign: ",
                value -> value != requiredAssignments
                        ? Optional.of("This venue has " + requiredAssignments
                                + " sections; enter " + requiredAssignments + " assignments.")
                        : Optional.empty()
        );
        if (assignmentCount == null) {
            return null;
        }

        Set<String> validSections = new HashSet<>();
        for (String section : venueSections) {
            validSections.add(normalize(section));
        }
        Set<String> assignedSections = new HashSet<>();
        Set<String> assignedTierCodes = new HashSet<>();
        List<SectionTierInput> assignments = new ArrayList<>();
        for (int index = 1; index <= assignmentCount; index++) {
            String section = readValidatedText(
                    "Section " + index + " name: ",
                    value -> {
                        String normalized = normalize(value);
                        if (normalized.isEmpty()) {
                            return Optional.of("A section name is required.");
                        }
                        if (!validSections.contains(normalized)) {
                            return Optional.of(
                                    "Enter a section belonging to the performance venue."
                            );
                        }
                        if (assignedSections.contains(normalized)) {
                            return Optional.of(
                                    "A section can be assigned only once for a performance."
                            );
                        }
                        return Optional.empty();
                    }
            );
            if (section == null) {
                return null;
            }
            assignedSections.add(normalize(section));

            int remainingAssignments = assignmentCount - index;
            String tierCode = readValidatedText(
                    "Tier code for " + section + ": ",
                    value -> {
                        String normalized = normalize(value);
                        if (!tierCodes.contains(normalized)) {
                            return Optional.of("Enter one of the supplied tier codes.");
                        }
                        Set<String> usedAfterSelection = new HashSet<>(assignedTierCodes);
                        usedAfterSelection.add(normalized);
                        int unusedTierCount = tierCodes.size() - usedAfterSelection.size();
                        if (unusedTierCount > remainingAssignments) {
                            return Optional.of("Every tier must be assigned to at least one section. "
                                    + "Choose a tier that has not been used yet.");
                        }
                        return Optional.empty();
                    }
            );
            if (tierCode == null) {
                return null;
            }
            assignedTierCodes.add(normalize(tierCode));
            assignments.add(new SectionTierInput(section, tierCode));
        }

        return new PricingSetupInput(performanceId, tiers, assignments);
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
    boolean inMenu = true;

    while (running && inMenu) {
        printHeading("Required Reports");

        System.out.println("1. Ticket revenue report");
        System.out.println("2. Events and performances report");
        System.out.println("3. Organizer revenue ranking");
        System.out.println("4. Potential ticket scalpers");
        System.out.println("5. Customer order rankings");
        System.out.println("6. Cancellation report");
        System.out.println("7. Sell-through report");
        System.out.println("8. Resale report");
        System.out.println("9. Event word cloud report");
        System.out.println("0. Back");

        switch (readLine("Select an option: ")) {
            case "1" -> runOnlineAction(this::report1);
            case "2" -> runOnlineAction(this::report2);
            case "3" -> runOnlineAction(this::report3);
            case "4" -> runOnlineAction(this::report4);
            case "5" -> runOnlineAction(this::report5);
            case "6" -> runOnlineAction(this::report6);
            case "7" -> runOnlineAction(this::report7);
            case "8" -> runOnlineAction(this::report8);
            case "9" -> runOnlineAction(this::report9);
            case "0" -> inMenu = false;
            default -> System.out.println("Unknown report option.");
        }
    }
}


private LocalDateTime[] readReportDateRange() {

    LocalDateTime startDate = readDateTime(
            "Start date/time (YYYY-MM-DD HH:mm): "
    );

    LocalDateTime endDate = readDateTime(
            "End date/time (YYYY-MM-DD HH:mm): "
    );

    if (startDate == null || endDate == null) {
        return null;
    }

    if (!startDate.isBefore(endDate)) {
        System.out.println(
                "Start date/time must be before end date/time."
        );
        return null;
    }

    if (endDate.isAfter(LocalDateTime.now())) {
        System.out.println(
                "End date/time cannot be in the future."
        );
        return null;
    }

    return new LocalDateTime[]{startDate, endDate};
}


private void printTicketRevenueRows(
        OperationResult<List<TicketRevenueReport>> result
) {

    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }

        System.out.printf(
                "%-20s %-20s %-15s %-15s%n",
                "City",
                "Venue",
                "Tickets Sold",
                "Gross Revenue"
        );

        for (TicketRevenueReport row : rows) {

            System.out.printf(
                    "%-20s %-20s %-15d $%-15s%n",
                    row.getCity(),
                    row.getVenueName(),
                    row.getTicketsSold(),
                    row.getGrossRevenue().toPlainString()
            );
        }
    });
}


private void report1() {

    printHeading("Ticket Revenue Report");

    System.out.println("1. Revenue by city");
    System.out.println("2. Revenue by venue within a city");
    String choice = readLine("Select option: ");


    OperationResult<List<TicketRevenueReport>> result;
    LocalDateTime[] dates = readReportDateRange();
    if (dates == null) {
    pause();
    return;
}

    switch (choice) {
        case "1" -> {
            result = reports.report1a(
                    dates[0],
                    dates[1]
            );}

        case "2" -> {
            String city = readLine(
                    "City: "
            );
            if (city.isBlank()) {
                System.out.println("City is required.");
                pause();
                return;
            }

            result = reports.report1b(
                    city,
                    dates[0],
                    dates[1]
            );}

        default -> {
            System.out.println("Unknown report option.");
            pause();
            return;
        }
    }
    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-20s %-25s %-15s %-15s%n",
                "City",
                "Venue",
                "Tickets Sold",
                "Gross Revenue"
        );


        for (TicketRevenueReport row : rows) {

            System.out.printf(
                    "%-20s %-25s %-15d $%-15s%n",
                    row.getCity(),
                    row.getVenueName() == null
                            ? "-"
                            : row.getVenueName(),
                    row.getTicketsSold(),
                    row.getGrossRevenue().toPlainString()
            );
        }
    });
    pause();
}


private void report2() {

    printHeading("Events and Performances Report");

    System.out.println("1. By segment and genre");
    System.out.println("2. By country");
    System.out.println("3. By country and city");
    System.out.println("4. By country, city and venue");


    OperationResult<List<EventPerformanceReport>> result;


    switch (readLine("Select option: ")) {

        case "1" -> result = reports.report2a();

        case "2" -> result = reports.report2b();

        case "3" -> result = reports.report2c();

        case "4" -> result = reports.report2d();

        default -> {
            System.out.println("Unknown report option.");
            pause();
            return;
        }
    }


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-15s %-15s %-15s %-15s %-25s %-15s %-15s%n",
                "Segment",
                "Genre",
                "Country",
                "City",
                "Venue",
                "Events",
                "Performances"
        );


        for (EventPerformanceReport row : rows) {

            System.out.printf(
                    "%-15s %-15s %-15s %-15s %-25s %-15d %-15d%n",
                    safe(row.getSegmentName()),
                    safe(row.getGenreName()),
                    safe(row.getCountry()),
                    safe(row.getCity()),
                    safe(row.getVenueName()),
                    row.getEventCount(),
                    row.getPerformanceCount()
            );
        }
    });


    pause();
}

private String safe(String value) {
    return value == null ? "-" : value;
}

private void report3() {

    printHeading("Organizer Revenue Ranking");

    System.out.println("1. Overall organizer ranking");
    System.out.println("2. Organizer ranking by country");
    System.out.println("3. Organizer ranking by city");


    OperationResult<List<OrganizerRevenueReport>> result;


    switch (readLine("Select option: ")) {

        case "1" -> result = reports.report3a();

        case "2" -> result = reports.report3b();

        case "3" -> result = reports.report3c();

        default -> {
            System.out.println("Unknown report option.");
            pause();
            return;
        }
    }


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-10s %-25s %-20s %-20s %-15s%n",
                "ID",
                "Organizer",
                "Country",
                "City",
                "Revenue"
        );


        for (OrganizerRevenueReport row : rows) {

            System.out.printf(
                    "%-10d %-25s %-20s %-20s $%-15s%n",
                    row.getOrganizerId(),
                    row.getOrganizerName(),
                    safe(row.getCountry()),
                    safe(row.getCity()),
                    row.getGrossRevenue().toPlainString()
            );
        }
    });


    pause();
}

private void report4() {

    printHeading("Potential Ticket Scalpers");


    LocalDateTime oneYearAgo =
            LocalDateTime.now().minusYears(1);


    OperationResult<List<ScalperDetectionReport>> result =
            reports.report4(oneYearAgo);


    printResult(result);


    result.getValue().ifPresent(rows -> {


        if (rows.isEmpty()) {
            System.out.println("No potential scalpers found.");
            return;
        }


        System.out.printf(
                "%-10s %-25s %-20s %-20s %-15s%n",
                "ID",
                "Customer",
                "City",
                "Purchased",
                "Listed"
        );


        for (ScalperDetectionReport row : rows) {

            System.out.printf(
                    "%-10d %-25s %-20s %-20d %-15d%n",
                    row.getCustomerId(),
                    row.getCustomerName(),
                    row.getCity(),
                    row.getTicketsPurchased(),
                    row.getTicketsListed()
            );
        }

    });


    pause();
}

private void report5() {
    System.out.println("Report 5 not implemented yet.");
    pause();
}

private void report6() {
    System.out.println("Report 6 not implemented yet.");
    pause();
}

private void report7() {
    System.out.println("Report 7 not implemented yet.");
    pause();
}

private void report8() {
    System.out.println("Report 8 not implemented yet.");
    pause();
}

private void report9() {
    System.out.println("Report 9 not implemented yet.");
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

    private Integer readPositiveIntWithRetry(String prompt) {
        return readValidatedInteger(
                prompt,
                value -> value <= 0
                        ? Optional.of("Enter a positive whole number.")
                        : Optional.empty()
        );
    }

    private Integer readValidatedInteger(
            String prompt,
            Function<Integer, Optional<String>> validator
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                printInputError("Enter a whole number.");
                if (!promptToRetry()) {
                    return null;
                }
                continue;
            }

            Optional<String> error = validator.apply(parsed);
            if (error.isEmpty()) {
                return parsed;
            }
            printInputError(error.get());
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private Integer readCheckedId(
            String prompt,
            Function<Integer, OperationResult<Void>> checker
    ) {
        while (running) {
            Integer id = readPositiveIntWithRetry(prompt);
            if (id == null) {
                return null;
            }
            OperationResult<Void> result = checker.apply(id);
            if (result.isSuccess()) {
                return id;
            }
            printResult(result);
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private Integer readUniqueCheckedId(
            String prompt,
            Function<Integer, OperationResult<Void>> checker,
            Set<Integer> usedValues,
            String duplicateMessage
    ) {
        while (running) {
            Integer id = readCheckedId(prompt, checker);
            if (id == null) {
                return null;
            }
            if (usedValues.add(id)) {
                return id;
            }
            printInputError(duplicateMessage);
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private Integer readUniquePositiveInt(
            String prompt,
            Set<Integer> usedValues,
            String duplicateMessage
    ) {
        while (running) {
            Integer value = readPositiveIntWithRetry(prompt);
            if (value == null) {
                return null;
            }
            if (usedValues.add(value)) {
                return value;
            }
            printInputError(duplicateMessage);
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal readDecimalWithRetry(
            String prompt,
            Function<BigDecimal, Optional<String>> validator
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }
            BigDecimal parsed;
            try {
                parsed = new BigDecimal(value);
            } catch (NumberFormatException exception) {
                printInputError("Enter a valid decimal number.");
                if (!promptToRetry()) {
                    return null;
                }
                continue;
            }

            Optional<String> error = validator.apply(parsed);
            if (error.isEmpty()) {
                return parsed;
            }
            printInputError(error.get());
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal readDecimalWithDefaultRetry(
            String prompt,
            BigDecimal defaultValue,
            Function<BigDecimal, Optional<String>> validator
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }
            BigDecimal parsed;
            try {
                parsed = value.isEmpty() ? defaultValue : new BigDecimal(value);
            } catch (NumberFormatException exception) {
                printInputError("Enter a valid decimal number.");
                if (!promptToRetry()) {
                    return null;
                }
                continue;
            }

            Optional<String> error = validator.apply(parsed);
            if (error.isEmpty()) {
                return parsed;
            }
            printInputError(error.get());
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime readDateTimeWithRetry(String prompt) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }
            try {
                return LocalDateTime.parse(value, DATE_TIME_FORMAT);
            } catch (DateTimeParseException exception) {
                printInputError("Enter date and time in YYYY-MM-DD HH:mm format.");
                if (!promptToRetry()) {
                    return null;
                }
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
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
