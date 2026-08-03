package common;

import java.util.Objects;
import java.util.Optional;

public final class OperationResult<T> {
    private final OperationStatus status;
    private final String message;
    private final T value;

    private OperationResult(OperationStatus status, String message, T value) {
        this.status = Objects.requireNonNull(status, "status");
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("An operation result message is required");
        }
        this.message = message.trim();
        this.value = value;
    }

    public static <T> OperationResult<T> success(String message, T value) {
        return new OperationResult<>(OperationStatus.SUCCESS, message, value);
    }

    public static OperationResult<Void> success(String message) {
        return new OperationResult<>(OperationStatus.SUCCESS, message, null);
    }

    public static <T> OperationResult<T> invalidInput(String message) {
        return new OperationResult<>(OperationStatus.INVALID_INPUT, message, null);
    }

    public static <T> OperationResult<T> notFound(String message) {
        return new OperationResult<>(OperationStatus.NOT_FOUND, message, null);
    }

    public static <T> OperationResult<T> forbidden(String message) {
        return new OperationResult<>(OperationStatus.FORBIDDEN, message, null);
    }

    public static <T> OperationResult<T> conflict(String message) {
        return new OperationResult<>(OperationStatus.CONFLICT, message, null);
    }

    public static <T> OperationResult<T> databaseFailure(String message) {
        return new OperationResult<>(OperationStatus.DATABASE_FAILURE, message, null);
    }

    public OperationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Optional<T> getValue() {
        return Optional.ofNullable(value);
    }

    public boolean isSuccess() {
        return status == OperationStatus.SUCCESS;
    }
}
