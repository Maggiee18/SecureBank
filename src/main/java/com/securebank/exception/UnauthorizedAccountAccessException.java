package com.securebank.exception;

public class UnauthorizedAccountAccessException extends BankingException {

    public UnauthorizedAccountAccessException(String accountNumber) {
        super(ErrorCode.ACCOUNT_ACCESS_DENIED, "You do not have access to account " + accountNumber);
    }
}
