package com.securebank.integration;

import com.securebank.util.TransactionReferenceGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forces a failure AFTER the debit and credit have been flushed to the database, then checks
 * that @Transactional rolled both back.
 */
class TransferRollbackIntegrationTest extends IntegrationTestSupport {

    @MockBean
    private TransactionReferenceGenerator referenceGenerator;

    @Test
    void failureAfterDebitAndCreditRollsBackEverything() throws Exception {
        String token = registerAndLogin();
        String source = createAccount(token);
        String destination = createAccount(token);
        when(referenceGenerator.next())
                .thenReturn("TXN-20261006-SEED01")
                .thenThrow(new IllegalStateException("Simulated failure while writing the transaction record"));
        deposit(token, source, "10000.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, "2000.00")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                // Internal exception text must not leak to the client.
                .andExpect(jsonPath("$.message").value(not(containsString("Simulated"))));

        assertThat(balance(token, source)).isEqualByComparingTo("10000.00");
        assertThat(balance(token, destination)).isEqualByComparingTo("0.00");
        mockMvc.perform(get("/api/v1/accounts/{number}/transactions", destination)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
