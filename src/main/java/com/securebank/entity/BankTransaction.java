package com.securebank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable record of one money movement attempt. A deposit has only a destination,
 * a withdrawal only a source, a transfer has both.
 */
@Entity
@Table(name = "bank_transactions")
public class BankTransaction {

    private static final int MAX_FAILURE_REASON_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 30, updatable = false)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private TransactionStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", updatable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id", updatable = false)
    private Account destinationAccount;

    @Column(name = "description", length = 140, updatable = false)
    private String description;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON_LENGTH, updatable = false)
    private String failureReason;

    @Column(name = "initiated_by", nullable = false, updatable = false)
    private Long initiatedBy;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BankTransaction() {
        // required by JPA
    }

    private BankTransaction(String transactionReference, TransactionType type, TransactionStatus status,
                            BigDecimal amount, Account sourceAccount, Account destinationAccount,
                            String description, String failureReason, Long initiatedBy, String requestId) {
        this.transactionReference = transactionReference;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.description = description;
        this.failureReason = truncate(failureReason);
        this.initiatedBy = initiatedBy;
        this.requestId = requestId;
    }

    public static BankTransaction success(String reference, TransactionType type, BigDecimal amount,
                                          Account source, Account destination, String description,
                                          Long initiatedBy, String requestId) {
        return new BankTransaction(reference, type, TransactionStatus.SUCCESS, amount, source, destination,
                description, null, initiatedBy, requestId);
    }

    public static BankTransaction failed(String reference, TransactionType type, BigDecimal amount,
                                         Account source, Account destination, String description,
                                         String failureReason, Long initiatedBy, String requestId) {
        return new BankTransaction(reference, type, TransactionStatus.FAILED, amount, source, destination,
                description, failureReason, initiatedBy, requestId);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_FAILURE_REASON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Account getDestinationAccount() {
        return destinationAccount;
    }

    public String getDescription() {
        return description;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Long getInitiatedBy() {
        return initiatedBy;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
