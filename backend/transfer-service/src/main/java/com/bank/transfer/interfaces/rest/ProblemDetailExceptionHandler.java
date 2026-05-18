package com.bank.transfer.interfaces.rest;

import com.bank.transfer.domain.exception.AccountFrozenException;
import com.bank.transfer.domain.exception.DependencyUnavailableException;
import com.bank.transfer.domain.exception.DomainException;
import com.bank.transfer.domain.exception.IdempotencyKeyConflictException;
import com.bank.transfer.domain.exception.InsufficientFundsException;
import com.bank.transfer.domain.exception.TransferNotFoundException;
import com.bank.transfer.interfaces.rest.dto.ProblemDetailResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler mapping domain exceptions to RFC 7807 Problem Detail responses.
 *
 * <p>The sealed {@link DomainException} hierarchy enables exhaustive pattern matching
 * (Java 21) so every domain exception subtype has an explicit, non-generic handler.
 * No {@code Exception} is swallowed — unhandled exceptions propagate to Spring's
 * default handler which emits a 500.
 *
 * <p>Content-Type for all error responses: {@code application/problem+json}.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);
    private static final String ERROR_BASE_URI = "https://errors.bank.local/";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    /**
     * Handles all {@link DomainException} subtypes via sealed-hierarchy pattern matching.
     * HTTP status is determined by the specific subtype.
     *
     * @param ex      the domain exception
     * @param request the current request for building the {@code instance} field
     * @return RFC 7807 Problem Detail response
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetailResponse> handleDomainException(
            final DomainException ex,
            final HttpServletRequest request) {

        HttpStatus status = switch (ex) {
            case IdempotencyKeyConflictException e -> HttpStatus.CONFLICT;
            case TransferNotFoundException e      -> HttpStatus.NOT_FOUND;
            case InsufficientFundsException e     -> HttpStatus.UNPROCESSABLE_ENTITY;
            case AccountFrozenException e         -> HttpStatus.UNPROCESSABLE_ENTITY;
            case DependencyUnavailableException e -> HttpStatus.SERVICE_UNAVAILABLE;
            default                              -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        // Log at WARN for 4xx (client error), ERROR for 5xx (unexpected)
        if (status.is4xxClientError()) {
            log.warn("domain.exception code={} status={} path={}",
                ex.getErrorCode(), status.value(), request.getRequestURI());
        } else {
            log.error("domain.exception code={} status={} path={}",
                ex.getErrorCode(), status.value(), request.getRequestURI(), ex);
        }

        ProblemDetailResponse body = new ProblemDetailResponse(
            ERROR_BASE_URI + kebab(ex.getErrorCode()),
            toTitle(ex.getErrorCode()),
            status.value(),
            ex.getMessage(),
            request.getRequestURI(),
            ex.getErrorCode(),
            MDC.get(TRACE_ID_MDC_KEY)
        );

        return ResponseEntity
            .status(status)
            .contentType(MediaType.parseMediaType("application/problem+json"))
            .body(body);
    }

    /**
     * Handles Bean Validation failures ({@code @Valid} on request body).
     * Returns 400 with a consolidated detail listing all field errors.
     *
     * @param ex      the validation exception
     * @param request the current request
     * @return 400 Problem Detail
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidation(
            final MethodArgumentNotValidException ex,
            final HttpServletRequest request) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));

        log.warn("validation.failed path={} detail={}", request.getRequestURI(), detail);

        ProblemDetailResponse body = new ProblemDetailResponse(
            ERROR_BASE_URI + "validation-error",
            "Validation error",
            HttpStatus.BAD_REQUEST.value(),
            detail,
            request.getRequestURI(),
            "VALIDATION_ERROR",
            MDC.get(TRACE_ID_MDC_KEY)
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.parseMediaType("application/problem+json"))
            .body(body);
    }

    /**
     * Handles missing required request headers (e.g. missing {@code Idempotency-Key}).
     *
     * @param ex      the missing header exception
     * @param request the current request
     * @return 400 Problem Detail
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetailResponse> handleMissingHeader(
            final MissingRequestHeaderException ex,
            final HttpServletRequest request) {

        log.warn("missing.header header={} path={}", ex.getHeaderName(), request.getRequestURI());

        ProblemDetailResponse body = new ProblemDetailResponse(
            ERROR_BASE_URI + "validation-error",
            "Required header missing",
            HttpStatus.BAD_REQUEST.value(),
            "Required request header '" + ex.getHeaderName() + "' is not present.",
            request.getRequestURI(),
            "VALIDATION_ERROR",
            MDC.get(TRACE_ID_MDC_KEY)
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.parseMediaType("application/problem+json"))
            .body(body);
    }

    /**
     * Catch-all for unexpected exceptions. Logs at ERROR; returns 500 without internal details.
     *
     * @param ex      the unexpected exception
     * @param request the current request
     * @return 500 Problem Detail (no stack trace in response body)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleUnexpected(
            final Exception ex,
            final HttpServletRequest request) {

        log.error("unexpected.exception path={}", request.getRequestURI(), ex);

        ProblemDetailResponse body = new ProblemDetailResponse(
            ERROR_BASE_URI + "internal-error",
            "Internal server error",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred. The request was not committed.",
            request.getRequestURI(),
            "INTERNAL_ERROR",
            MDC.get(TRACE_ID_MDC_KEY)
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.parseMediaType("application/problem+json"))
            .body(body);
    }

    // --- Private helpers ---

    /** Converts SNAKE_CASE error code to kebab-case URI slug. */
    private String kebab(final String errorCode) {
        return errorCode.toLowerCase().replace('_', '-');
    }

    /** Converts SNAKE_CASE error code to a short human-readable title. */
    private String toTitle(final String errorCode) {
        return switch (errorCode) {
            case "INSUFFICIENT_FUNDS"      -> "Insufficient funds";
            case "IDEMPOTENCY_KEY_CONFLICT" -> "Idempotency-Key conflict";
            case "TRANSFER_NOT_FOUND"      -> "Transfer not found";
            case "SOURCE_ACCOUNT_FROZEN"   -> "Source account frozen";
            case "ACCOUNT_FROZEN"          -> "Account frozen";
            case "DEPENDENCY_UNAVAILABLE"  -> "Service temporarily unavailable";
            default -> errorCode.charAt(0)
                + errorCode.substring(1).toLowerCase().replace('_', ' ');
        };
    }
}
