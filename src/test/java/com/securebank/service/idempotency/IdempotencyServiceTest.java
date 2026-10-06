package com.securebank.service.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.dto.transaction.TransferResponse;
import com.securebank.entity.IdempotencyRecord;
import com.securebank.entity.TransactionType;
import com.securebank.exception.ErrorCode;
import com.securebank.exception.IdempotencyConflictException;
import com.securebank.exception.InvalidRequestException;
import com.securebank.repository.IdempotencyRecordRepository;
import com.securebank.service.money.MoneyOperationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final String KEY = "8d9f7a21-4c1e-4b0a";
    private static final String HASH = RequestHasher.sha256("TRANSFER|a|b|5000|Rent");

    @Mock
    private IdempotencyRecordRepository repository;
    // A mocked transaction manager makes TransactionTemplate simply run its callback.
    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, objectMapper, transactionManager);
    }

    @Test
    void newKeyIsClaimed() {
        when(repository.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 55L);
            return record;
        });

        ClaimResult<TransferResponse> result = service.claim(CUSTOMER_ID, KEY, TransactionType.TRANSFER, HASH,
                TransferResponse.class);

        assertThat(result.isReplay()).isFalse();
        assertThat(result.recordId()).isEqualTo(55L);
    }

    @Test
    void completedKeyWithSameRequestReplaysStoredResponse() throws Exception {
        TransferResponse original = new TransferResponse("TXN-20261006-A8F42K", "SUCCESS", "502100000001",
                "502100000002", new BigDecimal("5000.00"), new BigDecimal("5000.00"), "Rent",
                Instant.parse("2026-10-06T10:00:00Z"));
        IdempotencyRecord existing = new IdempotencyRecord(KEY, CUSTOMER_ID, TransactionType.TRANSFER, HASH);
        existing.complete(201, objectMapper.writeValueAsString(original));
        givenKeyAlreadyExists(existing);

        ClaimResult<TransferResponse> result = service.claim(CUSTOMER_ID, KEY, TransactionType.TRANSFER, HASH,
                TransferResponse.class);

        assertThat(result.isReplay()).isTrue();
        assertThat(result.replayResponse()).isEqualTo(original);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        givenKeyAlreadyExists(new IdempotencyRecord(KEY, CUSTOMER_ID, TransactionType.TRANSFER,
                RequestHasher.sha256("TRANSFER|a|b|9999|Rent")));

        assertThatThrownBy(() -> service.claim(CUSTOMER_ID, KEY, TransactionType.TRANSFER, HASH,
                TransferResponse.class))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting(ex -> ((IdempotencyConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void sameKeyStillInProgressIsRejectedWithConflict() {
        givenKeyAlreadyExists(new IdempotencyRecord(KEY, CUSTOMER_ID, TransactionType.TRANSFER, HASH));

        assertThatThrownBy(() -> service.claim(CUSTOMER_ID, KEY, TransactionType.TRANSFER, HASH,
                TransferResponse.class))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting(ex -> ((IdempotencyConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    @Test
    void concurrentRequestThatLosesTheInsertRaceIsToldTheKeyIsInProgress() {
        // Both requests saw no record; the other one inserted first and the unique constraint fired for us.
        when(repository.findByCustomerIdAndIdempotencyKey(CUSTOMER_ID, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyRecord(KEY, CUSTOMER_ID, TransactionType.TRANSFER, HASH)));
        when(repository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("uk_idempotency_customer_key"));

        assertThatThrownBy(() -> service.claim(CUSTOMER_ID, KEY, TransactionType.TRANSFER, HASH,
                TransferResponse.class))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting(ex -> ((IdempotencyConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    @Test
    void malformedKeyIsRejectedBeforeTouchingDatabase() {
        assertThatThrownBy(() -> service.claim(CUSTOMER_ID, "bad key!", TransactionType.TRANSFER, HASH,
                TransferResponse.class))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).findByCustomerIdAndIdempotencyKey(any(), any());
    }

    @Test
    void equivalentAmountsProduceSameFingerprint() {
        String a = new MoneyOperationCommand(1L, TransactionType.TRANSFER, "1", "2",
                new BigDecimal("100"), null, KEY).fingerprint();
        String b = new MoneyOperationCommand(1L, TransactionType.TRANSFER, "1", "2",
                new BigDecimal("100.00"), null, KEY).fingerprint();

        assertThat(RequestHasher.sha256(a)).isEqualTo(RequestHasher.sha256(b));
    }

    private void givenKeyAlreadyExists(IdempotencyRecord existing) {
        when(repository.findByCustomerIdAndIdempotencyKey(CUSTOMER_ID, KEY)).thenReturn(Optional.of(existing));
    }
}
