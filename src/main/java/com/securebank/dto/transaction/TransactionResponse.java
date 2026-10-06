package com.securebank.dto.transaction;

import com.securebank.entity.Account;
import com.securebank.entity.BankTransaction;
import com.securebank.util.AccountNumberMasker;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One statement line, seen from the point of view of the account being viewed.
 * The counterparty account number is masked, as on a printed bank statement.
 */
public record TransactionResponse(
        String transactionReference,
        String type,
        String direction,
        String status,
        BigDecimal amount,
        String counterpartyAccount,
        String description,
        String failureReason,
        Instant createdAt) {

    public static TransactionResponse from(BankTransaction transaction, Long viewedAccountId) {
        Account source = transaction.getSourceAccount();
        Account destination = transaction.getDestinationAccount();
        boolean outgoing = source != null && source.getId().equals(viewedAccountId);
        Account counterparty = outgoing ? destination : source;
        return new TransactionResponse(
                transaction.getTransactionReference(),
                transaction.getType().name(),
                outgoing ? "DEBIT" : "CREDIT",
                transaction.getStatus().name(),
                transaction.getAmount(),
                counterparty == null ? null : AccountNumberMasker.mask(counterparty.getAccountNumber()),
                transaction.getDescription(),
                transaction.getFailureReason(),
                transaction.getCreatedAt());
    }
}
