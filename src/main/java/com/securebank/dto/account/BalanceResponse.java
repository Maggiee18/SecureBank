package com.securebank.dto.account;

import com.securebank.entity.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(
        String accountNumber,
        BigDecimal balance,
        String currency,
        Instant asOf) {

    public static BalanceResponse from(Account account) {
        return new BalanceResponse(account.getAccountNumber(), account.getBalance(), account.getCurrency(),
                Instant.now());
    }
}
