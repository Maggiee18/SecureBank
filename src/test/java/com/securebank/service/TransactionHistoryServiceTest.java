package com.securebank.service;

import com.securebank.TestFixtures;
import com.securebank.dto.common.PageResponse;
import com.securebank.dto.transaction.TransactionResponse;
import com.securebank.entity.Account;
import com.securebank.entity.BankTransaction;
import com.securebank.entity.Customer;
import com.securebank.entity.TransactionStatus;
import com.securebank.entity.TransactionType;
import com.securebank.exception.InvalidRequestException;
import com.securebank.repository.BankTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private BankTransactionRepository transactionRepository;

    @InjectMocks
    private TransactionHistoryService service;

    private final Customer me = TestFixtures.customer(1L);
    private final Customer other = TestFixtures.customer(2L);

    @Test
    void returnsPageWithMetadataAndDirectionRelativeToViewedAccount() {
        Account mine = TestFixtures.account(10L, "502100000001", me, "0.00");
        Account theirs = TestFixtures.account(20L, "502100000002", other, "0.00");
        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<BankTransaction> rows = List.of(
                BankTransaction.success("TXN-1", TransactionType.TRANSFER, new BigDecimal("100.00"), mine, theirs,
                        null, 1L, null),
                BankTransaction.success("TXN-2", TransactionType.TRANSFER, new BigDecimal("50.00"), theirs, mine,
                        null, 2L, null));
        when(accountService.getOwnedAccount(1L, "502100000001")).thenReturn(mine);
        when(transactionRepository.findStatement(10L, TransactionStatus.SUCCESS, pageable))
                .thenReturn(new PageImpl<>(rows, pageable, 5));

        PageResponse<TransactionResponse> page = service.getStatement(1L, "502100000001", pageable);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.first()).isFalse();
        assertThat(page.last()).isFalse();
        assertThat(page.content()).extracting(TransactionResponse::direction).containsExactly("DEBIT", "CREDIT");
        assertThat(page.content().get(0).counterpartyAccount()).isEqualTo("XXXXXXXX0002");
    }

    @Test
    void sortingOnUnsupportedFieldIsRejected() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("sourceAccount.customer.passwordHash"));

        assertThatThrownBy(() -> service.getStatement(1L, "502100000001", pageable))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(transactionRepository);
    }
}
