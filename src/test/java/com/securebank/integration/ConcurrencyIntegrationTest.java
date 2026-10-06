package com.securebank.integration;

import com.securebank.dto.transaction.MoneyOperationRequest;
import com.securebank.entity.Account;
import com.securebank.exception.ConcurrentUpdateException;
import com.securebank.exception.InsufficientBalanceException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.CustomerRepository;
import com.securebank.service.money.MoneyOperationCommand;
import com.securebank.service.money.MoneyOperationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The classic race: balance 1000, two withdrawals of 800 and 700 arrive together.
 * Without concurrency control both could read 1000, both pass the balance check, and the
 * account would end at 200 or 300 with 1500 paid out. Exactly one must succeed.
 */
class ConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MoneyOperationFacade moneyOperationFacade;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void twoConcurrentWithdrawalsCannotBothSucceed() throws Exception {
        Fixture fixture = accountWithBalance("1000.00");
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String amount : List.of("800.00", "700.00")) {
                Callable<String> withdrawal = () -> {
                    startTogether.await();
                    try {
                        moneyOperationFacade.withdraw(fixture.withdrawal(amount));
                        return "SUCCESS";
                    } catch (ConcurrentUpdateException | InsufficientBalanceException expectedLoser) {
                        return expectedLoser.getErrorCode().name();
                    }
                };
                results.add(pool.submit(withdrawal));
            }
            startTogether.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes).filteredOn("SUCCESS"::equals).hasSize(1);
            assertThat(outcomes).filteredOn(o -> !o.equals("SUCCESS"))
                    .allMatch(o -> o.equals("CONCURRENT_UPDATE") || o.equals("INSUFFICIENT_BALANCE"));
            BigDecimal finalBalance = currentBalance(fixture.accountNumber());
            assertThat(finalBalance).isIn(new BigDecimal("200.00"), new BigDecimal("300.00"));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Deterministic version of the race: we hold a stale copy of the account (version N) while
     * another request commits a withdrawal (version N+1). Writing the stale copy must fail the
     * version check instead of overwriting the newer balance.
     */
    @Test
    void staleAccountUpdateIsRejectedByVersionCheck() throws Exception {
        Fixture fixture = accountWithBalance("1000.00");
        ExecutorService otherRequest = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                Account stale = accountRepository.findByAccountNumber(fixture.accountNumber()).orElseThrow();

                runAndWait(otherRequest, () -> moneyOperationFacade.withdraw(fixture.withdrawal("800.00")));

                // Our copy still says 1000, so the in-memory balance check passes...
                stale.debit(new BigDecimal("700.00"));
                // ...but UPDATE ... WHERE version = N matches zero rows.
                accountRepository.saveAndFlush(stale);
            })).isInstanceOf(ConcurrencyFailureException.class); // ObjectOptimisticLockingFailureException on PostgreSQL
        } finally {
            otherRequest.shutdownNow();
        }

        assertThat(currentBalance(fixture.accountNumber())).isEqualByComparingTo("200.00");
    }

    private Fixture accountWithBalance(String amount) throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email, PASSWORD);
        String accountNumber = createAccount(token);
        deposit(token, accountNumber, amount);
        Long customerId = customerRepository.findByEmail(email).orElseThrow().getId();
        return new Fixture(customerId, accountNumber);
    }

    private BigDecimal currentBalance(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }

    private static void runAndWait(ExecutorService executor, Runnable task) {
        try {
            executor.submit(task).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Fixture(Long customerId, String accountNumber) {

        MoneyOperationCommand withdrawal(String amount) {
            return MoneyOperationCommand.withdrawal(customerId, accountNumber,
                    new MoneyOperationRequest(new BigDecimal(amount), "concurrency test"), null);
        }
    }
}
