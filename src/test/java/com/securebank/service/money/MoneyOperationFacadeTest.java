package com.securebank.service.money;

import com.securebank.dto.transaction.TransferRequest;
import com.securebank.dto.transaction.TransferResponse;
import com.securebank.entity.AuditAction;
import com.securebank.entity.TransactionType;
import com.securebank.exception.ConcurrentUpdateException;
import com.securebank.exception.InsufficientBalanceException;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.service.AuditService;
import com.securebank.service.idempotency.ClaimResult;
import com.securebank.service.idempotency.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoneyOperationFacadeTest {

    private static final String KEY = "8d9f7a21-4c1e-4b0a-9d55-0e7c1f2a3b4c";

    @Mock
    private CashTransactionService cashTransactionService;
    @Mock
    private TransferService transferService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private FailedOperationRecorder failedOperationRecorder;
    @Mock
    private AuditService auditService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private MoneyOperationFacade facade;
    private final MoneyOperationCommand command = MoneyOperationCommand.transfer(1L,
            new TransferRequest("502100000001", "502100000002", new BigDecimal("5000.00"), "Rent"), KEY);

    @BeforeEach
    void setUp() {
        facade = new MoneyOperationFacade(cashTransactionService, transferService, idempotencyService,
                failedOperationRecorder, auditService, transactionTemplate);
        // Run the callback directly, as a real TransactionTemplate would inside a transaction.
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void firstRequestExecutesTransferAndStoresResponseInSameTransaction() {
        TransferResponse response = sampleResponse();
        when(idempotencyService.claim(eq(1L), eq(KEY), eq(TransactionType.TRANSFER), anyString(),
                eq(TransferResponse.class))).thenReturn(ClaimResult.claimed(100L));
        when(transferService.transfer(command)).thenReturn(response);

        OperationOutcome<TransferResponse> outcome = facade.transfer(command);

        assertThat(outcome.replayed()).isFalse();
        assertThat(outcome.body()).isEqualTo(response);
        verify(idempotencyService).complete(100L, 201, response);
    }

    @Test
    void repeatedRequestWithSameKeyReturnsStoredResultWithoutMovingMoney() {
        TransferResponse stored = sampleResponse();
        when(idempotencyService.claim(eq(1L), eq(KEY), eq(TransactionType.TRANSFER), anyString(),
                eq(TransferResponse.class))).thenReturn(ClaimResult.replay(stored));

        OperationOutcome<TransferResponse> outcome = facade.transfer(command);

        assertThat(outcome.replayed()).isTrue();
        assertThat(outcome.body()).isEqualTo(stored);
        verifyNoInteractions(transferService, transactionTemplate);
    }

    @Test
    void businessRejectionReleasesKeyAndRecordsFailedTransaction() {
        when(idempotencyService.claim(any(), any(), any(), any(), any())).thenReturn(ClaimResult.claimed(100L));
        when(transferService.transfer(command)).thenThrow(new InsufficientBalanceException("502100000001"));

        assertThatThrownBy(() -> facade.transfer(command)).isInstanceOf(InsufficientBalanceException.class);

        verify(idempotencyService).release(100L);
        verify(idempotencyService, never()).complete(any(), anyInt(), any());
        verify(failedOperationRecorder).recordRejected(eq(command), anyString());
        verify(auditService).recordFailure(eq(1L), eq(AuditAction.TRANSFER), anyString(), eq("502100000001"),
                anyString());
    }

    @Test
    void notFoundIsAuditedButNotRecordedAsFailedTransaction() {
        when(idempotencyService.claim(any(), any(), any(), any(), any())).thenReturn(ClaimResult.claimed(100L));
        when(transferService.transfer(command)).thenThrow(new ResourceNotFoundException("Account", "502100000002"));

        assertThatThrownBy(() -> facade.transfer(command)).isInstanceOf(ResourceNotFoundException.class);

        verify(failedOperationRecorder, never()).recordRejected(any(), any());
        verify(auditService).recordFailure(eq(1L), eq(AuditAction.TRANSFER), anyString(), anyString(), anyString());
    }

    @Test
    void optimisticLockFailureIsTranslatedToConcurrentUpdate() {
        when(idempotencyService.claim(any(), any(), any(), any(), any())).thenReturn(ClaimResult.claimed(100L));
        when(transferService.transfer(command))
                .thenThrow(new ObjectOptimisticLockingFailureException("Account", 10L));

        assertThatThrownBy(() -> facade.transfer(command)).isInstanceOf(ConcurrentUpdateException.class);
        verify(idempotencyService).release(100L);
    }

    @Test
    void failureWhileRecordingFailureDoesNotHideOriginalError() {
        when(idempotencyService.claim(any(), any(), any(), any(), any())).thenReturn(ClaimResult.claimed(100L));
        when(transferService.transfer(command)).thenThrow(new InsufficientBalanceException("502100000001"));
        doThrow(new IllegalStateException("db down"))
                .when(failedOperationRecorder).recordRejected(any(), any());

        assertThatThrownBy(() -> facade.transfer(command)).isInstanceOf(InsufficientBalanceException.class);
    }

    private static TransferResponse sampleResponse() {
        return new TransferResponse("TXN-20261006-A8F42K", "SUCCESS", "502100000001", "502100000002",
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), "Rent", Instant.now());
    }
}
