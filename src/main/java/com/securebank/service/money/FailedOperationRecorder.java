package com.securebank.service.money;

import com.securebank.entity.Account;
import com.securebank.entity.BankTransaction;
import com.securebank.entity.TransactionType;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.BankTransactionRepository;
import com.securebank.util.TransactionReferenceGenerator;
import com.securebank.web.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a FAILED transaction row for a money movement that a business rule rejected.
 * Runs in its own transaction, after the business transaction has rolled back, so the
 * evidence of the attempt is not rolled back with it.
 */
@Service
public class FailedOperationRecorder {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final TransactionReferenceGenerator referenceGenerator;

    public FailedOperationRecorder(AccountRepository accountRepository,
                                   BankTransactionRepository transactionRepository,
                                   TransactionReferenceGenerator referenceGenerator) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.referenceGenerator = referenceGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(MoneyOperationCommand command, String reason) {
        // Only ever attach the failed record to an account the caller owns, so a failed attempt
        // can never show up in a stranger's statement.
        Account ownAccount = accountRepository.findByAccountNumber(command.primaryAccountNumber())
                .filter(account -> account.isOwnedBy(command.customerId()))
                .orElse(null);
        if (ownAccount == null) {
            return;
        }
        Account source = null;
        Account destination = null;
        if (command.type() == TransactionType.DEPOSIT) {
            destination = ownAccount;
        } else {
            source = ownAccount;
            if (command.type() == TransactionType.TRANSFER) {
                destination = accountRepository.findByAccountNumber(command.destinationAccountNumber()).orElse(null);
            }
        }
        transactionRepository.save(BankTransaction.failed(referenceGenerator.next(), command.type(),
                command.amount(), source, destination, command.description(), reason, command.customerId(),
                RequestContext.requestId()));
    }
}
