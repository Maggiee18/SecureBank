package com.securebank.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId,
        List<FieldValidationError> fieldErrors) {

    public static ErrorResponse of(int status, String error, String message, String path, String requestId) {
        return new ErrorResponse(Instant.now(), status, error, message, path, requestId, null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message, String path,
                                                String requestId, List<FieldValidationError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, requestId, fieldErrors);
    }
}
