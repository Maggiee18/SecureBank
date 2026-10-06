package com.securebank;

import com.securebank.entity.Account;
import com.securebank.entity.AccountStatus;
import com.securebank.entity.AccountType;
import com.securebank.entity.Customer;
import com.securebank.entity.Role;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

/** Builds entities with ids and balances already set, the way they would come out of the database. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static Customer customer(long id) {
        Customer customer = new Customer("Customer " + id, "customer" + id + "@example.com", "9876543210",
                "$2a$12$hash", Role.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", id);
        ReflectionTestUtils.setField(customer, "createdAt", Instant.now());
        return customer;
    }

    public static Account account(long id, String accountNumber, Customer owner, String balance) {
        Account account = new Account(accountNumber, owner, AccountType.SAVINGS);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        ReflectionTestUtils.setField(account, "version", 0L);
        ReflectionTestUtils.setField(account, "createdAt", Instant.now());
        return account;
    }

    public static Account account(long id, String accountNumber, Customer owner, String balance, AccountStatus status) {
        Account account = account(id, accountNumber, owner, balance);
        ReflectionTestUtils.setField(account, "status", status);
        return account;
    }
}
