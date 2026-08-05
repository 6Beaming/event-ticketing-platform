package testing;

import common.OperationResult;
import common.OperationStatus;
import data.DevelopmentIds;
import database.DatabaseConfig;
import database.DatabaseConnection;
import database.SqlScriptRunner;
import database.TransactionManager;
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
