package com.securebank.exception;

public class InsufficientBalanceException extends TransactionRejectedException {

    public InsufficientBalanceException(String accountNumber) {
        super(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance in account " + accountNumber);
    }
}
