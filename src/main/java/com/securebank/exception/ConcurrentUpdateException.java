package com.securebank.exception;

public class ConcurrentUpdateException extends BankingException {

    public ConcurrentUpdateException() {
        super(ErrorCode.CONCURRENT_UPDATE,
                "The account was updated by another request at the same time. Please retry the request");
    }
}
