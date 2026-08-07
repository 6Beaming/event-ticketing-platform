package ui;

import common.OperationStatus;

/**
 * Shared terminal policy for query, report, and organizer-toolkit input failures.
 */
public final class FeatureInputPolicy {
    private FeatureInputPolicy() {
    }

    public static String conflictMessage(String message) {
        String detail = message == null ? "" : message.trim();
        if (detail.isEmpty()) {
            detail = "The supplied input cannot be used for this request.";
        }
        return "CONFLICT: " + detail;
    }

    public static boolean isRetryable(OperationStatus status) {
        return status == OperationStatus.INVALID_INPUT
                || status == OperationStatus.NOT_FOUND
                || status == OperationStatus.CONFLICT;
    }
}
