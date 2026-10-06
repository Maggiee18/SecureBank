package com.securebank.exception;

/**
 * Base class for every expected, business-level failure. Each subclass maps to one
 * {@link ErrorCode}, so the global handler needs a single method for all of them.
 */
public abstract class BankingException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BankingException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
