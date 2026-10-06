package com.securebank.dto.admin;

import com.securebank.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAccountStatusRequest(

        @Schema(example = "BLOCKED")
        @NotNull(message = "Status is required")
        AccountStatus status,

        @Schema(example = "Customer reported lost debit card")
        @NotBlank(message = "Reason is required")
        @Size(max = 200, message = "Reason must be at most 200 characters")
        String reason) {
}
