package com.securebank.dto.admin;

import com.securebank.entity.Account;

import java.math.BigDecimal;
import java.time.Instant;

/** Account as the operations team sees it, including who owns it. */
public record AdminAccountResponse(
        String accountNumber,
        String accountType,
        String status,
        BigDecimal balance,
        String currency,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        Instant createdAt) {

    public static AdminAccountResponse from(Account account) {
        return new AdminAccountResponse(
                account.getAccountNumber(),
                account.getAccountType().name(),
                account.getStatus().name(),
                account.getBalance(),
                account.getCurrency(),
                account.getCustomer().getId(),
                account.getCustomer().getFullName(),
                account.getCustomer().getEmail(),
                account.getCreatedAt());
    }
}
