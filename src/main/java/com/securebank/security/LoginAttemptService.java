package com.securebank.security;

import com.securebank.config.LoginProtectionProperties;
import com.securebank.exception.TooManyLoginAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basic brute-force protection: after N failed logins for an email, further attempts are
 * rejected for a lock period, even with the correct password.
 *
 * State is in memory, which is fine for a single-instance demo. A distributed deployment
 * would keep these counters in a shared store such as Redis, or enforce limits at an API gateway.
 * Locking by email also lets an attacker lock a victim out on purpose; production systems
 * usually combine per-IP limits, CAPTCHA and step-up verification.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    // Bounds memory if someone sprays random emails; expired entries are pruned at this size.
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();
    private final LoginProtectionProperties properties;
    private final Clock clock;

    public LoginAttemptService(LoginProtectionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void assertNotLocked(String email) {
        Attempts attempts = attemptsByEmail.get(normalize(email));
        if (attempts == null || attempts.lockedUntil() == null) {
            return;
        }
        Instant now = clock.instant();
        if (attempts.lockedUntil().isAfter(now)) {
            throw new TooManyLoginAttemptsException(Duration.between(now, attempts.lockedUntil()));
        }
        attemptsByEmail.remove(normalize(email));
    }

    public void recordFailure(String email) {
        if (attemptsByEmail.size() > PRUNE_THRESHOLD) {
            pruneExpired();
        }
        Instant now = clock.instant();
        Attempts updated = attemptsByEmail.compute(normalize(email), (key, current) -> {
            int failures = (current == null ? 0 : current.failures()) + 1;
            Instant lockedUntil = failures >= properties.maxAttempts()
                    ? now.plus(properties.lockDuration())
                    : null;
            return new Attempts(failures, lockedUntil);
        });
        if (updated.lockedUntil() != null) {
            log.warn("LOGIN_LOCKED failures={} lockSeconds={}", updated.failures(),
                    properties.lockDuration().toSeconds());
        }
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(normalize(email));
    }

    private void pruneExpired() {
        Instant now = clock.instant();
        attemptsByEmail.entrySet().removeIf(entry ->
                entry.getValue().lockedUntil() == null || entry.getValue().lockedUntil().isBefore(now));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempts(int failures, Instant lockedUntil) {
    }
}
