package com.securebank.dto.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Email is deliberately not editable here: it is the login identity, and changing it in a real
 * bank would need re-verification of the new address.
 */
public record UpdateCustomerRequest(

        @Schema(example = "Maggie R")
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        @Pattern(regexp = "^[A-Za-z][A-Za-z .'-]*$", message = "Full name may contain only letters, spaces, . ' and -")
        String fullName,

        @Schema(example = "9123456780")
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone must be a valid 10-digit Indian mobile number")
        String phone) {
}
