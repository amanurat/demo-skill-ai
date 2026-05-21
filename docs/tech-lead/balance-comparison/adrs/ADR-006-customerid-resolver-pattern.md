# ADR-006 — `CustomerIdResolver` Pattern (JWT `sub` as sole source of truth; structurally enforced)

> **Status:** ACCEPTED
> **Date:** 2026-05-21
> **Owner:** `banking-tech-lead`
> **Consumers:** `banking-backend-dev` (primary), `banking-reviewer-be`, `banking-security`, `banking-qa`
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Feature:** `balance-comparison`
> **Resolves:** Security Condition **C-3** (early consent-coverage review §5.3)
> **Related:**
> - [Security C-3](../../../security/balance-comparison/early-review-consent-coverage.md#condition-c-3-customerid-source-of-truth--jwt-sub-only)
> - [SA NFR `security_idor_guard_defense_in_depth`](../../../sa/balance-comparison/handoff-sa-001.json)
> - [SA architecture §4.1](../../../sa/balance-comparison/architecture.md)
> - [BA AC-001-E2 (cross-customer attempt → 403 + audit FORBIDDEN)](../../../ba/balance-comparison/user-stories.md)
> - [ADR-007 audit publisher (FORBIDDEN audit emit)](./ADR-007-audit-event-publisher.md)

---

## 1. Context

Security Condition C-3 (from the D2-D3 early-look) flagged a class of high-impact bug:

> Spring's `@AuthenticationPrincipal Jwt jwt` and a `@RequestHeader("X-Customer-Id") String customerId` parameter are easy to mix up; one PR could quietly switch the source from JWT to header and bypass IDOR.

The current defense-in-depth design relies on three independent enforcement points:

1. **api-gateway** validates JWT (RS256 + JWKS), injects `X-Customer-Id: {jwt.sub}`, and strips client-supplied `X-Customer-Id` values.
2. **balance-dashboard-service (BDS)** re-asserts at the controller layer: header value must equal `jwt.sub`, else HTTP 403 + audit `result=FORBIDDEN`.
3. **Downstream calls** to `account-service.listAccountsByCustomer(customerId)` use `customerId` derived from JWT — never the header.

Layer 2 and 3 require that `customerId` resolution is **structurally** consistent across the entire BDS codebase. Validating-then-trusting the header is a known anti-pattern (CWE-639, CWE-565) — it leaves a single-point-of-failure where a controller refactor can quietly switch the source.

This ADR makes the wrong source **impossible to use** rather than "validated and then trusted".

## 2. Decision

We adopt the **`CustomerIdResolver` + `IborCheckFilter`** pattern. Two collaborators, one responsibility each:

### 2.1 `IborCheckFilter` — IDOR detection only (`OncePerRequestFilter`)

```java
package com.bank.balancedashboard.infrastructure.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Defense-in-depth IDOR filter for balance-dashboard-service.
 *
 * Runs AFTER Spring Security has populated SecurityContext. Reads the X-Customer-Id
 * header (injected by api-gateway from JWT.sub) and compares to JWT sub claim.
 *
 * - Mismatch (tampered header) -> emit audit FORBIDDEN + return HTTP 403, abort chain.
 * - Match (or header absent)   -> proceed; controller resolves customerId via CustomerIdResolver.
 *
 * THIS IS THE ONLY CLASS PERMITTED TO READ request.getHeader("X-Customer-Id").
 * An ArchUnit rule enforces this (see ADR-006 §3 + implementation-notes §arch-unit).
 *
 * Resolves Security Condition C-3.
 */
@Component
@Order(SecurityFilterAfterAuthenticationOrder.ORDER) // runs after Spring Security
public class IborCheckFilter extends OncePerRequestFilter {

    static final String HEADER_CUSTOMER_ID = "X-Customer-Id";

    private final AuditEventPublisher auditEventPublisher;

    public IborCheckFilter(AuditEventPublisher auditEventPublisher) {
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            // Spring Security already rejected this — defensive no-op.
            chain.doFilter(req, res);
            return;
        }
        Jwt jwt = jwtAuth.getToken();
        String jwtSub = jwt.getSubject();
        String headerCustomerId = req.getHeader(HEADER_CUSTOMER_ID);

        if (headerCustomerId != null && !headerCustomerId.equals(jwtSub)) {
            auditEventPublisher.publish(AuditEventRecord.forbidden(
                /* actorId */ jwtSub,
                /* correlationId */ CorrelationIdHolder.get(),
                /* channel */ Channel.fromRequest(req)
            ));
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType("application/problem+json");
            res.getWriter().write(ProblemDetail.forbidden("IDOR_HEADER_MISMATCH").asJson());
            return; // do NOT chain.doFilter — abort
        }
        chain.doFilter(req, res);
    }
}
```

Key properties:
- The filter is the **only** code site permitted to call `request.getHeader("X-Customer-Id")`.
- Mismatch path always emits audit BEFORE returning 403 (BR-014 — cache never short-circuits audit; same rule applies to filters).
- The filter does NOT mutate `SecurityContext` or set `customerId` anywhere — it only **decides whether to abort**.

### 2.2 `CustomerIdResolver` — the only way controllers / services get `customerId`

```java
package com.bank.balancedashboard.infrastructure.rest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single source of truth for deriving customerId from an authenticated request.
 *
 * Always returns the JWT 'sub' claim parsed as UUID. NEVER reads HTTP headers,
 * query strings, or path variables. This is the ONLY abstraction permitted to
 * produce a customerId for downstream business logic.
 *
 * Usage:
 *   private final CustomerIdResolver customerIdResolver;
 *   public ResponseEntity<...> getDashboard(@AuthenticationPrincipal Jwt jwt) {
 *       UUID customerId = customerIdResolver.resolve(jwt);
 *       return useCase.loadDashboard(customerId);
 *   }
 *
 * Resolves Security Condition C-3.
 */
@Component
public class CustomerIdResolver {

    /**
     * @param jwt the authenticated JWT, never null (Spring Security guarantees presence
     *            because controller is @PreAuthorize("hasAuthority('SCOPE_accounts:read')")).
     * @return customerId UUID parsed from JWT sub claim.
     * @throws InvalidJwtSubException if sub claim is missing or not a UUID.
     */
    public UUID resolve(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new InvalidJwtSubException("JWT sub claim missing");
        }
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new InvalidJwtSubException("JWT sub claim is not a UUID: " + sub, e);
        }
    }
}
```

Key properties:
- **No `HttpServletRequest` injection.** The method takes a `Jwt` argument by design — auditing the signature alone proves the source.
- Throws on malformed `sub` rather than silently returning null — fails closed.
- Pure: testable without Spring context.

### 2.3 Controller wiring (mandatory pattern)

```java
@RestController
@RequestMapping("/api/v1/balance-dashboard")
@PreAuthorize("hasAuthority('SCOPE_accounts:read')")
@ConditionalOnProperty(name = "balance-dashboard.enabled", havingValue = "true", matchIfMissing = false)
public class BalanceDashboardController {

    private final LoadDashboardUseCase useCase;
    private final CustomerIdResolver customerIdResolver;

    public BalanceDashboardController(LoadDashboardUseCase useCase,
                                      CustomerIdResolver customerIdResolver) {
        this.useCase = useCase;
        this.customerIdResolver = customerIdResolver;
    }

    @GetMapping
    public ResponseEntity<BalanceDashboardResponse> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        UUID customerId = customerIdResolver.resolve(jwt);
        // NEVER: request.getHeader("X-Customer-Id"), @RequestHeader, @PathVariable, @RequestParam for customerId
        return ResponseEntity.ok(useCase.loadDashboard(customerId));
    }
}
```

### 2.4 Static enforcement (ArchUnit rule — mandatory)

`backend/balance-dashboard-service/src/test/java/com/bank/balancedashboard/arch/CustomerIdSourceRule.java`:

```java
@AnalyzeClasses(packages = "com.bank.balancedashboard")
class CustomerIdSourceRule {

    @ArchTest
    static final ArchRule only_filter_reads_x_customer_id_header =
        noClasses()
            .that().resideOutsideOfPackage("..infrastructure.rest..")
            .or().haveSimpleNameNotEndingWith("IborCheckFilter")
            .should()
            .callMethod(HttpServletRequest.class, "getHeader", String.class)
            .as("Only IborCheckFilter may call request.getHeader(\"X-Customer-Id\"). " +
                "All other code MUST derive customerId via CustomerIdResolver. See ADR-006.");

    @ArchTest
    static final ArchRule no_request_header_customer_id_annotation =
        noClasses()
            .should()
            .beAnnotatedWith(RestController.class)
            .andShould()
            .containAnyMethodsThat(haveParameterAnnotatedWith(RequestHeader.class)
                .withValueMatching("X-Customer-Id"))
            .as("Controllers MUST NOT bind X-Customer-Id via @RequestHeader. See ADR-006.");
}
```

Backup option (if ArchUnit setup is delayed): Spotbugs custom detector `com.bank.spotbugs.CustomerIdHeaderDetector` flagging the same anti-pattern at SAST time. ArchUnit is the primary; Spotbugs is suspenders.

### 2.5 Integration test (mandatory — proves the design)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("integration")
class IborGuardIntegrationTest {

    @Test
    void tamperedHeader_returnsForbidden_andEmitsAuditForbidden() {
        // Given: valid JWT for customer A
        String jwt = jwtFixture.forCustomer(CUSTOMER_A_ID);

        // When: request includes X-Customer-Id of customer B (tampered)
        var resp = restTemplate.exchange(
            "/api/v1/balance-dashboard",
            HttpMethod.GET,
            new HttpEntity<>(headers(
                "Authorization", "Bearer " + jwt,
                "X-Customer-Id", CUSTOMER_B_ID.toString()
            )),
            ProblemDetail.class
        );

        // Then: 403 Problem-Detail
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getType()).hasToString(
            "https://docs.bank.com/errors/idor-header-mismatch");

        // And: audit FORBIDDEN emitted with actorId = JWT sub (A), not header value (B)
        AuditEventRecord captured = auditTestHarness.lastEmitted();
        assertThat(captured.result()).isEqualTo(Result.FORBIDDEN);
        assertThat(captured.actorId()).isEqualTo(CUSTOMER_A_ID);
        assertThat(captured.purpose()).isEqualTo("balance-inquiry");
        // And: ZERO downstream calls to account-service
        verify(accountClient, never()).listAccountsByCustomer(any());
    }

    @Test
    void matchingHeader_proceedsNormally() {
        String jwt = jwtFixture.forCustomer(CUSTOMER_A_ID);
        var resp = restTemplate.exchange(
            "/api/v1/balance-dashboard",
            HttpMethod.GET,
            new HttpEntity<>(headers(
                "Authorization", "Bearer " + jwt,
                "X-Customer-Id", CUSTOMER_A_ID.toString()
            )),
            BalanceDashboardResponse.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void absentHeader_proceedsNormally_jwtSubIsSourceOfTruth() {
        String jwt = jwtFixture.forCustomer(CUSTOMER_A_ID);
        var resp = restTemplate.exchange(
            "/api/v1/balance-dashboard",
            HttpMethod.GET,
            new HttpEntity<>(headers("Authorization", "Bearer " + jwt)),
            BalanceDashboardResponse.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // customerId derived from JWT sub, regardless of header presence
    }
}
```

## 3. Consequences

### 3.1 Positive

- **Structurally impossible to use the wrong source.** A future PR cannot quietly switch `customerId` from JWT to header — ArchUnit fails the build.
- **One audit-FORBIDDEN call site.** Tampering is detected and emitted in exactly one place — easy to verify against BR-014 + AC-001-E2.
- **Testable in isolation.** `CustomerIdResolver` takes a `Jwt` argument and has no Spring deps — pure unit test.
- **Clear ownership separation.** `IborCheckFilter` *detects*; `CustomerIdResolver` *provides*. No mixed responsibility.
- **Pattern is reusable.** Future services (loan-dashboard, statement-service) can copy the same two classes verbatim.

### 3.2 Negative

- **Extra class** (`CustomerIdResolver`) for what could be a one-line `jwt.getSubject()` — paid back the first time someone tries to refactor a controller.
- **ArchUnit dependency** added to BDS test scope. Mitigation: ArchUnit is already in the monorepo's parent POM (used by money-transfer); zero net cost.

### 3.3 Risks

- **Spring filter ordering** — IborCheckFilter must run AFTER `JwtAuthenticationFilter`, otherwise `SecurityContextHolder.getContext().getAuthentication()` is null. Mitigation: explicit `@Order` constant `SecurityFilterAfterAuthenticationOrder.ORDER`; integration test covers unauthenticated case.
- **Header injection at api-gateway is the precondition.** If gateway is bypassed (e.g., direct pod-to-pod call inside the cluster), header is absent but JWT must still be present. Filter handles this correctly (absent header → proceed). Pod-to-pod auth is enforced by NetworkPolicy (DevOps P1).

## 4. Alternatives considered (rejected)

| Option | Rejected because |
|---|---|
| **Trust `X-Customer-Id` header value directly** | The exact anti-pattern Security C-3 forbids. Any header validation step that THEN uses the header value as the working `customerId` leaves a one-line refactor away from full IDOR. **REJECTED.** |
| **Gateway-only enforcement (no BDS-side check)** | Defense-in-depth lost. Bypassing the gateway (misconfigured ingress, direct pod call, future service-mesh egress mistake) = full IDOR. NFR `security_idor_guard_defense_in_depth` mandates 3 independent layers. **REJECTED.** |
| **No `CustomerIdResolver` abstraction — every controller writes `jwt.getSubject()` inline** | Works for one controller; fragile when the service grows (loan-dashboard, statement-service reuse). No central place to add (e.g., UUID format validation, future multi-tenancy claims, future "actAs" admin support). **REJECTED.** |
| **`@AuthenticationPrincipal` custom method-argument-resolver that returns `UUID customerId` directly** | Tempting but fights Spring's argument-resolver contract (it expects to return the principal type, not a derived value). Also less explicit at the call site (`UUID customerId` vs `customerIdResolver.resolve(jwt)`) — magic is a code-review hazard. **REJECTED.** |
| **`HandlerInterceptor` instead of `OncePerRequestFilter`** | Interceptors run inside DispatcherServlet — `SecurityContext` is populated, but Spring Security responses (401/403) at filter chain layer can sometimes bypass interceptors. Filter is the correct ring. **REJECTED.** |
| **Custom Spring `AuthenticationConverter` mapping JWT.sub → custom Principal with `customerId` field** | Adds a custom principal type that every controller must accept — invasive across the monorepo. Doesn't add safety beyond `CustomerIdResolver`. **REJECTED for now**; reconsider if multi-tenancy claims are added. |

## 5. Acceptance criteria

- [ ] `IborCheckFilter` class exists at `backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/infrastructure/rest/IborCheckFilter.java`.
- [ ] `CustomerIdResolver` class exists at the same package.
- [ ] `BalanceDashboardController` accepts only `@AuthenticationPrincipal Jwt jwt`; uses `customerIdResolver.resolve(jwt)`.
- [ ] ArchUnit rule `CustomerIdSourceRule` passes; deliberate violation (test fixture) fails the rule.
- [ ] Integration test `IborGuardIntegrationTest` covers: tampered header → 403 + audit FORBIDDEN; matching header → 200; absent header → 200.
- [ ] No `@RequestHeader("X-Customer-Id")` anywhere outside `IborCheckFilter`.
- [ ] No `@RequestParam("customerId")`, no `@PathVariable("customerId")` anywhere in BDS.

---

*ADR-006 · banking-tech-lead · 2026-05-21 · resolves Security C-3*
