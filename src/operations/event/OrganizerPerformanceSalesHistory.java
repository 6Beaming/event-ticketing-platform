package operations.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class OrganizerPerformanceSalesHistory {
    private final int eventId;
    private final String eventTitle;
    private final Integer performanceId;
    private final LocalDateTime performanceDateTime;
    private final String performanceStatus;
    private final String venueName;
    private final String venueCity;
    private final int originalTicketCount;
    private final BigDecimal originalGrossRevenue;
    private final int activeTicketCount;
    private final int cancelledTicketCount;
    private final BigDecimal refundedAmount;
    private final int completedResaleCount;
    private final BigDecimal resaleGrossRevenue;
    private final Integer transactionId;
    private final String transactionType;
    private final LocalDateTime transactionDate;
    private final Integer customerId;
    private final String customerName;
    private final Integer ticketId;
    private final BigDecimal salePrice;

    public OrganizerPerformanceSalesHistory(
            int eventId,
            String eventTitle,
            Integer performanceId,
            LocalDateTime performanceDateTime,
            String performanceStatus,
            String venueName,
            String venueCity,
            int originalTicketCount,
            BigDecimal originalGrossRevenue,
            int activeTicketCount,
            int cancelledTicketCount,
            BigDecimal refundedAmount,
            int completedResaleCount,
            BigDecimal resaleGrossRevenue,
            Integer transactionId,
            String transactionType,
            LocalDateTime transactionDate,
            Integer customerId,
            String customerName,
            Integer ticketId,
            BigDecimal salePrice
    ) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.performanceId = performanceId;
        this.performanceDateTime = performanceDateTime;
        this.performanceStatus = performanceStatus;
        this.venueName = venueName;
        this.venueCity = venueCity;
        this.originalTicketCount = originalTicketCount;
        this.originalGrossRevenue = originalGrossRevenue;
        this.activeTicketCount = activeTicketCount;
        this.cancelledTicketCount = cancelledTicketCount;
        this.refundedAmount = refundedAmount;
        this.completedResaleCount = completedResaleCount;
        this.resaleGrossRevenue = resaleGrossRevenue;
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.transactionDate = transactionDate;
        this.customerId = customerId;
        this.customerName = customerName;
        this.ticketId = ticketId;
        this.salePrice = salePrice;
    }

    public int getEventId() {
        return eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public Integer getPerformanceId() {
        return performanceId;
    }

    public LocalDateTime getPerformanceDateTime() {
        return performanceDateTime;
    }

    public String getPerformanceStatus() {
        return performanceStatus;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getVenueCity() {
        return venueCity;
    }

    public int getOriginalTicketCount() {
        return originalTicketCount;
    }

    public BigDecimal getOriginalGrossRevenue() {
        return originalGrossRevenue;
    }

    public int getActiveTicketCount() {
        return activeTicketCount;
    }

    public int getCancelledTicketCount() {
        return cancelledTicketCount;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public int getCompletedResaleCount() {
        return completedResaleCount;
    }

    public BigDecimal getResaleGrossRevenue() {
        return resaleGrossRevenue;
    }

    public Integer getTransactionId() {
        return transactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }
}
