package com.securebank.exception;

import com.securebank.dto.common.ErrorResponse;
import com.securebank.dto.common.FieldValidationError;
import com.securebank.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Turns every exception into the same JSON shape with a stable error code and the request id.
 * Stack traces and internal details are logged, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponse> handleBanking(BankingException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.httpStatus().is4xxClientError()) {
            log.info("REQUEST_REJECTED code={} message={}", code, ex.getMessage());
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.httpStatus());
        if (ex instanceof TooManyLoginAttemptsException locked) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, locked.getRetryAfter().toSeconds())));
        }
        return builder.body(body(code, ex.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .toList();
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.httpStatus()).body(ErrorResponse.withFieldErrors(
                code.httpStatus().value(), code.name(), "Request validation failed", request.getRequestURI(),
                RequestContext.requestId(), fieldErrors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformed(Exception ex, HttpServletRequest request) {
        log.info("MALFORMED_REQUEST cause={}", ex.getClass().getSimpleName());
        return respond(ErrorCode.MALFORMED_REQUEST, "Request body or parameter is malformed", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                             HttpServletRequest request) {
        return respond(ErrorCode.INVALID_REQUEST, "Required header '" + ex.getHeaderName() + "' is missing", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        return respond(ErrorCode.INVALID_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing",
                request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleBadSort(PropertyReferenceException ex, HttpServletRequest request) {
        return respond(ErrorCode.INVALID_REQUEST, "Unknown sort property '" + ex.getPropertyName() + "'", request);
    }

    /** Authentication could not run at all (for example the database is down): a server error, not a 401. */
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationInfrastructure(InternalAuthenticationServiceException ex,
                                                                           HttpServletRequest request) {
        return handleUnexpected(ex, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required", request);
    }

    /** Raised by @PreAuthorize inside the MVC layer, so it reaches this advice rather than the security filter. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(ConcurrencyFailureException ex, HttpServletRequest request) {
        log.warn("CONCURRENCY_CONFLICT cause={}", ex.getClass().getSimpleName());
        return respond(ErrorCode.CONCURRENT_UPDATE, new ConcurrentUpdateException().getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        // The constraint name is useful in logs but would leak schema details to clients.
        log.warn("DATA_INTEGRITY_VIOLATION cause={}", ex.getMostSpecificCause().getClass().getSimpleName());
        return respond(ErrorCode.DATA_CONFLICT, "The request conflicts with existing data", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint at this path", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "HTTP method " + ex.getMethod() + " is not supported here",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("UNEXPECTED_ERROR path={}", request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR,
                "Something went wrong. Quote the requestId when contacting support", request);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.httpStatus()).body(body(code, message, request));
    }

    private static ErrorResponse body(ErrorCode code, String message, HttpServletRequest request) {
        return ErrorResponse.of(code.httpStatus().value(), code.name(), message, request.getRequestURI(),
                RequestContext.requestId());
    }
}
