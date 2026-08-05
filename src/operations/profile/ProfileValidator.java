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
        Optional<String> error = validateName(profile.getName());
        if (error.isPresent()) {
            return error;
        }
        error = validateAddress(profile.getAddress());
        if (error.isPresent()) {
            return error;
        }
        error = validateEmail(profile.getEmail());
        if (error.isPresent()) {
            return error;
        }
        error = validateDateOfBirth(profile.getDateOfBirth(), today);
        if (error.isPresent()) {
            return error;
        }
        return Optional.empty();
    }

    public static Optional<String> validateName(String name) {
        return isBlank(name) ? Optional.of("Name is required.") : Optional.empty();
    }

    public static Optional<String> validateAddress(String address) {
        return isBlank(address) ? Optional.of("Address is required.") : Optional.empty();
    }

    public static Optional<String> validateEmail(String email) {
        if (isBlank(email)) {
            return Optional.of("Email is required.");
        }
        String trimmedEmail = email.trim();
        if (!trimmedEmail.contains("@") || trimmedEmail.contains(" ")) {
            return Optional.of("Enter a valid email address.");
        }
        return Optional.empty();
    }

    public static Optional<String> validateDateOfBirth(LocalDate dateOfBirth, LocalDate today) {
        if (dateOfBirth == null) {
            return Optional.of("Date of birth is required.");
        }
        if (dateOfBirth.isAfter(today)) {
            return Optional.of("Date of birth cannot be in the future.");
        }
        if (dateOfBirth.plusYears(18).isAfter(today)) {
            return Optional.of("A MyTix account holder must be at least 18 years old.");
        }
        return Optional.empty();
    }

    public static Optional<String> validatePayment(PaymentInput payment) {
        if (payment == null) {
            return Optional.of("Payment information is required for a customer.");
        }
        Optional<String> error = validateCardNumber(payment.getCardNumber());
        if (error.isPresent()) {
            return error;
        }
        error = validateCardHolderName(payment.getCardHolderName());
        if (error.isPresent()) {
            return error;
        }
        error = validateExpiryDate(payment.getExpiryDate());
        if (error.isPresent()) {
            return error;
        }
        error = validateBillingZip(payment.getBillingZip());
        if (error.isPresent()) {
            return error;
        }
        return Optional.empty();
    }

    public static Optional<String> validateCardNumber(String cardNumber) {
        return isBlank(cardNumber)
                ? Optional.of("Card number is required.")
                : Optional.empty();
    }

    public static Optional<String> validateCardHolderName(String cardHolderName) {
        return isBlank(cardHolderName)
                ? Optional.of("Card holder name is required.")
                : Optional.empty();
    }

    public static Optional<String> validateExpiryDate(LocalDate expiryDate) {
        return validateExpiryDate(expiryDate, LocalDate.now());
    }

    public static Optional<String> validateExpiryDate(LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            return Optional.of("Card expiry date is required.");
        }
        if (expiryDate.isBefore(today)) {
            return Optional.of("Card expiry date cannot be in the past.");
        }
        return Optional.empty();
    }

    public static Optional<String> validateBillingZip(String billingZip) {
        return isBlank(billingZip)
                ? Optional.of("Billing postal or ZIP code is required.")
                : Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
