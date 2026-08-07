package operations.resale;

import java.math.BigDecimal;

public final class ResaleListingSummary {
    private final int listingId;
    private final int ticketId;
    private final BigDecimal listingPrice;
    private final BigDecimal capPrice;

    public ResaleListingSummary(
            int listingId,
            int ticketId,
            BigDecimal listingPrice,
            BigDecimal capPrice
    ) {
        this.listingId = listingId;
        this.ticketId = ticketId;
        this.listingPrice = listingPrice;
        this.capPrice = capPrice;
    }

    public int getListingId() {
        return listingId;
    }

    public int getTicketId() {
        return ticketId;
    }

    public BigDecimal getListingPrice() {
        return listingPrice;
    }

    public BigDecimal getCapPrice() {
        return capPrice;
    }
}
