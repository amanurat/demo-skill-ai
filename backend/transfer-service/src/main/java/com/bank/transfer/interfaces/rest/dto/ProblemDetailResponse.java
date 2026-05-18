package com.bank.transfer.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 7807 Problem Details wire format extended with {@code code} and {@code traceId}.
 *
 * <p>Maps directly to the OpenAPI {@code ProblemDetail} schema. Used as the body
 * for all 4xx/5xx responses. The {@code traceId} is populated from the W3C
 * {@code traceparent} header propagated via MDC.
 *
 * <p>Content-Type for error responses: {@code application/problem+json}.
 *
 * @param type      URI reference identifying the problem type
 * @param title     short human-readable summary (safe for display — no PII)
 * @param status    HTTP status code
 * @param detail    human-readable explanation (safe for display — no PII, no secrets)
 * @param instance  URI of the specific occurrence of the problem
 * @param code      machine-readable error code from the taxonomy (e.g. INSUFFICIENT_FUNDS)
 * @param traceId   W3C traceparent id for cross-service correlation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetailResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String code,
    String traceId
) {}
