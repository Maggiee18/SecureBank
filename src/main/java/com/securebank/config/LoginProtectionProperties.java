package com.securebank.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "banking.security.login")
public record LoginProtectionProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration lockDuration) {
}
