package com.securebank.dto.account;

import com.securebank.entity.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(

        @Schema(example = "SAVINGS")
        @NotNull(message = "Account type is required")
        AccountType accountType) {
}
