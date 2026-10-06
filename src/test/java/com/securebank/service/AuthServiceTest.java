package com.securebank.service;

import com.securebank.TestFixtures;
import com.securebank.dto.auth.LoginRequest;
import com.securebank.dto.auth.LoginResponse;
import com.securebank.dto.auth.RegisterRequest;
import com.securebank.dto.customer.CustomerResponse;
import com.securebank.entity.AuditAction;
import com.securebank.entity.Customer;
import com.securebank.entity.Role;
import com.securebank.exception.DuplicateResourceException;
import com.securebank.exception.InvalidCredentialsException;
import com.securebank.exception.TooManyLoginAttemptsException;
import com.securebank.repository.CustomerRepository;
import com.securebank.security.JwtService;
import com.securebank.security.LoginAttemptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerHashesPasswordNormalisesEmailAndNeverStoresPlaintext() {
        RegisterRequest request = new RegisterRequest("Maggie Rao", "  Maggie@Example.COM ", "9876543210",
                "Str0ng@Pass");
        when(customerRepository.existsByEmail("maggie@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng@Pass")).thenReturn("$2a$12$hashed");
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        CustomerResponse response = authService.register(request);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).saveAndFlush(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("maggie@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$hashed").isNotEqualTo("Str0ng@Pass");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.email()).isEqualTo("maggie@example.com");
        verify(auditService).recordSuccess(eq(42L), eq(AuditAction.REGISTER), anyString(), anyString(), anyString());
    }

    @Test
    void registerWithExistingEmailIsRejected() {
        when(customerRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Taken User", "taken@example.com", "9876543210", "Str0ng@Pass")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(customerRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void successfulLoginReturnsBearerTokenAndResetsFailureCounter() {
        Customer customer = TestFixtures.customer(7L);
        when(customerRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(jwtService.generateToken(customer)).thenReturn("signed.jwt.token");
        when(jwtService.expirationSeconds()).thenReturn(1800L);

        LoginResponse response = authService.login(new LoginRequest(customer.getEmail(), "Str0ng@Pass"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(1800L);
        verify(loginAttemptService).recordSuccess(customer.getEmail());
        verify(auditService).recordSuccess(eq(7L), eq(AuditAction.LOGIN), anyString(), anyString(), anyString());
    }

    @Test
    void wrongPasswordIsCountedAuditedAndReportedGenerically() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(customerRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "Wrong@123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(loginAttemptService).recordFailure("nobody@example.com");
        verify(auditService).recordFailure(isNull(), eq(AuditAction.LOGIN_FAILED), anyString(), isNull(),
                anyString());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void lockedEmailIsRejectedBeforePasswordIsChecked() {
        doThrow(new TooManyLoginAttemptsException(Duration.ofMinutes(5)))
                .when(loginAttemptService).assertNotLocked("locked@example.com");

        assertThatThrownBy(() -> authService.login(new LoginRequest("locked@example.com", "Str0ng@Pass")))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verify(authenticationManager, never()).authenticate(any());
    }
}
