package com.securebank.service.money;

/**
 * Result of a money operation plus whether it was replayed from the idempotency store
 * rather than executed now.
 */
public record OperationOutcome<T>(T body, boolean replayed) {

    public static <T> OperationOutcome<T> executed(T body) {
        return new OperationOutcome<>(body, false);
    }

    public static <T> OperationOutcome<T> replayed(T body) {
        return new OperationOutcome<>(body, true);
    }
}
