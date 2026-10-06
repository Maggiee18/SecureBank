package com.securebank.service.money;

import com.securebank.TestFixtures;
import com.securebank.dto.transaction.TransferRequest;
import com.securebank.dto.transaction.TransferResponse;
import com.securebank.entity.Account;
import com.securebank.entity.AccountStatus;
import com.securebank.entity.BankTransaction;
import com.securebank.entity.Customer;
import com.securebank.entity.TransactionType;
import com.securebank.exception.AccountNotActiveException;
import com.securebank.exception.InsufficientBalanceException;
import com.securebank.exception.InvalidTransactionException;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.exception.UnauthorizedAccountAccessException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.BankTransactionRepository;
import com.securebank.service.AccountService;
import com.securebank.service.AuditService;
import com.securebank.util.TransactionReferenceGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final Long SENDER_ID = 1L;
    private static final String SOURCE = "502100000001";
    private static final String DESTINATION = "502100000002";

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
    private TransferService transferService;

    private final Customer sender = TestFixtures.customer(SENDER_ID);
    private final Customer receiver = TestFixtures.customer(2L);

    @Test
    void transferMovesMoneyBetweenAccounts() {
        Account source = TestFixtures.account(10L, SOURCE, sender, "10000.00");
        Account destination = TestFixtures.account(20L, DESTINATION, receiver, "5000.00");
        stubAccounts(source, destination);
        when(referenceGenerator.next()).thenReturn("TXN-20261006-CCCCCC");
        when(transactionRepository.save(any(BankTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferResponse response = transferService.transfer(command("2000.00"));

        assertThat(source.getBalance()).isEqualByComparingTo("8000.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("7000.00");
        assertThat(response.sourceBalanceAfter()).isEqualByComparingTo("8000.00");
        ArgumentCaptor<BankTransaction> captor = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(captor.getValue().getSourceAccount()).isSameAs(source);
        assertThat(captor.getValue().getDestinationAccount()).isSameAs(destination);
    }

    @Test
    void accountsAreUpdatedInAscendingIdOrderToAvoidDeadlocks() {
        // Source has the HIGHER id here, so the destination row must be written first.
        Account source = TestFixtures.account(99L, SOURCE, sender, "1000.00");
        Account destination = TestFixtures.account(5L, DESTINATION, receiver, "0.00");
        stubAccounts(source, destination);
        when(referenceGenerator.next()).thenReturn("TXN-20261006-DDDDDD");
        when(transactionRepository.save(any(BankTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transferService.transfer(command("100.00"));

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).saveAndFlush(destination);
        order.verify(accountRepository).saveAndFlush(source);
    }

    @Test
    void insufficientBalanceLeavesBothAccountsUntouched() {
        Account source = TestFixtures.account(10L, SOURCE, sender, "1000.00");
        Account destination = TestFixtures.account(20L, DESTINATION, receiver, "5000.00");
        stubAccounts(source, destination);

        assertThatThrownBy(() -> transferService.transfer(command("1000.01")))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("5000.00");
        verify(accountRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferToSameAccountIsRejected() {
        Account source = TestFixtures.account(10L, SOURCE, sender, "1000.00");
        when(accountService.getOwnedAccount(SENDER_ID, SOURCE)).thenReturn(source);
        MoneyOperationCommand sameAccount = MoneyOperationCommand.transfer(SENDER_ID,
                new TransferRequest(SOURCE, SOURCE, new BigDecimal("10.00"), null), "key-12345678");

        assertThatThrownBy(() -> transferService.transfer(sameAccount))
                .isInstanceOf(InvalidTransactionException.class);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void transferToBlockedAccountIsRejected() {
        Account source = TestFixtures.account(10L, SOURCE, sender, "1000.00");
        Account destination = TestFixtures.account(20L, DESTINATION, receiver, "0.00", AccountStatus.BLOCKED);
        stubAccounts(source, destination);

        assertThatThrownBy(() -> transferService.transfer(command("10.00")))
                .isInstanceOf(AccountNotActiveException.class);
        assertThat(source.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void transferFromAccountNotOwnedBySenderIsRejected() {
        when(accountService.getOwnedAccount(SENDER_ID, SOURCE))
                .thenThrow(new UnauthorizedAccountAccessException(SOURCE));

        assertThatThrownBy(() -> transferService.transfer(command("10.00")))
                .isInstanceOf(UnauthorizedAccountAccessException.class);
        verifyNoInteractions(accountRepository, transactionRepository, auditService);
    }

    @Test
    void transferToUnknownAccountIsRejected() {
        Account source = TestFixtures.account(10L, SOURCE, sender, "1000.00");
        when(accountService.getOwnedAccount(SENDER_ID, SOURCE)).thenReturn(source);
        when(accountService.findByNumber(DESTINATION)).thenThrow(new ResourceNotFoundException("Account", DESTINATION));

        assertThatThrownBy(() -> transferService.transfer(command("10.00")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(source.getBalance()).isEqualByComparingTo("1000.00");
    }

    private void stubAccounts(Account source, Account destination) {
        when(accountService.getOwnedAccount(SENDER_ID, SOURCE)).thenReturn(source);
        when(accountService.findByNumber(DESTINATION)).thenReturn(destination);
    }

    private static MoneyOperationCommand command(String amount) {
        return MoneyOperationCommand.transfer(SENDER_ID,
                new TransferRequest(SOURCE, DESTINATION, new BigDecimal(amount), "Rent"), "key-12345678");
    }
}
