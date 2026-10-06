package com.securebank.service.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.entity.IdempotencyRecord;
import com.securebank.entity.TransactionType;
import com.securebank.exception.IdempotencyConflictException;
import com.securebank.exception.InvalidRequestException;
import com.securebank.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Guarantees that one Idempotency-Key moves money at most once per customer.
 *
 * <ol>
 *   <li>{@link #claim} inserts an IN_PROGRESS row in its own short transaction. The unique
 *       constraint (customer_id, idempotency_key) means only one concurrent request can win;
 *       the database decides, so there is no check-then-insert race.</li>
 *   <li>The winner runs the operation and calls {@link #complete} inside the same transaction
 *       as the money movement. Either both the transfer and the stored response commit, or neither.</li>
 *   <li>If the operation fails, {@link #release} deletes the claim so the client can retry
 *       with the same key once the problem (for example, low balance) is fixed.</li>
 * </ol>
 */
@Service
public class IdempotencyService {

    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTransaction;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> ClaimResult<T> claim(Long customerId, String key, TransactionType operation, String requestHash,
                                    Class<T> responseType) {
        if (!VALID_KEY.matcher(key).matches()) {
            throw new InvalidRequestException(
                    "Idempotency-Key must be 8 to 100 characters of letters, digits, '-' or '_'");
        }
        // Fast path for ordinary retries: the key is already there, no insert attempt needed.
        Optional<IdempotencyRecord> existing = repository.findByCustomerIdAndIdempotencyKey(customerId, key);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash, responseType);
        }
        try {
            Long recordId = newTransaction.execute(status -> repository
                    .saveAndFlush(new IdempotencyRecord(key, customerId, operation, requestHash))
                    .getId());
            return ClaimResult.claimed(recordId);
        } catch (DataIntegrityViolationException lostTheRace) {
            // Another request with the same key inserted first; the unique constraint decided.
            IdempotencyRecord winner = repository.findByCustomerIdAndIdempotencyKey(customerId, key)
                    // The winner failed and released the key between our insert and this read.
                    .orElseThrow(IdempotencyConflictException::requestInProgress);
            return resolveExisting(winner, requestHash, responseType);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long recordId, int httpStatus, Object response) {
        IdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("Idempotency record disappeared: " + recordId));
        record.complete(httpStatus, toJson(response));
    }

    public void release(Long recordId) {
        newTransaction.executeWithoutResult(status -> repository.deleteById(recordId));
    }

    private <T> ClaimResult<T> resolveExisting(IdempotencyRecord existing, String requestHash, Class<T> responseType) {
        if (!existing.matchesRequest(requestHash)) {
            throw IdempotencyConflictException.keyReusedWithDifferentRequest();
        }
        if (!existing.isCompleted()) {
            throw IdempotencyConflictException.requestInProgress();
        }
        return ClaimResult.replay(fromJson(existing.getResponseBody(), responseType));
    }

    private String toJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise response for idempotency store", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read stored idempotent response", ex);
        }
    }
}
