package toolkit;

import java.math.BigDecimal;

public class TierRecommendation {

    private final int tierRank;
    private final BigDecimal capacityPct;
    private final BigDecimal suggestedPrice;


    public TierRecommendation(
            int tierRank,
            BigDecimal capacityPct,
            BigDecimal suggestedPrice
    ) {
        this.tierRank = tierRank;
        this.capacityPct = capacityPct;
        this.suggestedPrice = suggestedPrice;
    }


    public int getTierRank() {
        return tierRank;
    }


    public BigDecimal getCapacityPct() {
        return capacityPct;
    }


    public BigDecimal getSuggestedPrice() {
        return suggestedPrice;
    }
}
