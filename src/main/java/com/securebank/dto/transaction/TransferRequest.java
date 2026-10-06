package com.securebank.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(

        @Schema(example = "502100000001")
        @NotBlank(message = "Source account number is required")
        @Pattern(regexp = "^\\d{12}$", message = "Source account number must be 12 digits")
        String sourceAccountNumber,

        @Schema(example = "502100000002")
        @NotBlank(message = "Destination account number is required")
        @Pattern(regexp = "^\\d{12}$", message = "Destination account number must be 12 digits")
        String destinationAccountNumber,

        @Schema(example = "2000.00")
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 2, message = "Amount can have at most 15 digits and 2 decimal places")
        BigDecimal amount,

        @Schema(example = "Rent for October")
        @Size(max = 140, message = "Description must be at most 140 characters")
        String description) {
}
