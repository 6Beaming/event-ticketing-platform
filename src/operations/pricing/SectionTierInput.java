package operations.pricing;

public final class SectionTierInput {
    private final String sectionName;
    private final String tierCode;

    public SectionTierInput(String sectionName, String tierCode) {
        this.sectionName = sectionName;
        this.tierCode = tierCode;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getTierCode() {
        return tierCode;
    }
}
