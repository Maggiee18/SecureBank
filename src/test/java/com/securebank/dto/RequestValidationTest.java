package com.securebank.dto;

import com.securebank.dto.auth.RegisterRequest;
import com.securebank.dto.transaction.MoneyOperationRequest;
import com.securebank.dto.transaction.TransferRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "10.001"})
    void invalidAmountsAreRejected(String amount) {
        Set<ConstraintViolation<MoneyOperationRequest>> violations =
                validator.validate(new MoneyOperationRequest(new BigDecimal(amount), null));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("amount");
    }

    @Test
    void smallestPositiveAmountIsAccepted() {
        assertThat(validator.validate(new MoneyOperationRequest(new BigDecimal("0.01"), "ok"))).isEmpty();
    }

    @Test
    void missingAmountIsRejected() {
        assertThat(validator.validate(new MoneyOperationRequest(null, null))).hasSize(1);
    }

    @Test
    void transferRequiresTwelveDigitAccountNumbers() {
        Set<ConstraintViolation<TransferRequest>> violations = validator.validate(
                new TransferRequest("12345", "50210000000X", new BigDecimal("10.00"), null));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("sourceAccountNumber", "destinationAccountNumber");
    }

    @Test
    void weakPasswordAndBadPhoneAreRejected() {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(
                new RegisterRequest("Maggie", "maggie@example.com", "12345", "password"));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("phone", "password");
    }

    @Test
    void passwordNeverAppearsInToString() {
        RegisterRequest request = new RegisterRequest("Maggie", "maggie@example.com", "9876543210", "Str0ng@Pass");

        assertThat(request.toString()).doesNotContain("Str0ng@Pass");
    }
}
