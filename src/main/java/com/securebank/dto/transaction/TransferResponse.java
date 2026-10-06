package com.securebank.dto.transaction;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        String transactionReference,
        String status,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        BigDecimal sourceBalanceAfter,
        String description,
        Instant createdAt) {
}
