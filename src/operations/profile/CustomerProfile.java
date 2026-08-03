package operations.profile;

import java.time.LocalDate;

public final class CustomerProfile {
    private final int userId;
    private final String name;
    private final String address;
    private final String email;
    private final LocalDate dateOfBirth;
    private final String accountStatus;
    private final String maskedCardNumber;
    private final String cardHolderName;
    private final LocalDate expiryDate;
    private final String billingZip;

    public CustomerProfile(
            int userId,
            String name,
            String address,
            String email,
            LocalDate dateOfBirth,
            String accountStatus,
            String maskedCardNumber,
            String cardHolderName,
            LocalDate expiryDate,
            String billingZip
    ) {
        this.userId = userId;
        this.name = name;
        this.address = address;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.accountStatus = accountStatus;
        this.maskedCardNumber = maskedCardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.billingZip = billingZip;
    }

    public int getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getBillingZip() {
        return billingZip;
    }
}
