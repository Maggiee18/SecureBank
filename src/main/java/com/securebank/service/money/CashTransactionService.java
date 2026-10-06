package com.securebank.service.money;

import com.securebank.dto.transaction.MoneyOperationResponse;
import com.securebank.entity.Account;
import com.securebank.entity.AuditAction;
import com.securebank.entity.BankTransaction;
import com.securebank.entity.TransactionType;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.BankTransactionRepository;
import com.securebank.service.AccountService;
import com.securebank.service.AuditService;
import com.securebank.util.TransactionReferenceGenerator;
import com.securebank.web.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deposits and withdrawals on the caller's own account. Each method is one database
 * transaction: balance update, transaction record and audit row commit together or not at all.
 */
@Service
public class CashTransactionService {

    private static final Logger log = LoggerFactory.getLogger(CashTransactionService.class);

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final TransactionReferenceGenerator referenceGenerator;
    private final AuditService auditService;

    public CashTransactionService(AccountService accountService, AccountRepository accountRepository,
                                  BankTransactionRepository transactionRepository,
                                  TransactionReferenceGenerator referenceGenerator, AuditService auditService) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.referenceGenerator = referenceGenerator;
        this.auditService = auditService;
    }

    @Transactional
    public MoneyOperationResponse deposit(MoneyOperationCommand command) {
        Account account = accountService.getOwnedAccount(command.customerId(), command.destinationAccountNumber());
        account.credit(command.amount());
        // Flush now so an optimistic-lock conflict surfaces here, inside the service, not at commit.
        accountRepository.saveAndFlush(account);

        BankTransaction transaction = transactionRepository.save(BankTransaction.success(
                referenceGenerator.next(), TransactionType.DEPOSIT, command.amount(), null, account,
                command.description(), command.customerId(), RequestContext.requestId()));
        auditService.recordSuccess(command.customerId(), AuditAction.DEPOSIT, "ACCOUNT", account.getAccountNumber(),
                "Deposit " + command.amount() + " ref " + transaction.getTransactionReference());
        log.info("DEPOSIT_SUCCESS ref={} account={} amount={}", transaction.getTransactionReference(),
                account.getAccountNumber(), command.amount());
        return toResponse(transaction, account);
    }

    @Transactional
    public MoneyOperationResponse withdraw(MoneyOperationCommand command) {
        Account account = accountService.getOwnedAccount(command.customerId(), command.sourceAccountNumber());
        account.debit(command.amount());
        accountRepository.saveAndFlush(account);

        BankTransaction transaction = transactionRepository.save(BankTransaction.success(
                referenceGenerator.next(), TransactionType.WITHDRAWAL, command.amount(), account, null,
                command.description(), command.customerId(), RequestContext.requestId()));
        auditService.recordSuccess(command.customerId(), AuditAction.WITHDRAWAL, "ACCOUNT",
                account.getAccountNumber(),
                "Withdrawal " + command.amount() + " ref " + transaction.getTransactionReference());
        log.info("WITHDRAWAL_SUCCESS ref={} account={} amount={}", transaction.getTransactionReference(),
                account.getAccountNumber(), command.amount());
        return toResponse(transaction, account);
    }

    private static MoneyOperationResponse toResponse(BankTransaction transaction, Account account) {
        return new MoneyOperationResponse(
                transaction.getTransactionReference(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                account.getAccountNumber(),
                transaction.getAmount(),
                account.getBalance(),
                transaction.getDescription(),
                transaction.getCreatedAt());
    }
}
