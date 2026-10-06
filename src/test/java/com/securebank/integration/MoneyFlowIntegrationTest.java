package com.securebank.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.securebank.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MoneyFlowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void depositAndWithdrawalUpdateBalance() throws Exception {
        String token = registerAndLogin();
        String account = createAccount(token);

        mockMvc.perform(post("/api/v1/accounts/{number}/deposit", account)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", "1000.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.transactionReference").value(
                        matchesPattern("TXN-\\d{8}-[A-Z0-9]{6}")));

        mockMvc.perform(post("/api/v1/accounts/{number}/withdraw", account)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", "250.50"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(749.5));

        assertThat(balance(token, account)).isEqualByComparingTo("749.50");
    }

    @Test
    void negativeOrZeroAmountIsRejectedByValidation() throws Exception {
        String token = registerAndLogin();
        String account = createAccount(token);

        mockMvc.perform(post("/api/v1/accounts/{number}/deposit", account)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", "-100"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
        assertThat(balance(token, account)).isEqualByComparingTo("0");
    }

    @Test
    void overdraftIsRejectedAndRecordedAsFailedTransaction() throws Exception {
        String token = registerAndLogin();
        String account = createAccount(token);
        deposit(token, account, "100.00");

        mockMvc.perform(post("/api/v1/accounts/{number}/withdraw", account)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", "100.01"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));

        assertThat(balance(token, account)).isEqualByComparingTo("100.00");
        mockMvc.perform(get("/api/v1/accounts/{number}/transactions", account).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].failureReason").exists());
    }

    @Test
    void transferMovesMoneyAtomically() throws Exception {
        String senderToken = registerAndLogin();
        String source = createAccount(senderToken);
        deposit(senderToken, source, "10000.00");
        String receiverToken = registerAndLogin();
        String destination = createAccount(receiverToken);
        deposit(receiverToken, destination, "5000.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(senderToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, "2000.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.sourceBalanceAfter").value(8000.0));

        assertThat(balance(senderToken, source)).isEqualByComparingTo("8000.00");
        assertThat(balance(receiverToken, destination)).isEqualByComparingTo("7000.00");

        // The receiver sees it as a CREDIT with the sender's account number masked.
        mockMvc.perform(get("/api/v1/accounts/{number}/transactions", destination)
                        .header("Authorization", bearer(receiverToken)))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].counterpartyAccount").value("XXXXXXXX" + source.substring(8)));
    }

    @Test
    void sameIdempotencyKeyTransfersOnlyOnceAndReplaysOriginalResponse() throws Exception {
        String token = registerAndLogin();
        String source = createAccount(token);
        String destination = createAccount(token);
        deposit(token, source, "10000.00");
        String key = UUID.randomUUID().toString();
        String body = transferJson(source, destination, "5000.00");

        MvcResult first = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();

        JsonNode firstBody = read(first);
        JsonNode secondBody = read(second);
        assertThat(secondBody.get("transactionReference")).isEqualTo(firstBody.get("transactionReference"));
        assertThat(balance(token, source)).isEqualByComparingTo("5000.00");
        assertThat(balance(token, destination)).isEqualByComparingTo("5000.00");
    }

    @Test
    void reusingKeyWithDifferentAmountIsRejected() throws Exception {
        String token = registerAndLogin();
        String source = createAccount(token);
        String destination = createAccount(token);
        deposit(token, source, "1000.00");
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, "100.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, "900.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(balance(token, source)).isEqualByComparingTo("900.00");
    }

    @Test
    void failedTransferReleasesKeySoClientCanRetryAfterFixingTheProblem() throws Exception {
        String token = registerAndLogin();
        String source = createAccount(token);
        String destination = createAccount(token);
        String key = UUID.randomUUID().toString();
        String body = transferJson(source, destination, "500.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());

        deposit(token, source, "500.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
        assertThat(balance(token, destination)).isEqualByComparingTo("500.00");
    }

    @Test
    void transferWithoutIdempotencyKeyIsRejected() throws Exception {
        String token = registerAndLogin();
        String source = createAccount(token);
        String destination = createAccount(token);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, "1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required header 'Idempotency-Key' is missing"));
    }

    @Test
    void transferToSameAccountIsRejected() throws Exception {
        String token = registerAndLogin();
        String account = createAccount(token);
        deposit(token, account, "100.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(account, account, "10.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSACTION"));
    }

    @Test
    void cannotTransferFromSomeoneElsesAccount() throws Exception {
        String victimToken = registerAndLogin();
        String victimAccount = createAccount(victimToken);
        deposit(victimToken, victimAccount, "1000.00");
        String attackerToken = registerAndLogin();
        String attackerAccount = createAccount(attackerToken);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(attackerToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(victimAccount, attackerAccount, "1000.00")))
                .andExpect(status().isForbidden());

        assertThat(balance(victimToken, victimAccount)).isEqualByComparingTo("1000.00");
        // The rejected attempt must not leave any record on the victim's account.
        Long victimAccountId = accountRepository.findByAccountNumber(victimAccount).orElseThrow().getId();
        Long failedOnVictim = jdbcTemplate.queryForObject(
                "select count(*) from bank_transactions where source_account_id = ? and status = 'FAILED'",
                Long.class, victimAccountId);
        assertThat(failedOnVictim).isZero();
    }

    @Test
    void blockedAccountCannotTransactUntilUnblocked() throws Exception {
        String token = registerAndLogin();
        String account = createAccount(token);
        deposit(token, account, "500.00");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(patch("/api/v1/admin/accounts/{number}/status", account)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "BLOCKED", "reason", "Suspicious activity"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mockMvc.perform(post("/api/v1/accounts/{number}/withdraw", account)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", "10.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("ACCOUNT_BLOCKED"));

        mockMvc.perform(patch("/api/v1/admin/accounts/{number}/status", account)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTIVE", "reason", "Verified with customer"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{number}/withdraw", account)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", "10.00"))))
                .andExpect(status().isCreated());
    }

    @Test
    void statementIsPaginatedAndSortedNewestFirst() throws Exception {
        String token = registerAndLogin();
        String account = createAccount(token);
        for (int i = 1; i <= 12; i++) {
            deposit(token, account, i + ".00");
        }

        mockMvc.perform(get("/api/v1/accounts/{number}/transactions", account)
                        .header("Authorization", bearer(token))
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/v1/accounts/{number}/transactions", account)
                        .header("Authorization", bearer(token))
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/v1/accounts/{number}/transactions", account)
                        .header("Authorization", bearer(token))
                        .param("sort", "description,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void adminCanReadAuditTrail() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
