package com.securebank.util;

import com.securebank.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates 12-digit account numbers: a fixed 4-digit bank prefix plus 8 random digits.
 * SecureRandom makes numbers unguessable; the existence check avoids most collisions and the
 * unique constraint on accounts.account_number catches the rare race between two creations.
 */
@Component
public class AccountNumberGenerator {

    static final String BANK_PREFIX = "5021";
    private static final int RANDOM_DIGITS = 8;
    private static final int RANDOM_BOUND = 100_000_000;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accountRepository;

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = BANK_PREFIX + String.format("%0" + RANDOM_DIGITS + "d", random.nextInt(RANDOM_BOUND));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }
}
