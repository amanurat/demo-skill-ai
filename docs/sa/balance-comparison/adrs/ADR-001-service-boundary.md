# ADR-001. Service Boundary — New `balance-dashboard-service` vs. Endpoint on `account-service`

- **Status:** accepted
- **Date:** 2026-05-21
- **Deciders:** banking-solution-architect (with input from banking-pm risk register)
- **Tags:** service-boundary, bounded-context, ddd, cqrs
- **Resolves:** SUBDEC-004 (from BA-001)

## Context and Problem

US-BC-001/002/003 require aggregating a customer's accounts, ranking by balance, caching the result, and emitting an audit event on each retrieval. This logic must live somewhere in the existing service topology. There are two viable homes:

1. Add a new endpoint `GET /api/v1/accounts/dashboard` directly inside the existing `account-service`.
2. Stand up a new `balance-dashboard-service` as a sibling in the Accounts bounded context.

The BA artifact (`SUBDEC-004`) defaults to "new service" but explicitly asks SA to own the trade-off ADR.

## Decision Drivers

- **Reuse-first policy** (PM risk register, RISK-002 mitigation) — we must NOT rebuild `AccountClient` or `AccountInfo`.
- **10-day demo deadline** (RISK-002) — chosen option must be demoable.
- **NFR p95 < 500ms warm / < 800ms cold** — caching layer is essential; cache must be feature-specific.
- **Single-responsibility / Conway's Law** — `account-service` is on the critical path of every money transfer (S3 ADR-006). Adding a read-aggregation surface increases its blast radius.
- **Cache layer pollution** — a per-customer Redis cache for dashboard is feature-specific; embedding it in `account-service` mixes two cache concerns (existing tier-limit cache + new dashboard cache).
- **Independent deploy + rollback** — demo-day must be able to roll back balance-dashboard without touching the transfer flow.
- **Future-proof for v1.1** — event-driven cache invalidation (deferred per ADR-002) is easier to wire into a service whose only job is the dashboard.

## Considered Options

### Option A — Add endpoint to existing `account-service`

- ✅ Zero new deployable artifacts; fastest scaffolding.
- ✅ No new Helm chart, no new container image, no new K8s manifests.
- ❌ Mixes responsibilities: `account-service` becomes both authoritative CRUD store AND read-side aggregator + cache layer.
- ❌ Cache layer (`balance-dashboard:*` namespace) pollutes the service's Redis usage that already holds `customer_tier_limit:*` cache (money-transfer convention).
- ❌ Adds read-load to a service that is on the synchronous critical path of every transfer; a misbehaving cache layer could cascade into transfer p95 (money-transfer S3 RISK-006: "Account-service becomes synchronous bottleneck").
- ❌ Couples balance-dashboard demo rollback to `account-service` deployment.
- ❌ Conway: dashboard is a different feature concern; mixing it into account CRUD violates SRP and breaks future ownership clarity.
- ❌ Audit-event extension (ADR-003) would touch a service with much wider blast radius.

### Option B — NEW `balance-dashboard-service` (sibling in Accounts BC) ← CHOSEN

- ✅ Clean SRP: one service = one feature = one deploy = one rollback.
- ✅ Cache namespace owned cleanly: `balance-dashboard:*`.
- ✅ Decouples demo-rollback from money-transfer / `account-service` flows.
- ✅ Read-aggregation as a CQRS read-side projection — well-understood pattern for bounded contexts.
- ✅ Account-service stays focused on its existing authoritative responsibilities.
- ✅ Independent scaling profile: dashboard is read-heavy at unpredictable times (dashboard refreshes), account-service is write-heavy during transfer hours.
- ✅ Future event-driven invalidation (v1.1) lands in a service whose only job is cache.
- ⚠️ One more Helm chart, container image, Maven module, Grafana panel, NetworkPolicy.
- ⚠️ One more network hop (gateway → BDS → account-service) — but this hop is unavoidable in any design that uses Resilience4j + caching upstream of `account-service`.
- ⚠️ TL must add `listAccountsByCustomer(customerId)` endpoint to `account-service` regardless (SUBDEC-002), so neither option avoids touching `account-service` at the contract level.

### Option C — Library + endpoint in `account-service`

- "Compromise": expose logic as a Spring auto-config library that `account-service` mounts.
- ❌ Still pollutes `account-service` runtime with dashboard cache and Kafka producer.
- ❌ Couples deploys.
- ❌ Worst of both worlds: complexity of two artifacts without the isolation benefit.

## Decision Outcome

**Chosen: Option B — new `balance-dashboard-service`.**

Rationale: the operational benefits (isolation, independent rollback, clean cache namespace, no blast-radius increase on `account-service`) outweigh the ~1 day of additional scaffolding work. Reuse-first policy is preserved because the new service is a **thin aggregator**: it depends on `AccountClient`, `AccountInfo`, OAuth2/JWT filter, `audit-lib`, `observability-lib` — all reused unchanged. The only NEW code is the hexagonal layout in `balance-dashboard-service/`. Scaffolding cost is one-day work for the backend dev; the operational payoff is multi-sprint.

### Consequences

- ✅ Demo-day rollback is single-service: `helm rollback balance-dashboard-service-staging`.
- ✅ Money-transfer flow is unaffected by any dashboard incident (RISK-006 mitigation extension).
- ✅ Cache key prefix `balance-dashboard:*` is owned cleanly; no naming-conflict risk.
- ✅ Hexagonal layout keeps domain logic (`Ranker`, `EligibilityPolicy`) pure-Java and unit-testable in milliseconds.
- ⚠️ DevOps P1 must add: new Helm chart, container image build pipeline, K8s manifest, NetworkPolicy, Grafana panel. Estimated +0.5 day vs Option A — acceptable.
- ⚠️ One additional network hop in cold-cache path: gateway → BDS → account-service. Time budget shows 300ms `AccountClient` + ~50ms BDS work + ~20ms gateway = 370ms, leaving 430ms headroom under 800ms SLA. ✅ Safe.
- ⚠️ SUBDEC-002 (new batched endpoint on `account-service`) is required regardless of this decision.

### Consequences for SUBDEC-001 (FIXED_DEPOSIT balance composition)

Because `balance-dashboard-service` is a thin aggregator, the FIXED_DEPOSIT balance semantics are **delegated to `account-service` / `ledger-service`**. The BA default ("principal + accrued interest as of `balance_as_of`") is honored by `balance-dashboard-service` if and only if `account-service` returns it that way. If the ledger returns principal-only, that becomes a `ledger-service` concern, not ours. **Action for Tech Lead:** confirm `AccountInfo.balance` semantics for `accountType=FIXED_DEPOSIT` during OpenAPI authoring; if principal-only, escalate to PM/BA per SUBDEC-001 fallback rule.

## Demo-Timeline Reality Check

Time cost comparison (rough):

| Step | Option A (extend account-service) | Option B (new service) ← chosen |
|---|---|---|
| Maven module scaffolding | 0 | 0.25 day |
| Hexagonal layout + base classes | 0.25 day | 0.5 day |
| Helm chart + K8s manifests | 0 | 0.25 day |
| Wire AccountClient + Redis + Audit publisher | 1 day | 1 day |
| Risk on cross-team contention with account-service deploys | HIGH | LOW |
| **Total** | **1.25 day + risk** | **2 day no risk** |

The extra ~0.75 day buys deploy isolation and clean rollback path — worth it for a demo where one regression on Option A could brick the money-transfer demo too.

## Links

- [ADR-002 cache strategy](ADR-002-cache-strategy.md)
- [ADR-003 audit event evolution](ADR-003-audit-event-evolution.md)
- [ADR-004 server-side ranking](ADR-004-server-side-ranking.md)
- [Service decomposition](../service-decomposition.md)
- Money-transfer S3 ADR-006: payee-service bounded context separation (analogous reasoning)
- Money-transfer S3 RISK-006: account-service sync bottleneck (this ADR is one mitigation)
- BA SUBDEC-004: `docs/ba/balance-comparison/handoff-ba-001.json`
- PM RISK-002 (deadline): `docs/pm/balance-comparison/risk-register.md`
