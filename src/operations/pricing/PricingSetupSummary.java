package operations.pricing;

public final class PricingSetupSummary {
    private final int performanceId;
    private final int tierCount;
    private final int assignedSectionCount;

    public PricingSetupSummary(int performanceId, int tierCount, int assignedSectionCount) {
        this.performanceId = performanceId;
        this.tierCount = tierCount;
        this.assignedSectionCount = assignedSectionCount;
    }

    public int getPerformanceId() {
        return performanceId;
    }

    public int getTierCount() {
        return tierCount;
    }

    public int getAssignedSectionCount() {
        return assignedSectionCount;
    }
}
