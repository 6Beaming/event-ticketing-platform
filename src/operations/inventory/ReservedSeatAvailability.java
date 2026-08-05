package operations.inventory;

import java.math.BigDecimal;

public final class ReservedSeatAvailability {
    private final int performanceSeatId;
    private final String sectionName;
    private final String rowName;
    private final int seatNumber;
    private final String tierCode;
    private final BigDecimal price;
    private final InventoryState state;

    public ReservedSeatAvailability(
            int performanceSeatId,
            String sectionName,
            String rowName,
            int seatNumber,
            String tierCode,
            BigDecimal price,
            InventoryState state
    ) {
        this.performanceSeatId = performanceSeatId;
        this.sectionName = sectionName;
        this.rowName = rowName;
        this.seatNumber = seatNumber;
        this.tierCode = tierCode;
        this.price = price;
        this.state = state;
    }

    public int getPerformanceSeatId() {
        return performanceSeatId;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getRowName() {
        return rowName;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public String getTierCode() {
        return tierCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public InventoryState getState() {
        return state;
    }
}
