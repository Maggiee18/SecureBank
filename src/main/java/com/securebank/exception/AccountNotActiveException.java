package com.securebank.exception;

import com.securebank.entity.AccountStatus;

public class AccountNotActiveException extends TransactionRejectedException {

    public AccountNotActiveException(String accountNumber, AccountStatus status) {
        super(status == AccountStatus.CLOSED ? ErrorCode.ACCOUNT_CLOSED : ErrorCode.ACCOUNT_BLOCKED,
                "Account " + accountNumber + " is " + status + " and cannot be used for transactions");
    }
}
