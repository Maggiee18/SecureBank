package com.securebank.exception;

import java.time.Duration;

public class TooManyLoginAttemptsException extends BankingException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, "Too many failed login attempts. Try again later");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
