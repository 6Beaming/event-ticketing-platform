package toolkit;

import java.math.BigDecimal;
import java.util.List;

public class PricingRecommendation {

    private final int tierCount;
    private final List<TierRecommendation> tiers;
    private final int comparablesUsed;
    private final BigDecimal expectedRevenue;
    private final List<ComparablePerformance> comparablePerformances;
    private final boolean fallbackUsed;
    private final String strategyDescription;


    public PricingRecommendation(
            int tierCount,
            List<TierRecommendation> tiers,
            int comparablesUsed,
            BigDecimal expectedRevenue,
            List<ComparablePerformance> comparablePerformances,
            boolean fallbackUsed,
            String strategyDescription
    ) {
        this.tierCount = tierCount;
        this.tiers = List.copyOf(tiers);
        this.comparablesUsed = comparablesUsed;
        this.expectedRevenue = expectedRevenue;
        this.comparablePerformances = List.copyOf(comparablePerformances);
        this.fallbackUsed = fallbackUsed;
        this.strategyDescription = strategyDescription;
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
        return comparablePerformances.stream()
                .map(ComparablePerformance::getPerformanceId)
                .toList();
    }

    public List<ComparablePerformance> getComparablePerformances() {
        return comparablePerformances;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public String getStrategyDescription() {
        return strategyDescription;
    }
}
