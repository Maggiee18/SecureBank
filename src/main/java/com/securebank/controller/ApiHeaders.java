package com.securebank.controller;

import com.securebank.service.money.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class ApiHeaders {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private ApiHeaders() {
    }

    /** A replayed response keeps its original 201 status; the header tells the client it is a replay. */
    static <T> ResponseEntity<T> created(OperationOutcome<T> outcome) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAYED, String.valueOf(outcome.replayed()))
                .body(outcome.body());
    }
}
