package com.securebank.security;

import com.securebank.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * The principal placed in the SecurityContext after a JWT is verified.
 * Controllers receive it with @AuthenticationPrincipal and pass the id to services,
 * so services never depend on Spring Security directly.
 */
public record AuthenticatedCustomer(Long id, String email, Role role) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
