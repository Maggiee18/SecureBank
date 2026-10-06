package com.securebank.exception;

public class InvalidCredentialsException extends BankingException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }
}
