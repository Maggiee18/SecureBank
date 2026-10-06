package com.securebank.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndAccountIntegrationTest extends IntegrationTestSupport {

    @Test
    void registrationReturnsProfileWithoutPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Maggie Rao", "email", uniqueEmail(),
                                "phone", "9876543210", "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/me"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Someone Else", "email", email.toUpperCase(),
                                "phone", "9876543210", "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-ID", "test-req-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "", "email", "not-an-email",
                                "phone", "123", "password", "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("test-req-001"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("password")));
    }

    @Test
    void wrongPasswordReturnsGenericUnauthorized() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Wrong@Pass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void repeatedFailedLoginsLockTheAccountTemporarily() throws Exception {
        String email = uniqueEmail();
        register(email);
        String wrong = json(Map.of("email", email, "password", "Wrong@Pass1"));
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }

        // Even the correct password is refused while locked.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void protectedEndpointWithoutTokenReturnsJson401WithRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = registerAndLogin();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/v1/customers/me").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void customerCanReadAndUpdateOwnProfile() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(put("/api/v1/customers/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Updated Name", "phone", "9123456780"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"));

        mockMvc.perform(get("/api/v1/customers/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("9123456780"));
    }

    @Test
    void newAccountHasGeneratedNumberAndZeroBalance() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountType", "CURRENT"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/accounts/\\d{12}")))
                .andExpect(jsonPath("$.accountNumber").value(matchesPattern("\\d{12}")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void customerCannotSeeAnotherCustomersAccount() throws Exception {
        String ownerToken = registerAndLogin();
        String accountNumber = createAccount(ownerToken);
        String intruderToken = registerAndLogin();

        mockMvc.perform(get("/api/v1/accounts/{number}", accountNumber).header("Authorization", bearer(intruderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/accounts/{number}/transactions", accountNumber)
                        .header("Authorization", bearer(intruderToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownAccountReturnsNotFound() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/accounts/{number}", "999999999999").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void customerCannotCallAdminEndpoints() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    void browserPreflightFromAllowedOriginIsAccepted() throws Exception {
        mockMvc.perform(options("/api/v1/transfers")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void browserPreflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/transfers")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointIsPublicAndHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(content().string(not(containsString("components"))));
    }
}
