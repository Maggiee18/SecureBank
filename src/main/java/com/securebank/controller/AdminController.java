package com.securebank.controller;

import com.securebank.config.OpenApiConfig;
import com.securebank.dto.account.AccountResponse;
import com.securebank.dto.admin.AdminAccountResponse;
import com.securebank.dto.admin.AuditLogResponse;
import com.securebank.dto.admin.UpdateAccountStatusRequest;
import com.securebank.dto.common.PageResponse;
import com.securebank.entity.AccountStatus;
import com.securebank.security.AuthenticatedCustomer;
import com.securebank.service.AccountService;
import com.securebank.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations-team endpoints. Protected twice: the URL rule in SecurityConfig and
 * @PreAuthorize here, so moving this controller to another path cannot silently open it up.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Operations endpoints (ADMIN role only)")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class AdminController {

    private final AccountService accountService;
    private final AuditService auditService;

    public AdminController(AccountService accountService, AuditService auditService) {
        this.accountService = accountService;
        this.auditService = auditService;
    }

    @GetMapping("/accounts")
    @Operation(summary = "List all accounts with their owners, optionally filtered by status")
    public PageResponse<AdminAccountResponse> getAccounts(
            @RequestParam(required = false) AccountStatus status,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return accountService.listAllAccounts(status, pageable);
    }

    @PatchMapping("/accounts/{accountNumber}/status")
    @Operation(summary = "Block, unblock or close an account")
    public AccountResponse changeAccountStatus(
            @AuthenticationPrincipal AuthenticatedCustomer admin,
            @PathVariable String accountNumber,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return accountService.changeStatus(admin.id(), accountNumber, request);
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Search audit logs, optionally for one customer")
    public PageResponse<AuditLogResponse> getAuditLogs(
            @RequestParam(required = false) Long customerId,
            @ParameterObject
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.search(customerId, pageable);
    }
}
