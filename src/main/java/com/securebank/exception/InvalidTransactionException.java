package com.securebank.exception;

public class InvalidTransactionException extends TransactionRejectedException {

    public InvalidTransactionException(String message) {
        super(ErrorCode.INVALID_TRANSACTION, message);
    }
}
