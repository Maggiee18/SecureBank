package com.securebank.entity;

import com.securebank.exception.AccountNotActiveException;
import com.securebank.exception.InsufficientBalanceException;
import com.securebank.exception.InvalidAccountStatusChangeException;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A bank account. Balance changes only happen through {@link #debit} and {@link #credit},
 * so the "no overdraft" and "only active accounts move money" rules live in one place.
 */
@Entity
@Table(name = "accounts")
public class Account {

    public static final String DEFAULT_CURRENCY = "INR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 12)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Optimistic lock. Every UPDATE runs "... WHERE id = ? AND version = ?"; if another
     * transaction changed the row first, zero rows match and Hibernate raises a conflict
     * instead of silently overwriting the other transaction's balance.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // required by JPA
    }

    public Account(String accountNumber, Customer customer, AccountType accountType) {
        this.accountNumber = accountNumber;
        this.customer = customer;
        this.accountType = accountType;
        this.status = AccountStatus.ACTIVE;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.currency = DEFAULT_CURRENCY;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void assertActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(accountNumber, status);
        }
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        assertActive();
        if (!hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException(accountNumber);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        assertActive();
        balance = balance.add(amount);
    }

    public void changeStatus(AccountStatus newStatus) {
        if (status == AccountStatus.CLOSED) {
            throw new InvalidAccountStatusChangeException("A closed account cannot be reopened or changed");
        }
        if (status == newStatus) {
            throw new InvalidAccountStatusChangeException("Account is already " + newStatus);
        }
        if (newStatus == AccountStatus.CLOSED && balance.signum() != 0) {
            throw new InvalidAccountStatusChangeException("Only an account with zero balance can be closed");
        }
        status = newStatus;
    }

    public boolean isOwnedBy(Long customerId) {
        return customer.getId().equals(customerId);
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
