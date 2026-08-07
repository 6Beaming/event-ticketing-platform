package ui;

import common.OperationResult;
import common.DateRange;
import database.DatabaseConnection;
import database.TransactionManager;
import operations.booking.BookingOperations;
import operations.booking.BookingSummary;
import operations.cancellation.CancellationOperations;
import operations.cancellation.CancellationSummary;
import operations.event.ArtistBillingInput;
import operations.event.EventInput;
import operations.event.OrganizerEventOperations;
import operations.event.OrganizerPerformanceSalesHistory;
import operations.event.PerformanceInput;
import operations.inventory.GeneralAdmissionAvailability;
import operations.inventory.InventoryOperations;
import operations.inventory.InventoryState;
import operations.inventory.ReservedSeatAvailability;
import operations.inventory.ReservedSeatLocation;
import operations.pricing.PerformancePricingOperations;
import operations.pricing.PricingSetupInput;
import operations.pricing.PricingSetupSummary;
import operations.pricing.SectionTierInput;
import operations.pricing.TierInput;
import operations.profile.CustomerProfile;
import operations.profile.CustomerOrderHistoryEntry;
import operations.profile.PaymentInput;
import operations.profile.ProfileInput;
import operations.profile.ProfileValidator;
import operations.profile.UserProfileOperations;
import operations.resale.AvailableResaleTicket;
import operations.resale.OwnedResaleTicket;
import operations.resale.ResaleListingSummary;
import operations.resale.ResaleOperations;
import operations.resale.ResalePurchaseSummary;
import operations.resale.TicketOwnershipHistoryEntry;
import operations.review.CustomerReview;
import operations.review.ReviewOperations;
import operations.validation.OperationInputChecks;

import reports.ReportOperations;
import reports.TicketRevenueReport;
import reports.EventPerformanceReport;
import reports.OrganizerRevenueReport;
import reports.ScalperDetectionReport;
import reports.CustomerOrderRankingReport;
import reports.CancellationReport;
import reports.ResaleReport;
import reports.EventNounPhraseReport;
import reports.NounPhraseCount;
import reports.SellThroughReport;
import reports.SellThroughTierReport;
import reports.SellThroughBucketReport;


import queries.QueryOperations;
import queries.UpcomingPerformanceQuery;
import queries.PostalCodePerformanceQuery;
import queries.AddressPerformanceQuery;
import queries.DateRangePerformanceQuery;
import queries.FilteredPerformanceQuery;
import queries.LocationSearchInput;
import queries.SeatMapSummaryQuery;
import queries.BestAvailableQuery;

import toolkit.ToolkitOperations;
import toolkit.ComparablePerformance;
import toolkit.PricingRecommendation;
import toolkit.PricingRecommendationInput;
import toolkit.TierRecommendation;
import toolkit.RevenueImpactInput;
import toolkit.RevenueImpactEstimate;

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
    private final BookingOperations bookings;
    private final CancellationOperations cancellations;
    private final OrganizerEventOperations events;
    private final PerformancePricingOperations pricing;
    private final InventoryOperations inventory;
    private final ResaleOperations resale;
    private final ReviewOperations reviews;
    private final OperationInputChecks inputChecks;
    private final ReportOperations reports;
    private final QueryOperations queries;
    private final ToolkitOperations toolkit;

    private boolean running;
    private boolean featureInputMode;

    public TerminalApplication(Scanner input, DatabaseConnection database) {
        if (input == null || database == null) {
            throw new IllegalArgumentException("Terminal input and database are required");
        }
        this.input = input;
        this.database = database;
        TransactionManager transactions = new TransactionManager(database);
        this.profiles = new UserProfileOperations(transactions);
        this.bookings = new BookingOperations(transactions);
        this.cancellations = new CancellationOperations(transactions);
        this.events = new OrganizerEventOperations(transactions);
        this.pricing = new PerformancePricingOperations(transactions);
        this.inventory = new InventoryOperations(transactions);
        this.resale = new ResaleOperations(transactions);
        this.reviews = new ReviewOperations(transactions);
        this.inputChecks = new OperationInputChecks(transactions);
        this.reports = new ReportOperations(transactions);
        this.queries = new QueryOperations(transactions);
        this.toolkit = new ToolkitOperations(transactions);
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
        System.out.println("             Event ticketing database terminal");
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
        System.out.println(" 5. Ticket resale and histories");
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
            case "4" -> showBookingAndCancellationMenu();
            case "5" -> showResaleMenu();
            case "6" -> showReviewMenu();
            case "7" -> runFeatureAction(this::showSearches);
            case "8" -> runFeatureAction(this::showReports);
            case "9" -> runOnlineAction(() -> runFeatureAction(this::showToolkit));
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
            System.out.println("5. View customer order and ticket history");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::createCustomer);
                case "2" -> runOnlineAction(this::createOrganizer);
                case "3" -> runOnlineAction(this::viewCustomer);
                case "4" -> runOnlineAction(this::deactivateUser);
                case "5" -> runOnlineAction(this::viewCustomerOrderHistory);
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

    private void printInputError(String message) {
        if (featureInputMode) {
            printFeatureConflict(message);
            return;
        }
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
        Integer customerId = readCheckedId("Customer ID: ", inputChecks::checkCustomer);
        if (customerId == null) {
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

    private void viewCustomerOrderHistory() {
        Integer customerId = readCheckedId("Customer ID: ", inputChecks::checkCustomer);
        if (customerId == null) {
            return;
        }
        OperationResult<List<CustomerOrderHistoryEntry>> result =
                profiles.getCustomerOrderHistory(customerId);
        printResult(result);
        if (!result.isSuccess()) {
            pause();
            return;
        }

        List<CustomerOrderHistoryEntry> history = result.getValue().orElseThrow();
        if (history.isEmpty()) {
            System.out.println("No order or ticket history found for this customer.");
            pause();
            return;
        }

        int displayedOrderId = -1;
        for (CustomerOrderHistoryEntry entry : history) {
            if (entry.getOrderId() != displayedOrderId) {
                displayedOrderId = entry.getOrderId();
                System.out.println();
                System.out.println("Order ID: " + entry.getOrderId());
                System.out.println("Order type: "
                        + entry.getOrderType().toUpperCase(Locale.ROOT));
                System.out.println("Order date: "
                        + entry.getOrderDate().format(DATE_TIME_FORMAT));
                System.out.println("Payment card: " + entry.getMaskedCardNumber());
            }

            String location = entry.getRowName() == null
                    ? entry.getSectionName() + " (general admission)"
                    : entry.getSectionName() + ", row " + entry.getRowName()
                            + ", seat " + entry.getSeatNumber();
            System.out.println("  Ticket ID: " + entry.getTicketId());
            System.out.println("    Performance: " + entry.getPerformanceId()
                    + " - " + entry.getEventTitle());
            System.out.println("    Date/status: "
                    + entry.getPerformanceDateTime().format(DATE_TIME_FORMAT)
                    + " / " + entry.getPerformanceStatus());
            System.out.println("    Venue: " + entry.getVenueName()
                    + " (" + entry.getVenueCity() + ")");
            System.out.println("    Location/tier: " + location
                    + " / " + entry.getTierCode());
            System.out.println("    Paid: $" + entry.getPurchasePrice().toPlainString()
                    + " / ticket status: " + entry.getTicketStatus());
            System.out.println("    Ownership acquired: "
                    + entry.getAcquiredAt().format(DATE_TIME_FORMAT));
            System.out.println("    Ownership: "
                    + (entry.getOwnershipEndedAt() == null
                            ? "current"
                            : "ended "
                                    + entry.getOwnershipEndedAt().format(DATE_TIME_FORMAT)));
            if (entry.getCancellationDate() != null) {
                System.out.println("    Cancelled: "
                        + entry.getCancellationDate().format(DATE_TIME_FORMAT)
                        + " / refund: $" + entry.getRefundAmount().toPlainString());
            }
        }
        pause();
    }

    private void deactivateUser() {
        Integer userId = readCheckedId(
                "User ID to deactivate: ",
                inputChecks::checkActiveUser
        );
        if (userId == null) {
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
            System.out.println("3. Update event resale cap");
            System.out.println("4. View managed events and performance sales history");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::createEvent);
                case "2" -> runOnlineAction(this::addPerformance);
                case "3" -> runOnlineAction(this::updateResaleCap);
                case "4" -> runOnlineAction(this::viewOrganizerSalesHistory);
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
                    artistCount,
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
            Integer eventId = readCheckedId(
                    "Event ID: ",
                    events::checkEvent
            );
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

    private void updateResaleCap() {
        Integer eventId = readCheckedId(
                "Event ID: ",
                events::checkEvent
        );
        if (eventId == null) {
            return;
        }
        BigDecimal cap = readDecimalWithRetry(
                "New resale cap multiplier: ",
                value -> value.compareTo(BigDecimal.ONE) < 0
                        ? Optional.of("Resale cap multiplier must be at least 1.00.")
                        : Optional.empty()
        );
        if (cap == null) {
            return;
        }
        printResult(events.updateResaleCap(eventId, cap));
        pause();
    }

    private void viewOrganizerSalesHistory() {
        Integer organizerId = readCheckedId("Organizer ID: ", inputChecks::checkOrganizer);
        if (organizerId == null) {
            return;
        }
        OperationResult<List<OrganizerPerformanceSalesHistory>> result =
                events.getOrganizerPerformanceSalesHistory(organizerId);
        printResult(result);
        if (!result.isSuccess()) {
            pause();
            return;
        }

        List<OrganizerPerformanceSalesHistory> history = result.getValue().orElseThrow();
        if (history.isEmpty()) {
            System.out.println("This organizer does not manage any events.");
            pause();
            return;
        }

        int displayedEventId = -1;
        Integer displayedPerformanceId = null;
        for (OrganizerPerformanceSalesHistory entry : history) {
            if (entry.getEventId() != displayedEventId) {
                displayedEventId = entry.getEventId();
                displayedPerformanceId = null;
                System.out.println();
                System.out.println("Event " + entry.getEventId()
                        + ": " + entry.getEventTitle());
            }
            if (entry.getPerformanceId() == null) {
                System.out.println("  No performances have been added.");
                continue;
            }
            if (!entry.getPerformanceId().equals(displayedPerformanceId)) {
                displayedPerformanceId = entry.getPerformanceId();
                System.out.println("  Performance ID: " + entry.getPerformanceId());
                System.out.println("    Date/status: "
                        + entry.getPerformanceDateTime().format(DATE_TIME_FORMAT)
                        + " / " + entry.getPerformanceStatus());
                System.out.println("    Venue: " + entry.getVenueName()
                        + " (" + entry.getVenueCity() + ")");
                System.out.println("    Original sales: " + entry.getOriginalTicketCount()
                        + " tickets / $" + entry.getOriginalGrossRevenue().toPlainString());
                System.out.println("    Ticket status: " + entry.getActiveTicketCount()
                        + " active / " + entry.getCancelledTicketCount() + " cancelled");
                System.out.println("    Refunds: $" + entry.getRefundedAmount().toPlainString());
                System.out.println("    Completed resales: " + entry.getCompletedResaleCount()
                        + " / $" + entry.getResaleGrossRevenue().toPlainString());
                System.out.println("    Sales:");
            }
            if (entry.getTransactionId() == null) {
                System.out.println("      No sales recorded.");
                continue;
            }
            System.out.println("      Transaction " + entry.getTransactionId()
                    + " / " + entry.getTransactionType().toUpperCase(Locale.ROOT)
                    + " / " + entry.getTransactionDate().format(DATE_TIME_FORMAT));
            System.out.println("        Customer: " + entry.getCustomerId()
                    + " - " + entry.getCustomerName());
            System.out.println("        Ticket: " + entry.getTicketId()
                    + " / $" + entry.getSalePrice().toPlainString());
        }
        pause();
    }

    private void showPricingAndInventoryMenu() {
        boolean inMenu = true;
        while (running && inMenu) {
            printHeading("Performance pricing and inventory");
            System.out.println("1. View reserved-seat inventory");
            System.out.println("2. View general-admission inventory");
            System.out.println("3. Configure tiers and all section assignments");
            System.out.println("4. Update one tier price");
            System.out.println("5. Block a reserved seat");
            System.out.println("6. Unblock a reserved seat");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::viewReservedInventory);
                case "2" -> runOnlineAction(this::viewGeneralInventory);
                case "3" -> runOnlineAction(this::configurePricing);
                case "4" -> runOnlineAction(this::updateTierPrice);
                case "5" -> runOnlineAction(() -> changeSeatBlock(true));
                case "6" -> runOnlineAction(() -> changeSeatBlock(false));
                case "0" -> inMenu = false;
                default -> System.out.println("Unknown pricing/inventory option.");
            }
        }
    }

    private void configurePricing() {
        while (running) {
            PricingSetupInput pricingInput = readPricingSetup();
            if (pricingInput == null) {
                return;
            }
            OperationResult<PricingSetupSummary> result =
                    pricing.configurePricing(pricingInput);
            printResult(result);
            if (result.isSuccess()) {
                result.getValue().ifPresent(summary -> {
                    System.out.println("Performance ID: " + summary.getPerformanceId());
                    System.out.println("Tiers created: " + summary.getTierCount());
                    System.out.println("Sections assigned: " + summary.getAssignedSectionCount());
                    System.out.println("Tier and section map:");
                    for (SectionTierInput assignment : pricingInput.getAssignments()) {
                        System.out.println("  " + assignment.getSectionName()
                                + " -> " + assignment.getTierCode());
                    }
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

        Set<String> assignedTierCodes = new HashSet<>();
        List<SectionTierInput> assignments = new ArrayList<>();
        for (int index = 0; index < requiredAssignments; index++) {
            String section = venueSections.get(index);
            int remainingAssignments = requiredAssignments - index - 1;
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

    private void updateTierPrice() {
        while (running) {
            Integer performanceId = readCheckedId(
                    "Performance ID: ",
                    pricing::checkPerformanceForTierPriceUpdate
            );
            if (performanceId == null) {
                return;
            }
            String tierCode = readCheckedText(
                    "Tier: ",
                    value -> pricing.checkTierForPriceUpdate(performanceId, value)
            );
            if (tierCode == null) {
                return;
            }
            BigDecimal price = readDecimalWithRetry(
                    "New tier price: ",
                    value -> value.compareTo(BigDecimal.ZERO) <= 0
                            ? Optional.of("Tier price must be positive.")
                            : Optional.empty()
            );
            if (price == null) {
                return;
            }
            OperationResult<Void> result = pricing.updateTierPrice(
                    performanceId,
                    tierCode,
                    price
            );
            printResult(result);
            if (result.isSuccess()) {
                pause();
                return;
            }
            if (!promptToRetry()) {
                return;
            }
        }
    }

    private void changeSeatBlock(boolean block) {
        while (running) {
            Integer performanceId = readCheckedId(
                    "Performance ID: ",
                    inventory::checkPerformanceForSeatBlocking
            );
            if (performanceId == null) {
                return;
            }
            String rowName = readValidatedText(
                    "Row: ",
                    value -> value.isBlank()
                            ? Optional.of("A row letter is required.")
                            : Optional.empty()
            );
            if (rowName == null) {
                return;
            }
            Integer seatNumber = readPositiveIntWithRetry("Seat#: ");
            if (seatNumber == null) {
                return;
            }
            OperationResult<Void> result = block
                    ? inventory.blockSeat(performanceId, rowName, seatNumber)
                    : inventory.unblockSeat(performanceId, rowName, seatNumber);
            printResult(result);
            if (result.isSuccess()) {
                pause();
                return;
            }
            if (!promptToRetry()) {
                return;
            }
        }
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
            System.out.printf("%-20s %-8s %-6s %-8s %-10s %-10s%n",
                    "Section", "Row", "Seat", "Tier", "Price", "Status");
            for (ReservedSeatAvailability seat : seats) {
                System.out.printf("%-20s %-8s %-6d %-8s $%-9s %-10s%n",
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

    private void showBookingAndCancellationMenu() {
        boolean inMenu = true;
        while (running && inMenu) {
            printHeading("Ticket booking and cancellations");
            System.out.println("1. View available reserved seats");
            System.out.println("2. Book reserved seats");
            System.out.println("3. Book general-admission tickets");
            System.out.println("4. Cancel customer tickets");
            System.out.println("5. Cancel an organizer's performance");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::viewAvailableReservedSeats);
                case "2" -> runOnlineAction(this::bookReservedSeats);
                case "3" -> runOnlineAction(this::bookGeneralAdmission);
                case "4" -> runOnlineAction(this::cancelCustomerTickets);
                case "5" -> runOnlineAction(this::cancelPerformance);
                case "0" -> inMenu = false;
                default -> System.out.println("Unknown booking/cancellation option.");
            }
        }
    }

    private void viewAvailableReservedSeats() {
        Integer performanceId = readCheckedId(
                "Performance ID: ",
                bookings::checkPerformanceForBooking
        );
        if (performanceId == null) {
            return;
        }
        OperationResult<List<ReservedSeatAvailability>> result =
                inventory.getReservedInventory(performanceId);
        printResult(result);
        result.getValue().ifPresent(seats -> {
            List<ReservedSeatAvailability> availableSeats = seats.stream()
                    .filter(seat -> seat.getState() == InventoryState.AVAILABLE)
                    .toList();
            if (availableSeats.isEmpty()) {
                System.out.println("No reserved seats are currently available.");
                return;
            }
            System.out.printf("%-20s %-8s %-6s %-8s %-10s%n",
                    "Section", "Row", "Seat", "Tier", "Price");
            for (ReservedSeatAvailability seat : availableSeats) {
                System.out.printf("%-20s %-8s %-6d %-8s $%-9s%n",
                        seat.getSectionName(),
                        seat.getRowName(),
                        seat.getSeatNumber(),
                        seat.getTierCode(),
                        seat.getPrice().toPlainString());
            }
        });
        pause();
    }

    private void bookReservedSeats() {
        Integer customerId = readCheckedId("Customer ID: ", bookings::checkCustomerForBooking);
        if (customerId == null) {
            return;
        }
        Integer performanceId = readCheckedId(
                "Performance ID: ",
                bookings::checkPerformanceForBooking
        );
        if (performanceId == null) {
            return;
        }
        List<Integer> seatIds = null;
        while (running) {
            List<ReservedSeatLocation> locations = readReservedSeatLocationsWithRetry(
                    "Reserved seats (Row Seat#, comma-separated; e.g., A 1, A 2): "
            );
            if (locations == null) {
                return;
            }
            OperationResult<List<Integer>> resolved = inventory.resolveReservedSeatIds(
                    performanceId,
                    locations
            );
            if (resolved.isSuccess()) {
                seatIds = resolved.getValue().orElseThrow();
                break;
            }
            printResult(resolved);
            if (!promptToRetry()) {
                return;
            }
        }
        if (seatIds == null) {
            return;
        }
        OperationResult<BookingSummary> result = bookings.bookReservedSeats(
                customerId,
                performanceId,
                seatIds
        );
        printBookingResult(result);
        pause();
    }

    private void bookGeneralAdmission() {
        Integer customerId = readCheckedId("Customer ID: ", bookings::checkCustomerForBooking);
        if (customerId == null) {
            return;
        }
        Integer performanceId = null;
        while (running) {
            Integer candidatePerformanceId = readCheckedId(
                    "Performance ID: ",
                    bookings::checkPerformanceForBooking
            );
            if (candidatePerformanceId == null) {
                return;
            }
            OperationResult<List<GeneralAdmissionAvailability>> inventoryResult =
                    inventory.getGeneralAdmissionInventory(candidatePerformanceId);
            if (!inventoryResult.isSuccess()) {
                printResult(inventoryResult);
                if (!promptToRetry()) {
                    return;
                }
                continue;
            }
            List<String> sectionNames = inventoryResult.getValue().orElseThrow().stream()
                    .map(GeneralAdmissionAvailability::getSectionName)
                    .toList();
            if (sectionNames.isEmpty()) {
                System.out.println(
                        "CONFLICT: No general-admission sections are configured "
                                + "for this performance."
                );
                if (!promptToRetry()) {
                    return;
                }
                continue;
            }
            System.out.println(
                    "General-admission sections of this performance: "
                            + String.join(", ", sectionNames)
            );
            performanceId = candidatePerformanceId;
            break;
        }
        if (performanceId == null) {
            return;
        }
        int selectedPerformanceId = performanceId;
        String sectionName = readCheckedText(
                "General-admission section name: ",
                value -> inputChecks.checkGeneralAdmissionSection(selectedPerformanceId, value)
        );
        if (sectionName == null) {
            return;
        }
        Integer quantity = readPositiveIntWithRetry("Quantity: ");
        if (quantity == null) {
            return;
        }
        OperationResult<BookingSummary> result = bookings.bookGeneralAdmission(
                customerId,
                selectedPerformanceId,
                sectionName,
                quantity
        );
        printBookingResult(result);
        pause();
    }

    private void printBookingResult(OperationResult<BookingSummary> result) {
        printResult(result);
        result.getValue().ifPresent(summary -> {
            System.out.println("Transaction ID: " + summary.getTransactionId());
            System.out.println("Ticket IDs: " + summary.getTicketIds());
            System.out.println("Total: $" + summary.getTotal().toPlainString());
        });
    }

    private void cancelCustomerTickets() {
        List<Integer> ticketIds = readCheckedPositiveIntList(
                "Ticket IDs to cancel (comma-separated): ",
                inputChecks::checkTickets
        );
        if (ticketIds == null) {
            return;
        }
        String reason = readLine("Cancellation reason (optional): ");
        OperationResult<CancellationSummary> result = cancellations.cancelCustomerTickets(
                ticketIds,
                reason
        );
        printCancellationResult(result);
        pause();
    }

    private void cancelPerformance() {
        Integer performanceId = readCheckedId(
                "Performance ID: ",
                inputChecks::checkPerformance
        );
        if (performanceId == null) {
            return;
        }
        String reason = readLine("Cancellation reason (optional): ");
        OperationResult<CancellationSummary> result = cancellations.cancelPerformance(
                performanceId,
                reason
        );
        printCancellationResult(result);
        pause();
    }

    private void printCancellationResult(OperationResult<CancellationSummary> result) {
        printResult(result);
        result.getValue().ifPresent(summary -> {
            System.out.println("Tickets cancelled: " + summary.getCancelledTicketCount());
            System.out.println("Refund total: $" + summary.getRefundTotal().toPlainString());
        });
    }

    private void showResaleMenu() {
        boolean inMenu = true;
        while (running && inMenu) {
            printHeading("Ticket resale");
            System.out.println("1. View a customer's currently owned tickets");
            System.out.println("2. View available resale tickets by performance");
            System.out.println("3. Resale a ticket");
            System.out.println("4. Withdraw an active listing");
            System.out.println("5. Purchase a resale ticket");
            System.out.println("6. View a ticket's ownership history");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::viewCustomerOwnedTickets);
                case "2" -> runOnlineAction(this::viewAvailableResaleTickets);
                case "3" -> runOnlineAction(this::listTicketForResale);
                case "4" -> runOnlineAction(this::withdrawResaleListing);
                case "5" -> runOnlineAction(this::purchaseResaleListing);
                case "6" -> runOnlineAction(this::viewTicketOwnershipHistory);
                case "0" -> inMenu = false;
                default -> System.out.println("Unknown resale option.");
            }
        }
    }

    private void viewCustomerOwnedTickets() {
        Integer customerId = readCheckedId(
                "Customer ID: ",
                inputChecks::checkActiveCustomer
        );
        if (customerId == null) {
            return;
        }
        OperationResult<List<OwnedResaleTicket>> result = resale.getOwnedTickets(customerId);
        printResult(result);
        result.getValue().ifPresent(tickets -> {
            if (tickets.isEmpty()) {
                System.out.println("No active tickets are currently owned by this customer.");
                return;
            }
            System.out.printf(
                    "%-12s %-15s %-10s%n",
                    "Ticket ID",
                    "Performance ID",
                    "Status"
            );
            for (OwnedResaleTicket ticket : tickets) {
                System.out.printf(
                        "%-12d %-15d %-10s%n",
                        ticket.getTicketId(),
                        ticket.getPerformanceId(),
                        ticket.getStatus()
                );
            }
        });
        pause();
    }

    private void viewAvailableResaleTickets() {
        Integer performanceId = readCheckedId(
                "Performance ID: ",
                inputChecks::checkPerformance
        );
        if (performanceId == null) {
            return;
        }
        OperationResult<List<AvailableResaleTicket>> result =
                resale.getAvailableListings(performanceId);
        printResult(result);
        result.getValue().ifPresent(listings -> {
            if (listings.isEmpty()) {
                System.out.println(
                        "No resale tickets are currently available for this performance."
                );
                return;
            }
            System.out.printf(
                    "%-12s %-20s %-15s%n",
                    "Ticket ID",
                    "Seller customer ID",
                    "Listing price"
            );
            for (AvailableResaleTicket listing : listings) {
                System.out.printf(
                        "%-12d %-20d $%-14s%n",
                        listing.getTicketId(),
                        listing.getSellerCustomerId(),
                        listing.getListingPrice().toPlainString()
                );
            }
        });
        pause();
    }

    private void listTicketForResale() {
        Integer sellerId = readCheckedId(
                "Seller customer ID: ",
                inputChecks::checkActiveCustomer
        );
        if (sellerId == null) {
            return;
        }
        Integer ticketId = readCheckedId(
                "Ticket ID: ",
                id -> inputChecks.checkTicketForResale(sellerId, id)
        );
        if (ticketId == null) {
            return;
        }
        BigDecimal listingPrice = readCheckedDecimal(
                "Listing price: ",
                value -> inputChecks.checkResaleListingPrice(ticketId, value)
        );
        if (listingPrice == null) {
            return;
        }
        OperationResult<ResaleListingSummary> result = resale.listTicket(
                sellerId,
                ticketId,
                listingPrice
        );
        printResult(result);
        result.getValue().ifPresent(listing -> {
            System.out.println("Listing ID: " + listing.getListingId());
            System.out.println("Ticket ID: " + listing.getTicketId());
            System.out.println("Listing price: $" + listing.getListingPrice().toPlainString());
            System.out.println("Cap price: $" + listing.getCapPrice().toPlainString());
        });
        pause();
    }

    private void withdrawResaleListing() {
        Integer sellerId = readCheckedId(
                "Seller customer ID: ",
                inputChecks::checkActiveCustomer
        );
        if (sellerId == null) {
            return;
        }
        Integer ticketId = readCheckedId(
                "Ticket ID: ",
                inputChecks::checkActiveResaleTicket
        );
        if (ticketId == null) {
            return;
        }
        printResult(resale.withdrawListingByTicket(sellerId, ticketId));
        pause();
    }

    private void purchaseResaleListing() {
        Integer buyerId = readCheckedId(
                "Buyer customer ID: ",
                inputChecks::checkActiveCustomer
        );
        if (buyerId == null) {
            return;
        }
        Integer ticketId = readCheckedId(
                "Ticket ID: ",
                inputChecks::checkActiveResaleTicket
        );
        if (ticketId == null) {
            return;
        }
        OperationResult<ResalePurchaseSummary> result = resale.purchaseListingByTicket(
                buyerId,
                ticketId
        );
        printResult(result);
        result.getValue().ifPresent(purchase -> {
            System.out.println("Transaction ID: " + purchase.getTransactionId());
            System.out.println("Listing ID: " + purchase.getListingId());
            System.out.println("Ticket ID: " + purchase.getTicketId());
            System.out.println("Purchase price: $"
                    + purchase.getPurchasePrice().toPlainString());
        });
        pause();
    }

    private void viewTicketOwnershipHistory() {
        Integer ticketId = readCheckedId("Ticket ID: ", inputChecks::checkTicket);
        if (ticketId == null) {
            return;
        }
        OperationResult<List<TicketOwnershipHistoryEntry>> result =
                resale.getTicketOwnershipHistory(ticketId);
        printResult(result);
        if (!result.isSuccess()) {
            pause();
            return;
        }

        for (TicketOwnershipHistoryEntry entry : result.getValue().orElseThrow()) {
            System.out.println();
            System.out.println("Owner: " + entry.getCustomerId()
                    + " - " + entry.getCustomerName());
            System.out.println("Acquisition: "
                    + entry.getTransactionType().toUpperCase(Locale.ROOT)
                    + " transaction " + entry.getTransactionId()
                    + " / $" + entry.getPurchasePrice().toPlainString());
            if (entry.getListingId() != null) {
                System.out.println("Resale listing ID: " + entry.getListingId());
            }
            System.out.println("Owned from: " + entry.getAcquiredAt().format(DATE_TIME_FORMAT));
            System.out.println("Ownership: "
                    + (entry.getEndedAt() == null
                            ? "current"
                            : "ended " + entry.getEndedAt().format(DATE_TIME_FORMAT)));
        }
        pause();
    }

    private void showReviewMenu() {
        boolean inMenu = true;
        while (running && inMenu) {
            printHeading("Attendance reviews");
            System.out.println("1. Submit an event and venue review");
            System.out.println("2. View a customer's submitted reviews");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::submitReview);
                case "2" -> runOnlineAction(this::viewCustomerReviews);
                case "0" -> inMenu = false;
                default -> System.out.println("Unknown review option.");
            }
        }
    }

    private void submitReview() {
        Integer customerId = readCheckedId("Customer ID: ", inputChecks::checkActiveCustomer);
        if (customerId == null) {
            return;
        }
        OperationResult<List<Integer>> reviewableResult =
                reviews.getReviewablePerformanceIds(customerId);
        if (!reviewableResult.isSuccess()) {
            printResult(reviewableResult);
            pause();
            return;
        }
        List<Integer> reviewablePerformanceIds = reviewableResult.getValue().orElseThrow();
        if (reviewablePerformanceIds.isEmpty()) {
            System.out.println(
                    "No attended and completed performances are currently available for review."
            );
            System.out.println("Use option 2 to view this customer's submitted reviews.");
            pause();
            return;
        } else {
            System.out.println(
                    "Performance IDs available for review: "
                            + String.join(
                                    ", ",
                                    reviewablePerformanceIds.stream()
                                            .map(String::valueOf)
                                            .toList()
                            )
            );
        }
        Integer performanceId = readCheckedId(
                "Performance ID: ",
                id -> reviews.checkReviewEligibility(customerId, id)
        );
        if (performanceId == null) {
            return;
        }
        Integer eventRating = readValidatedInteger(
                "Event rating (1-5): ",
                value -> value < 1 || value > 5
                        ? Optional.of("Event rating must be from 1 to 5.")
                        : Optional.empty()
        );
        if (eventRating == null) {
            return;
        }
        Integer venueRating = readValidatedInteger(
                "Venue rating (1-5): ",
                value -> value < 1 || value > 5
                        ? Optional.of("Venue rating must be from 1 to 5.")
                        : Optional.empty()
        );
        if (venueRating == null) {
            return;
        }
        String comment = readValidatedText(
                "Comment: ",
                value -> value.trim().isEmpty()
                        ? Optional.of("Review comment is required.")
                        : Optional.empty()
        );
        if (comment == null) {
            return;
        }
        printResult(reviews.submitReview(
                customerId,
                performanceId,
                eventRating,
                venueRating,
                comment
        ));
        pause();
    }

    private void viewCustomerReviews() {
        Integer customerId = readCheckedId(
                "Customer ID: ",
                inputChecks::checkActiveCustomer
        );
        if (customerId == null) {
            return;
        }

        OperationResult<List<CustomerReview>> result = reviews.getCustomerReviews(customerId);
        printResult(result);
        result.getValue().ifPresent(customerReviews -> {
            if (customerReviews.isEmpty()) {
                System.out.println("No submitted reviews found for this customer.");
                return;
            }
            for (CustomerReview review : customerReviews) {
                System.out.println();
                System.out.println("Performance ID: " + review.getPerformanceId());
                System.out.println("Event rating: " + review.getEventRating() + "/5");
                System.out.println("Venue rating: " + review.getVenueRating() + "/5");
                System.out.println("Comment: " + review.getComment());
                System.out.println(
                        "Review date: " + review.getReviewDate().format(DATE_TIME_FORMAT)
                );
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
        boolean inMenu = true;

        while (running && inMenu) {
            printHeading("Required Searches");

            System.out.println("1. Upcoming performances near a location");
            System.out.println("2. Search by postal code");
            System.out.println("3. Search by exact address");
            System.out.println("4. Date range and ticket availability");
            System.out.println("5. Performance filter search");
            System.out.println("6. Seat map summary");
            System.out.println("7. Best available seats");
            System.out.println("0. Back");

            switch (readLine("Select an option: ")) {
                case "1" -> runOnlineAction(this::query1);
                case "2" -> runOnlineAction(this::query2);
                case "3" -> runOnlineAction(this::query3);
                case "4" -> runOnlineAction(this::query4);
                case "5" -> runOnlineAction(this::query5);
                case "6" -> runOnlineAction(this::query6);
                case "7" -> runOnlineAction(this::query7);
                case "0" -> inMenu = false;
                default -> {
                    printFeatureConflict("Enter a search option from 0 to 7.");
                    if (!promptToRetry()) {
                        inMenu = false;
                    }
                }
            }
        }
    }

 private void query1() {

    printHeading("Upcoming Performances Near Location");

    Double latitude = readValidatedDouble(
            "Latitude: ",
            value -> value < -90 || value > 90
                    ? Optional.of("Latitude must be between -90 and 90.")
                    : Optional.empty()
    );
    if (latitude == null) {
        return;
    }

    Double longitude = readValidatedDouble(
            "Longitude: ",
            value -> value < -180 || value > 180
                    ? Optional.of("Longitude must be between -180 and 180.")
                    : Optional.empty()
    );
    if (longitude == null) {
        return;
    }

    Double radiusKm = readDoubleWithDefaultRetry(
            "Maximum distance in km (default 50): ",
            50.0,
            value -> value <= 0
                    ? Optional.of("Search distance must be greater than zero.")
                    : Optional.empty()
    );
    if (radiusKm == null) {
        return;
    }


    System.out.println("1. Rank by distance");
    System.out.println("2. Rank by cheapest price ascending");
    System.out.println("3. Rank by cheapest price descending");


    Integer sortChoice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 3
                    ? Optional.of("Select sort option 1, 2, or 3.")
                    : Optional.empty()
    );
    if (sortChoice == null) {
        return;
    }

    String sortBy = switch (sortChoice) {
        case 2 -> "price_asc";
        case 3 -> "price_desc";
        default -> "distance";
    };

    OperationResult<List<UpcomingPerformanceQuery>> result =
            queries.query1(
                    latitude,
                    longitude,
                    radiusKm,
                    sortBy
            );


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No performances found.");
            return;
        }


        System.out.printf(
                "%-8s %-25s %-20s %-15s %-12s %-12s%n",
                "ID",
                "Event",
                "Venue",
                "City",
                "Distance",
                "Price"
        );


        for (UpcomingPerformanceQuery row : rows) {

            System.out.printf(
                    "%-8d %-25s %-20s %-15s %-12.2f %-12.2f%n",
                    row.getPerformanceId(),
                    row.getTitle(),
                    row.getVenueName(),
                    row.getCity(),
                    row.getDistanceKm(),
                    row.getCheapestAvailablePrice()
            );
        }
    });


    pause();
}


private void query2() {

    printHeading("Search Performances By Postal Code");

    String postalCode = readValidatedText(
            "Enter postal code: ",
            value -> value.isBlank()
                    ? Optional.of("Postal code is required.")
                    : Optional.empty()
    );
    if (postalCode == null) {
        return;
    }


    OperationResult<List<PostalCodePerformanceQuery>> result =
            queries.query2(postalCode);


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No performances found.");
            return;
        }


        System.out.printf(
                "%-8s %-25s %-20s %-12s %-15s %-20s%n",
                "ID",
                "Event",
                "Venue",
                "Postal",
                "City",
                "Date"
        );


        for (PostalCodePerformanceQuery row : rows) {

            System.out.printf(
                    "%-8d %-25s %-20s %-12s %-15s %-20s%n",
                    row.getPerformanceId(),
                    row.getTitle(),
                    row.getVenueName(),
                    row.getPostalCode(),
                    row.getCity(),
                    row.getDateTime()
            );
        }
    });


    pause();
}

private void query3() {

    printHeading("Search Venue By Exact Address");

    String address = readValidatedText(
            "Enter address: ",
            value -> value.isBlank()
                    ? Optional.of("Address is required.")
                    : Optional.empty()
    );
    if (address == null) {
        return;
    }


    OperationResult<List<AddressPerformanceQuery>> result =
            queries.query3(address);


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No venue found.");
            return;
        }


        System.out.printf(
                "%-8s %-20s %-25s %-15s %-15s %-8s %-25s %-20s%n",
                "VenueID",
                "Venue",
                "Address",
                "City",
                "Country",
                "PerfID",
                "Event",
                "Date"
        );


        for (AddressPerformanceQuery row : rows) {

            System.out.printf(
                    "%-8d %-20s %-25s %-15s %-15s %-8d %-25s %-20s%n",
                    row.getVenueId(),
                    row.getVenueName(),
                    row.getAddress(),
                    row.getCity(),
                    row.getCountry(),
                    row.getPerformanceId(),
                    row.getTitle(),
                    row.getDateTime()
            );
        }
    });


    pause();
}

private void query4() {

    printHeading("Location Search With Date and Availability");

    System.out.println("Refine which location search?");
    System.out.println("1. Coordinates and distance");
    System.out.println("2. Postal code");
    System.out.println("3. Exact address");

    Integer locationChoice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 3
                    ? Optional.of("Select location option 1, 2, or 3.")
                    : Optional.empty()
    );
    if (locationChoice == null) {
        return;
    }

    LocationSearchInput location;
    switch (locationChoice) {
        case 1 -> {
            Double latitude = readValidatedDouble(
                    "Latitude: ",
                    value -> value < -90 || value > 90
                            ? Optional.of("Latitude must be between -90 and 90.")
                            : Optional.empty()
            );
            if (latitude == null) {
                return;
            }

            Double longitude = readValidatedDouble(
                    "Longitude: ",
                    value -> value < -180 || value > 180
                            ? Optional.of("Longitude must be between -180 and 180.")
                            : Optional.empty()
            );
            if (longitude == null) {
                return;
            }

            Double radiusKm = readDoubleWithDefaultRetry(
                    "Maximum distance in km (default 50): ",
                    50.0,
                    value -> value <= 0
                            ? Optional.of("Search distance must be greater than zero.")
                            : Optional.empty()
            );
            if (radiusKm == null) {
                return;
            }

            System.out.println("1. Rank by distance");
            System.out.println("2. Rank by cheapest price ascending");
            System.out.println("3. Rank by cheapest price descending");
            Integer sortChoice = readValidatedInteger(
                    "Select option: ",
                    value -> value < 1 || value > 3
                            ? Optional.of("Select sort option 1, 2, or 3.")
                            : Optional.empty()
            );
            if (sortChoice == null) {
                return;
            }

            String sortBy = switch (sortChoice) {
                case 2 -> "price_asc";
                case 3 -> "price_desc";
                default -> "distance";
            };
            location = LocationSearchInput.coordinates(
                    latitude,
                    longitude,
                    radiusKm,
                    sortBy
            );
        }
        case 2 -> {
            String postalCode = readValidatedText(
                    "Enter postal code: ",
                    value -> value.isBlank()
                            ? Optional.of("Postal code is required.")
                            : Optional.empty()
            );
            if (postalCode == null) {
                return;
            }
            location = LocationSearchInput.postalCode(postalCode);
        }
        case 3 -> {
            String address = readValidatedText(
                    "Enter exact address: ",
                    value -> value.isBlank()
                            ? Optional.of("Address is required.")
                            : Optional.empty()
            );
            if (address == null) {
                return;
            }
            location = LocationSearchInput.address(address);
        }
        default -> throw new IllegalStateException("Unexpected location option");
    }

    LocalDateTime[] dates = readSearchDateRange(false);
    if (dates == null) {
        return;
    }

    Integer minTickets = readPositiveIntWithRetry(
            "Minimum available tickets: "
    );
    if (minTickets == null) {
        return;
    }


    OperationResult<List<DateRangePerformanceQuery>> result =
            queries.query4(
                    location,
                    dates[0],
                    dates[1],
                    minTickets
            );


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No performances found.");
            return;
        }


        System.out.printf(
                "%-8s %-22s %-20s %-14s %-17s %-10s %-10s %-9s%n",
                "ID",
                "Event",
                "Venue",
                "City",
                "Date",
                "Available",
                "Price",
                "Distance"
        );


        for (DateRangePerformanceQuery row : rows) {

            System.out.printf(
                    "%-8d %-22s %-20s %-14s %-17s %-10d $%-9.2f %-9s%n",
                    row.getPerformanceId(),
                    row.getTitle(),
                    row.getVenueName(),
                    row.getCity(),
                    row.getDateTime().format(DATE_TIME_FORMAT),
                    row.getAvailableTickets(),
                    row.getCheapestPrice(),
                    row.getDistanceKm() == null
                            ? "-"
                            : String.format(Locale.ROOT, "%.2f km", row.getDistanceKm())
            );
        }
    });


    pause();
}

private void query5() {

    printHeading("Filtered Performance Search");
    System.out.println("Press Enter to skip any filter.");

    String city = readLine("City: ");
    String segment = readLine("Segment: ");
    String genre = readLine("Genre: ");

    LocalDateTime[] dates = readSearchDateRange(true);
    if (dates == null) {
        return;
    }

    Double minPrice = readOptionalDoubleWithRetry(
            "Minimum price: ",
            value -> value < 0
                    ? Optional.of("Minimum price must be zero or greater.")
                    : Optional.empty()
    );
    if (!running) {
        return;
    }

    Double maxPrice = readOptionalDoubleWithRetry(
            "Maximum price: ",
            value -> value < 0
                    ? Optional.of("Maximum price must be zero or greater.")
                    : Optional.empty()
    );
    if (!running) {
        return;
    }

    while (minPrice != null && maxPrice != null && minPrice > maxPrice) {
        printFeatureConflict("Minimum price cannot exceed maximum price.");
        if (!promptToRetry()) {
            return;
        }
        minPrice = readOptionalDoubleWithRetry(
                "Minimum price: ",
                value -> value < 0
                        ? Optional.of("Minimum price must be zero or greater.")
                        : Optional.empty()
        );
        if (!running) {
            return;
        }
        maxPrice = readOptionalDoubleWithRetry(
                "Maximum price: ",
                value -> value < 0
                        ? Optional.of("Maximum price must be zero or greater.")
                        : Optional.empty()
        );
        if (!running) {
            return;
        }
    }

    Integer minAvailable = readOptionalPositiveIntWithRetry(
            "Minimum available tickets: "
    );
    if (!running) {
        return;
    }

    String sectionType = readOptionalSectionTypeWithRetry(
            "Section type (reserved/general): "
    );
    if (!running) {
        return;
    }


    OperationResult<List<FilteredPerformanceQuery>> result =
            queries.query5(
                    city,
                    segment,
                    genre,
                    dates.length == 0 ? null : dates[0],
                    dates.length == 0 ? null : dates[1],
                    minPrice,
                    maxPrice,
                    minAvailable,
                    sectionType
            );


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No performances found.");
            return;
        }


        System.out.printf(
                "%-8s %-22s %-18s %-14s %-14s %-14s %-17s %-10s %-10s%n",
                "ID",
                "Event",
                "Venue",
                "City",
                "Segment",
                "Genre",
                "Date",
                "Price",
                "Available"
        );


        for (FilteredPerformanceQuery row : rows) {

            System.out.printf(
                    "%-8d %-22s %-18s %-14s %-14s %-14s %-17s $%-9.2f %-10d%n",
                    row.getPerformanceId(),
                    row.getTitle(),
                    row.getVenueName(),
                    row.getCity(),
                    row.getSegment(),
                    row.getGenre(),
                    row.getDateTime().format(DATE_TIME_FORMAT),
                    row.getCheapestPrice(),
                    row.getAvailableTickets()
            );
        }

    });


    pause();
}

private void query6() {

    printHeading("Seat Map Summary");

    while (running) {
        Integer performanceId = readCheckedId(
                "Performance ID: ",
                inputChecks::checkPerformance
        );
        if (performanceId == null) {
            return;
        }


        OperationResult<List<SeatMapSummaryQuery>> result =
                queries.query6(performanceId);

        if (!result.isSuccess()) {
            boolean retry = promptAfterFeatureFailure(result);
            if (retry) {
                continue;
            }
            if (!FeatureInputPolicy.isRetryable(result.getStatus())) {
                pause();
            }
            return;
        }

        List<SeatMapSummaryQuery> rows = result.getValue().orElseThrow();
        if (rows.isEmpty()) {
            printFeatureConflict(
                    "No seat-map inventory is configured for this performance."
            );
            if (promptToRetry()) {
                continue;
            }
            return;
        }

        printResult(result);
        System.out.printf(
                "%-20s %-10s %-10s %-10s %-10s %-10s%n",
                "Section",
                "Tier",
                "Price",
                "Available",
                "Sold",
                "Blocked"
        );

        for (SeatMapSummaryQuery row : rows) {
            System.out.printf(
                    "%-20s %-10s %-10.2f %-10d %-10d %-10d%n",
                    row.getSectionName(),
                    row.getTierCode(),
                    row.getPrice(),
                    row.getAvailable(),
                    row.getSold(),
                    row.getBlocked()
            );
        }

        pause();
        return;
    }
}

private void query7() {

    printHeading("Best Available Seats");

    while (running) {
        Integer performanceId = readCheckedId(
                "Performance ID: ",
                inputChecks::checkPerformance
        );
        if (performanceId == null) {
            return;
        }

        Integer quantity = readPositiveIntWithRetry("Number of seats required: ");
        if (quantity == null) {
            return;
        }

        Double budget = readOptionalDoubleWithRetry(
                "Budget (optional): ",
                value -> value <= 0
                        ? Optional.of("Budget must be positive when supplied.")
                        : Optional.empty()
        );
        if (!running) {
            return;
        }

        OperationResult<BestAvailableQuery> result =
                queries.query7(
                        performanceId,
                        quantity,
                        budget
                );

        if (!result.isSuccess()) {
            boolean retry = promptAfterFeatureFailure(result);
            if (retry) {
                continue;
            }
            if (!FeatureInputPolicy.isRetryable(result.getStatus())) {
                pause();
            }
            return;
        }

        BestAvailableQuery row = result.getValue().orElse(null);
        if (row == null) {
            printFeatureConflict(
                    "No available seat group satisfies the quantity and budget."
            );
            if (promptToRetry()) {
                continue;
            }
            return;
        }

        printResult(result);
        System.out.println();

        System.out.printf(
                "Section: %s%n" +
                "Row: %s%n" +
                "Seats: %d - %d%n" +
                "Total Price: %.2f%n",
                row.getSectionName(),
                row.getRowName(),
                row.getStartSeat(),
                row.getEndSeat(),
                row.getTotalPrice()
        );

        pause();
        return;
    }
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
            default -> {
                printFeatureConflict("Enter a report option from 0 to 9.");
                if (!promptToRetry()) {
                    inMenu = false;
                }
            }
        }
    }
}


private LocalDateTime[] readReportDateRange() {
    while (running) {
        String startValue = readLine("Start date (YYYY-MM-DD): ");
        if (!running) {
            return null;
        }

        String endValue = readLine("End date (YYYY-MM-DD, inclusive): ");
        if (!running) {
            return null;
        }

        try {
            LocalDate startDate = LocalDate.parse(startValue);
            LocalDate endDate = LocalDate.parse(endValue);
            DateRange range = DateRange.fromInclusiveDates(startDate, endDate);
            return new LocalDateTime[]{
                    range.getStartInclusive(),
                    range.getEndExclusive()
            };
        } catch (DateTimeParseException exception) {
            printInputError("Enter dates in YYYY-MM-DD format.");
        } catch (IllegalArgumentException exception) {
            printInputError("Start date cannot be after end date.");
        }

        if (!promptToRetry()) {
            return null;
        }
    }
    return null;
}

private LocalDateTime[] readSearchDateRange(boolean optional) {
    while (running) {
        String startValue = readLine(
                optional
                        ? "Start date (YYYY-MM-DD, blank to skip): "
                        : "Start date (YYYY-MM-DD): "
        );
        if (!running) {
            return null;
        }
        if (optional && startValue.isBlank()) {
            return new LocalDateTime[0];
        }

        String endValue = readLine("End date (YYYY-MM-DD, inclusive): ");
        if (!running) {
            return null;
        }

        try {
            LocalDate startDate = LocalDate.parse(startValue);
            LocalDate endDate = LocalDate.parse(endValue);
            DateRange range = DateRange.fromInclusiveDates(startDate, endDate);
            return new LocalDateTime[]{
                    range.getStartInclusive(),
                    range.getEndExclusive()
            };
        } catch (DateTimeParseException exception) {
            printInputError("Enter dates in YYYY-MM-DD format.");
        } catch (IllegalArgumentException exception) {
            printInputError("Start date cannot be after end date.");
        }

        if (!promptToRetry()) {
            return null;
        }
    }
    return null;
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
    Integer choice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 2
                    ? Optional.of("Select report option 1 or 2.")
                    : Optional.empty()
    );
    if (choice == null) {
        return;
    }


    OperationResult<List<TicketRevenueReport>> result;
    LocalDateTime[] dates = readReportDateRange();
    if (dates == null) {
        return;
    }

    switch (choice) {
        case 1 -> {
            result = reports.report1a(
                    dates[0],
                    dates[1]
            );}

        case 2 -> {
            String city = readValidatedText(
                    "City: ",
                    value -> value.isBlank()
                            ? Optional.of("City is required.")
                            : Optional.empty()
            );
            if (city == null) {
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


    Integer choice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 4
                    ? Optional.of("Select report option 1, 2, 3, or 4.")
                    : Optional.empty()
    );
    if (choice == null) {
        return;
    }

    OperationResult<List<EventPerformanceReport>> result;
    switch (choice) {
        case 1 -> result = reports.report2a();
        case 2 -> result = reports.report2b();
        case 3 -> result = reports.report2c();
        case 4 -> result = reports.report2d();
        default -> throw new IllegalStateException("Unexpected R2 option");
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


    Integer choice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 3
                    ? Optional.of("Select report option 1, 2, or 3.")
                    : Optional.empty()
    );
    if (choice == null) {
        return;
    }

    OperationResult<List<OrganizerRevenueReport>> result;
    switch (choice) {
        case 1 -> result = reports.report3a();
        case 2 -> result = reports.report3b();
        case 3 -> result = reports.report3c();
        default -> throw new IllegalStateException("Unexpected R3 option");
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
            LocalDateTime.now(ZoneOffset.UTC).minusYears(1);


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

    printHeading("Customer Order Rankings");

    System.out.println("1. Ranking by order count in period");
    System.out.println("2. Ranking by city (past year)");


    Integer choice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 2
                    ? Optional.of("Select report option 1 or 2.")
                    : Optional.empty()
    );
    if (choice == null) {
        return;
    }

    OperationResult<List<CustomerOrderRankingReport>> result;
    switch (choice) {
        case 1 -> {

            LocalDateTime[] dates = readReportDateRange();

            if (dates == null) {
                return;
            }

            result = reports.report5a(
                    dates[0],
                    dates[1]
            );
        }


        case 2 -> {

            result = reports.report5b(
                    LocalDateTime.now(ZoneOffset.UTC).minusYears(1)
            );
        }


        default -> throw new IllegalStateException("Unexpected R5 option");
    }


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-10s %-25s %-20s %-15s%n",
                "ID",
                "Customer",
                "City",
                "Orders"
        );


        for (CustomerOrderRankingReport row : rows) {

            System.out.printf(
                    "%-10d %-25s %-20s %-15d%n",
                    row.getCustomerId(),
                    row.getCustomerName(),
                    safe(row.getCity()),
                    row.getNumberOfOrders()
            );
        }

    });


    pause();
}

private void report6() {

    printHeading("Cancellation Reports");

    System.out.println("1. Customers with most cancelled tickets");
    System.out.println("2. Organizers with most cancelled performances");


    OperationResult<List<CancellationReport>> result;


    LocalDateTime oneYearAgo =
            LocalDateTime.now(ZoneOffset.UTC).minusYears(1);


    Integer choice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 2
                    ? Optional.of("Select report option 1 or 2.")
                    : Optional.empty()
    );
    if (choice == null) {
        return;
    }

    switch (choice) {
        case 1 -> result = reports.report6a(oneYearAgo);
        case 2 -> result = reports.report6b(oneYearAgo);
        default -> throw new IllegalStateException("Unexpected R6 option");
    }


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-10s %-25s %-20s%n",
                "ID",
                "Name",
                "Cancelled"
        );


        for (CancellationReport row : rows) {

            System.out.printf(
                    "%-10d %-25s %-20d%n",
                    row.getId(),
                    row.getName(),
                    row.getCount()
            );
        }

    });


    pause();
}

private void report7() {

    printHeading("Sell-Through Report");

    System.out.println("1. Sell-through by performance");
    System.out.println("2. Sell-through by tier");
    System.out.println("3. Sold-out / under 25% by city");


    Integer choice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 3
                    ? Optional.of("Select report option 1, 2, or 3.")
                    : Optional.empty()
    );
    if (choice == null) {
        return;
    }

    switch (choice) {
        case 1 -> report7a();
        case 2 -> report7b();
        case 3 -> report7c();
        default -> throw new IllegalStateException("Unexpected R7 option");
    }
}


private void report7a() {

    printHeading("Sell-through by Performance");


    OperationResult<List<SellThroughReport>> result =
            reports.report7a();


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-12s %-25s %-20s %-12s %-12s %-15s%n",
                "Performance",
                "Event",
                "City",
                "Capacity",
                "Sold",
                "Rate"
        );


        for (SellThroughReport row : rows) {

            System.out.printf(
                    "%-12d %-25s %-20s %-12d %-12d %-15s%n",
                    row.getPerformanceId(),
                    row.getTitle(),
                    row.getCity(),
                    row.getCapacity(),
                    row.getNumSold(),
                    formatPercentage(row.getSellThroughRate())
            );
        }

    });


    pause();
}

private void report7b() {

    printHeading("Sell-through by Tier");


    OperationResult<List<SellThroughTierReport>> result =
            reports.report7b();


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-12s %-12s %-12s %-12s %-15s%n",
                "Performance",
                "Tier",
                "Capacity",
                "Sold",
                "Rate"
        );


        for (SellThroughTierReport row : rows) {

            System.out.printf(
                    "%-12d %-12s %-12d %-12d %-15s%n",
                    row.getPerformanceId(),
                    row.getTierCode(),
                    row.getCapacity(),
                    row.getNumSold(),
                    formatPercentage(row.getSellThroughRate())
            );
        }

    });


    pause();
}

private void report7c() {

    printHeading("Sold-out / Under 25% Sell-through");


    Integer year = readValidatedInteger(
            "Year: ",
            value -> value < 1000 || value > 9999
                    ? Optional.of("Enter a four-digit year.")
                    : Optional.empty()
    );
    if (year == null) {
        return;
    }

    Integer month = readValidatedInteger(
            "Month: ",
            value -> value < 1 || value > 12
                    ? Optional.of("Month must be from 1 to 12.")
                    : Optional.empty()
    );
    if (month == null) {
        return;
    }


    OperationResult<List<SellThroughBucketReport>> result =
            reports.report7c(
                    year,
                    month
            );


    printResult(result);


    result.getValue().ifPresent(rows -> {

        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-20s %-12s %-25s %-15s %-15s%n",
                "City",
                "Performance",
                "Event",
                "Rate",
                "Bucket"
        );


        for (SellThroughBucketReport row : rows) {

            System.out.printf(
                    "%-20s %-12d %-25s %-15s %-15s%n",
                    safe(row.getCity()),
                    row.getPerformanceId(),
                    row.getTitle(),
                    formatPercentage(row.getSellThroughRate()),
                    row.getBucket()
            );
        }

    });


    pause();
}

private void report8() {

    printHeading("Resale Reports");


    System.out.println("1. Resale statistics per event");
    System.out.println("2. Top 10 events by resale volume");


    Integer choice = readValidatedInteger(
            "Select option: ",
            value -> value < 1 || value > 2
                    ? Optional.of("Select report option 1 or 2.")
                    : Optional.empty()
    );
    if (choice == null) {
        return;
    }

    OperationResult<List<ResaleReport>> result;
    switch (choice) {
        case 1 -> result = reports.report8a();
        case 2 -> {

            LocalDateTime[] dates = readReportDateRange();

            if (dates == null) {
                return;
            }

            result = reports.report8b(
                    dates[0],
                    dates[1]
            );
        }


        default -> throw new IllegalStateException("Unexpected R8 option");
    }


    printResult(result);


    result.getValue().ifPresent(rows -> {


        if (rows.isEmpty()) {
            System.out.println("No report data found.");
            return;
        }


        System.out.printf(
                "%-10s %-30s %-15s %-15s %-15s%n",
                "ID",
                "Event",
                "Resales",
                "Avg Markup",
                "At Cap"
        );


        for (ResaleReport row : rows) {


            System.out.printf(
                    "%-10d %-30s %-15d %-15s %-15s%n",
                    row.getEventId(),
                    row.getEventTitle(),
                    row.getResaleCount(),
                    formatPercentage(row.getAvgMarkupPct()),
                    formatPercentage(row.getPctAtCap())
            );
        }

    });


    pause();
}

private void report9() {

    printHeading("Event Word Cloud Report");


    OperationResult<List<EventNounPhraseReport>> result =
            reports.report9();


    printResult(result);


    result.getValue().ifPresent(rows -> {


        if (rows.isEmpty()) {

            System.out.println(
                    "No review data found."
            );

            return;
        }


        for (EventNounPhraseReport report : rows) {


            System.out.println();
            System.out.println(
                    "Event: "
                            + report.getEventTitle()
            );


            System.out.printf(
                    "%-30s %-10s%n",
                    "Noun Phrase",
                    "Count"
            );


            for (NounPhraseCount phrase :
                    report.getPhrases()) {


                System.out.printf(
                        "%-30s %-10d%n",
                        phrase.getPhrase(),
                        phrase.getCount()
                );
            }
        }

    });


    pause();
}
    private void showToolkit() {
        printHeading("Performance pricing recommendation");
        PricingRecommendation recommendation = null;
        while (running) {
            Integer genreId = readPositiveIntWithRetry("Genre ID: ");
            if (genreId == null) {
                return;
            }
            String city = readValidatedText(
                    "City: ",
                    value -> value == null || value.isBlank()
                            ? Optional.of("City is required.")
                            : Optional.empty()
            );
            if (city == null) {
                return;
            }
            Integer venueCapacity = readPositiveIntWithRetry("Venue capacity: ");
            if (venueCapacity == null) {
                return;
            }

            PricingRecommendationInput input = new PricingRecommendationInput(
                    genreId,
                    city.trim(),
                    venueCapacity,
                    0.25,
                    24,
                    20
            );
            OperationResult<PricingRecommendation> result =
                    toolkit.recommendPricing(input);
            if (!result.isSuccess()) {
                boolean retry = promptAfterFeatureFailure(result);
                if (retry) {
                    continue;
                }
                if (!FeatureInputPolicy.isRetryable(result.getStatus())) {
                    pause();
                }
                return;
            }

            printResult(result);
            recommendation = result.getValue().orElseThrow();
            break;
        }
        if (recommendation == null) {
            return;
        }

        System.out.println();
        System.out.println("Recommendation method:");
        System.out.println(recommendation.getStrategyDescription());
        System.out.println();
        System.out.println("Comparable performances used: "
                + recommendation.getComparablesUsed());
        System.out.println("Recommended number of tiers: "
                + recommendation.getTierCount());
        if (recommendation.getExpectedRevenue().signum() > 0) {
            System.out.println("Average historical primary-ticket revenue: $"
                    + recommendation.getExpectedRevenue().toPlainString());
        }

        System.out.println();
        System.out.printf(
                "%-12s %-20s %-20s%n",
                "Tier",
                "Capacity %",
                "Suggested price"
        );
        System.out.println("------------------------------------------------");
        for (TierRecommendation tier : recommendation.getTiers()) {
            System.out.printf(
                    "%-12d %-20s $%-20s%n",
                    tier.getTierRank(),
                    tier.getCapacityPct().toPlainString(),
                    tier.getSuggestedPrice().toPlainString()
            );
        }

        System.out.println();
        if (recommendation.getComparablePerformances().isEmpty()) {
            System.out.println("Historical comparables: none; the documented rule-based fallback was used.");
        } else {
            System.out.println("Comparable performances that influenced this recommendation:");
            for (ComparablePerformance comparable :
                    recommendation.getComparablePerformances()) {
                System.out.printf(
                        "- %d | %s | %s/%s | %s, %s | capacity %d%n",
                        comparable.getPerformanceId(),
                        comparable.getTitle(),
                        comparable.getSegmentName(),
                        comparable.getGenreName(),
                        comparable.getVenueName(),
                        comparable.getCity(),
                        comparable.getVenueCapacity()
                );
                System.out.println("  Why selected: " + comparable.getSelectionReason());
            }
        }

        pause();
        while (running) {
            System.out.println();
            System.out.println("1. Estimate revenue change for a tier-price change");
            System.out.println("0. Back");
            switch (readLine("Select an option: ")) {
                case "1" -> {
                    estimateRevenueImpact(recommendation);
                    return;
                }
                case "0" -> {
                    return;
                }
                default -> {
                    printFeatureConflict("Select toolkit option 1 or 0.");
                    if (!promptToRetry()) {
                        return;
                    }
                }
            }
        }
    }

private void estimateRevenueImpact(PricingRecommendation lastRecommendation) {
    if (lastRecommendation == null
            || lastRecommendation.getComparablePerformanceIds().isEmpty()) {
        System.out.println();
        printFeatureConflict(
                "No comparable performances available — run a pricing recommendation above first."
        );
        promptToRetry();
        return;
    }
    List<Integer> comparablePerformanceIds =
            lastRecommendation.getComparablePerformanceIds();

    System.out.println();
    System.out.println("=== Revenue Impact Estimate ===");
    System.out.println(
            "Using "
            + comparablePerformanceIds.size()
            + " comparable performance(s) from the recommendation above."
    );

    while (running) {
        BigDecimal currentPrice = readDecimalWithRetry(
                "Current price: $",
                value -> value.signum() > 0
                        ? Optional.empty()
                        : Optional.of("Current price must be positive.")
        );
        if (currentPrice == null) {
            return;
        }
        BigDecimal proposedPrice = readDecimalWithRetry(
                "Proposed price: $",
                value -> value.signum() > 0
                        ? Optional.empty()
                        : Optional.of("Proposed price must be positive.")
        );
        if (proposedPrice == null) {
            return;
        }
        BigDecimal bandWidth = readDecimalWithRetry(
                "Band width ($): ",
                value -> value.signum() > 0
                        ? Optional.empty()
                        : Optional.of("Band width must be positive.")
        );
        if (bandWidth == null) {
            return;
        }

        RevenueImpactInput input =
                new RevenueImpactInput(
                        comparablePerformanceIds,
                        currentPrice,
                        proposedPrice,
                        bandWidth
                );

        OperationResult<RevenueImpactEstimate> result =
                toolkit.estimateRevenueImpact(input);
        if (!result.isSuccess()) {
            boolean retry = promptAfterFeatureFailure(result);
            if (retry) {
                continue;
            }
            if (!FeatureInputPolicy.isRetryable(result.getStatus())) {
                pause();
            }
            return;
        }

        printResult(result);
        RevenueImpactEstimate estimate = result.getValue().orElseThrow();
        System.out.println();
        System.out.println("Current-price comparable tiers: "
                + estimate.getCurrentSampleSize());
        System.out.printf(
                "Expected sell-through at current price: %.1f%%%n",
                estimate.getCurrentExpectedSellThroughPct()
        );
        System.out.println("Expected revenue at current price: $"
                + estimate.getCurrentExpectedRevenue().toPlainString());
        System.out.println("Proposed-price comparable tiers: "
                + estimate.getProposedSampleSize());
        System.out.printf(
                "Expected sell-through at proposed price: %.1f%%%n",
                estimate.getProposedExpectedSellThroughPct()
        );
        System.out.println("Expected revenue at proposed price: $"
                + estimate.getProposedExpectedRevenue().toPlainString());
        System.out.println("Estimated revenue change: $"
                + estimate.getExpectedRevenueChange().toPlainString());

        pause();
        return;
    }
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

    private void runFeatureAction(Runnable action) {
        boolean previousMode = featureInputMode;
        featureInputMode = true;
        try {
            action.run();
        } finally {
            featureInputMode = previousMode;
        }
    }

    private void printFeatureConflict(String message) {
        System.out.println(FeatureInputPolicy.conflictMessage(message));
    }

    private boolean promptAfterFeatureFailure(OperationResult<?> result) {
        if (!FeatureInputPolicy.isRetryable(result.getStatus())) {
            printResult(result);
            return false;
        }
        printFeatureConflict(result.getMessage());
        return promptToRetry();
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

    private Double readValidatedDouble(
            String prompt,
            Function<Double, Optional<String>> validator
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }

            double parsed;
            try {
                parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed)) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                printInputError("Enter a valid number.");
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

    private Double readDoubleWithDefaultRetry(
            String prompt,
            double defaultValue,
            Function<Double, Optional<String>> validator
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }

            double parsed;
            try {
                parsed = value.isBlank() ? defaultValue : Double.parseDouble(value);
                if (!Double.isFinite(parsed)) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                printInputError("Enter a valid number.");
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

    private Double readOptionalDoubleWithRetry(
            String prompt,
            Function<Double, Optional<String>> validator
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running || value.isBlank()) {
                return null;
            }

            double parsed;
            try {
                parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed)) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                printInputError("Enter a valid number or leave the field blank.");
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

    private Integer readOptionalPositiveIntWithRetry(String prompt) {
        while (running) {
            String value = readLine(prompt);
            if (!running || value.isBlank()) {
                return null;
            }
            try {
                int parsed = Integer.parseInt(value);
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Handled by the shared message below.
            }

            printInputError("Enter a positive whole number or leave the field blank.");
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private String readOptionalSectionTypeWithRetry(String prompt) {
        while (running) {
            String value = readLine(prompt);
            if (!running || value.isBlank()) {
                return null;
            }
            if (value.equalsIgnoreCase("reserved")
                    || value.equalsIgnoreCase("general")) {
                return value.toLowerCase(Locale.ROOT);
            }

            printInputError("Enter reserved, general, or leave the field blank.");
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
            if (featureInputMode && FeatureInputPolicy.isRetryable(result.getStatus())) {
                printFeatureConflict(result.getMessage());
            } else {
                printResult(result);
            }
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private String readCheckedText(
            String prompt,
            Function<String, OperationResult<Void>> checker
    ) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }
            if (value.isBlank()) {
                printInputError("A value is required.");
                if (!promptToRetry()) {
                    return null;
                }
                continue;
            }
            OperationResult<Void> result = checker.apply(value);
            if (result.isSuccess()) {
                return value;
            }
            if (featureInputMode && FeatureInputPolicy.isRetryable(result.getStatus())) {
                printFeatureConflict(result.getMessage());
            } else {
                printResult(result);
            }
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
            int maximumValue,
            Set<Integer> usedValues,
            String duplicateMessage
    ) {
        while (running) {
            Integer value = readValidatedInteger(
                    prompt,
                    candidate -> candidate < 1 || candidate > maximumValue
                            ? Optional.of("Enter a billing rank from 1 to "
                                    + maximumValue + ".")
                            : Optional.empty()
            );
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

    private List<Integer> readPositiveIntListWithRetry(String prompt) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }
            List<Integer> values = new ArrayList<>();
            Set<Integer> unique = new HashSet<>();
            boolean valid = !value.isEmpty();
            if (valid) {
                for (String part : value.split(",")) {
                    try {
                        int parsed = Integer.parseInt(part.trim());
                        if (parsed <= 0 || !unique.add(parsed)) {
                            valid = false;
                            break;
                        }
                        values.add(parsed);
                    } catch (NumberFormatException exception) {
                        valid = false;
                        break;
                    }
                }
            }
            if (valid) {
                return values;
            }
            printInputError("Enter unique positive IDs separated by commas.");
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private List<ReservedSeatLocation> readReservedSeatLocationsWithRetry(String prompt) {
        while (running) {
            String value = readLine(prompt);
            if (!running) {
                return null;
            }

            List<ReservedSeatLocation> locations = new ArrayList<>();
            Set<String> uniqueLocations = new HashSet<>();
            boolean valid = !value.isBlank();
            if (valid) {
                for (String entry : value.split(",")) {
                    String[] parts = entry.trim().split("\\s+");
                    if (parts.length != 2 || parts[0].isBlank()) {
                        valid = false;
                        break;
                    }
                    try {
                        int seatNumber = Integer.parseInt(parts[1]);
                        String key = normalize(parts[0]) + ":" + seatNumber;
                        if (seatNumber <= 0 || !uniqueLocations.add(key)) {
                            valid = false;
                            break;
                        }
                        locations.add(new ReservedSeatLocation(parts[0], seatNumber));
                    } catch (NumberFormatException exception) {
                        valid = false;
                        break;
                    }
                }
            }
            if (valid) {
                return List.copyOf(locations);
            }
            printInputError(
                    "Enter unique seats as Row Seat#, separated by commas "
                            + "(for example: A 1, A 2)."
            );
            if (!promptToRetry()) {
                return null;
            }
        }
        return null;
    }

    private List<Integer> readCheckedPositiveIntList(
            String prompt,
            Function<List<Integer>, OperationResult<Void>> checker
    ) {
        while (running) {
            List<Integer> values = readPositiveIntListWithRetry(prompt);
            if (values == null) {
                return null;
            }
            OperationResult<Void> result = checker.apply(values);
            if (result.isSuccess()) {
                return values;
            }
            printResult(result);
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

    private BigDecimal readCheckedDecimal(
            String prompt,
            Function<BigDecimal, OperationResult<Void>> checker
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

            OperationResult<Void> result = checker.apply(parsed);
            if (result.isSuccess()) {
                return parsed;
            }
            printResult(result);
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

    private String formatPercentage(BigDecimal fraction) {
        if (fraction == null) {
            return "-";
        }
        return fraction.multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(character);
        }
        return result.toString();
    }
}
