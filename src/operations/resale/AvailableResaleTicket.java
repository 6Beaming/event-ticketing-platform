package operations.resale;

import java.math.BigDecimal;

public final class AvailableResaleTicket {
    private final int ticketId;
    private final int sellerCustomerId;
    private final BigDecimal listingPrice;

    public AvailableResaleTicket(
            int ticketId,
            int sellerCustomerId,
            BigDecimal listingPrice
    ) {
        this.ticketId = ticketId;
        this.sellerCustomerId = sellerCustomerId;
        this.listingPrice = listingPrice;
    }

    public int getTicketId() {
        return ticketId;
    }

    public int getSellerCustomerId() {
        return sellerCustomerId;
    }

    public BigDecimal getListingPrice() {
        return listingPrice;
    }
}
