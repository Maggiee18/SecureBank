package com.securebank.controller;

import com.securebank.config.OpenApiConfig;
import com.securebank.dto.common.ErrorResponse;
import com.securebank.dto.transaction.TransferRequest;
import com.securebank.dto.transaction.TransferResponse;
import com.securebank.security.AuthenticatedCustomer;
import com.securebank.service.money.MoneyOperationCommand;
import com.securebank.service.money.MoneyOperationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Idempotent fund transfers between accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class TransferController {

    private final MoneyOperationFacade moneyOperationFacade;

    public TransferController(MoneyOperationFacade moneyOperationFacade) {
        this.moneyOperationFacade = moneyOperationFacade;
    }

    @PostMapping
    @Operation(summary = "Transfer money from one of my accounts to any active account",
            description = "Idempotency-Key is required. Sending the same key and body again returns the original "
                    + "result with header Idempotent-Replayed: true and moves no money.")
    @ApiResponse(responseCode = "201", description = "Transfer completed (or replayed)")
    @ApiResponse(responseCode = "400", description = "Validation failed or Idempotency-Key missing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Source account belongs to another customer",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Concurrent update, or same key still processing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Insufficient balance, inactive account, same account, "
            + "or key reused with a different body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TransferResponse> transfer(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @Parameter(description = "Unique key per transfer, e.g. a UUID", required = true)
            @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return ApiHeaders.created(moneyOperationFacade.transfer(
                MoneyOperationCommand.transfer(principal.id(), request, idempotencyKey)));
    }
}
