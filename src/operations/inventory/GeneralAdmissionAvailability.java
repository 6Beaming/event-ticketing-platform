package operations.inventory;

import java.math.BigDecimal;

public final class GeneralAdmissionAvailability {
    private final int capacityId;
    private final String sectionName;
    private final String tierCode;
    private final BigDecimal price;
    private final int totalCapacity;
    private final int soldQuantity;
    private final int remainingCapacity;

    public GeneralAdmissionAvailability(
            int capacityId,
            String sectionName,
            String tierCode,
            BigDecimal price,
            int totalCapacity,
            int soldQuantity,
            int remainingCapacity
    ) {
        this.capacityId = capacityId;
        this.sectionName = sectionName;
        this.tierCode = tierCode;
        this.price = price;
        this.totalCapacity = totalCapacity;
        this.soldQuantity = soldQuantity;
        this.remainingCapacity = remainingCapacity;
    }

    public int getCapacityId() {
        return capacityId;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getTierCode() {
        return tierCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getSoldQuantity() {
        return soldQuantity;
    }

    public int getRemainingCapacity() {
        return remainingCapacity;
    }
}
