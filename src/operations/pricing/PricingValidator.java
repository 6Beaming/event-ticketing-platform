package operations.pricing;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public final class PricingValidator {
    private PricingValidator() {
    }

    public static String validateShape(PricingSetupInput input) {
        if (input == null) {
            return "Pricing setup is required.";
        }
        if (input.getPerformanceId() <= 0) {
            return "Performance ID must be positive.";
        }
        if (input.getTiers().size() < 2) {
            return "A performance must have at least two price tiers.";
        }

        Set<String> tierCodes = new HashSet<>();
        for (TierInput tier : input.getTiers()) {
            if (tier == null || isBlank(tier.getTierCode())) {
                return "Every tier needs a code.";
            }
            if (tier.getPrice() == null || tier.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return "Every tier price must be positive.";
            }
            if (!tierCodes.add(normalize(tier.getTierCode()))) {
                return "Tier codes must be unique within a performance.";
            }
        }

        if (input.getAssignments().isEmpty()) {
            return "Every venue section must be assigned to a tier.";
        }
        Set<String> sectionNames = new HashSet<>();
        for (SectionTierInput assignment : input.getAssignments()) {
            if (assignment == null || isBlank(assignment.getSectionName())
                    || isBlank(assignment.getTierCode())) {
                return "Each section assignment needs a section and tier code.";
            }
            if (!sectionNames.add(normalize(assignment.getSectionName()))) {
                return "A section can be assigned only once for a performance.";
            }
            if (!tierCodes.contains(normalize(assignment.getTierCode()))) {
                return "Every section assignment must reference a supplied tier.";
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
