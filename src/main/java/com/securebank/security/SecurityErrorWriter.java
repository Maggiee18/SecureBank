package com.securebank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.dto.common.ErrorResponse;
import com.securebank.exception.ErrorCode;
import com.securebank.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Security failures happen in the filter chain, before any controller, so @RestControllerAdvice
 * never sees them. This writes the same JSON error shape the advice produces.
 */
@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        ErrorResponse body = ErrorResponse.of(code.httpStatus().value(), code.name(), message,
                request.getRequestURI(), RequestContext.requestId());
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
