package operations.resale;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class TicketOwnershipHistoryEntry {
    private final int ticketId;
    private final int performanceId;
    private final int customerId;
    private final String customerName;
    private final int transactionId;
    private final String transactionType;
    private final Integer listingId;
    private final BigDecimal purchasePrice;
    private final LocalDateTime acquiredAt;
    private final LocalDateTime endedAt;

    public TicketOwnershipHistoryEntry(
            int ticketId,
            int performanceId,
            int customerId,
            String customerName,
            int transactionId,
            String transactionType,
            Integer listingId,
            BigDecimal purchasePrice,
            LocalDateTime acquiredAt,
            LocalDateTime endedAt
    ) {
        this.ticketId = ticketId;
        this.performanceId = performanceId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.listingId = listingId;
        this.purchasePrice = purchasePrice;
        this.acquiredAt = acquiredAt;
        this.endedAt = endedAt;
    }

    public int getTicketId() {
        return ticketId;
    }

    public int getPerformanceId() {
        return performanceId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public Integer getListingId() {
        return listingId;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public LocalDateTime getAcquiredAt() {
        return acquiredAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }
}
