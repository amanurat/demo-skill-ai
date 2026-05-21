# Test Plan — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** QA P1 (shift-left — test plan only; no automation code yet)
> **Sprint:** SPRINT-2026-Q2-BC-01 (2026-05-21 to 2026-06-04)
> **QA artifact:** QA-P1-001
> **Input artifact:** BA-001 (`c4e9f1b2-7a3d-4c85-b6f0-1e8d2a5c9047`)
> **Status:** PLANNING phase — parallel with SA and Designer P1

---

## 1. Scope

### In Scope

| Story | Title | Priority |
|---|---|---|
| US-BC-001 | List accounts ranked by balance (descending) | MUST |
| US-BC-002 | Per-account detail row (type, balance, currency, last updated) | MUST |
| US-BC-003 | Dashboard meets p95 < 500ms for 10 accounts | MUST |
| US-BC-005 | Mobile-first responsive layout and accessibility (WCAG 2.1 AA) | SHOULD |

**ขอบเขตทดสอบครอบคลุมทุก AC ที่ BA กำหนด รวม 25 ACs (US-BC-001: 7, US-BC-002: 6, US-BC-003: 6, US-BC-005: 6)**

### Out of Scope (mirrors BA)

- US-BC-004: Multi-currency total / FX aggregation — DEFERRED
- US-BC-006..011: Filter, sparkline, hide/pin, export, joint-account, push notification
- Dormant/closed account display (excluded by BR-003; tested only at filter layer)
- Loan and credit-card balance types
- Event-driven cache invalidation (v2)
- Write or transaction operations (read-only sprint)
- Native iOS/Android app testing
- New OAuth2/auth flow (reuse existing)
- p95 < 500ms cold-cache SLA (documented as known v1 limitation per RISK-005; cold-cache gate is 800ms)

---

## 2. Test Pyramid Mix

**เป้าหมาย:** ปิรามิดกลับหัว (ice-cream cone) จะทำให้ pipeline ช้าและ flaky — ต้องมีฐาน unit กว้างที่สุด

```
           /\
          /E2E\              8 tests  (5%)
         /------\
        /Contract\           4 tests  (3%)
       /----------\
      /Integration \        18 tests (25%)
     /--------------\
    /  Unit (FE+BE)  \      22 tests (31%)
   /------------------\
  /Security (API sec) \      8 tests (11%)
 /--------------------\
/ Perf (k6/Gatling)    \     5 scripts (7%)
/----------------------\
/ A11y (axe + manual)  \     6 tests  (8%)
```

| Layer | Count | Mix % | Rationale |
|---|---|---|---|
| Unit (JUnit 5 + Jest) | 22 | 31% | Domain logic: ranking, filter, masking, balance_as_of sourcing, staleness flag |
| Integration (Testcontainers) | 18 | 25% | All endpoints + cache hit/miss/Redis-fail paths + audit emission |
| Contract (Pact) | 4 | 6% | AccountClient provider + audit-service consumer boundaries |
| E2E (Playwright/Cypress) | 8 | 11% | Happy path + IDOR + empty state + a11y smoke |
| Performance (k6) | 5 | 7% | Warm cache, cold cache, concurrent 50 VUs, cache hit ratio, AccountClient fan-out |
| Security (REST Assured) | 8 | 11% | IDOR, 401/403, masking in all surfaces, no balance in logs |
| A11y (axe-core + Lighthouse + manual) | 6 | 8% | WCAG 2.1 AA, Lighthouse ≥ 90, screen reader, keyboard, 200% font |
| **Total** | **71** | **100%** | |

---

## 3. Environments

| Environment | Purpose | Notes |
|---|---|---|
| Local dev | Unit tests, component tests | No external dependencies; mock AccountClient via WireMock |
| CI containers | Integration + contract tests | Testcontainers spins Postgres 16 + Redis 7 + Kafka per test run |
| Staging (demo target) | E2E + performance + a11y + security DAST | Pre-provisioned staging with realistic 10-account fixtures; used for all SLA measurements per BA NFR |

**หมายเหตุ:** การวัด p95 ต้องรันบน staging เสมอ — localhost latency ต่ำกว่าความเป็นจริงและให้ค่าที่ misleading

**Staging สำคัญมาก:** NFR ระบุชัดเจนว่า p95 < 500ms / p95 < 800ms วัดบน staging ด้วย 10-account fixture ไม่ใช่ localhost

---

## 4. Data Strategy

### Test Customer Profiles (synthetic data only — no production data per PDPA)

| Profile ID | Account Count | Account Types | Status Mix | Purpose |
|---|---|---|---|---|
| CUST-T01 | 0 | N/A | N/A | Empty state test (AC-001-E1) |
| CUST-T02 | 1 | SAVINGS | ACTIVE | Single account, no tie-break needed |
| CUST-T03 | 3 | SAVINGS, CURRENT, FIXED_DEPOSIT | ACTIVE | One of each type; distinct balances; label mapping test |
| CUST-T04 | 10 | Mix of SAVINGS (5), CURRENT (3), FIXED_DEPOSIT (2) | All ACTIVE | Performance fixture — 10-account SLA test |
| CUST-T05 | 4 | SAVINGS (2 equal balance) + CURRENT + FIXED_DEPOSIT | ACTIVE | Tie-break determinism (AC-001-H2) |
| CUST-T06 | 5 | SAVINGS (2 ACTIVE) + SAVINGS (1 DORMANT) + CURRENT (1 CLOSED) + FIXED_DEPOSIT (1 ACTIVE) | Mixed | Dormant/closed filter test (OPEN-003) |
| CUST-T07 | 3 | SAVINGS + CURRENT + LOAN (out-of-scope type) | ACTIVE | Account-type filter test (OPEN-004) |
| CUST-T08 | 1 | FIXED_DEPOSIT | ACTIVE, balance_as_of = null | Null balance_as_of graceful handling (AC-002-E2) |
| CUST-T09 | 1 | FIXED_DEPOSIT | ACTIVE, balance_as_of = 24h ago | Stale indicator (AC-002-E1) |
| CUST-B | 1 | SAVINGS | ACTIVE | Customer B — used only in IDOR tests; never returned to Customer A |

**PDPA ข้อควรระวัง:**
- ข้อมูลทั้งหมดต้องเป็น synthetic — ห้ามใช้ข้อมูล production ใดๆ
- `accountNumber` ในชุดทดสอบจะมีรูปแบบ `TEST-XXXX` เสมอ ก่อนทำการ mask
- `balance` ใช้ค่าที่ชัดเจนแบบ round numbers เพื่อง่ายต่อการ assert (45000.00, 30000.00, เป็นต้น)

### AccountClient Mock Strategy

**Integration tests → WireMock** (recommended):
- WireMock เป็น HTTP-level mock ที่ทดสอบ Feign client configuration ได้จริง
- Mock สถานการณ์: success response (200), timeout (simulate latency), 500 error, empty list
- ตั้งค่า WireMock stubs เป็น JSON fixtures ภายใต้ `src/test/resources/wiremock/`
- หลีกเลี่ยง `@MockBean` สำหรับ AccountClient เพราะจะข้าม HTTP serialization/deserialization

**Unit tests → Mockito**: mock AccountClient interface โดยตรงสำหรับ unit tests ของ service layer

### Redis Test Strategy

- **Integration tests:** Testcontainers Redis 7 (`@Container static GenericContainer<?> redis`)
- ใช้ `@Container static` (static annotation) เพื่อ reuse container ใน test class — ห้าม start/stop ทุก test method
- flush cache ระหว่าง test cases ที่ต้องการ cold-cache state โดยใช้ `RedisTemplate.getConnectionFactory().getConnection().flushAll()`

### Kafka Test Strategy

- **Integration tests:** Testcontainers Kafka (Confluent image)
- verify audit event โดยใช้ `KafkaTestUtils.getSingleRecord(consumer, topic, timeout)` ด้วย explicit timeout
- ห้ามใช้ `Thread.sleep()` สำหรับ Kafka assertions เด็ดขาด

---

## 5. Coverage Targets

| Metric | Threshold | Source |
|---|---|---|
| Overall line coverage | ≥ 80% | `banking-test-automation` SKILL.md + `spring-boot-banking` Testing Policy |
| Branch coverage | ≥ 70% | Every error path has ≥ 1 test |
| Critical financial paths (balance ranking, filter, masking) | ≥ 95% | `banking-test-automation` SKILL.md "money-handling code" rule |
| Mutation score (PIT) on balance/filter/masking packages | ≥ 70% | `banking-test-automation` SKILL.md |
| Contract test coverage | 100% of cross-service boundaries | Every consumer-provider pair must have a contract |

**หมายเหตุ:** Balance ranking, account filter, และ account-number masking ถือเป็น "money-handling code" ในบริบทนี้ เพราะเป็น financial data ที่กระทบต่อความถูกต้องและความน่าเชื่อถือ — ตั้ง threshold 95% สำหรับ package เหล่านั้น

---

## 6. Tooling Plan

### Backend (Java 21 + Spring Boot 3.x)

| Layer | Tool | Version | Notes |
|---|---|---|---|
| Unit | JUnit 5 + Mockito + AssertJ | JUnit 5.11, Mockito 5.x | No Spring context; < 100ms per test |
| Integration | Spring Boot Test + Testcontainers | Testcontainers 1.20.x | Real Postgres 16 + Redis 7 + Kafka 7.x |
| AccountClient mock (integration) | WireMock | 3.x (standalone or JUnit extension) | HTTP-level stub; replaces `@MockBean` for Feign |
| Contract | Pact JVM (consumer-driven) | 4.x | balance-dashboard-service as consumer; account-service and audit-service as providers |
| Mutation | PIT (pitest-maven-plugin) | 1.15.x | Scope: `com.example.balancedashboard.domain.*` + `com.example.balancedashboard.application.*` |
| Coverage report | JaCoCo | Maven plugin | Report during `mvn verify`; enforce thresholds via `<rules>` |
| Async assertion | Awaitility | 4.x | Kafka consumer waits; audit event waits |

### Frontend (Angular)

| Layer | Tool | Notes |
|---|---|---|
| Unit / component | Jest + Angular Testing Library | Pure pipes, formatters, ARIA attribute rendering |
| A11y automated | axe-core via `@axe-core/playwright` or `jest-axe` | Integrated into component tests + E2E |
| E2E | Playwright | Cross-browser; supports mobile viewport emulation |
| Lighthouse | Playwright + `lighthouse` npm package | Run against staging; assert score ≥ 90 |

### Performance

| Tool | Usage | SLA Gate |
|---|---|---|
| k6 | Primary perf tool; scripts in `infra/k6/balance-dashboard/` | p95 warm < 500ms; p95 cold < 800ms; cache hit ratio > 70%; error rate < 0.1% |
| Gatling | Optional alternative if team prefers JVM tooling | Same SLA gates |

**k6 script structure:**
- `warm-cache-load.js` — pre-warm Redis, then 30 VUs x 5 min sustained
- `cold-cache-load.js` — flush Redis before each iteration, 30 VUs x 5 min
- `concurrent-50-vus.js` — 50 VUs x 5 min, mixed warm/cold
- `cache-hit-ratio.js` — assert hit ratio > 70% after 5 min warm-up
- `accountclient-fanout.js` — verify single batched call vs N round-trips via tracing spans

---

## 7. Exit Criteria for QA Phase 2 (automation gate)

QA P2 starts only after `banking-security` approves code. The following must all be true before P2 sign-off:

1. All P0 test cases automated and green (zero failures)
2. All P1 test cases automated and green
3. Coverage thresholds met: line ≥ 80% overall; critical paths ≥ 95%; mutation ≥ 70%
4. Performance SLA met on staging: p95 warm < 500ms, p95 cold < 800ms
5. Cache hit ratio > 70% under sustained 5-min load
6. All 25 BA ACs have at least one automated test mapped to them
7. All 8 banking-specific must-have cases implemented (IDOR, audit-on-every-read, GET idempotency, ranking determinism, dormant filter, account-type filter, stale-snapshot degraded mode, PDPA masking)
8. Contract tests pass for AccountClient and audit-service boundaries
9. E2E happy path passes on staging
10. Lighthouse mobile a11y score ≥ 90 with evidence attached
11. No flaky tests (re-run 3x; all pass)
12. Test plan document updated with P2 results
13. `handoff-qa-p2-001.json` emitted with `quality_gate_passed: true`

---

## 8. Banking-Specific Test Requirements Summary

ข้อกำหนดเหล่านี้เป็น non-negotiable ต้องครอบคลุมใน P2 automation:

| # | Requirement | Test ID | Priority |
|---|---|---|---|
| 1 | IDOR guard: JWT sub mismatch → 403 + FORBIDDEN audit event | TC-SEC-001 | P0 |
| 2 | Audit on every read: cache hit AND cache miss both emit event | TC-INT-009, TC-INT-010 | P0 |
| 3 | GET idempotency: same request within TTL returns same snapshot | TC-INT-011 | P0 |
| 4 | Ranking determinism: tie-break by accountId ASC, consistent across calls | TC-UNIT-005, TC-INT-004 | P0 |
| 5 | Dormant/closed filter + audit still emitted on filtered access | TC-INT-006, TC-INT-007 | P0 |
| 6 | Account-type filter: loan/credit not returned even if in AccountClient response | TC-UNIT-004, TC-INT-005 | P0 |
| 7 | Stale-snapshot degraded mode: Redis unavailable → cached snapshot + staleness flag | TC-INT-014 | P1 |
| 8 | PDPA data minimization: masked accountNumber in JSON, DOM, and logs | TC-SEC-004, TC-SEC-005, TC-E2E-006 | P0 |

---

## 9. Known Gaps and Dependencies

### Dependencies that may affect test design (flagged for orchestrator)

| Dependency | Impact | Blocked? |
|---|---|---|
| SUBDEC-001: FIXED_DEPOSIT balance composition (principal-only vs principal+accrued) | TC-UNIT-003 (balance display) needs SA/TL confirmation before final assertion value | No — test can assert "balance reflects AccountInfo.balance value" without knowing composition |
| SUBDEC-002: AccountClient batch vs N-call API | TC-INT-013 (single batched call) needs TL to confirm endpoint signature | No — test plan stubs both; implementation selects correct stub |
| SUBDEC-003: Audit-service schema (existing vs new BalanceInquiry event type) | TC-INT-009/010 assertion on event schema fields (cacheHit, purpose) | No — test asserts fields exist regardless of event type name |
| SUBDEC-004: Service boundary (new service vs account-service extension) | Integration test base URL differs | No — test plan uses configurable `${balance.dashboard.base-url}` |
| Resilience4j circuit breaker threshold (SA decision pending) | TC-INT-015 (CB opens after N failures) requires SA-confirmed N value | Mark as "depends-on-SA-decision" — test skeleton ready |

### AC Gaps Identified (back-pressure to BA/orchestrator)

| Gap | Detail | Recommendation |
|---|---|---|
| AC-003-E2: Grafana panel observability | AC verifies Grafana panel is visible, but no testable assertion on metric values from code path | Recommend BA split into: (a) metrics emitted by service (testable via Micrometer registry assertion in integration test) + (b) Grafana panel visual verification (manual QA) |
| BR-006: PDPA consent reuse verification | BA states "verify consent scope at D2-D3 by banking-security" — no automated test can verify consent scope without knowing the consent registry schema | Flag to banking-security agent: confirm consent-registry API so QA can add automated consent-check test in P2 |
| AC-005-H2: VoiceOver/TalkBack manual test | Automated axe-core cannot fully verify VoiceOver announcement text and prosody | Plan as manual test with scripted tester steps; add to test-cases.md as TC-A11Y-005 (manual, P1) |

---

## 10. Observability Alignment

QA P2 sẽ verify metrics ผ่าน Micrometer registry assertions ใน integration tests:

| Metric | Test Assertion |
|---|---|
| `balance_dashboard_requests_total{status="200", cache_hit="true"}` | Increment by 1 on cache-hit request |
| `balance_dashboard_requests_total{status="403"}` | Increment by 1 on IDOR attempt |
| `balance_dashboard_cache_hit_ratio` | > 70% after 5-min warm-up in perf test |
| `balance_dashboard_audit_events_total{result="SUCCESS"}` | Increment on each successful request |
| `balance_dashboard_excluded_accounts_total{reason="STATUS_FILTER"}` | Increments when dormant/closed accounts exist for customer |
| `cache_miss_reason=REDIS_UNAVAILABLE` | Emitted when Redis connection fails |
