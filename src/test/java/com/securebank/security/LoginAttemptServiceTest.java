package com.securebank.security;

import com.securebank.config.LoginProtectionProperties;
import com.securebank.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final String EMAIL = "maggie@example.com";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-06T10:00:00Z"));
    private final LoginAttemptService service =
            new LoginAttemptService(new LoginProtectionProperties(3, Duration.ofMinutes(15)), clock);

    @Test
    void locksAfterMaxFailures() {
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);
        assertThatCode(() -> service.assertNotLocked(EMAIL)).doesNotThrowAnyException();

        service.recordFailure(EMAIL);

        assertThatThrownBy(() -> service.assertNotLocked(EMAIL)).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void lockIsCaseInsensitiveOnEmail() {
        service.recordFailure(EMAIL);
        service.recordFailure("MAGGIE@example.com");
        service.recordFailure(" maggie@EXAMPLE.com ");

        assertThatThrownBy(() -> service.assertNotLocked(EMAIL)).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void lockExpiresAfterLockDuration() {
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);

        clock.advance(Duration.ofMinutes(16));

        assertThatCode(() -> service.assertNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginResetsCounter() {
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);
        service.recordSuccess(EMAIL);
        service.recordFailure(EMAIL);

        assertThatCode(() -> service.assertNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
