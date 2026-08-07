package operations.profile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CustomerOrderHistoryEntry {
    private final int orderId;
    private final String orderType;
    private final LocalDateTime orderDate;
    private final String maskedCardNumber;
    private final int ticketId;
    private final int performanceId;
    private final String eventTitle;
    private final LocalDateTime performanceDateTime;
    private final String performanceStatus;
    private final String venueName;
    private final String venueCity;
    private final String tierCode;
    private final BigDecimal purchasePrice;
    private final String ticketStatus;
    private final String sectionName;
    private final String rowName;
    private final Integer seatNumber;
    private final LocalDateTime acquiredAt;
    private final LocalDateTime ownershipEndedAt;
    private final LocalDateTime cancellationDate;
    private final BigDecimal refundAmount;

    public CustomerOrderHistoryEntry(
            int orderId,
            String orderType,
            LocalDateTime orderDate,
            String maskedCardNumber,
            int ticketId,
            int performanceId,
            String eventTitle,
            LocalDateTime performanceDateTime,
            String performanceStatus,
            String venueName,
            String venueCity,
            String tierCode,
            BigDecimal purchasePrice,
            String ticketStatus,
            String sectionName,
            String rowName,
            Integer seatNumber,
            LocalDateTime acquiredAt,
            LocalDateTime ownershipEndedAt,
            LocalDateTime cancellationDate,
            BigDecimal refundAmount
    ) {
        this.orderId = orderId;
        this.orderType = orderType;
        this.orderDate = orderDate;
        this.maskedCardNumber = maskedCardNumber;
        this.ticketId = ticketId;
        this.performanceId = performanceId;
        this.eventTitle = eventTitle;
        this.performanceDateTime = performanceDateTime;
        this.performanceStatus = performanceStatus;
        this.venueName = venueName;
        this.venueCity = venueCity;
        this.tierCode = tierCode;
        this.purchasePrice = purchasePrice;
        this.ticketStatus = ticketStatus;
        this.sectionName = sectionName;
        this.rowName = rowName;
        this.seatNumber = seatNumber;
        this.acquiredAt = acquiredAt;
        this.ownershipEndedAt = ownershipEndedAt;
        this.cancellationDate = cancellationDate;
        this.refundAmount = refundAmount;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getOrderType() {
        return orderType;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public int getTicketId() {
        return ticketId;
    }

    public int getPerformanceId() {
        return performanceId;
    }

    public String getEventTitle() {
        return eventTitle;
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

    public String getTierCode() {
        return tierCode;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getRowName() {
        return rowName;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public LocalDateTime getAcquiredAt() {
        return acquiredAt;
    }

    public LocalDateTime getOwnershipEndedAt() {
        return ownershipEndedAt;
    }

    public LocalDateTime getCancellationDate() {
        return cancellationDate;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }
}
