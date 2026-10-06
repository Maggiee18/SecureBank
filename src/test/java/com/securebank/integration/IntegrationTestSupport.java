package com.securebank.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application (security, JPA, Flyway) against an in-memory H2 database in
 * PostgreSQL mode, and drives it over HTTP through MockMvc.
 */
@SpringBootTest(properties = {
        "banking.admin.email=ops-admin@securebank.test",
        "banking.admin.password=Admin@12345"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    static final String PASSWORD = "Str0ng@Pass";
    static final String ADMIN_EMAIL = "ops-admin@securebank.test";
    static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Test User", "email", email,
                                "phone", "9876543210", "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    protected String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("accessToken").asText();
    }

    protected String registerAndLogin() throws Exception {
        String email = uniqueEmail();
        register(email);
        return login(email, PASSWORD);
    }

    protected String createAccount(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountType", "SAVINGS"))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("accountNumber").asText();
    }

    protected void deposit(String token, String accountNumber, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{number}/deposit", accountNumber)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", amount, "description", "Opening deposit"))))
                .andExpect(status().isCreated());
    }

    protected BigDecimal balance(String token, String accountNumber) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/{number}/balance", accountNumber)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(read(result).get("balance").asText());
    }

    protected String transferJson(String from, String to, String amount) throws Exception {
        return json(Map.of("sourceAccountNumber", from, "destinationAccountNumber", to,
                "amount", amount, "description", "Test transfer"));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
