package com.securebank.service;

import com.securebank.dto.account.AccountResponse;
import com.securebank.dto.account.BalanceResponse;
import com.securebank.dto.account.CreateAccountRequest;
import com.securebank.dto.admin.AdminAccountResponse;
import com.securebank.dto.admin.UpdateAccountStatusRequest;
import com.securebank.dto.common.PageResponse;
import com.securebank.entity.Account;
import com.securebank.entity.AccountStatus;
import com.securebank.entity.AuditAction;
import com.securebank.entity.Customer;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.exception.UnauthorizedAccountAccessException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.CustomerRepository;
import com.securebank.util.AccountNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String RESOURCE = "ACCOUNT";

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository,
                          AccountNumberGenerator accountNumberGenerator, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.auditService = auditService;
    }

    @Transactional
    public AccountResponse createAccount(Long customerId, CreateAccountRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", String.valueOf(customerId)));
        Account account = accountRepository.save(
                new Account(accountNumberGenerator.generate(), customer, request.accountType()));
        auditService.recordSuccess(customerId, AuditAction.ACCOUNT_CREATED, RESOURCE, account.getAccountNumber(),
                request.accountType() + " account opened");
        log.info("ACCOUNT_CREATED customerId={} account={}", customerId, account.getAccountNumber());
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(Long customerId) {
        return accountRepository.findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long customerId, String accountNumber) {
        return AccountResponse.from(getOwnedAccount(customerId, accountNumber));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long customerId, String accountNumber) {
        return BalanceResponse.from(getOwnedAccount(customerId, accountNumber));
    }

    /**
     * The single ownership check used by every account-level operation. Keeping it in one
     * place means a new endpoint cannot forget it by accident.
     */
    @Transactional(readOnly = true)
    public Account getOwnedAccount(Long customerId, String accountNumber) {
        Account account = findByNumber(accountNumber);
        if (!account.isOwnedBy(customerId)) {
            log.warn("ACCOUNT_ACCESS_DENIED customerId={} account={}", customerId, accountNumber);
            throw new UnauthorizedAccountAccessException(accountNumber);
        }
        return account;
    }

    @Transactional(readOnly = true)
    public Account findByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAccountResponse> listAllAccounts(AccountStatus status, Pageable pageable) {
        return PageResponse.from((status == null
                ? accountRepository.findAllBy(pageable)
                : accountRepository.findByStatus(status, pageable)).map(AdminAccountResponse::from));
    }

    @Transactional
    public AccountResponse changeStatus(Long adminId, String accountNumber, UpdateAccountStatusRequest request) {
        Account account = findByNumber(accountNumber);
        AccountStatus previous = account.getStatus();
        account.changeStatus(request.status());
        accountRepository.saveAndFlush(account);
        auditService.recordSuccess(adminId, AuditAction.ACCOUNT_STATUS_CHANGED, RESOURCE, accountNumber,
                previous + " -> " + request.status() + ": " + request.reason());
        log.info("ACCOUNT_STATUS_CHANGED account={} from={} to={} by={}", accountNumber, previous,
                request.status(), adminId);
        return AccountResponse.from(account);
    }
}
