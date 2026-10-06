package com.securebank.service;

import com.securebank.dto.auth.LoginRequest;
import com.securebank.dto.auth.LoginResponse;
import com.securebank.dto.auth.RegisterRequest;
import com.securebank.dto.customer.CustomerResponse;
import com.securebank.entity.AuditAction;
import com.securebank.entity.Customer;
import com.securebank.entity.Role;
import com.securebank.exception.DuplicateResourceException;
import com.securebank.exception.InvalidCredentialsException;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.repository.CustomerRepository;
import com.securebank.security.JwtService;
import com.securebank.security.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String RESOURCE = "CUSTOMER";

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AuthService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       LoginAttemptService loginAttemptService, AuditService auditService) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        Customer customer = new Customer(request.fullName().trim(), email, request.phone(),
                passwordEncoder.encode(request.password()), Role.CUSTOMER);
        try {
            // Flush now so a concurrent registration with the same email fails here, not at commit.
            customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        auditService.recordSuccess(customer.getId(), AuditAction.REGISTER, RESOURCE,
                String.valueOf(customer.getId()), "Customer registered");
        log.info("CUSTOMER_REGISTERED customerId={}", customer.getId());
        return CustomerResponse.from(customer);
    }

    /**
     * Not @Transactional: a failed login must still write its audit row, and the lookup after
     * authentication is a single read.
     */
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        loginAttemptService.assertNotLocked(email);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException ex) {
            // Only wrong credentials count as a failed attempt. Infrastructure problems (for example
            // the database being down) surface as other exceptions and must not lock anyone out.
            loginAttemptService.recordFailure(email);
            Long knownCustomerId = customerRepository.findByEmail(email).map(Customer::getId).orElse(null);
            auditService.recordFailure(knownCustomerId, AuditAction.LOGIN_FAILED, RESOURCE,
                    knownCustomerId == null ? null : String.valueOf(knownCustomerId), "Invalid credentials");
            log.warn("LOGIN_FAILED knownCustomer={}", knownCustomerId != null);
            // Same message whether the email exists or not, so accounts cannot be enumerated.
            throw new InvalidCredentialsException();
        }

        loginAttemptService.recordSuccess(email);
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", email));
        auditService.recordSuccess(customer.getId(), AuditAction.LOGIN, RESOURCE,
                String.valueOf(customer.getId()), "Login successful");
        log.info("LOGIN_SUCCESS customerId={}", customer.getId());
        return LoginResponse.bearer(jwtService.generateToken(customer), jwtService.expirationSeconds(),
                customer.getId(), customer.getRole().name());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
