package com.securebank.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test
    void safeClientRequestIdIsPropagated() {
        assertThat(RequestIdFilter.resolveRequestId("7f83a9d2")).isEqualTo("7f83a9d2");
    }

    @Test
    void missingRequestIdIsGenerated() {
        assertThat(RequestIdFilter.resolveRequestId(null)).hasSize(36);
    }

    @Test
    void unsafeRequestIdIsReplacedToPreventLogInjection() {
        String generated = RequestIdFilter.resolveRequestId("abc\nFAKE LOG LINE");

        assertThat(generated).doesNotContain("FAKE").hasSize(36);
    }
}
