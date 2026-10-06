package com.securebank.service.idempotency;

/**
 * Either a fresh claim on the key (recordId set, caller must execute the operation)
 * or a stored response to replay (replayResponse set, caller must not execute anything).
 */
public record ClaimResult<T>(Long recordId, T replayResponse) {

    public static <T> ClaimResult<T> claimed(Long recordId) {
        return new ClaimResult<>(recordId, null);
    }

    public static <T> ClaimResult<T> replay(T response) {
        return new ClaimResult<>(null, response);
    }

    public boolean isReplay() {
        return recordId == null;
    }
}
