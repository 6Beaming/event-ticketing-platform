package operations.resale;

import java.math.BigDecimal;

public final class ResalePurchaseSummary {
    private final int transactionId;
    private final int listingId;
    private final int ticketId;
    private final BigDecimal purchasePrice;

    public ResalePurchaseSummary(
            int transactionId,
            int listingId,
            int ticketId,
            BigDecimal purchasePrice
    ) {
        this.transactionId = transactionId;
        this.listingId = listingId;
        this.ticketId = ticketId;
        this.purchasePrice = purchasePrice;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public int getListingId() {
        return listingId;
    }

    public int getTicketId() {
        return ticketId;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }
}
