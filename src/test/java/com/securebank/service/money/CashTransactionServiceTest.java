package com.securebank.service.money;

import com.securebank.TestFixtures;
import com.securebank.dto.transaction.MoneyOperationRequest;
import com.securebank.dto.transaction.MoneyOperationResponse;
import com.securebank.entity.Account;
import com.securebank.entity.AccountStatus;
import com.securebank.entity.AuditAction;
import com.securebank.entity.BankTransaction;
import com.securebank.entity.Customer;
import com.securebank.entity.TransactionStatus;
import com.securebank.entity.TransactionType;
import com.securebank.exception.AccountNotActiveException;
import com.securebank.exception.InsufficientBalanceException;
import com.securebank.exception.UnauthorizedAccountAccessException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.BankTransactionRepository;
import com.securebank.service.AccountService;
import com.securebank.service.AuditService;
import com.securebank.util.TransactionReferenceGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashTransactionServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final String ACCOUNT_NUMBER = "502100000001";

    @Mock
    private AccountService accountService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BankTransactionRepository transactionRepository;
    @Mock
    private TransactionReferenceGenerator referenceGenerator;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CashTransactionService service;

    private final Customer owner = TestFixtures.customer(CUSTOMER_ID);

    @Test
    void depositCreditsAccountAndRecordsSuccessfulTransaction() {
        Account account = TestFixtures.account(10L, ACCOUNT_NUMBER, owner, "1000.00");
        when(accountService.getOwnedAccount(CUSTOMER_ID, ACCOUNT_NUMBER)).thenReturn(account);
        when(referenceGenerator.next()).thenReturn("TXN-20261006-AAAAAA");
        when(transactionRepository.save(any(BankTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MoneyOperationResponse response = service.deposit(deposit("2500.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("3500.50");
        assertThat(response.balanceAfter()).isEqualByComparingTo("3500.50");
        assertThat(response.transactionReference()).isEqualTo("TXN-20261006-AAAAAA");
        ArgumentCaptor<BankTransaction> captor = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionRepository).save(captor.capture());
        BankTransaction saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.getDestinationAccount()).isSameAs(account);
        assertThat(saved.getSourceAccount()).isNull();
        verify(accountRepository).saveAndFlush(account);
        verify(auditService).recordSuccess(eq(CUSTOMER_ID), eq(AuditAction.DEPOSIT), anyString(),
                eq(ACCOUNT_NUMBER), anyString());
    }

    @Test
    void depositIntoBlockedAccountIsRejectedWithoutSideEffects() {
        Account account = TestFixtures.account(10L, ACCOUNT_NUMBER, owner, "1000.00", AccountStatus.BLOCKED);
        when(accountService.getOwnedAccount(CUSTOMER_ID, ACCOUNT_NUMBER)).thenReturn(account);

        assertThatThrownBy(() -> service.deposit(deposit("100.00"))).isInstanceOf(AccountNotActiveException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void withdrawalDebitsAccount() {
        Account account = TestFixtures.account(10L, ACCOUNT_NUMBER, owner, "1000.00");
        when(accountService.getOwnedAccount(CUSTOMER_ID, ACCOUNT_NUMBER)).thenReturn(account);
        when(referenceGenerator.next()).thenReturn("TXN-20261006-BBBBBB");
        when(transactionRepository.save(any(BankTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MoneyOperationResponse response = service.withdraw(withdrawal("400.00"));

        assertThat(response.balanceAfter()).isEqualByComparingTo("600.00");
        assertThat(response.type()).isEqualTo("WITHDRAWAL");
    }

    @Test
    void withdrawalBeyondBalanceIsRejectedAndNothingIsSaved() {
        Account account = TestFixtures.account(10L, ACCOUNT_NUMBER, owner, "300.00");
        when(accountService.getOwnedAccount(CUSTOMER_ID, ACCOUNT_NUMBER)).thenReturn(account);

        assertThatThrownBy(() -> service.withdraw(withdrawal("300.01")))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("300.00");
        verify(accountRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdrawalFromSomeoneElsesAccountIsRejected() {
        when(accountService.getOwnedAccount(CUSTOMER_ID, ACCOUNT_NUMBER))
                .thenThrow(new UnauthorizedAccountAccessException(ACCOUNT_NUMBER));

        assertThatThrownBy(() -> service.withdraw(withdrawal("10.00")))
                .isInstanceOf(UnauthorizedAccountAccessException.class);
        verifyNoInteractions(accountRepository, transactionRepository, auditService);
    }

    private static MoneyOperationCommand deposit(String amount) {
        return MoneyOperationCommand.deposit(CUSTOMER_ID, ACCOUNT_NUMBER,
                new MoneyOperationRequest(new BigDecimal(amount), "test"), null);
    }

    private static MoneyOperationCommand withdrawal(String amount) {
        return MoneyOperationCommand.withdrawal(CUSTOMER_ID, ACCOUNT_NUMBER,
                new MoneyOperationRequest(new BigDecimal(amount), "test"), null);
    }
}
