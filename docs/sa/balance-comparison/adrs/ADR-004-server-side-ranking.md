# ADR-004. Aggregation & Ranking Responsibility — Server-Side vs. Client-Side

- **Status:** accepted
- **Date:** 2026-05-21
- **Deciders:** banking-solution-architect
- **Tags:** ranking, caching, determinism, client-server

## Context and Problem

US-BC-001 requires ranking the customer's accounts by `balance` DESC with `accountId` ASC tie-break (BR-002, AC-001-H1, AC-001-H2). Two homes for this logic:

1. **Server-side** in `balance-dashboard-service` — rank then cache the ranked result.
2. **Client-side** in Angular `<balance-dashboard>` — server returns unordered list, FE sorts.

This is a tactical decision but has performance, determinism, and a11y implications.

## Decision Drivers

- **Cache friendliness (NFR p95 < 500ms warm).** A cached response must be served as-is without per-request re-computation.
- **Deterministic tie-break (AC-001-H2).** "Ordering never changes between identical requests within TTL window." If FE sorts, two FE instances (e.g., one in browser, one in screenshot test) might produce different orderings due to library differences.
- **Accessibility (AC-005-H2).** Screen reader announces "Account 1 of N, Account 2 of N…". Rank numbering must be present in the DOM at render time; if FE computes ranking client-side, there's a race window where ARIA announces incorrect rank.
- **Mobile CPU cost.** Sort of 10 items is trivial, but if v1.1 grows the list, server-side is the safer scaling default.
- **Single source of truth for audit.** The audit event includes `accountCount`; if we filter+rank server-side, BDS knows the authoritative count. If we ship raw list to FE, BDS must still apply EligibilityPolicy (ACTIVE + types) somewhere — and we'd be doing the filter server but the sort client, which is a smelly split.

## Considered Options

### Option A — Server-side ranking (in `balance-dashboard-service`) ← CHOSEN

- ✅ Cache holds the **ranked** result; warm-cache path skips rank computation entirely (microseconds of CPU saved per hit).
- ✅ Deterministic across all clients: BDS uses one `Ranker` implementation; all FE instances render the same order.
- ✅ ARIA `aria-label="Account 1 of 3"` is correct at first render — no FE sort race.
- ✅ Audit event `accountCount` is from the same code path that produced the response.
- ✅ Symmetric: BDS already filters by `EligibilityPolicy`; keeping the filter + sort together in one domain object (`BalanceSnapshot`) preserves SRP.
- ✅ Easier QA — one unit test for `Ranker` covers all clients.
- ⚠️ A future FE feature that wants to "sort by accountType ascending" would require either a server-side sort param or client-side re-sort. Out of scope for v1 (no sort override per BA out-of-scope).

### Option B — Client-side ranking (in Angular component)

- ✅ Saves a tiny amount of server CPU.
- ✅ Lets FE offer alternative sorts in future without server change.
- ❌ Cache must hold UNRANKED list, then warm-cache responses re-send unranked → FE re-sorts every render → wasted bytes + work.
- ❌ Two FE instances might produce different orderings if locale-aware sort differs (unlikely with UUID accountId but possible with Intl.Collator quirks).
- ❌ ARIA rank cannot be set at server-render time; SSR (if added) would emit wrong rank.
- ❌ AC-001-H2 ("ordering never changes between identical requests") becomes a client-test concern, not a server contract — harder to assert in API integration tests.
- ❌ Filter (server) and rank (client) split smells like a leaky abstraction.

### Option C — Hybrid: server applies stable sort by accountId; client applies balance DESC

- ❌ Worst of both worlds: server CPU + client CPU + FE sort race + cache pollution.

## Decision Outcome

**Chosen: Option A — server-side ranking.**

The `Ranker` lives in `balance-dashboard-service/domain/Ranker.java` as a pure function:

```java
public final class Ranker {
    public static List<AccountView> rank(List<AccountView> accounts) {
        return accounts.stream()
            .sorted(Comparator
                .comparing(AccountView::balance).reversed()         // balance DESC
                .thenComparing(AccountView::accountId))             // accountId ASC tie-break
            .toList();
    }
}
```

Cache stores the post-rank result. FE component receives a pre-ranked list with explicit `rank` field on each row (1-based), uses that for ARIA `aria-label="Account {rank} of {total}"`.

### Consequences

- ✅ Cache friendliness: warm hits return ranked list verbatim.
- ✅ Determinism is a server contract; integration test asserts ordering directly.
- ✅ ARIA rank is server-authoritative — accessibility tests can assert from API response.
- ✅ `Ranker` is a pure, allocation-free unit test — < 1ms per test case.
- ⚠️ If a future story (US-BC-006: filter / search) adds client-side sort overrides, the FE can do it client-side then; server stays the default-rank source.

### Implementation Notes

- `AccountView` carries `rank: int` (1-based) populated by `BalanceSnapshot` factory after `Ranker.rank()`.
- FE component does NOT re-sort. It iterates the array as received and binds `[attr.aria-label]="'Account ' + view.rank + ' of ' + total"`.
- Empty list: `accounts: []`, `accountCount: 0`, UI shows empty state per AC-001-E1.

## Links

- [ADR-001 service boundary](ADR-001-service-boundary.md)
- [ADR-002 cache strategy](ADR-002-cache-strategy.md) (cache holds ranked result)
- [Event flows §4 sequence](../event-flows.md)
- BA AC-001-H2 (deterministic tie-break), AC-005-H2 (screen reader rank)
