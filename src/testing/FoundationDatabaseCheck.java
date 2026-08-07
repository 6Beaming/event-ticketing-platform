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
import operations.inventory.ReservedSeatLocation;
import operations.pricing.PerformancePricingOperations;
import operations.pricing.PricingSetupInput;
import operations.pricing.SectionTierInput;
import operations.pricing.TierInput;
import operations.profile.PaymentInput;
import operations.profile.CustomerProfile;
import operations.profile.ProfileInput;
import operations.profile.UserProfileOperations;
import operations.resale.AvailableResaleTicket;
import operations.resale.OwnedResaleTicket;
import operations.resale.ResaleListingSummary;
import operations.resale.ResaleOperations;
import operations.resale.ResalePurchaseSummary;
import operations.review.CustomerReview;
import operations.review.ReviewOperations;

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

        DatabaseConfig config = databaseCheckConfig();
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

    private static DatabaseConfig databaseCheckConfig() {
        String overrideUrl = System.getProperty("mytix.databaseCheck.url");
        if (overrideUrl == null || overrideUrl.isBlank()) {
            return DatabaseConfig.defaults();
        }
        return DatabaseConfig.fromValues(
                overrideUrl,
                System.getProperty("mytix.databaseCheck.user", "root"),
                System.getProperty("mytix.databaseCheck.password", "")
        );
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
        ReviewOperations reviews = new ReviewOperations(transactions);

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
                new PerformanceInput(
                        DevelopmentIds.EVENT,
                        999999,
                        LocalDateTime.now(ZoneOffset.UTC).plusDays(60)
                )
        );
        if (invalidVenue.getStatus() != OperationStatus.NOT_FOUND) {
            throw new IllegalStateException("nonexistent venue ID was not rejected");
        }

        for (int performanceId : List.of(
                DevelopmentIds.PERFORMANCE_CONFIGURABLE_MIXED,
                DevelopmentIds.PERFORMANCE_CONFIGURABLE_RESERVED
        )) {
            OperationResult<List<String>> configurable =
                    pricing.getVenueSectionsForPricing(performanceId);
            requireSuccess(configurable, "sample pricing-configuration preflight");
            if (!configurable.getMessage().contains(
                    "already has tiers and section assignments"
            )) {
                throw new IllegalStateException(
                        "sample performance " + performanceId
                                + " was not ready for pricing replacement"
                );
            }
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
                new PerformanceInput(
                        event.getValue().orElseThrow(),
                        DevelopmentIds.VENUE,
                        LocalDateTime.now(ZoneOffset.UTC).plusDays(60)
                )
        );
        requireSuccess(performance, "performance creation");

        OperationResult<List<String>> venueSections = pricing.getVenueSectionsForPricing(
                performance.getValue().orElseThrow()
        );
        requireSuccess(venueSections, "performance venue-section preflight check");
        if (venueSections.getValue().orElseThrow().size() != 3) {
            throw new IllegalStateException("performance venue sections were not loaded");
        }

        OperationResult<?> pricingResult = pricing.configurePricing(
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
                        performance.getValue().orElseThrow()
                );
        requireSuccess(replacementPreflight, "unsold pricing replacement preflight");
        if (!replacementPreflight.getMessage().contains("already has tiers and section assignments")) {
            throw new IllegalStateException("existing pricing notice was not returned");
        }

        OperationResult<?> replacementPricing = pricing.configurePricing(
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
        requireSuccess(
                bookings.checkPerformanceForBooking(performance.getValue().orElseThrow()),
                "future scheduled booking preflight"
        );
        OperationResult<List<Integer>> resolvedSeatIds = inventory.resolveReservedSeatIds(
                performance.getValue().orElseThrow(),
                List.of(
                        new ReservedSeatLocation(
                                newSeats.get(0).getRowName(),
                                newSeats.get(0).getSeatNumber()
                        ),
                        new ReservedSeatLocation(
                                newSeats.get(1).getRowName(),
                                newSeats.get(1).getSeatNumber()
                        ),
                        new ReservedSeatLocation(
                                newSeats.get(2).getRowName(),
                                newSeats.get(2).getSeatNumber()
                        )
                )
        );
        requireSuccess(resolvedSeatIds, "reserved row-and-seat lookup");
        List<Integer> selectedSeatIds = resolvedSeatIds.getValue().orElseThrow();
        int firstSeatId = selectedSeatIds.get(0);
        int secondSeatId = selectedSeatIds.get(1);
        int rollbackSeatId = selectedSeatIds.get(2);
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
                new BigDecimal("80.00")
        );
        requireSuccess(listing, "owned ticket resale listing");
        OperationResult<List<OwnedResaleTicket>> ownedTickets = resale.getOwnedTickets(
                bookingCustomerOne.getValue().orElseThrow()
        );
        requireSuccess(ownedTickets, "customer-owned ticket lookup");
        boolean listedTicketShown = ownedTickets.getValue().orElseThrow().stream()
                .anyMatch(ticket -> ticket.getTicketId() == secondBookedTicket
                        && "listed".equals(ticket.getStatus()));
        boolean activeTicketShown = ownedTickets.getValue().orElseThrow().stream()
                .anyMatch(ticket -> ticket.getTicketId() == firstBookedTicket
                        && "active".equals(ticket.getStatus()));
        if (!listedTicketShown || !activeTicketShown) {
            throw new IllegalStateException(
                    "owned tickets were not categorized as active and listed"
            );
        }
        OperationResult<List<AvailableResaleTicket>> availableListings =
                resale.getAvailableListings(performance.getValue().orElseThrow());
        requireSuccess(availableListings, "available resale ticket lookup");
        boolean availableTicketShown = availableListings.getValue().orElseThrow().stream()
                .anyMatch(ticket -> ticket.getTicketId() == secondBookedTicket
                        && ticket.getSellerCustomerId()
                        == bookingCustomerOne.getValue().orElseThrow());
        if (!availableTicketShown) {
            throw new IllegalStateException("active resale ticket was not available by performance");
        }
        OperationResult<ResalePurchaseSummary> sellerPurchase = resale.purchaseListingByTicket(
                bookingCustomerOne.getValue().orElseThrow(),
                secondBookedTicket
        );
        if (sellerPurchase.getStatus() != OperationStatus.FORBIDDEN) {
            throw new IllegalStateException("seller was allowed to buy their own listing");
        }
        requireSuccess(
                resale.purchaseListingByTicket(
                        bookingCustomerTwo.getValue().orElseThrow(),
                        secondBookedTicket
                ),
                "resale purchase and ownership transfer"
        );
        OperationResult<ResalePurchaseSummary> competingPurchase =
                resale.purchaseListingByTicket(
                bookingCustomerOne.getValue().orElseThrow(),
                secondBookedTicket
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
        OperationResult<Void> wrongSellerWithdrawal = resale.withdrawListingByTicket(
                bookingCustomerTwo.getValue().orElseThrow(),
                firstGaTicket
        );
        if (wrongSellerWithdrawal.getStatus() != OperationStatus.FORBIDDEN) {
            throw new IllegalStateException("non-seller listing withdrawal was not rejected");
        }
        requireSuccess(
                resale.withdrawListingByTicket(
                        bookingCustomerOne.getValue().orElseThrow(),
                        firstGaTicket
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
                        DevelopmentIds.PERFORMANCE_RESERVED
                );
        if (soldPricingPreflight.getStatus() != OperationStatus.CONFLICT
                || !soldPricingPreflight.getMessage().contains("tickets have already been sold")) {
            throw new IllegalStateException("sold performance pricing replacement was not rejected");
        }

        requireSuccess(
                pricing.checkPerformanceForTierPriceUpdate(
                        DevelopmentIds.PERFORMANCE_RESERVED
                ),
                "future performance tier-price preflight"
        );
        requireSuccess(
                pricing.checkTierForPriceUpdate(
                        DevelopmentIds.PERFORMANCE_RESERVED,
                        "P3"
                ),
                "unsold tier-price preflight"
        );
        OperationResult<Void> soldTierPreflight = pricing.checkTierForPriceUpdate(
                DevelopmentIds.PERFORMANCE_RESERVED,
                "P1"
        );
        if (soldTierPreflight.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("sold tier preflight was not rejected");
        }
        OperationResult<Void> completedTierPricePreflight =
                pricing.checkPerformanceForTierPriceUpdate(
                        DevelopmentIds.PERFORMANCE_PAST
                );
        if (completedTierPricePreflight.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException(
                    "completed performance tier-price preflight was not rejected"
            );
        }
        requireSuccess(
                pricing.updateTierPrice(
                        DevelopmentIds.PERFORMANCE_RESERVED,
                        "P3",
                        new BigDecimal("52.00")
                ),
                "unsold tier price update"
        );
        OperationResult<Void> soldTierUpdate = pricing.updateTierPrice(
                DevelopmentIds.PERFORMANCE_RESERVED,
                "P1",
                new BigDecimal("125.00")
        );
        if (soldTierUpdate.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("sold tier price update was not rejected");
        }

        requireSuccess(
                inventory.checkPerformanceForSeatBlocking(
                        DevelopmentIds.PERFORMANCE_RESERVED
                ),
                "future scheduled seat-blocking preflight"
        );
        OperationResult<Void> completedSeatBlockingPreflight =
                inventory.checkPerformanceForSeatBlocking(
                        DevelopmentIds.PERFORMANCE_LOW_SELL_THROUGH
                );
        if (completedSeatBlockingPreflight.getStatus() != OperationStatus.CONFLICT
                || !completedSeatBlockingPreflight.getMessage().contains(
                        "scheduled future performance"
                )) {
            throw new IllegalStateException(
                    "completed performance seat-blocking preflight was not rejected"
            );
        }

        requireSuccess(
                inventory.blockSeat(
                        DevelopmentIds.PERFORMANCE_RESERVED,
                        "A",
                        1
                ),
                "available seat block"
        );
        requireSuccess(
                inventory.unblockSeat(
                        DevelopmentIds.PERFORMANCE_RESERVED,
                        "A",
                        1
                ),
                "blocked seat unblock"
        );
        OperationResult<Void> soldSeatBlock = inventory.blockSeat(
                DevelopmentIds.PERFORMANCE_RESERVED,
                "A",
                5
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

        OperationResult<List<ReservedSeatAvailability>> completedReserved =
                inventory.getReservedInventory(DevelopmentIds.PERFORMANCE_PAST);
        requireSuccess(completedReserved, "completed performance reserved inventory lookup");
        if (completedReserved.getValue().orElseThrow().isEmpty()) {
            throw new IllegalStateException("completed performance reserved inventory was hidden");
        }

        OperationResult<List<GeneralAdmissionAvailability>> completedGeneral =
                inventory.getGeneralAdmissionInventory(
                        DevelopmentIds.PERFORMANCE_LOW_SELL_THROUGH
                );
        requireSuccess(completedGeneral, "completed performance general inventory lookup");
        if (completedGeneral.getValue().orElseThrow().isEmpty()) {
            throw new IllegalStateException("completed performance general inventory was hidden");
        }

        OperationResult<List<Integer>> reviewablePerformances =
                reviews.getReviewablePerformanceIds(2011);
        requireSuccess(reviewablePerformances, "reviewable performance lookup");
        if (!reviewablePerformances.getValue().orElseThrow().contains(
                DevelopmentIds.PERFORMANCE_PAST
        )) {
            throw new IllegalStateException(
                    "reviewable performance lookup omitted an eligible performance"
            );
        }
        OperationResult<List<Integer>> alreadyReviewedPerformances =
                reviews.getReviewablePerformanceIds(DevelopmentIds.CUSTOMER_ALICE);
        requireSuccess(alreadyReviewedPerformances, "already-reviewed performance lookup");
        if (alreadyReviewedPerformances.getValue().orElseThrow().contains(
                DevelopmentIds.PERFORMANCE_PAST
        )) {
            throw new IllegalStateException(
                    "reviewable performance lookup included an already-reviewed performance"
            );
        }
        OperationResult<List<CustomerReview>> seededReviews =
                reviews.getCustomerReviews(DevelopmentIds.CUSTOMER_ALICE);
        requireSuccess(seededReviews, "seeded customer review lookup");
        if (seededReviews.getValue().orElseThrow().stream().noneMatch(
                review -> review.getPerformanceId() == DevelopmentIds.PERFORMANCE_PAST
        )) {
            throw new IllegalStateException("seeded customer review was not retrieved");
        }
        requireSuccess(
                reviews.checkReviewEligibility(2011, DevelopmentIds.PERFORMANCE_PAST),
                "eligible attendance review preflight"
        );
        OperationResult<Void> incompleteReviewPreflight = reviews.checkReviewEligibility(
                DevelopmentIds.CUSTOMER_ALICE,
                DevelopmentIds.PERFORMANCE_RESERVED
        );
        if (incompleteReviewPreflight.getStatus() != OperationStatus.CONFLICT
                || !incompleteReviewPreflight.getMessage().contains(
                        "only after a completed performance"
                )) {
            throw new IllegalStateException(
                    "incomplete performance review preflight was not rejected"
            );
        }

        requireSuccess(
                reviews.submitReview(
                        2011,
                        DevelopmentIds.PERFORMANCE_PAST,
                        5,
                        4,
                        "The performance was engaging and the venue service was helpful."
                ),
                "eligible attendance review"
        );
        OperationResult<Void> duplicateReview = reviews.submitReview(
                2011,
                DevelopmentIds.PERFORMANCE_PAST,
                4,
                4,
                "A duplicate review should not be saved."
        );
        if (duplicateReview.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException("duplicate attendance review was not rejected");
        }
        OperationResult<Void> duplicateReviewPreflight = reviews.checkReviewEligibility(
                2011,
                DevelopmentIds.PERFORMANCE_PAST
        );
        if (duplicateReviewPreflight.getStatus() != OperationStatus.CONFLICT) {
            throw new IllegalStateException(
                    "duplicate attendance review preflight was not rejected"
            );
        }
        OperationResult<List<CustomerReview>> submittedReviews = reviews.getCustomerReviews(2011);
        requireSuccess(submittedReviews, "submitted customer review lookup");
        CustomerReview submittedReview = submittedReviews.getValue().orElseThrow().stream()
                .filter(review -> review.getPerformanceId() == DevelopmentIds.PERFORMANCE_PAST)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "submitted customer review was not retrieved"
                ));
        if (submittedReview.getEventRating() != 5
                || submittedReview.getVenueRating() != 4
                || !submittedReview.getComment().contains("engaging")) {
            throw new IllegalStateException("submitted customer review details did not match");
        }
        OperationResult<List<Integer>> postReviewPerformances =
                reviews.getReviewablePerformanceIds(2011);
        requireSuccess(postReviewPerformances, "post-review performance lookup");
        if (postReviewPerformances.getValue().orElseThrow().contains(
                DevelopmentIds.PERFORMANCE_PAST
        )) {
            throw new IllegalStateException(
                    "reviewable performance lookup retained a submitted review"
            );
        }
        OperationResult<Void> ineligibleReview = reviews.submitReview(
                bookingCustomerOne.getValue().orElseThrow(),
                DevelopmentIds.PERFORMANCE_PAST,
                4,
                4,
                "This customer did not attend the performance."
        );
        if (ineligibleReview.getStatus() != OperationStatus.FORBIDDEN) {
            throw new IllegalStateException("non-attendee review was not rejected");
        }
    }

    private static void requireSuccess(OperationResult<?> result, String check) {
        if (!result.isSuccess()) {
            throw new IllegalStateException(check + " failed: " + result.getMessage());
        }
    }
}
