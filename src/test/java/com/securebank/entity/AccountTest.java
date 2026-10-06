package com.securebank.entity;

import com.securebank.TestFixtures;
import com.securebank.exception.AccountNotActiveException;
import com.securebank.exception.ErrorCode;
import com.securebank.exception.InsufficientBalanceException;
import com.securebank.exception.InvalidAccountStatusChangeException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private final Customer owner = TestFixtures.customer(1L);

    @Test
    void debitReducesBalanceExactly() {
        Account account = TestFixtures.account(1L, "502100000001", owner, "1000.00");

        account.debit(new BigDecimal("0.10"));
        account.debit(new BigDecimal("0.20"));

        // With double this would be 999.6999999999999; BigDecimal keeps it exact.
        assertThat(account.getBalance()).isEqualByComparingTo("999.70");
    }

    @Test
    void debitOfEntireBalanceIsAllowed() {
        Account account = TestFixtures.account(1L, "502100000001", owner, "500.00");

        account.debit(new BigDecimal("500.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void debitMoreThanBalanceIsRejectedAndBalanceUnchanged() {
        Account account = TestFixtures.account(1L, "502100000001", owner, "100.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void blockedAccountCannotBeDebitedOrCredited() {
        Account account = TestFixtures.account(1L, "502100000001", owner, "100.00", AccountStatus.BLOCKED);

        assertThatThrownBy(() -> account.debit(BigDecimal.ONE))
                .isInstanceOf(AccountNotActiveException.class)
                .extracting(ex -> ((AccountNotActiveException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_BLOCKED);
        assertThatThrownBy(() -> account.credit(BigDecimal.ONE))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void closedAccountReportsClosedErrorCode() {
        Account account = TestFixtures.account(1L, "502100000001", owner, "0.00", AccountStatus.CLOSED);

        assertThatThrownBy(() -> account.credit(BigDecimal.TEN))
                .isInstanceOf(AccountNotActiveException.class)
                .extracting(ex -> ((AccountNotActiveException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_CLOSED);
    }

    @Test
    void accountWithMoneyCannotBeClosed() {
        Account account = TestFixtures.account(1L, "502100000001", owner, "10.00");

        assertThatThrownBy(() -> account.changeStatus(AccountStatus.CLOSED))
                .isInstanceOf(InvalidAccountStatusChangeException.class);
    }

    @Test
    void closedAccountCannotBeReopened() {
        Account account = TestFixtures.account(1L, "502100000001", owner, "0.00", AccountStatus.CLOSED);

        assertThatThrownBy(() -> account.changeStatus(AccountStatus.ACTIVE))
                .isInstanceOf(InvalidAccountStatusChangeException.class);
    }

    @Test
    void blockedAccountCanBeUnblocked() {
        Account account = TestFixtures.account(1L, "502100000001", owner, "10.00", AccountStatus.BLOCKED);

        account.changeStatus(AccountStatus.ACTIVE);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
