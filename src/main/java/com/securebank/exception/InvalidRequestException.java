package com.securebank.exception;

public class InvalidRequestException extends BankingException {

    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
}
