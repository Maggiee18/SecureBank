package com.securebank.dto.transaction;

import java.math.BigDecimal;
import java.time.Instant;

public record MoneyOperationResponse(
        String transactionReference,
        String type,
        String status,
        String accountNumber,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt) {
}
