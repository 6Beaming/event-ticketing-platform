package testing;

import common.OperationResult;
import common.OperationStatus;
import data.DevelopmentIds;
import database.DatabaseConfig;
import database.DatabaseConnection;
import database.SqlScriptRunner;
import database.TransactionManager;
import operations.booking.BookingOperations;
import operations.booking.BookingSummary;
import operations.cancellation.CancellationOperations;
import operations.cancellation.CancellationSummary;
import operations.event.ArtistBillingInput;
import operations.event.EventInput;
import operations.event.OrganizerEventOperations;
import operations.event.PerformanceInput;
import operations.inventory.GeneralAdmissionAvailability;
import operations.inventory.InventoryOperations;
import operations.inventory.InventoryState;
import operations.inventory.ReservedSeatAvailability;
import operations.pricing.PerformancePricingOperations;
import operations.pricing.PricingSetupInput;
import operations.pricing.SectionTierInput;
import operations.pricing.TierInput;
import operations.profile.PaymentInput;
import operations.profile.CustomerProfile;
import operations.profile.ProfileInput;
import operations.profile.UserProfileOperations;
import operations.resale.ResaleListingSummary;
import operations.resale.ResaleOperations;
import operations.resale.ResalePurchaseSummary;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public final class FoundationDatabaseCheck {
    private FoundationDatabaseCheck() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || !"--reset-database".equals(args[0])) {
            System.out.println("Usage: FoundationDatabaseCheck --reset-database");
            System.out.println("This command drops and reloads only the configured MyTix tables.");
            System.exit(2);
        }

        DatabaseConfig config = DatabaseConfig.defaults();
        try (DatabaseConnection database = new DatabaseConnection(config)) {
            Connection connection = database.connect();
            resetDatabase(connection);
            runOperationChecks(database);
            resetDatabase(connection);
            System.out.println("Foundation database check passed; deterministic data was restored.");
        } catch (Exception exception) {
            String message = exception.getMessage();
            System.out.println("Foundation database check failed: "
                    + (message == null ? exception.getClass().getSimpleName() : message));
            System.exit(1);
        }
    }

    private static void resetDatabase(Connection connection) throws Exception {
        SqlScriptRunner scripts = new SqlScriptRunner();
        scripts.run(connection, Paths.get("sql", "drop.sql"));
        scripts.run(connection, Paths.get("sql", "schema.sql"));
        scripts.run(connection, Paths.get("sql", "load.sql"));
    }

    private static void runOperationChecks(DatabaseConnection database) {
        TransactionManager transactions = new TransactionManager(database);
        UserProfileOperations profiles = new UserProfileOperations(transactions);
        OrganizerEventOperations events = new OrganizerEventOperations(transactions);
        PerformancePricingOperations pricing = new PerformancePricingOperations(transactions);
        InventoryOperations inventory = new InventoryOperations(transactions);
        BookingOperations bookings = new BookingOperations(transactions);
        CancellationOperations cancellations = new CancellationOperations(transactions);
        ResaleOperations resale = new ResaleOperations(transactions);

        OperationResult<Void> duplicateEmail = profiles.checkEmailAvailability(
                "customer001@example.test"
        );
        if (duplicateEmail.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("an existing email address was not rejected");
        }
        OperationResult<Void> availableEmail = profiles.checkEmailAvailability(
                "available-database-check@example.test"
        );
        requireSuccess(availableEmail, "available email address check");
        requireSuccess(
                events.checkActiveOrganizer(DevelopmentIds.ORGANIZER),
                "active organizer preflight check"
        );
        if (events.checkArtist(999999).getStatus() != OperationStatus.NOT_FOUND) {
            throw new IllegalStateException("nonexistent artist preflight check was not rejected");
        }

        OperationResult<Integer> customer = profiles.createCustomer(
                new ProfileInput(
                        "Database Check Customer",
                        "10 Test Street",
                        "database-check-customer@example.test",
                        LocalDate.of(1990, 1, 1)
                ),
                new PaymentInput(
                        "4000000000000002",
                        "Database Check Customer",
                        LocalDate.of(2032, 12, 31),
                        "M5V 1A1"
                )
        );
        requireSuccess(customer, "adult customer creation");

        OperationResult<CustomerProfile> retrievedCustomer = profiles.getCustomerProfile(
                customer.getValue().orElseThrow()
        );
        requireSuccess(retrievedCustomer, "customer profile retrieval");
        String displayedCard = retrievedCustomer.getValue().orElseThrow().getMaskedCardNumber();
        if (!displayedCard.startsWith("****") || displayedCard.contains("4000000000000002")) {
            throw new IllegalStateException("customer profile exposed a full card number");
        }

        OperationResult<Void> deactivated = profiles.deactivateUser(customer.getValue().orElseThrow());
        requireSuccess(deactivated, "history-preserving customer deactivation");
        OperationResult<Void> repeatedDeactivation = profiles.deactivateUser(
                customer.getValue().orElseThrow()
        );
        if (repeatedDeactivation.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("repeated deactivation was not rejected");
        }

        OperationResult<Integer> underAge = profiles.createCustomer(
                new ProfileInput(
                        "Under Age",
                        "11 Test Street",
                        "under-age@example.test",
                        LocalDate.now(ZoneOffset.UTC).minusYears(17)
                ),
                new PaymentInput(
                        "4000000000000010",
                        "Under Age",
                        LocalDate.of(2032, 12, 31),
                        "M5V 1A2"
                )
        );
        if (underAge.getStatus() != OperationStatus.INVALID_INPUT) {
            throw new IllegalStateException("under-age customer creation was not rejected");
        }

        OperationResult<Integer> organizer = profiles.createOrganizer(
                new ProfileInput(
                        "Database Check Organizer",
                        "12 Test Street",
                        "database-check-organizer@example.test",
                        LocalDate.of(1985, 1, 1)
                )
        );
        requireSuccess(organizer, "organizer creation");

        OperationResult<Integer> invalidTaxonomy = events.createEvent(new EventInput(
                DevelopmentIds.ORGANIZER,
                "Invalid Taxonomy Event",
                null,
                new BigDecimal("1.20"),
                999999,
                List.of(new ArtistBillingInput(DevelopmentIds.ARTIST, 1))
        ));
        if (invalidTaxonomy.getStatus() != OperationStatus.NOT_FOUND) {
            throw new IllegalStateException("nonexistent taxonomy ID was not rejected");
        }

        OperationResult<Integer> invalidVenue = events.addPerformance(
                DevelopmentIds.ORGANIZER,
                new PerformanceInput(
                        DevelopmentIds.EVENT,
                        999999,
                        LocalDateTime.now(ZoneOffset.UTC).plusDays(60)
                )
        );
        if (invalidVenue.getStatus() != OperationStatus.NOT_FOUND) {
            throw new IllegalStateException("nonexistent venue ID was not rejected");
        }

        OperationResult<Integer> event = events.createEvent(new EventInput(
                organizer.getValue().orElseThrow(),
                "Database Check Event",
                "Foundation integration check",
                new BigDecimal("1.20"),
                DevelopmentIds.GENRE_ROCK,
                List.of(new ArtistBillingInput(DevelopmentIds.ARTIST, 1))
        ));
        requireSuccess(event, "event creation");

        OperationResult<Integer> performance = events.addPerformance(
                organizer.getValue().orElseThrow(),
                new PerformanceInput(
                        event.getValue().orElseThrow(),
                        DevelopmentIds.VENUE,
                        LocalDateTime.now(ZoneOffset.UTC).plusDays(60)
                )
        );
        requireSuccess(performance, "performance creation");

        OperationResult<List<String>> venueSections = pricing.getVenueSectionsForPricing(
                organizer.getValue().orElseThrow(),
                performance.getValue().orElseThrow()
        );
        requireSuccess(venueSections, "performance venue-section preflight check");
        if (venueSections.getValue().orElseThrow().size() != 3) {
            throw new IllegalStateException("performance venue sections were not loaded");
        }

        OperationResult<?> pricingResult = pricing.configurePricing(
                organizer.getValue().orElseThrow(),
                new PricingSetupInput(
                        performance.getValue().orElseThrow(),
                        List.of(
                                new TierInput("P1", new BigDecimal("120.00")),
                                new TierInput("P2", new BigDecimal("70.00")),
                                new TierInput("P3", new BigDecimal("50.00"))
                        ),
                        List.of(
                                new SectionTierInput("Orchestra", "P1"),
                                new SectionTierInput("Balcony", "P2"),
                                new SectionTierInput("General Floor", "P3")
                        )
                )
        );
        requireSuccess(pricingResult, "pricing setup");

        OperationResult<List<ReservedSeatAvailability>> createdReserved =
                inventory.getReservedInventory(performance.getValue().orElseThrow());
        requireSuccess(createdReserved, "new performance reserved inventory creation");
        if (createdReserved.getValue().orElseThrow().size() != 42) {
            throw new IllegalStateException("new performance reserved inventory was not created");
        }
        OperationResult<List<GeneralAdmissionAvailability>> createdGeneral =
                inventory.getGeneralAdmissionInventory(performance.getValue().orElseThrow());
        requireSuccess(createdGeneral, "new performance general inventory creation");
        if (createdGeneral.getValue().orElseThrow().size() != 1) {
            throw new IllegalStateException("new performance general inventory was not created");
        }

        OperationResult<List<String>> replacementPreflight =
                pricing.getVenueSectionsForPricing(
                        organizer.getValue().orElseThrow(),
                        performance.getValue().orElseThrow()
                );
        requireSuccess(replacementPreflight, "unsold pricing replacement preflight");
        if (!replacementPreflight.getMessage().contains("already has tiers and section assignments")) {
            throw new IllegalStateException("existing pricing notice was not returned");
        }

        OperationResult<?> replacementPricing = pricing.configurePricing(
                organizer.getValue().orElseThrow(),
                new PricingSetupInput(
                        performance.getValue().orElseThrow(),
                        List.of(
                                new TierInput("Q1", new BigDecimal("125.00")),
                                new TierInput("Q2", new BigDecimal("75.00")),
                                new TierInput("Q3", new BigDecimal("55.00"))
                        ),
                        List.of(
                                new SectionTierInput("Orchestra", "Q1"),
                                new SectionTierInput("Balcony", "Q2"),
                                new SectionTierInput("General Floor", "Q3")
                        )
                )
        );
        requireSuccess(replacementPricing, "unsold pricing replacement");

        OperationResult<Integer> bookingCustomerOne = profiles.createCustomer(
                new ProfileInput(
                        "Booking Customer One",
                        "20 Test Street",
                        "database-check-booking-one@example.test",
                        LocalDate.of(1991, 2, 3)
                ),
                new PaymentInput(
                        "4000000000000028",
                        "Booking Customer One",
                        LocalDate.of(2032, 12, 31),
                        "M5V 2A1"
                )
        );
        requireSuccess(bookingCustomerOne, "first booking customer creation");
        OperationResult<Integer> bookingCustomerTwo = profiles.createCustomer(
                new ProfileInput(
                        "Booking Customer Two",
                        "21 Test Street",
                        "database-check-booking-two@example.test",
                        LocalDate.of(1992, 3, 4)
                ),
                new PaymentInput(
                        "4000000000000036",
                        "Booking Customer Two",
                        LocalDate.of(2032, 12, 31),
                        "M5V 2A2"
                )
        );
        requireSuccess(bookingCustomerTwo, "second booking customer creation");

        List<ReservedSeatAvailability> newSeats = createdReserved.getValue().orElseThrow();
        int firstSeatId = newSeats.get(0).getPerformanceSeatId();
        int secondSeatId = newSeats.get(1).getPerformanceSeatId();
        int rollbackSeatId = newSeats.get(2).getPerformanceSeatId();
        int restrictedSeatId = newSeats.get(3).getPerformanceSeatId();
        OperationResult<BookingSummary> reservedBooking = bookings.bookReservedSeats(
                bookingCustomerOne.getValue().orElseThrow(),
                performance.getValue().orElseThrow(),
                List.of(firstSeatId, secondSeatId)
        );
        requireSuccess(reservedBooking, "atomic reserved-seat booking");
        if (reservedBooking.getValue().orElseThrow().getTicketIds().size() != 2) {
            throw new IllegalStateException("reserved booking did not create two tickets");
        }
        OperationResult<BookingSummary> duplicateSeatSale = bookings.bookReservedSeats(
                bookingCustomerTwo.getValue().orElseThrow(),
                performance.getValue().orElseThrow(),
                List.of(firstSeatId)
        );
        if (duplicateSeatSale.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("a sold reserved seat was sold twice");
        }
        OperationResult<BookingSummary> failedMultiSeat = bookings.bookReservedSeats(
                bookingCustomerTwo.getValue().orElseThrow(),
                performance.getValue().orElseThrow(),
                List.of(rollbackSeatId, firstSeatId)
        );
        if (failedMultiSeat.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("mixed-availability booking was not rejected");
        }
        OperationResult<List<ReservedSeatAvailability>> afterFailedBooking =
                inventory.getReservedInventory(performance.getValue().orElseThrow());
        requireSuccess(afterFailedBooking, "reserved rollback inventory check");
        boolean rollbackSeatStillAvailable = afterFailedBooking.getValue().orElseThrow().stream()
                .anyMatch(seat -> seat.getPerformanceSeatId() == rollbackSeatId
                        && seat.getState() == InventoryState.AVAILABLE);
        if (!rollbackSeatStillAvailable) {
            throw new IllegalStateException("failed multi-seat booking did not roll back");
        }
        OperationResult<BookingSummary> restrictedBooking = bookings.bookReservedSeats(
                DevelopmentIds.CUSTOMER_ALICE,
                performance.getValue().orElseThrow(),
                List.of(restrictedSeatId)
        );
        if (restrictedBooking.getStatus() != OperationStatus.FORBIDDEN) {
            throw new IllegalStateException("restricted customer booking was not rejected");
        }

        OperationResult<BookingSummary> smallGaBooking = bookings.bookGeneralAdmission(
                bookingCustomerOne.getValue().orElseThrow(),
                performance.getValue().orElseThrow(),
                "General Floor",
                2
        );
        requireSuccess(smallGaBooking, "general-admission booking below capacity");
        OperationResult<List<GeneralAdmissionAvailability>> afterSmallGaBooking =
                inventory.getGeneralAdmissionInventory(performance.getValue().orElseThrow());
        requireSuccess(afterSmallGaBooking, "general inventory after small booking");
        int remaining = afterSmallGaBooking.getValue().orElseThrow().get(0).getRemainingCapacity();
        requireSuccess(
                bookings.bookGeneralAdmission(
                        bookingCustomerTwo.getValue().orElseThrow(),
                        performance.getValue().orElseThrow(),
                        "General Floor",
                        remaining
                ),
                "general-admission booking equal to remaining capacity"
        );
        OperationResult<BookingSummary> aboveCapacity = bookings.bookGeneralAdmission(
                bookingCustomerOne.getValue().orElseThrow(),
                performance.getValue().orElseThrow(),
                "General Floor",
                1
        );
        if (aboveCapacity.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("general admission was oversold");
        }

        int firstBookedTicket = reservedBooking.getValue().orElseThrow().getTicketIds().get(0);
        int secondBookedTicket = reservedBooking.getValue().orElseThrow().getTicketIds().get(1);
        OperationResult<ResaleListingSummary> aboveCapListing = resale.listTicket(
                bookingCustomerOne.getValue().orElseThrow(),
                secondBookedTicket,
                new BigDecimal("1000.00")
        );
        if (aboveCapListing.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("above-cap resale listing was not rejected");
        }
        OperationResult<ResaleListingSummary> listing = resale.listTicket(
                bookingCustomerOne.getValue().orElseThrow(),
                secondBookedTicket,
                new BigDecimal("100.00")
        );
        requireSuccess(listing, "owned ticket resale listing");
        int listingId = listing.getValue().orElseThrow().getListingId();
        OperationResult<ResalePurchaseSummary> sellerPurchase = resale.purchaseListing(
                bookingCustomerOne.getValue().orElseThrow(),
                listingId
        );
        if (sellerPurchase.getStatus() != OperationStatus.FORBIDDEN) {
            throw new IllegalStateException("seller was allowed to buy their own listing");
        }
        requireSuccess(
                resale.purchaseListing(
                        bookingCustomerTwo.getValue().orElseThrow(),
                        listingId
                ),
                "resale purchase and ownership transfer"
        );
        OperationResult<ResalePurchaseSummary> competingPurchase = resale.purchaseListing(
                bookingCustomerOne.getValue().orElseThrow(),
                listingId
        );
        if (competingPurchase.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("a sold listing was purchased twice");
        }

        int firstGaTicket = smallGaBooking.getValue().orElseThrow().getTicketIds().get(0);
        int secondGaTicket = smallGaBooking.getValue().orElseThrow().getTicketIds().get(1);
        OperationResult<ResaleListingSummary> withdrawnListing = resale.listTicket(
                bookingCustomerOne.getValue().orElseThrow(),
                firstGaTicket,
                new BigDecimal("50.00")
        );
        requireSuccess(withdrawnListing, "resale listing for withdrawal");
        int withdrawnListingId = withdrawnListing.getValue().orElseThrow().getListingId();
        OperationResult<Void> wrongSellerWithdrawal = resale.withdrawListing(
                bookingCustomerTwo.getValue().orElseThrow(),
                withdrawnListingId
        );
        if (wrongSellerWithdrawal.getStatus() != OperationStatus.FORBIDDEN) {
            throw new IllegalStateException("non-seller listing withdrawal was not rejected");
        }
        requireSuccess(
                resale.withdrawListing(
                        bookingCustomerOne.getValue().orElseThrow(),
                        withdrawnListingId
                ),
                "seller listing withdrawal"
        );
        requireSuccess(
                resale.listTicket(
                        bookingCustomerOne.getValue().orElseThrow(),
                        secondGaTicket,
                        new BigDecimal("50.00")
                ),
                "active listing for performance-cancellation closure"
        );

        OperationResult<CancellationSummary> wrongCustomerCancellation =
                cancellations.cancelCustomerTickets(
                        bookingCustomerOne.getValue().orElseThrow(),
                        List.of(secondBookedTicket),
                        "Wrong customer check"
                );
        if (wrongCustomerCancellation.getStatus() != OperationStatus.FORBIDDEN) {
            throw new IllegalStateException("non-owner customer cancellation was not rejected");
        }
        OperationResult<CancellationSummary> customerCancellation =
                cancellations.cancelCustomerTickets(
                        bookingCustomerOne.getValue().orElseThrow(),
                        List.of(firstBookedTicket),
                        "Database check cancellation"
                );
        requireSuccess(customerCancellation, "eligible customer ticket cancellation");
        if (customerCancellation.getValue().orElseThrow().getCancelledTicketCount() != 1) {
            throw new IllegalStateException("customer cancellation count was incorrect");
        }
        OperationResult<List<ReservedSeatAvailability>> afterCancellation =
                inventory.getReservedInventory(performance.getValue().orElseThrow());
        requireSuccess(afterCancellation, "released reserved inventory check");
        boolean cancelledSeatAvailable = afterCancellation.getValue().orElseThrow().stream()
                .anyMatch(seat -> seat.getPerformanceSeatId() == firstSeatId
                        && seat.getState() == InventoryState.AVAILABLE);
        if (!cancelledSeatAvailable) {
            throw new IllegalStateException("customer cancellation did not release the seat");
        }
        OperationResult<CancellationSummary> lateCancellation =
                cancellations.cancelCustomerTickets(
                        DevelopmentIds.CUSTOMER_BOB,
                        List.of(DevelopmentIds.TICKET_GENERAL),
                        "Too late check"
                );
        if (lateCancellation.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("fewer-than-seven-day cancellation was not rejected");
        }

        OperationResult<CancellationSummary> wrongOrganizerCancellation =
                cancellations.cancelPerformance(
                        1002,
                        performance.getValue().orElseThrow(),
                        "Wrong organizer check"
                );
        if (wrongOrganizerCancellation.getStatus() != OperationStatus.FORBIDDEN) {
            throw new IllegalStateException("non-owner performance cancellation was not rejected");
        }
        OperationResult<CancellationSummary> performanceCancellation =
                cancellations.cancelPerformance(
                        organizer.getValue().orElseThrow(),
                        performance.getValue().orElseThrow(),
                        "Database check performance cancellation"
                );
        requireSuccess(performanceCancellation, "organizer performance cancellation");
        if (performanceCancellation.getValue().orElseThrow().getCancelledTicketCount() < 1) {
            throw new IllegalStateException("performance cancellation refunded no active tickets");
        }
        OperationResult<BookingSummary> cancelledPerformanceBooking = bookings.bookReservedSeats(
                bookingCustomerOne.getValue().orElseThrow(),
                performance.getValue().orElseThrow(),
                List.of(firstSeatId)
        );
        if (cancelledPerformanceBooking.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("cancelled performance accepted a new booking");
        }

        OperationResult<List<String>> soldPricingPreflight =
                pricing.getVenueSectionsForPricing(
                        DevelopmentIds.ORGANIZER,
                        DevelopmentIds.PERFORMANCE_RESERVED
                );
        if (soldPricingPreflight.getStatus() != OperationStatus.CONFLICT
                || !soldPricingPreflight.getMessage().contains("tickets have already been sold")) {
            throw new IllegalStateException("sold performance pricing replacement was not rejected");
        }

        requireSuccess(
                pricing.updateTierPrice(
                        DevelopmentIds.ORGANIZER,
                        DevelopmentIds.PERFORMANCE_RESERVED,
                        "P3",
                        new BigDecimal("52.00")
                ),
                "unsold tier price update"
        );
        OperationResult<Void> soldTierUpdate = pricing.updateTierPrice(
                DevelopmentIds.ORGANIZER,
                DevelopmentIds.PERFORMANCE_RESERVED,
                "P1",
                new BigDecimal("125.00")
        );
        if (soldTierUpdate.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("sold tier price update was not rejected");
        }

        requireSuccess(
                inventory.blockSeat(
                        DevelopmentIds.ORGANIZER,
                        DevelopmentIds.PERFORMANCE_RESERVED,
                        610001
                ),
                "available seat block"
        );
        requireSuccess(
                inventory.unblockSeat(
                        DevelopmentIds.ORGANIZER,
                        DevelopmentIds.PERFORMANCE_RESERVED,
                        610001
                ),
                "blocked seat unblock"
        );
        OperationResult<Void> soldSeatBlock = inventory.blockSeat(
                DevelopmentIds.ORGANIZER,
                DevelopmentIds.PERFORMANCE_RESERVED,
                610005
        );
        if (soldSeatBlock.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("sold seat block was not rejected");
        }

        OperationResult<List<ReservedSeatAvailability>> reserved =
                inventory.getReservedInventory(DevelopmentIds.PERFORMANCE_RESERVED);
        requireSuccess(reserved, "reserved inventory lookup");
        List<ReservedSeatAvailability> seats = reserved.getValue().orElseThrow();
        long soldSeats = seats.stream()
                .filter(seat -> seat.getState() == InventoryState.SOLD)
                .count();
        long blockedSeats = seats.stream()
                .filter(seat -> seat.getState() == InventoryState.BLOCKED)
                .count();
        if (seats.size() != 42 || soldSeats != 12 || blockedSeats != 7) {
            throw new IllegalStateException("reserved inventory states did not match development data");
        }

        OperationResult<List<GeneralAdmissionAvailability>> general =
                inventory.getGeneralAdmissionInventory(DevelopmentIds.PERFORMANCE_GENERAL);
        requireSuccess(general, "general-admission inventory lookup");
        List<GeneralAdmissionAvailability> sections = general.getValue().orElseThrow();
        if (sections.size() != 1 || sections.get(0).getSoldQuantity() != 3) {
            throw new IllegalStateException("general-admission inventory did not match development data");
        }
    }

    private static void requireSuccess(OperationResult<?> result, String check) {
        if (!result.isSuccess()) {
            throw new IllegalStateException(check + " failed: " + result.getMessage());
        }
    }
}
