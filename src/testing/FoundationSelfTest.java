package testing;

import common.OperationResult;
import common.OperationStatus;
import data.DevelopmentDataGenerator;
import database.ConnectionProvider;
import database.TransactionManager;
import operations.booking.BookingOperations;
import operations.cancellation.CancellationOperations;
import operations.event.ArtistBillingInput;
import operations.event.EventInput;
import operations.event.OrganizerEventOperations;
import operations.inventory.InventoryMath;
import operations.inventory.InventoryState;
import operations.pricing.PerformancePricingOperations;
import operations.pricing.PricingSetupInput;
import operations.pricing.PricingValidator;
import operations.pricing.SectionTierInput;
import operations.pricing.TierInput;
import operations.profile.PaymentInput;
import operations.profile.ProfileInput;
import operations.profile.ProfileValidator;
import operations.resale.ResaleOperations;
import operations.review.ReviewOperations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class FoundationSelfTest {
    private int passed;
    private int failed;

    private FoundationSelfTest() {
    }

    public static void main(String[] args) {
        FoundationSelfTest suite = new FoundationSelfTest();
        int result = suite.run();
        if (result != 0) {
            System.exit(result);
        }
    }

    private int run() {
        test("adult profile is accepted", this::adultProfileAccepted);
        test("under-18 profile is rejected", this::underAgeProfileRejected);
        test("required payment fields are checked", this::paymentFieldsChecked);
        test("successful transaction commits", this::successfulTransactionCommits);
        test("validation result rolls back", this::validationFailureRollsBack);
        test("SQL failure rolls back safely", this::sqlFailureRollsBack);
        test("inventory calculations are consistent", this::inventoryCalculations);
        test("duplicate artist billing rank is rejected", this::duplicateBillingRejected);
        test("invalid organizer/taxonomy IDs are rejected", this::invalidEventIdsRejected);
        test("invalid pricing setup and unsafe replacement are rejected", this::pricingShapeRejected);
        test("cross-venue coverage is rejected", this::pricingCoverageRejected);
        test("tier-price and seat-state rules are enforced", this::organizerControlsValidated);
        test("booking request shapes are validated", this::bookingRequestsValidated);
        test("customer cancellation deadline is inclusive", this::cancellationDeadlineValidated);
        test("resale cap uses decimal money", this::resaleCapValidated);
        test("review rules are validated", this::reviewRulesValidated);
        test("development SQL generation is deterministic", this::dataGenerationDeterministic);

        System.out.println();
        System.out.println("Foundation self-test: " + passed + " passed, " + failed + " failed.");
        return failed == 0 ? 0 : 1;
    }

    private void adultProfileAccepted() {
        ProfileInput profile = new ProfileInput(
                "Adult User",
                "1 Test Street",
                "adult@example.test",
                LocalDate.of(2000, 1, 1)
        );
        assertTrue(ProfileValidator.validateProfile(profile, LocalDate.of(2026, 8, 3)).isEmpty());
        assertTrue(ProfileValidator.validateEmail(profile.getEmail()).isEmpty());
    }

    private void underAgeProfileRejected() {
        ProfileInput profile = new ProfileInput(
                "Young User",
                "1 Test Street",
                "young@example.test",
                LocalDate.of(2010, 1, 1)
        );
        assertTrue(ProfileValidator.validateProfile(profile, LocalDate.of(2026, 8, 3)).isPresent());
        assertTrue(ProfileValidator.validateDateOfBirth(
                profile.getDateOfBirth(),
                LocalDate.of(2026, 8, 3)
        ).isPresent());
    }

    private void paymentFieldsChecked() {
        PaymentInput payment = new PaymentInput("", "Test User", LocalDate.of(2030, 1, 1), "A1A 1A1");
        assertTrue(ProfileValidator.validatePayment(payment).isPresent());
        assertTrue(ProfileValidator.validateCardNumber(payment.getCardNumber()).isPresent());
        assertTrue(ProfileValidator.validateExpiryDate(
                LocalDate.of(2020, 5, 25),
                LocalDate.of(2026, 8, 5)
        ).isPresent());
        assertTrue(ProfileValidator.validateExpiryDate(
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5)
        ).isEmpty());
    }

    private void successfulTransactionCommits() {
        FakeConnection fake = new FakeConnection();
        TransactionManager transactions = new TransactionManager(fake.provider());
        OperationResult<Integer> result = transactions.execute(
                connection -> OperationResult.success("Committed.", 7)
        );
        assertEquals(OperationStatus.SUCCESS, result.getStatus());
        assertEquals(1, fake.commits);
        assertEquals(0, fake.rollbacks);
        assertTrue(fake.autoCommit);
    }

    private void validationFailureRollsBack() {
        FakeConnection fake = new FakeConnection();
        TransactionManager transactions = new TransactionManager(fake.provider());
        OperationResult<Integer> result = transactions.execute(
                connection -> OperationResult.invalidInput("Rejected.")
        );
        assertEquals(OperationStatus.INVALID_INPUT, result.getStatus());
        assertEquals(0, fake.commits);
        assertEquals(1, fake.rollbacks);
        assertTrue(fake.autoCommit);
    }

    private void sqlFailureRollsBack() {
        FakeConnection fake = new FakeConnection();
        TransactionManager transactions = new TransactionManager(fake.provider());
        OperationResult<Integer> result = transactions.execute(connection -> {
            throw new SQLException("Sensitive database detail", "42000");
        });
        assertEquals(OperationStatus.DATABASE_FAILURE, result.getStatus());
        assertEquals(1, fake.rollbacks);
        assertTrue(!result.getMessage().contains("Sensitive"));
    }

    private void inventoryCalculations() {
        assertEquals(InventoryState.AVAILABLE, InventoryMath.reservedState(false, false));
        assertEquals(InventoryState.SOLD, InventoryMath.reservedState(false, true));
        assertEquals(InventoryState.BLOCKED, InventoryMath.reservedState(true, false));
        assertEquals(4, InventoryMath.soldQuantity(20, 16));
    }

    private void duplicateBillingRejected() {
        EventInput event = new EventInput(
                1,
                "Test Event",
                null,
                new BigDecimal("1.20"),
                1,
                List.of(new ArtistBillingInput(1, 1), new ArtistBillingInput(2, 1))
        );
        assertTrue(OrganizerEventOperations.validateEvent(event).contains("unique"));
    }

    private void invalidEventIdsRejected() {
        EventInput event = new EventInput(
                0,
                "Test Event",
                null,
                new BigDecimal("1.20"),
                0,
                List.of(new ArtistBillingInput(1, 1))
        );
        assertTrue(OrganizerEventOperations.validateEvent(event).contains("positive"));
    }

    private void pricingShapeRejected() {
        PricingSetupInput tooFewTiers = new PricingSetupInput(
                1,
                List.of(new TierInput("P1", BigDecimal.TEN)),
                List.of(new SectionTierInput("Floor", "P1"))
        );
        assertTrue(PricingValidator.validateShape(tooFewTiers).contains("at least two"));

        PricingSetupInput duplicateSection = new PricingSetupInput(
                1,
                List.of(
                        new TierInput("P1", BigDecimal.TEN),
                        new TierInput("P2", BigDecimal.ONE)
                ),
                List.of(
                        new SectionTierInput("Floor", "P1"),
                        new SectionTierInput("Floor", "P2")
                )
        );
        assertTrue(PricingValidator.validateShape(duplicateSection).contains("only once"));

        PricingSetupInput unusedTier = new PricingSetupInput(
                1,
                List.of(
                        new TierInput("P1", BigDecimal.TEN),
                        new TierInput("P2", BigDecimal.ONE)
                ),
                List.of(
                        new SectionTierInput("Floor", "P1"),
                        new SectionTierInput("Balcony", "P1")
                )
        );
        assertTrue(PricingValidator.validateShape(unusedTier).contains("Unused tiers"));

        assertTrue(PerformancePricingOperations.validateReplacement(
                6004,
                true,
                true,
                true
        ).contains("tickets have already been sold"));
        assertTrue(PerformancePricingOperations.validateReplacement(
                6004,
                true,
                false,
                false
        ).contains("scheduled in the future"));
        assertTrue(PerformancePricingOperations.validateReplacement(
                6004,
                true,
                true,
                false
        ) == null);
    }

    private void pricingCoverageRejected() {
        PricingSetupInput input = new PricingSetupInput(
                1,
                List.of(
                        new TierInput("P1", BigDecimal.TEN),
                        new TierInput("P2", BigDecimal.ONE)
                ),
                List.of(new SectionTierInput("Floor", "P1"))
        );
        String error = PerformancePricingOperations.validateCoverage(
                input,
                Set.of("Floor", "Balcony")
        );
        assertTrue(error.contains("Missing"));
    }

    private void organizerControlsValidated() {
        assertTrue(PerformancePricingOperations.validateTierPriceUpdate(true, false) == null);
        assertTrue(PerformancePricingOperations.validateTierPriceUpdate(false, false)
                .contains("scheduled future"));
        assertTrue(PerformancePricingOperations.validateTierPriceUpdate(true, true)
                .contains("already been sold"));
        assertTrue(operations.inventory.InventoryOperations.validateSeatChange(
                false,
                false,
                true
        ) == null);
        assertTrue(operations.inventory.InventoryOperations.validateSeatChange(
                false,
                true,
                true
        ).contains("sold seat"));
        assertTrue(operations.inventory.InventoryOperations.validateSeatChange(
                false,
                false,
                false
        ).contains("not blocked"));
    }

    private void bookingRequestsValidated() {
        assertTrue(BookingOperations.validateReservedRequest(1, 1, List.of(10, 11)) == null);
        assertTrue(BookingOperations.validateReservedRequest(1, 1, List.of(10, 10))
                .contains("only once"));
        assertTrue(BookingOperations.validateReservedRequest(1, 1, List.of())
                .contains("At least one"));
        assertTrue(BookingOperations.validateGeneralRequest(1, 1, "Floor", 2) == null);
        assertTrue(BookingOperations.validateGeneralRequest(1, 1, "Floor", 0)
                .contains("positive"));
    }

    private void cancellationDeadlineValidated() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        assertTrue(CancellationOperations.meetsSevenDayDeadline(now, now.plusDays(7)));
        assertTrue(!CancellationOperations.meetsSevenDayDeadline(
                now,
                now.plusDays(7).minusMinutes(1)
        ));
        assertTrue(CancellationOperations.validateCustomerCancellation(1, List.of(7, 8)) == null);
        assertTrue(CancellationOperations.validateCustomerCancellation(1, List.of(7, 7))
                .contains("only once"));
    }

    private void resaleCapValidated() {
        assertEquals(
                new BigDecimal("138.00"),
                ResaleOperations.calculateCapPrice(
                        new BigDecimal("120.00"),
                        new BigDecimal("1.15")
                )
        );
    }

    private void reviewRulesValidated() {
        assertTrue(ReviewOperations.validateReview(1, 1, 5, 1, "A useful comment.") == null);
        assertTrue(ReviewOperations.validateReview(1, 1, 0, 5, "A useful comment.")
                .contains("1 to 5"));
        assertTrue(ReviewOperations.validateReview(1, 1, 5, 5, " ")
                .contains("required"));
    }

    private void dataGenerationDeterministic() {
        String first = DevelopmentDataGenerator.generateSql();
        String second = DevelopmentDataGenerator.generateSql();
        assertEquals(first, second);
        assertTrue(first.contains("(7001, 2001, 2101"));
        assertTrue(first.contains("(8002, 7002, 6002"));
    }

    private void test(String name, CheckedTest test) {
        try {
            test.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Exception | AssertionError failure) {
            failed++;
            System.out.println("FAIL  " + name + ": " + failure.getMessage());
        }
    }

    private void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("expected condition to be true");
        }
    }

    private void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    @FunctionalInterface
    private interface CheckedTest {
        void run() throws Exception;
    }

    private static final class FakeConnection implements InvocationHandler {
        private boolean autoCommit = true;
        private int commits;
        private int rollbacks;

        private ConnectionProvider provider() {
            Connection connection = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this
            );
            return new ConnectionProvider() {
                @Override
                public Connection requireConnection() {
                    return connection;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> {
                    autoCommit = (Boolean) args[0];
                    yield null;
                }
                case "commit" -> {
                    commits++;
                    yield null;
                }
                case "rollback" -> {
                    rollbacks++;
                    yield null;
                }
                case "close" -> null;
                case "isClosed" -> false;
                case "isValid" -> true;
                case "isWrapperFor" -> false;
                case "unwrap" -> null;
                case "toString" -> "FakeConnection";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
