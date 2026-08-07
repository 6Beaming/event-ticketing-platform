package toolkit;

import java.math.BigDecimal;

public class RevenueImpactEstimate {

    private final double currentExpectedSellThroughPct;
    private final double proposedExpectedSellThroughPct;
    private final BigDecimal currentExpectedRevenue;
    private final BigDecimal proposedExpectedRevenue;
    private final BigDecimal expectedRevenueChange;
    private final int currentSampleSize;
    private final int proposedSampleSize;


    public RevenueImpactEstimate(
            double currentExpectedSellThroughPct,
            double proposedExpectedSellThroughPct,
            BigDecimal currentExpectedRevenue,
            BigDecimal proposedExpectedRevenue,
            BigDecimal expectedRevenueChange,
            int currentSampleSize,
            int proposedSampleSize
    ) {
        this.currentExpectedSellThroughPct = currentExpectedSellThroughPct;
        this.proposedExpectedSellThroughPct = proposedExpectedSellThroughPct;
        this.currentExpectedRevenue = currentExpectedRevenue;
        this.proposedExpectedRevenue = proposedExpectedRevenue;
        this.expectedRevenueChange = expectedRevenueChange;
        this.currentSampleSize = currentSampleSize;
        this.proposedSampleSize = proposedSampleSize;
    }


    public double getCurrentExpectedSellThroughPct() {
        return currentExpectedSellThroughPct;
    }

    public double getProposedExpectedSellThroughPct() {
        return proposedExpectedSellThroughPct;
    }

    public BigDecimal getCurrentExpectedRevenue() {
        return currentExpectedRevenue;
    }

    public BigDecimal getProposedExpectedRevenue() {
        return proposedExpectedRevenue;
    }

    public BigDecimal getExpectedRevenueChange() {
        return expectedRevenueChange;
    }

    public int getCurrentSampleSize() {
        return currentSampleSize;
    }

    public int getProposedSampleSize() {
        return proposedSampleSize;
    }
}
