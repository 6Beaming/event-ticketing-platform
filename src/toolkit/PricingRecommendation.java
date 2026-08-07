package toolkit;

import java.math.BigDecimal;
import java.util.List;

public class PricingRecommendation {

    private final int tierCount;
    private final List<TierRecommendation> tiers;
    private final int comparablesUsed;
    private final BigDecimal expectedRevenue;
    private final List<Integer> comparablePerformanceIds;


    public PricingRecommendation(
            int tierCount,
            List<TierRecommendation> tiers,
            int comparablesUsed,
            BigDecimal expectedRevenue,
            List<Integer> comparablePerformanceIds
    ) {
        this.tierCount = tierCount;
        this.tiers = tiers;
        this.comparablesUsed = comparablesUsed;
        this.expectedRevenue = expectedRevenue;
        this.comparablePerformanceIds = comparablePerformanceIds;
    }


    public int getTierCount() {
        return tierCount;
    }


    public List<TierRecommendation> getTiers() {
        return tiers;
    }


    public int getComparablesUsed() {
        return comparablesUsed;
    }


    public BigDecimal getExpectedRevenue() {
        return expectedRevenue;
    }


    /**
     * The performance IDs behind this recommendation (as found by PR1),
     * kept around so a follow-up "what if I change the price?" call
     * (estimateRevenueImpact) can reuse the same comparable set instead
     * of the user having to re-enter or re-look-up IDs.
     */
    public List<Integer> getComparablePerformanceIds() {
        return comparablePerformanceIds;
    }
}