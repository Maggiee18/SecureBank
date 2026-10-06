package com.securebank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional bootstrap admin, created at startup only when both email and password are provided
 * through environment variables. Nothing is hardcoded.
 */
@ConfigurationProperties(prefix = "banking.admin")
public record AdminProperties(String email, String password, String fullName, String phone) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
