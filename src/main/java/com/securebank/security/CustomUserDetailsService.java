package com.securebank.security;

import com.securebank.repository.CustomerRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Used only at login by Spring's DaoAuthenticationProvider to load the BCrypt hash.
 * Authenticated requests are verified from the JWT and do not hit the database.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return customerRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(customer -> User.withUsername(customer.getEmail())
                        .password(customer.getPasswordHash())
                        .roles(customer.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Customer not found"));
    }
}
