package operations.profile;

import java.time.LocalDate;

public final class PaymentInput {
    private final String cardNumber;
    private final String cardHolderName;
    private final LocalDate expiryDate;
    private final String billingZip;

    public PaymentInput(
            String cardNumber,
            String cardHolderName,
            LocalDate expiryDate,
            String billingZip
    ) {
        this.cardNumber = cardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.billingZip = billingZip;
    }

    public String getCardNumber() {
        return cardNumber;
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
