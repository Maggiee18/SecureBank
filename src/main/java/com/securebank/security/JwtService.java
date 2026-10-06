package com.securebank.security;

import com.securebank.config.JwtProperties;
import com.securebank.entity.Customer;
import com.securebank.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    // HS256 needs a key of at least 256 bits.
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.properties = properties;
        this.clock = clock;
    }

    public String generateToken(Customer customer) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(customer.getId()))
                .issuer(properties.issuer())
                .claim(CLAIM_EMAIL, customer.getEmail())
                .claim(CLAIM_ROLE, customer.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, then builds the principal from the claims.
     *
     * @throws JwtException if the token is invalid, tampered with or expired
     */
    public AuthenticatedCustomer parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new AuthenticatedCustomer(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (RuntimeException ex) {
            throw new JwtException("Token claims are malformed", ex);
        }
    }

    public long expirationSeconds() {
        return properties.expiration().toSeconds();
    }
}
