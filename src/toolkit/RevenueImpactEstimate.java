package toolkit;

import java.math.BigDecimal;

public class RevenueImpactEstimate {

    private final double expectedSellThroughPct;
    private final BigDecimal expectedRevenue;
    private final int sampleSize;


    public RevenueImpactEstimate(
            double expectedSellThroughPct,
            BigDecimal expectedRevenue,
            int sampleSize
    ) {
        this.expectedSellThroughPct = expectedSellThroughPct;
        this.expectedRevenue = expectedRevenue;
        this.sampleSize = sampleSize;
    }


    public double getExpectedSellThroughPct() {
        return expectedSellThroughPct;
    }


    public BigDecimal getExpectedRevenue() {
        return expectedRevenue;
    }


    public int getSampleSize() {
        return sampleSize;
    }
}