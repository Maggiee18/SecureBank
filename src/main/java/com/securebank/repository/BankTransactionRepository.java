package com.securebank.repository;

import com.securebank.entity.BankTransaction;
import com.securebank.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    /**
     * Statement for one account: everything it sent (including failed attempts) plus successful
     * money it received. Failed incoming transfers are hidden because they belong to the payer's
     * history, not the payee's.
     *
     * Both accounts are fetch-joined so mapping a page of N rows costs one query instead of 1 + 2N.
     * The separate count query avoids joins it does not need.
     */
    @Query(value = """
            select t from BankTransaction t
            left join fetch t.sourceAccount
            left join fetch t.destinationAccount
            where t.sourceAccount.id = :accountId
               or (t.destinationAccount.id = :accountId and t.status = :visibleIncomingStatus)
            """,
            countQuery = """
            select count(t) from BankTransaction t
            where t.sourceAccount.id = :accountId
               or (t.destinationAccount.id = :accountId and t.status = :visibleIncomingStatus)
            """)
    Page<BankTransaction> findStatement(@Param("accountId") Long accountId,
                                        @Param("visibleIncomingStatus") TransactionStatus visibleIncomingStatus,
                                        Pageable pageable);
}
