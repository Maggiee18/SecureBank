package com.securebank.service.money;

import com.securebank.dto.transaction.TransferResponse;
import com.securebank.entity.Account;
import com.securebank.entity.AuditAction;
import com.securebank.entity.BankTransaction;
import com.securebank.entity.TransactionType;
import com.securebank.exception.InsufficientBalanceException;
import com.securebank.exception.InvalidTransactionException;
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

import java.math.BigDecimal;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final TransactionReferenceGenerator referenceGenerator;
    private final AuditService auditService;

    public TransferService(AccountService accountService, AccountRepository accountRepository,
                           BankTransactionRepository transactionRepository,
                           TransactionReferenceGenerator referenceGenerator, AuditService auditService) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.referenceGenerator = referenceGenerator;
        this.auditService = auditService;
    }

    /**
     * Moves money between two accounts atomically. The transaction boundary is here, at the
     * service layer, because this is the smallest unit that must succeed or fail as a whole:
     * debit, credit, transaction record and audit row. Any RuntimeException thrown before commit
     * makes Spring roll all of it back.
     */
    @Transactional
    public TransferResponse transfer(MoneyOperationCommand command) {
        Account source = accountService.getOwnedAccount(command.customerId(), command.sourceAccountNumber());
        if (command.sourceAccountNumber().equals(command.destinationAccountNumber())) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }
        Account destination = accountService.findByNumber(command.destinationAccountNumber());

        // Validate everything before changing anything.
        source.assertActive();
        destination.assertActive();
        if (!source.hasSufficientBalance(command.amount())) {
            throw new InsufficientBalanceException(source.getAccountNumber());
        }

        applyInLockOrder(source, destination, command.amount());

        BankTransaction transaction = transactionRepository.save(BankTransaction.success(
                referenceGenerator.next(), TransactionType.TRANSFER, command.amount(), source, destination,
                command.description(), command.customerId(), RequestContext.requestId()));
        auditService.recordSuccess(command.customerId(), AuditAction.TRANSFER, "ACCOUNT", source.getAccountNumber(),
                "Transfer " + command.amount() + " to " + destination.getAccountNumber()
                        + " ref " + transaction.getTransactionReference());
        log.info("TRANSFER_SUCCESS ref={} from={} to={} amount={}", transaction.getTransactionReference(),
                source.getAccountNumber(), destination.getAccountNumber(), command.amount());

        return new TransferResponse(
                transaction.getTransactionReference(),
                transaction.getStatus().name(),
                source.getAccountNumber(),
                destination.getAccountNumber(),
                transaction.getAmount(),
                source.getBalance(),
                transaction.getDescription(),
                transaction.getCreatedAt());
    }

    /**
     * Updates the two rows in ascending id order. If A->B and B->A run at the same time, both
     * transactions try to lock the lower id first, so they queue instead of deadlocking.
     * Each flush also runs the optimistic version check for that row.
     */
    private void applyInLockOrder(Account source, Account destination, BigDecimal amount) {
        if (source.getId() < destination.getId()) {
            source.debit(amount);
            accountRepository.saveAndFlush(source);
            destination.credit(amount);
            accountRepository.saveAndFlush(destination);
        } else {
            destination.credit(amount);
            accountRepository.saveAndFlush(destination);
            source.debit(amount);
            accountRepository.saveAndFlush(source);
        }
    }
}
