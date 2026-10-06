package com.securebank.exception;

public class DuplicateResourceException extends BankingException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
