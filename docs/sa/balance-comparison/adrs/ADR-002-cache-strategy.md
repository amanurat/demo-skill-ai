# ADR-002. Cache Strategy — TTL-only for v1 vs. Event-Driven Invalidation

- **Status:** accepted
- **Date:** 2026-05-21
- **Deciders:** banking-solution-architect
- **Tags:** caching, performance, redis, event-driven

## Context and Problem

US-BC-003 requires p95 < 500ms warm / < 800ms cold. The only way to meet 500ms warm at 50 peak concurrent users is to cache. The cache must include `balance_as_of` (BR-013) so the "Last updated" display is accurate. Two strategies are viable:

1. **TTL-only:** cache for 30s, no active invalidation; staleness bounded by TTL window.
2. **Event-driven invalidation:** consume `AccountDebited` / `AccountCredited` Kafka events; on receive, DEL the affected customer's cache key.

The BA artifact (BR-012) defaults to TTL-only for v1 and explicitly defers event-driven invalidation. SA confirms the rationale via this ADR.

## Decision Drivers

- **Demo deadline (10 days, RISK-002).** Every deferrable complexity should be deferred.
- **Acceptable staleness ≤ 30s.** Balance dashboard is informational, not transactional. A balance that's 29 seconds stale is acceptable per BA AC.
- **`balance_as_of` already conveys ledger-freshness.** The UI shows "Last updated" + "may be stale" indicator (BR-013) — so the customer already sees the actual freshness even if cache itself is fresh.
- **Event-driven invalidation requires a customer-lookup-from-accountId.** `AccountDebited` carries `accountId`, not `customerId`. Resolving customerId on every account event requires either: (a) calling `account-service`, (b) a local mapping table in `balance-dashboard-service` (which contradicts "no RDBMS" decision in ADR-001), (c) embedding `customerId` in account events as a new field (requires money-transfer schema evolution + producer change).
- **Cache hit ratio target > 70%.** Need a TTL high enough that successive dashboard refreshes within a session reuse cache. 30s is a reasonable session-refresh window; if user navigates dashboard → another page → back, the cache likely still serves.
- **Read-staleness vs write-frequency.** A retail customer's balance typically changes a few times per day, not per second. TTL-only is appropriate for low-write-frequency domains.

## Considered Options

### Option A — TTL-only, 30s ← CHOSEN for v1

- ✅ Simple: 2 lines of code (`SETEX` on write, `GET` on read).
- ✅ Bounded staleness ≤ 30s; combined with BR-013 60s staleness indicator, customer never sees grossly stale data unknowingly.
- ✅ No Kafka consumer to operate; no cross-service event contract to maintain.
- ✅ Cache-hit ratio target > 70% is reasonable: assuming ~50% of users refresh within 30s of opening, hit ratio is ~70-80% in practice.
- ✅ No customerId-from-accountId lookup problem.
- ⚠️ A balance modified at t=0 won't reflect in dashboard until cache expires at t=30s. UI "may be stale" indicator activates if `balance_as_of > 60s old` — but if cache holds a 25s-old `balance_as_of` and was written 25s ago, the row may show stale=false while another customer's view sees the post-mutation value. This is acceptable for v1 because dashboard is read-only and stakes are low.

### Option B — Event-driven invalidation (consume AccountDebited / AccountCredited)

- ✅ Cache always reflects latest ledger.
- ✅ Better UX on rapid balance changes (e.g., right after a transfer completes).
- ❌ **Customer-lookup problem:** `AccountDebited` event payload (per money-transfer S3 ADR-003) carries `accountId`, not `customerId`. Three subtions, none cheap for v1:
  - Add `customerId` to AccountDebited/AccountCredited Avro schema → coordinate with money-transfer team → schema-evolution check → producer code change. Schedule risk.
  - Call `account-service` on every event to resolve customerId → adds sync load to account-service on hot path → defeats the purpose.
  - Maintain `account → customer` mapping table in `balance-dashboard-service` → contradicts ADR-001 ("no RDBMS") → introduces a new sync point.
- ❌ Adds Kafka consumer group + consumer lag monitoring + dead-letter handling.
- ❌ Eventual consistency window between event publish and cache DEL — TTL still needed as backstop.
- ❌ +2 SP minimum (consumer wiring, customerId resolution decision, test coverage); we have 5 SP headroom on 12-SP capacity per PM-001 but RISK-002 says preserve slack.

### Option C — Hybrid: TTL-only v1 + event-driven invalidation v1.1 ← FUTURE WORK

- Same as Option A for v1; revisit in a follow-up sprint once we have demo evidence of cache hit ratio and real-world staleness complaints.

### Option D — Shorter TTL (5s) to compensate for no invalidation

- ❌ Cache hit ratio drops to ~20% (most refresh intervals > 5s).
- ❌ Cold-cache rate increases → p95 < 800ms cold-cache budget gets exercised constantly → NFR risk.
- ❌ Defeats the purpose of caching.

### Option E — Longer TTL (5 min) for aggressive caching

- ❌ Staleness window too wide; UI "may be stale" indicator would trigger constantly (60s threshold).
- ❌ Bad UX after balance changes.

## Decision Outcome

**Chosen for v1: Option A — TTL-only, 30 seconds.** Option C (TTL-only v1 + event-driven v1.1) describes the roadmap.

Sweet spot at 30s:
- Long enough for cache hit ratio > 70% in normal user sessions.
- Short enough that staleness rarely surprises customers.
- Aligns with BR-013 60s "may be stale" threshold — most cache hits are well below the threshold.
- 0 operational surface (no consumer group, no DLQ).

### Implementation Notes

- Cache write: `SETEX balance-dashboard:customer:{customerId} 30 <json>` after successful AccountClient call.
- Cache read: `GET balance-dashboard:customer:{customerId}`.
- On Redis exception: fail-open to AccountClient (BR-015). Do NOT wrap in circuit breaker — every request retries Redis on its own.
- Encryption at rest: cluster-default AES-256-GCM (consistent with money-transfer Redis usage).
- No explicit DEL — never invalidate by code in v1.

### Consequences

- ✅ Fastest path to demo. No new event contracts.
- ✅ Cache layer is self-contained in `balance-dashboard-service`.
- ⚠️ Up to 30s of read-staleness is possible. Mitigated by: (a) `balance_as_of` in cached payload reflects actual ledger freshness, (b) UI "may be stale" indicator at 60s threshold, (c) audit log records `cacheHit=true` so we can correlate complaints.
- ⚠️ If real-world cache hit ratio is < 70% post-launch, we revisit TTL (longer) or move to event-driven (v1.1).

### Re-open Criteria for v1.1 (Event-Driven Invalidation)

Trigger an ADR-002b in a future sprint if any of:
1. Post-launch metric shows cache hit ratio < 50% for 2+ weeks (TTL not delivering expected value).
2. Customer complaints about stale balance > 5/week.
3. BA story emerges requiring sub-5s freshness.
4. Money-transfer team agrees to add `customerId` to `AccountDebited`/`AccountCredited` Avro v2 schema (removes the customerId-lookup blocker).

When we revisit, the chosen pattern will be:
- Consume `AccountDebited` / `AccountCredited` from existing Kafka topics.
- Resolve `customerId` from event (assuming schema extension) OR via batched lookup with local cache.
- `DEL balance-dashboard:customer:{customerId}` on event.
- Keep TTL as a backstop (in case consumer lag).

## Links

- [ADR-001 service boundary](ADR-001-service-boundary.md)
- [ADR-003 audit event evolution](ADR-003-audit-event-evolution.md) — analogous schema-evolution pattern
- [Event & cache flows §2](../event-flows.md)
- BA BR-012 / BR-013 (TTL + staleness threshold)
- Money-transfer S3 ADR-003 (transactional outbox — analogous "deferred complexity" pattern)
- PM RISK-005 (cold-cache perf risk — this ADR's caching strategy is one mitigation)
