package toolkit;

import java.math.BigDecimal;
import java.util.List;

public class RevenueImpactInput {

    private final List<Integer> comparablePerformanceIds;
    private final BigDecimal currentPrice;
    private final BigDecimal proposedPrice;
    private final BigDecimal bandWidth;


    public RevenueImpactInput(
            List<Integer> comparablePerformanceIds,
            BigDecimal currentPrice,
            BigDecimal proposedPrice,
            BigDecimal bandWidth
    ) {
        this.comparablePerformanceIds = List.copyOf(comparablePerformanceIds);
        this.currentPrice = currentPrice;
        this.proposedPrice = proposedPrice;
        this.bandWidth = bandWidth;
    }


    public List<Integer> getComparablePerformanceIds() {
        return comparablePerformanceIds;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getProposedPrice() {
        return proposedPrice;
    }

    public BigDecimal getBandWidth() {
        return bandWidth;
    }
}
