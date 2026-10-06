package com.securebank.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MoneyOperationRequest(

        @Schema(example = "5000.00")
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 2, message = "Amount can have at most 15 digits and 2 decimal places")
        BigDecimal amount,

        @Schema(example = "Cash deposit at branch")
        @Size(max = 140, message = "Description must be at most 140 characters")
        String description) {
}
