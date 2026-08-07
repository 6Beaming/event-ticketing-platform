package toolkit;

import java.math.BigDecimal;

public class TierRecommendation {

    private final int tierRank;
    private final double capacityPct;
    private final BigDecimal suggestedPrice;


    public TierRecommendation(
            int tierRank,
            double capacityPct,
            BigDecimal suggestedPrice
    ) {
        this.tierRank = tierRank;
        this.capacityPct = capacityPct;
        this.suggestedPrice = suggestedPrice;
    }


    public int getTierRank() {
        return tierRank;
    }


    public double getCapacityPct() {
        return capacityPct;
    }


    public BigDecimal getSuggestedPrice() {
        return suggestedPrice;
    }
}