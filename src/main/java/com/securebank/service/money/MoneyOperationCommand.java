package com.securebank.service.money;

import com.securebank.dto.transaction.MoneyOperationRequest;
import com.securebank.dto.transaction.TransferRequest;
import com.securebank.entity.TransactionType;

import java.math.BigDecimal;

/**
 * Everything needed to execute (and, if it fails, to record) one money movement.
 * Built by controllers from the authenticated principal plus the validated request.
 */
public record MoneyOperationCommand(
        Long customerId,
        TransactionType type,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String description,
        String idempotencyKey) {

    public static MoneyOperationCommand deposit(Long customerId, String accountNumber, MoneyOperationRequest request,
                                                String idempotencyKey) {
        return new MoneyOperationCommand(customerId, TransactionType.DEPOSIT, null, accountNumber,
                request.amount(), clean(request.description()), idempotencyKey);
    }

    public static MoneyOperationCommand withdrawal(Long customerId, String accountNumber,
                                                   MoneyOperationRequest request, String idempotencyKey) {
        return new MoneyOperationCommand(customerId, TransactionType.WITHDRAWAL, accountNumber, null,
                request.amount(), clean(request.description()), idempotencyKey);
    }

    public static MoneyOperationCommand transfer(Long customerId, TransferRequest request, String idempotencyKey) {
        return new MoneyOperationCommand(customerId, TransactionType.TRANSFER, request.sourceAccountNumber(),
                request.destinationAccountNumber(), request.amount(), clean(request.description()), idempotencyKey);
    }

    /** The customer's own account for this operation: credited for deposits, debited otherwise. */
    public String primaryAccountNumber() {
        return type == TransactionType.DEPOSIT ? destinationAccountNumber : sourceAccountNumber;
    }

    /**
     * Canonical text of the business request, hashed for idempotency. 100, 100.0 and 100.00
     * are the same amount, so the amount is normalised before hashing.
     */
    public String fingerprint() {
        return String.join("|",
                type.name(),
                String.valueOf(sourceAccountNumber),
                String.valueOf(destinationAccountNumber),
                amount.stripTrailingZeros().toPlainString(),
                String.valueOf(description));
    }

    private static String clean(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
