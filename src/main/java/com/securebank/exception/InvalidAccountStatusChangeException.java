package com.securebank.exception;

public class InvalidAccountStatusChangeException extends BankingException {

    public InvalidAccountStatusChangeException(String message) {
        super(ErrorCode.INVALID_ACCOUNT_STATUS_CHANGE, message);
    }
}
