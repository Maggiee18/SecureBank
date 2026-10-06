package com.securebank.controller;

import com.securebank.config.OpenApiConfig;
import com.securebank.dto.account.AccountResponse;
import com.securebank.dto.account.BalanceResponse;
import com.securebank.dto.account.CreateAccountRequest;
import com.securebank.dto.common.ErrorResponse;
import com.securebank.dto.common.PageResponse;
import com.securebank.dto.transaction.MoneyOperationRequest;
import com.securebank.dto.transaction.MoneyOperationResponse;
import com.securebank.dto.transaction.TransactionResponse;
import com.securebank.security.AuthenticatedCustomer;
import com.securebank.service.AccountService;
import com.securebank.service.TransactionHistoryService;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Accounts, balances, deposits, withdrawals and statements")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class AccountController {

    private final AccountService accountService;
    private final MoneyOperationFacade moneyOperationFacade;
    private final TransactionHistoryService transactionHistoryService;

    public AccountController(AccountService accountService, MoneyOperationFacade moneyOperationFacade,
                             TransactionHistoryService transactionHistoryService) {
        this.accountService = accountService;
        this.moneyOperationFacade = moneyOperationFacade;
        this.transactionHistoryService = transactionHistoryService;
    }

    @PostMapping
    @Operation(summary = "Open a new account")
    @ApiResponse(responseCode = "201", description = "Account created")
    public ResponseEntity<AccountResponse> createAccount(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                                         @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = accountService.createAccount(principal.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.accountNumber())).body(account);
    }

    @GetMapping
    @Operation(summary = "List my accounts")
    public List<AccountResponse> listAccounts(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        return accountService.listAccounts(principal.id());
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get one of my accounts")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "403", description = "Account belongs to another customer",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AccountResponse getAccount(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                      @PathVariable String accountNumber) {
        return accountService.getAccount(principal.id(), accountNumber);
    }

    @GetMapping("/{accountNumber}/balance")
    @Operation(summary = "Get the current balance of one of my accounts")
    public BalanceResponse getBalance(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                      @PathVariable String accountNumber) {
        return accountService.getBalance(principal.id(), accountNumber);
    }

    @PostMapping("/{accountNumber}/deposit")
    @Operation(summary = "Deposit money into one of my accounts")
    @ApiResponse(responseCode = "201", description = "Deposit recorded")
    @ApiResponse(responseCode = "422", description = "Account blocked or closed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<MoneyOperationResponse> deposit(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable String accountNumber,
            @Parameter(description = "Optional. Reuse the same key when retrying to avoid a double deposit")
            @RequestHeader(value = ApiHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody MoneyOperationRequest request) {
        return ApiHeaders.created(moneyOperationFacade.deposit(
                MoneyOperationCommand.deposit(principal.id(), accountNumber, request, idempotencyKey)));
    }

    @PostMapping("/{accountNumber}/withdraw")
    @Operation(summary = "Withdraw money from one of my accounts")
    @ApiResponse(responseCode = "201", description = "Withdrawal recorded")
    @ApiResponse(responseCode = "409", description = "Concurrent update, retry",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Insufficient balance, or account blocked or closed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<MoneyOperationResponse> withdraw(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable String accountNumber,
            @Parameter(description = "Optional. Reuse the same key when retrying to avoid a double withdrawal")
            @RequestHeader(value = ApiHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody MoneyOperationRequest request) {
        return ApiHeaders.created(moneyOperationFacade.withdraw(
                MoneyOperationCommand.withdrawal(principal.id(), accountNumber, request, idempotencyKey)));
    }

    @GetMapping("/{accountNumber}/transactions")
    @Operation(summary = "Paginated statement for one of my accounts",
            description = "Sortable by createdAt or amount, e.g. ?page=0&size=10&sort=createdAt,desc. Max page size 100.")
    public PageResponse<TransactionResponse> getTransactions(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable String accountNumber,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionHistoryService.getStatement(principal.id(), accountNumber, pageable);
    }
}
