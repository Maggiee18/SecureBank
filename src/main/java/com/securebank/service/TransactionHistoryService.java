package com.securebank.service;

import com.securebank.dto.common.PageResponse;
import com.securebank.dto.transaction.TransactionResponse;
import com.securebank.entity.Account;
import com.securebank.entity.TransactionStatus;
import com.securebank.exception.InvalidRequestException;
import com.securebank.repository.BankTransactionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class TransactionHistoryService {

    /**
     * Only these fields may be sorted on. An open sort parameter would let clients sort by
     * unindexed or nested columns (slow queries) or trigger errors with unknown property names.
     */
    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "amount");

    private final AccountService accountService;
    private final BankTransactionRepository transactionRepository;

    public TransactionHistoryService(AccountService accountService, BankTransactionRepository transactionRepository) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getStatement(Long customerId, String accountNumber, Pageable pageable) {
        validateSort(pageable.getSort());
        Account account = accountService.getOwnedAccount(customerId, accountNumber);
        Long accountId = account.getId();
        return PageResponse.from(transactionRepository
                .findStatement(accountId, TransactionStatus.SUCCESS, pageable)
                .map(transaction -> TransactionResponse.from(transaction, accountId)));
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed: " + SORTABLE_FIELDS);
            }
        }
    }
}
