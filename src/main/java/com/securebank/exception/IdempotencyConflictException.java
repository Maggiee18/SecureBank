package com.securebank.exception;

public class IdempotencyConflictException extends BankingException {

    private IdempotencyConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static IdempotencyConflictException keyReusedWithDifferentRequest() {
        return new IdempotencyConflictException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                "Idempotency-Key was already used for a different request");
    }

    public static IdempotencyConflictException requestInProgress() {
        return new IdempotencyConflictException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                "A request with this Idempotency-Key is still being processed");
    }
}
