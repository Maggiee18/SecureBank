package com.securebank.dto.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Long customerId,
        String role) {

    public static LoginResponse bearer(String token, long expiresInSeconds, Long customerId, String role) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, customerId, role);
    }
}
