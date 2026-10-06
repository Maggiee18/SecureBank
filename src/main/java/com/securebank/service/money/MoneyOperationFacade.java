package com.securebank.service.money;

import com.securebank.dto.transaction.MoneyOperationResponse;
import com.securebank.dto.transaction.TransferResponse;
import com.securebank.entity.AuditAction;
import com.securebank.exception.BankingException;
import com.securebank.exception.ConcurrentUpdateException;
import com.securebank.exception.TransactionRejectedException;
import com.securebank.service.AuditService;
import com.securebank.service.idempotency.ClaimResult;
import com.securebank.service.idempotency.IdempotencyService;
import com.securebank.service.idempotency.RequestHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Entry point for every money movement. Owns everything that must happen OUTSIDE the
 * business transaction:
 * <ul>
 *   <li>idempotency claim before it starts and replay of earlier results,</li>
 *   <li>opening the transaction (so the idempotency record completes atomically with the money movement),</li>
 *   <li>after a rollback: releasing the claim, recording the failure, translating lock conflicts.</li>
 * </ul>
 * This class itself is deliberately not @Transactional.
 */
@Service
public class MoneyOperationFacade {

    private static final Logger log = LoggerFactory.getLogger(MoneyOperationFacade.class);

    private final CashTransactionService cashTransactionService;
    private final TransferService transferService;
    private final IdempotencyService idempotencyService;
    private final FailedOperationRecorder failedOperationRecorder;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public MoneyOperationFacade(CashTransactionService cashTransactionService, TransferService transferService,
                                IdempotencyService idempotencyService, FailedOperationRecorder failedOperationRecorder,
                                AuditService auditService, TransactionTemplate transactionTemplate) {
        this.cashTransactionService = cashTransactionService;
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
        this.failedOperationRecorder = failedOperationRecorder;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    public OperationOutcome<MoneyOperationResponse> deposit(MoneyOperationCommand command) {
        return execute(command, MoneyOperationResponse.class, () -> cashTransactionService.deposit(command));
    }

    public OperationOutcome<MoneyOperationResponse> withdraw(MoneyOperationCommand command) {
        return execute(command, MoneyOperationResponse.class, () -> cashTransactionService.withdraw(command));
    }

    public OperationOutcome<TransferResponse> transfer(MoneyOperationCommand command) {
        return execute(command, TransferResponse.class, () -> transferService.transfer(command));
    }

    private <T> OperationOutcome<T> execute(MoneyOperationCommand command, Class<T> responseType, Supplier<T> work) {
        log.info("{}_STARTED account={} amount={}", command.type(), command.primaryAccountNumber(), command.amount());

        Long claimId = null;
        if (command.idempotencyKey() != null) {
            ClaimResult<T> claim = idempotencyService.claim(command.customerId(), command.idempotencyKey(),
                    command.type(), RequestHasher.sha256(command.fingerprint()), responseType);
            if (claim.isReplay()) {
                log.info("IDEMPOTENCY_REPLAY type={} account={}", command.type(), command.primaryAccountNumber());
                return OperationOutcome.replayed(claim.replayResponse());
            }
            claimId = claim.recordId();
        }

        final Long recordId = claimId;
        try {
            T response = transactionTemplate.execute(status -> {
                T result = work.get();
                if (recordId != null) {
                    idempotencyService.complete(recordId, HttpStatus.CREATED.value(), result);
                }
                return result;
            });
            log.info("{}_COMPLETED account={}", command.type(), command.primaryAccountNumber());
            return OperationOutcome.executed(response);
        } catch (RuntimeException ex) {
            // By the time we are here the business transaction has already rolled back.
            RuntimeException failure = translate(ex);
            if (recordId != null) {
                idempotencyService.release(recordId);
            }
            recordFailure(command, failure);
            throw failure;
        }
    }

    private RuntimeException translate(RuntimeException ex) {
        if (ex instanceof ConcurrencyFailureException) {
            log.warn("CONCURRENCY_CONFLICT cause={}", ex.getClass().getSimpleName());
            return new ConcurrentUpdateException();
        }
        return ex;
    }

    private void recordFailure(MoneyOperationCommand command, RuntimeException failure) {
        String reason = failure instanceof BankingException banking
                ? banking.getErrorCode() + ": " + banking.getMessage()
                : "Unexpected error";
        try {
            if (failure instanceof TransactionRejectedException) {
                failedOperationRecorder.recordRejected(command, reason);
            }
            auditService.recordFailure(command.customerId(), auditActionFor(command), "ACCOUNT",
                    command.primaryAccountNumber(), reason);
        } catch (RuntimeException recordingError) {
            // Never let a failure to write the failure record hide the original error from the client.
            log.error("FAILURE_RECORDING_FAILED type={}", command.type(), recordingError);
        }
        log.warn("{}_FAILED account={} reason={}", command.type(), command.primaryAccountNumber(), reason);
    }

    private static AuditAction auditActionFor(MoneyOperationCommand command) {
        return switch (command.type()) {
            case DEPOSIT -> AuditAction.DEPOSIT;
            case WITHDRAWAL -> AuditAction.WITHDRAWAL;
            case TRANSFER -> AuditAction.TRANSFER;
        };
    }
}
