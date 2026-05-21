package com.bank.balancedashboard.infrastructure.rest;

import com.bank.balancedashboard.domain.exception.DashboardUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps domain exceptions to RFC 7807 Problem Detail responses.
 *
 * <p>Security C-2 compliance: error messages are DELIBERATELY GENERIC.
 * No balance values, account numbers, account IDs, or upstream service names
 * are included in any error response.
 *
 * <p>Content-Type: {@code application/problem+json} on ALL 4xx/5xx responses.
 * {@code code} field enables FE to branch behavior per error type.
 */
@RestControllerAdvice
public class ProblemDetailAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAdvice.class);
    private static final String PROBLEM_JSON = "application/problem+json";

    /**
     * Maps DashboardUnavailableException → HTTP 503 with Retry-After: 1.
     * All Resilience4j failure modes (timeout, CB-open, bulkhead, 5xx after retries).
     */
    @ExceptionHandler(DashboardUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleDashboardUnavailable(
            DashboardUnavailableException e) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("upstream.unavailable correlationId={}", correlationId, e);

        Map<String, Object> problem = buildProblem(
                "https://errors.bank.local/balance-dashboard/unavailable",
                "ไม่สามารถโหลดข้อมูลได้ในขณะนี้",
                503,
                "กรุณาลองใหม่อีกครั้งในอีกสักครู่",
                correlationId,
                "SERVICE_UNAVAILABLE"
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.valueOf(PROBLEM_JSON))
                .header("Retry-After", "1")
                .header("X-Correlation-Id", correlationId)
                .body(problem);
    }

    /**
     * Maps InvalidJwtSubException → HTTP 403 Forbidden.
     * JWT sub claim is missing or not a valid UUID.
     */
    @ExceptionHandler(InvalidJwtSubException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJwtSub(InvalidJwtSubException e) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("jwt.sub.invalid correlationId={}", correlationId, e);

        Map<String, Object> problem = buildProblem(
                "https://errors.bank.local/balance-dashboard/forbidden",
                "ไม่สามารถเข้าถึงข้อมูลได้",
                403,
                "คำขอนี้ไม่ได้รับอนุญาต",
                correlationId,
                "FORBIDDEN"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.valueOf(PROBLEM_JSON))
                .header("X-Correlation-Id", correlationId)
                .body(problem);
    }

    /**
     * Safe fallback for unexpected RuntimeExceptions (programming errors, not upstream failures).
     * Maps to HTTP 500 — FE MUST NOT auto-retry developer bugs.
     * No Retry-After header.
     * No stack trace or internal detail is leaked per Security C-2.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGenericRuntimeException(RuntimeException e) {
        String correlationId = UUID.randomUUID().toString();
        // Log at ERROR — ops alert should fire here (not WARN)
        log.error("internal.error correlationId={}", correlationId, e);

        Map<String, Object> problem = buildProblem(
                "https://errors.bank.local/balance-dashboard/internal-error",
                "เกิดข้อผิดพลาดภายในระบบ",
                500,
                "กรุณาติดต่อทีมงานหากปัญหายังคงอยู่",
                correlationId,
                "INTERNAL_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.valueOf(PROBLEM_JSON))
                .header("X-Correlation-Id", correlationId)
                .body(problem);
    }

    private Map<String, Object> buildProblem(String type, String title, int status,
                                              String detail, String correlationId, String code) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", type);
        problem.put("title", title);
        problem.put("status", status);
        problem.put("detail", detail);
        problem.put("instance", "/api/v1/balance-dashboard");
        problem.put("correlationId", correlationId);
        problem.put("code", code);
        return problem;
    }
}
