package com.securebank.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * The secret has no default in application.yml: the application refuses to start without
 * JWT_SECRET rather than silently signing tokens with a known key.
 */
@Validated
@ConfigurationProperties(prefix = "banking.security.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET environment variable must be set") String secret,
        @NotNull Duration expiration,
        @NotBlank String issuer) {
}
