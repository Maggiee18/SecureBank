package com.securebank.repository;

import com.securebank.entity.Account;
import com.securebank.entity.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    /** Admin listing: the owner is fetched in the same query so a page of N accounts is one query, not N + 1. */
    @EntityGraph(attributePaths = "customer")
    Page<Account> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Page<Account> findByStatus(AccountStatus status, Pageable pageable);
}
