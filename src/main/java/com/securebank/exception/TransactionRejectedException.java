package com.securebank.exception;

/**
 * A money movement that was well-formed and authorised but broke a business rule
 * (insufficient balance, inactive account, same-account transfer). These attempts are
 * recorded as FAILED transactions so they stay traceable after the rollback.
 */
public abstract class TransactionRejectedException extends BankingException {

    protected TransactionRejectedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
