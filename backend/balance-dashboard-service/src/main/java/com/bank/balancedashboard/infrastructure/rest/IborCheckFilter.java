package com.bank.balancedashboard.infrastructure.rest;

import com.bank.balancedashboard.application.port.out.AuditEventPublisher;
import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.balancedashboard.domain.audit.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Defense-in-depth IDOR filter for balance-dashboard-service.
 *
 * <p>Runs AFTER Spring Security has populated SecurityContext. Reads the
 * {@code X-Customer-Id} header (injected by api-gateway from JWT.sub) and compares
 * to the JWT {@code sub} claim.
 *
 * <ul>
 *   <li>Mismatch (tampered header) → emit audit FORBIDDEN + return HTTP 403, abort chain.</li>
 *   <li>Match or header absent → proceed; controller resolves customerId via CustomerIdResolver.</li>
 * </ul>
 *
 * <p><strong>THIS IS THE ONLY CLASS PERMITTED TO READ request.getHeader("X-Customer-Id").</strong>
 * ArchUnit {@code CustomerIdSourceRule} enforces this at build time (ADR-006 §2.4).
 *
 * <p>The filter does NOT set customerId anywhere — it only decides whether to abort.
 * After passing through, the controller calls {@code CustomerIdResolver.resolve(jwt)} to
 * derive customerId from JWT sub for all business logic.
 *
 * <p>Resolves Security Condition C-3.
 */
@Component
@Order(IborCheckFilter.FILTER_ORDER)
public class IborCheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IborCheckFilter.class);

    /**
     * Order constant: must run AFTER Spring Security filter chain (which sets SecurityContext).
     * Spring Security's FilterSecurityInterceptor is around Integer.MAX_VALUE - 100;
     * we run at MAX_VALUE - 50 (later = after security context is populated).
     */
    static final int FILTER_ORDER = Integer.MAX_VALUE - 50;

    /**
     * THE ONLY CONSTANT HOLDING "X-Customer-Id" in this service.
     * ArchUnit rule ensures no other class references this header directly.
     */
    static final String HEADER_CUSTOMER_ID = "X-Customer-Id";

    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public IborCheckFilter(AuditEventPublisher auditEventPublisher,
                           ObjectMapper objectMapper) {
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            // Spring Security has not yet authenticated — let Spring Security handle it
            chain.doFilter(req, res);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String jwtSub = jwt.getSubject();

        // THIS IS THE ONLY PERMITTED CALL TO getHeader("X-Customer-Id") IN THE ENTIRE CODEBASE
        String headerCustomerId = req.getHeader(HEADER_CUSTOMER_ID);

        if (headerCustomerId != null && !headerCustomerId.equals(jwtSub)) {
            // IDOR tamper detected: header value does not match JWT sub
            UUID actorId = parseUuidOrNull(jwtSub);
            // R-BE-205: derive correlationId from the active OTel span so the FORBIDDEN audit
            // record carries the same trace ID as logs/spans — operators can correlate in Tempo.
            // Falls back to a random UUID when no active OTel span exists (e.g., tests).
            Span currentSpan = Span.current();
            String correlationId = currentSpan.getSpanContext().isValid()
                    ? currentSpan.getSpanContext().getTraceId()
                    : UUID.randomUUID().toString();
            Channel channel = Channel.fromUserAgent(req.getHeader("User-Agent"));

            // Audit FORBIDDEN before aborting (ADR-006 §2.5, ADR-007 call site 2)
            if (actorId != null) {
                auditEventPublisher.publish(
                        AuditEventRecord.forbidden(actorId, correlationId, channel)
                );
            }

            log.warn("idor.tamper.detected jwtSub={} headerValue={} correlationId={}",
                    maskId(jwtSub), maskId(headerCustomerId), correlationId);

            // Return 403 Problem-Detail — abort filter chain
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType("application/problem+json;charset=UTF-8");

            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("type", "https://errors.bank.local/balance-dashboard/forbidden");
            problem.put("title", "ไม่สามารถเข้าถึงข้อมูลได้");
            problem.put("status", HttpStatus.FORBIDDEN.value());
            problem.put("detail", "คำขอนี้ไม่ได้รับอนุญาต");
            problem.put("instance", "/api/v1/balance-dashboard");
            problem.put("correlationId", correlationId);
            problem.put("code", "FORBIDDEN");

            res.getWriter().write(objectMapper.writeValueAsString(problem));
            return; // do NOT call chain.doFilter — abort
        }

        chain.doFilter(req, res);
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Masks UUID for structured logging — shows only first 8 chars. Never logs full ID. */
    private String maskId(String id) {
        if (id == null || id.length() < 8) return "***";
        return id.substring(0, 8) + "***";
    }
}
