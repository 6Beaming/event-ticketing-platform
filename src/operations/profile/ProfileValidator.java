package operations.profile;

import java.time.LocalDate;
import java.util.Optional;

public final class ProfileValidator {
    private ProfileValidator() {
    }

    public static Optional<String> validateProfile(ProfileInput profile, LocalDate today) {
        if (profile == null) {
            return Optional.of("Profile information is required.");
        }
        if (isBlank(profile.getName())) {
            return Optional.of("Name is required.");
        }
        if (isBlank(profile.getAddress())) {
            return Optional.of("Address is required.");
        }
        if (isBlank(profile.getEmail())) {
            return Optional.of("Email is required.");
        }
        String email = profile.getEmail().trim();
        if (!email.contains("@") || email.contains(" ")) {
            return Optional.of("Enter a valid email address.");
        }
        if (profile.getDateOfBirth() == null) {
            return Optional.of("Date of birth is required.");
        }
        if (profile.getDateOfBirth().isAfter(today)) {
            return Optional.of("Date of birth cannot be in the future.");
        }
        if (profile.getDateOfBirth().plusYears(18).isAfter(today)) {
            return Optional.of("A MyTix account holder must be at least 18 years old.");
        }
        return Optional.empty();
    }

    public static Optional<String> validatePayment(PaymentInput payment) {
        if (payment == null) {
            return Optional.of("Payment information is required for a customer.");
        }
        if (isBlank(payment.getCardNumber())) {
            return Optional.of("A fictional card number is required.");
        }
        if (isBlank(payment.getCardHolderName())) {
            return Optional.of("Card holder name is required.");
        }
        if (payment.getExpiryDate() == null) {
            return Optional.of("Card expiry date is required.");
        }
        if (isBlank(payment.getBillingZip())) {
            return Optional.of("Billing postal or ZIP code is required.");
        }
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
