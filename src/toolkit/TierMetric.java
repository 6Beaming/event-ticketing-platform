package toolkit;

import java.math.BigDecimal;

public class TierMetric {

    private final int performanceId;
    private final int tierRank;
    private final BigDecimal price;
    private final int tierCapacity;
    private final int tierSold;
    private final BigDecimal tierRevenue;
    private final double sellThroughPct;
    private final double capacityPct;


    public TierMetric(
            int performanceId,
            int tierRank,
            BigDecimal price,
            int tierCapacity,
            int tierSold,
            BigDecimal tierRevenue,
            double sellThroughPct,
            double capacityPct
    ) {
        this.performanceId = performanceId;
        this.tierRank = tierRank;
        this.price = price;
        this.tierCapacity = tierCapacity;
        this.tierSold = tierSold;
        this.tierRevenue = tierRevenue;
        this.sellThroughPct = sellThroughPct;
        this.capacityPct = capacityPct;
    }


    public int getPerformanceId() {
        return performanceId;
    }

    public int getTierRank() {
        return tierRank;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getTierCapacity() {
        return tierCapacity;
    }

    public int getTierSold() {
        return tierSold;
    }

    public BigDecimal getTierRevenue() {
        return tierRevenue;
    }

    public double getSellThroughPct() {
        return sellThroughPct;
    }

    public double getCapacityPct() {
        return capacityPct;
    }
}