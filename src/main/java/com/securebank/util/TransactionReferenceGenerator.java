package com.securebank.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Produces human-friendly references such as TXN-20261006-A8F42K that a customer can read out
 * to support staff. 36^6 (about 2.1 billion) combinations per day; the unique constraint on
 * bank_transactions.transaction_reference is the final guarantee.
 */
@Component
public class TransactionReferenceGenerator {

    private static final String PREFIX = "TXN-";
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SUFFIX_LENGTH = 6;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public TransactionReferenceGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder reference = new StringBuilder(PREFIX)
                .append(LocalDate.now(clock).format(DATE_FORMAT))
                .append('-');
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            reference.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
