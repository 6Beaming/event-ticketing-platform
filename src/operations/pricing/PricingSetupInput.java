package operations.pricing;

import java.util.List;

public final class PricingSetupInput {
    private final int performanceId;
    private final List<TierInput> tiers;
    private final List<SectionTierInput> assignments;

    public PricingSetupInput(
            int performanceId,
            List<TierInput> tiers,
            List<SectionTierInput> assignments
    ) {
        this.performanceId = performanceId;
        this.tiers = tiers == null ? List.of() : List.copyOf(tiers);
        this.assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }

    public int getPerformanceId() {
        return performanceId;
    }

    public List<TierInput> getTiers() {
        return tiers;
    }

    public List<SectionTierInput> getAssignments() {
        return assignments;
    }
}
