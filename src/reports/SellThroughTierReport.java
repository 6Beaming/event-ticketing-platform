package reports;

import java.math.BigDecimal;

public final class SellThroughTierReport {

    private final int performanceId;
    private final String tierCode;
    private final int capacity;
    private final int numSold;
    private final BigDecimal sellThroughRate;


    public SellThroughTierReport(
            int performanceId,
            String tierCode,
            int capacity,
            int numSold,
            BigDecimal sellThroughRate
    ) {
        this.performanceId = performanceId;
        this.tierCode = tierCode;
        this.capacity = capacity;
        this.numSold = numSold;
        this.sellThroughRate = sellThroughRate;
    }


    public int getPerformanceId() {
        return performanceId;
    }


    public String getTierCode() {
        return tierCode;
    }


    public int getCapacity() {
        return capacity;
    }


    public int getNumSold() {
        return numSold;
    }


    public BigDecimal getSellThroughRate() {
        return sellThroughRate;
    }
}