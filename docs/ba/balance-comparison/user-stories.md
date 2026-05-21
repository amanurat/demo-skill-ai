# User Stories — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Sprint:** SPRINT-2026-Q2-BC-01 (2026-05-21 → 2026-06-04)
> **BA artifact:** BA-001 (initial)
> **Previous artifact:** PM-001 (`9b1f6c4a-2d83-4b67-a410-7e5fa9d62c81`)
> **OPEN-001..004 status:** ALL RESOLVED — see stakeholder decisions below
> **Source of truth JSON:** `docs/ba/balance-comparison/handoff-ba-001.json`

---

## Resolved Stakeholder Decisions (baked into all AC below)

| Open ID | Decision | Implication |
|---|---|---|
| OPEN-001 | Native currency only | US-BC-004 DEFERRED — no FX conversion in this sprint |
| OPEN-002 | `balance_as_of` from ledger | "Last updated" label = ledger `balance_as_of`, NOT row `updated_at` |
| OPEN-003 | Hidden by default | Dormant/closed accounts excluded from response; audit trail still records the access |
| OPEN-004 | SAVINGS + CURRENT + FIXED_DEPOSIT only | Loan and credit-card balances excluded from aggregation |

---

## In-Scope Stories (this sprint)

---

### US-BC-001 — List My Accounts Ranked by Balance (Descending)

**As a** authenticated retail customer  
**I want** to see all my own active accounts in a single ranked list, ordered from highest to lowest balance  
**So that** I can compare my balances at a glance and decide where to move funds

**Priority:** MUST  
**Estimated SP:** 2  
**Dependencies:**
- REUSE: `AccountClient.listAccountsByCustomer(customerId)` — from `money-transfer` feature (see `docs/artifacts/S2-ba-money-transfer.json`, entity: Account)
- REUSE: `AccountInfo` DTO — same source; fields: `accountId`, `accountNumber` (masked), `accountType`, `balance`, `currency`, `status`, `balance_as_of`
- REUSE: OAuth2/OIDC + JWT auth filter chain
- REUSE: `audit-service` event emitter (append-only, per BoT requirement)
- NEW: balance-dashboard aggregation endpoint (SA to decide service boundary via ADR)
- NEW: ranking logic (balance DESC, `accountId` ASC tie-break)

**Business Rules:**
- BR-001: Only accounts belonging to the authenticated customer (JWT `sub` claim = `customerId`) are returned. Cross-customer access is forbidden at service level.
- BR-002: Ranking is by `balance` DESC. When two accounts have identical balance values, tie-break is `accountId` ASC (lexicographic/UUID natural order) for deterministic ordering across page loads and caching.
- BR-003: Only accounts with `status = 'ACTIVE'` are included. Accounts with status `DORMANT`, `CLOSED`, `FROZEN`, or `INACTIVE` are excluded from the ranked list.
- BR-004: Only `accountType IN ('SAVINGS', 'CURRENT', 'FIXED_DEPOSIT')` are included. Loan and credit-card account types are excluded.
- BR-005: Every successful retrieval emits an audit event to `audit-service` with `purpose = 'balance-inquiry'` and `actor = customerId`, regardless of cache hit or miss.
- BR-006: PDPA consent covering "view own account balance information" must be verified to exist before the response is returned (reuse money-transfer consent scope — see RISK-003 mitigation).

**Acceptance Criteria:**

**AC-001-H1 (Happy path — ranked list returned)**
> Given I am authenticated with a valid JWT (scope includes `accounts:read`) and I have 3 or more ACTIVE SAVINGS/CURRENT/FIXED_DEPOSIT accounts with distinct balances,
> When I request the balance dashboard endpoint,
> Then the response contains all my active in-scope accounts ordered by `balance` DESC, each entry includes `accountId`, masked `accountNumber` (last 4 digits visible, format `****XXXX`), `accountType`, `balance`, `currency`, and `balance_as_of` timestamp,
> And the HTTP response status is 200 OK with a non-empty `accounts` array.

**AC-001-H2 (Deterministic tie-break when balances are equal)**
> Given I have two or more accounts with exactly the same `balance` value and the same `currency`,
> When I request the dashboard (including multiple requests in sequence from a warm cache),
> Then the accounts with equal balance are always returned in ascending `accountId` order (lexicographic),
> And the relative ordering never changes between identical requests within the cache TTL window.

**AC-001-H3 (Audit event emitted on every retrieval)**
> Given I successfully retrieve the dashboard (whether the response was served from cache or live AccountClient),
> When the response is returned to me,
> Then `audit-service` has received an event containing: `eventType = BALANCE_INQUIRY`, `actorId = customerId` (from JWT sub), `purpose = 'balance-inquiry'`, `channel = MOBILE_BANKING`, `correlationId` (trace ID), `timestamp` (UTC), `result = SUCCESS`,
> And the audit event is emitted even when the data is served from cache.

**AC-001-H4 (Native currency — accounts displayed in their own currency)**
> Given I have accounts denominated in THB and the system is operating in native-currency-only mode (OPEN-001 resolved),
> When I view the ranked list,
> Then each account row shows its own `currency` code,
> And no FX conversion or total-in-home-currency aggregation is performed or displayed.

**AC-001-E1 (Empty state — customer has zero in-scope active accounts)**
> Given I am authenticated but have no ACTIVE accounts of type SAVINGS, CURRENT, or FIXED_DEPOSIT,
> When I request the dashboard,
> Then the response is HTTP 200 with an empty `accounts` array and a UI indicator showing "No accounts to display",
> And an audit event is still emitted with `result = SUCCESS` and `accountCount = 0`.

**AC-001-E2 (Authorization — cross-customer IDOR attempt)**
> Given I am authenticated as customer A with a valid JWT,
> When I attempt to retrieve the dashboard by supplying a `customerId` query parameter or path segment belonging to customer B (or any customer other than the JWT `sub`),
> Then the service returns HTTP 403 Forbidden,
> And no account data for customer B is returned or logged in non-audit logs,
> And an audit event is emitted with `result = FORBIDDEN` and `actorId = customerId A`.

**AC-001-E3 (Unauthenticated access)**
> Given I am not authenticated (no JWT or expired JWT),
> When I request the dashboard endpoint,
> Then the service returns HTTP 401 Unauthorized,
> And no account data is returned.

**Out of scope for this story:**
- Multi-currency total (US-BC-004 — deferred, see below)
- Dormant/closed account display or grey-out (excluded silently per OPEN-003)
- Filter, sort override, or search (US-BC-006 — next sprint)
- Loan or credit-card balance (OPEN-004 exclusion)

---

### US-BC-002 — See Per-Account Details (Type, Balance, Currency, Last Updated)

**As a** authenticated retail customer  
**I want** each account row to clearly show the account type (human-readable label), balance, currency, and when the balance was last updated  
**So that** I have enough context to evaluate each account and act on the comparison

**Priority:** MUST  
**Estimated SP:** 2  
**Dependencies:**
- REUSE: `AccountInfo` DTO fields — same as US-BC-001 (DO NOT redefine; reference `docs/artifacts/S2-ba-money-transfer.json` entity: Account)
- REUSE: PDPA account-number masking convention from money-transfer (last 4 digits visible: `****XXXX`)
- NEW: `accountType` display-label mapping (Thai + English per locale) — TL/FE to implement
- NEW: `balance_as_of` relative-time formatter (e.g., "2 minutes ago" on hover shows absolute ISO timestamp) — FE concern, BA specifies behavior
- NEW: Fixed Deposit balance composition rule (see sub-decision below)

**Sub-decision for SA/TL (BA default provided — do not block parallel tracks):**
> **SUBDEC-001:** For `accountType = FIXED_DEPOSIT`, the displayed `balance` is **principal + accrued interest as of `balance_as_of`** if `AccountInfo` exposes it that way from ledger. If `AccountInfo` returns principal only, TL must flag to PM before implementation and default to principal-only with a "+" accrued interest note. BA default: principal + accrued interest as of `balance_as_of`. SA/TL to confirm in ADR.

**Business Rules:**
- BR-007: `accountNumber` MUST always be masked to last 4 digits in: (a) UI DOM, (b) API JSON response payload, (c) structured logs, (d) browser developer console / network inspector. Full account number must never appear in any of these surfaces. Convention: `****XXXX` where `XXXX` is the last 4 digits.
- BR-008: `accountType` is rendered as a human-readable label, not the raw enum value. Minimum mapping: `SAVINGS` → "บัญชีออมทรัพย์" / "Savings Account"; `CURRENT` → "บัญชีกระแสรายวัน" / "Current Account"; `FIXED_DEPOSIT` → "บัญชีเงินฝากประจำ" / "Fixed Deposit Account". TL/Designer to confirm bilingual labelling approach.
- BR-009: The "Last updated" label displayed to the customer is sourced exclusively from `balance_as_of` (the ledger timestamp of the last balance-affecting event), NOT from the database row `updated_at` column (OPEN-002 decision). This affects both the UI display and cache invalidation logic (see US-BC-003).
- BR-010: Dormant and closed accounts are filtered at the aggregation service level and never appear in this view (OPEN-003). No "grey-out" or "hidden" indicator is shown to the customer.
- BR-011: `balance` is formatted with the appropriate currency symbol and 2 decimal places. For THB: "฿1,234.56". Currency code is shown alongside the formatted amount.

**Acceptance Criteria:**

**AC-002-H1 (Happy path — all display fields correct)**
> Given I have an ACTIVE SAVINGS account with balance 45,000.00 THB and `balance_as_of = 2026-05-21T08:00:00Z`,
> When I view the dashboard,
> Then the row displays: type label "บัญชีออมทรัพย์" (or locale-appropriate label), `balance` as "฿45,000.00", `currency` as "THB", `accountNumber` as "****" followed by the last 4 digits only, and "Last updated" as a relative time derived from `balance_as_of` (e.g., "3 hours ago"),
> And hovering over "Last updated" shows the absolute timestamp in "DD MMM YYYY HH:mm ICT" format.

**AC-002-H2 (Fixed Deposit shows principal + accrued interest as of balance_as_of)**
> Given I have an ACTIVE FIXED_DEPOSIT account where `AccountInfo` exposes `balance = principal + accrued_interest` as of `balance_as_of`,
> When I view the dashboard,
> Then the displayed balance reflects the combined value (principal + accrued interest to date),
> And the label clearly shows "Fixed Deposit Account" (not the raw enum),
> And `balance_as_of` is used as the "Last updated" source (not the row timestamp).

**AC-002-H3 (Masked account number never exposes full number)**
> Given my account number is "1234567890",
> When I view the dashboard in the UI or inspect the API JSON response or browser network tab,
> Then in all three surfaces the account number appears as "******7890" (last 4 only),
> And the full 10-digit number is absent from the DOM, JSON payload, and browser console logs.

**AC-002-H4 (All three in-scope account types display correct labels)**
> Given I have one account of each type: SAVINGS, CURRENT, FIXED_DEPOSIT,
> When I view the dashboard,
> Then each row shows the correct Thai-language label (and English equivalent if locale = EN),
> And no raw enum string (e.g., "SAVINGS") is visible to the customer.

**AC-002-E1 (balance_as_of is stale but still displayed correctly)**
> Given a FIXED_DEPOSIT account whose `balance_as_of` is 24 hours old (e.g., a time deposit not updated since yesterday),
> When I view the dashboard with that account in the list,
> Then the displayed "Last updated" reflects the actual `balance_as_of` value (e.g., "1 day ago"),
> And the system does not substitute `updated_at` or the current timestamp as a replacement,
> And a "may be stale" indicator is shown if `balance_as_of` is older than the staleness threshold defined in NFR (see `nfr.md`).

**AC-002-E2 (AccountInfo field missing — balance_as_of null or absent)**
> Given `AccountInfo` returned from `AccountClient` has a null or absent `balance_as_of` field for a particular account,
> When the aggregation service processes that account,
> Then the "Last updated" field displays "–" (em dash) or a locale-appropriate "Not available" label,
> And the account is still included in the ranked list (field absence does not suppress the account),
> And an observability warning log is emitted (not an error) with the affected `accountId` for monitoring.

**Out of scope for this story:**
- Full account number display in any channel
- Loan or credit-card balance or type labels
- Balance history or sparkline (US-BC-007 — next sprint)

---

### US-BC-003 — Dashboard Meets p95 < 500ms for 10 Accounts

**As a** authenticated retail customer on a mobile device (4G network)  
**I want** the balance dashboard to feel instant when I open it  
**So that** I do not abandon the screen while waiting for my account data to load

**Priority:** MUST  
**Estimated SP:** 3  
**Dependencies:**
- REUSE: Existing Redis cluster (ASSUME-004 — shared infra, pre-provisioned for staging)
- REUSE: Existing OpenTelemetry tracing setup
- NEW: Redis cache key strategy: `customer:{customerId}:accounts`, TTL 30 seconds
- NEW: Batched `AccountClient` call pattern (single call returning all accounts — NOT N sequential round-trips)
- NEW: Grafana panel for cache hit ratio + p95 latency + audit-event rate
- NOTE: Cache must include `balance_as_of` field in cached payload so that "Last updated" display remains accurate on cache hits

**Business Rules:**
- BR-012: The Redis cache key is scoped per customer: `customer:{customerId}:accounts`. TTL is 30 seconds for v1. Event-driven invalidation (on ledger update) is DEFERRED to the next sprint; TTL-only is the v1 invalidation strategy.
- BR-013: The cache payload MUST include `balance_as_of` for each account entry. A cache hit that returns stale `balance_as_of` is acceptable within the 30-second TTL window. The "may be stale" UI indicator is triggered when `now() - balance_as_of > staleness_threshold` (default: 60 seconds — see `nfr.md`).
- BR-014: An audit event MUST be emitted on every dashboard retrieval, regardless of whether the response was served from Redis cache or from a live `AccountClient` call. The audit path is never short-circuited by the cache.
- BR-015: On Redis cache miss or Redis failure, the service MUST fall back to `AccountClient` (fail-open, not fail-closed). A Redis failure must not cause the dashboard endpoint to return an error response.
- BR-016: `AccountClient` calls MUST be batched (a single request for all accounts belonging to `customerId`), not issued as N sequential requests. SA/TL to confirm batch API signature on `AccountClient`.
- BR-017: p95 < 500ms is measured at the balance-dashboard service response time (server-side, excluding network transit to mobile device), with a warm Redis cache and a fixture of 10 accounts per customer in the staging environment.
- BR-018: p95 < 800ms is the acceptable degraded target for cold-cache (first request after TTL expiry or cold start). This is the v1 documented graceful degradation threshold.

**Acceptance Criteria:**

**AC-003-H1 (Warm-cache response within 500ms p95)**
> Given the Redis cache has been warmed with my account data (i.e., at least one prior request within the 30-second TTL),
> When I request the balance dashboard,
> Then the server-side response time is below 500ms at p95, measured via k6 or Gatling load test on staging with a 10-account fixture for each test customer,
> And the response data is returned from the Redis cache (observable via `X-Cache: HIT` response header or equivalent tracing span).

**AC-003-H2 (Cold-cache response within 800ms p95 — graceful degradation)**
> Given the Redis cache has no entry for my `customerId` (first request or post-TTL expiry),
> When I request the balance dashboard,
> Then the server-side response time is below 800ms at p95 (measured identically to AC-003-H1),
> And the response data is fetched from `AccountClient` (single batched call — not N round-trips),
> And the result is written to Redis cache with TTL 30 seconds.

**AC-003-H3 (Audit event emitted regardless of cache state)**
> Given any dashboard request — whether served from Redis cache or from AccountClient —
> When the response is returned,
> Then an audit event is emitted to `audit-service` with `eventType = BALANCE_INQUIRY` within the request processing cycle,
> And the audit event emission is observable in the audit-service log and in the Grafana audit-event rate panel.

**AC-003-H4 (Cache includes balance_as_of — no stale field substitution)**
> Given the Redis cache holds account data with `balance_as_of` timestamps,
> When the cached response is returned,
> Then `balance_as_of` for each account in the response matches the value that was stored when the cache was populated,
> And the "Last updated" UI label reflects that timestamp (not the time of the cache-hit request).

**AC-003-E1 (Redis unavailable — fail-open to AccountClient)**
> Given the Redis instance is unreachable (connection timeout or refused) at the time of a dashboard request,
> When I request the dashboard,
> Then the service falls back to `AccountClient` and returns my account data successfully (HTTP 200),
> And the response does not expose a Redis-specific error to the customer,
> And an observability warning metric/log is emitted indicating `cache_miss_reason = REDIS_UNAVAILABLE`,
> And the audit event is still emitted.

**AC-003-E2 (Cache hit ratio monitoring — observable via Grafana)**
> Given the balance-dashboard service has been running for at least 5 minutes in staging,
> When I view the Grafana dashboard,
> Then I can observe: (a) p95 request latency per endpoint, (b) Redis cache hit ratio as a percentage (target: > 70% after warm-up), (c) audit-event emission rate per minute,
> And the Grafana panel is sourced from the existing OpenTelemetry / Prometheus pipeline (no new infra required).

**Out of scope for this story:**
- Event-driven cache invalidation on ledger update (deferred to next sprint per BR-012)
- p95 < 500ms cold-cache (cold-cache SLA is 800ms — see BR-018; if not achievable, contingency per RISK-005 is to document as v1 known limitation)
- Client-side (mobile) network latency — SLA is server-side only

---

### US-BC-005 — Mobile-First Responsive Layout and Accessibility (WCAG 2.1 AA)

**As a** retail customer using a mobile device (375px viewport) or assistive technology (screen reader)  
**I want** the balance dashboard to be fully usable on small screens and with keyboard / screen-reader navigation  
**So that** the feature is accessible to all customers, including those using assistive technology

**Priority:** SHOULD (stretch — drop if any MUST story slips past D7 review)  
**Estimated SP:** 2 (stretch)  
**Drop condition:** If reviewer iterations exceed 2 rounds by D8, or any MUST story has unresolved AC on D7, PM drops this story per sprint plan.  
**Dependencies:**
- REUSE: Existing design system tokens (Designer P2 to map to balance-dashboard component)
- REUSE: Existing a11y utilities in Angular base (confirmed in PM-001 reuse inventory)
- NEW: Responsive breakpoints for 375px / 768px / 1024px
- NEW: ARIA roles on ranked account list (`role="list"`, `role="listitem"`, `aria-label` on balance figures)
- NEW: Angular standalone `<balance-dashboard>` component (FE dev concern — BA specifies behavioral requirements only)

**Business Rules:**
- BR-019: The ranked account list MUST render without horizontal scrolling at a 375px viewport width. All content (account type label, masked number, balance, currency, last updated) must be legible without side-scrolling.
- BR-020: All interactive controls (if any in v1 read-only dashboard) MUST be reachable via keyboard Tab order. Focus must follow a logical DOM sequence matching visual order.
- BR-021: Each account row MUST have an ARIA label that announces to screen readers: the rank position (e.g., "Account 1 of 3"), the account type label, the masked account number, the formatted balance with currency, and the "last updated" text.
- BR-022: Balance figures MUST use `aria-label` with the full spoken form (e.g., `aria-label="45,000 baht"`) to prevent screen readers from reading digit-by-digit.
- BR-023: Lighthouse mobile accessibility score on the demo page MUST be >= 90. This is a demo exit criterion (see sprint-goal.md).
- BR-024: Color contrast ratio for all text on background MUST be >= 4.5:1 (WCAG 2.1 AA) for normal text and >= 3:1 for large text.

**Acceptance Criteria:**

**AC-005-H1 (No horizontal scroll at 375px viewport)**
> Given I open the balance dashboard on a device with a 375px viewport width (e.g., iPhone SE / emulated in Chrome DevTools),
> When the dashboard renders with 3 or more accounts,
> Then no horizontal scrollbar appears, all content is readable without side-scrolling, and the account type label, masked number, balance, and last-updated text are all fully visible in the viewport.

**AC-005-H2 (Screen reader announces rank position, type, balance, last updated)**
> Given I am using a screen reader (e.g., VoiceOver on iOS or TalkBack on Android),
> When I navigate to the balance dashboard,
> Then the screen reader announces each account row with: (a) its rank position ("Account 1 of 3"), (b) the human-readable account type label, (c) the masked account number, (d) the formatted balance read as a spoken amount (e.g., "45,000 baht"), and (e) the "last updated" relative time,
> And the list structure is conveyed (e.g., "list of 3 items").

**AC-005-H3 (Keyboard navigation — all rows reachable via Tab)**
> Given I am navigating with keyboard only (no mouse),
> When I press Tab from the page's first focusable element,
> Then focus moves through each account row in rank order (1 → 2 → 3 → ...),
> And a visible focus ring is present on each focused row (contrast >= 3:1 against adjacent colors),
> And Shift+Tab reverses navigation correctly.

**AC-005-H4 (Lighthouse mobile a11y >= 90)**
> Given the `<balance-dashboard>` Angular component is rendered on the demo staging URL,
> When I run a Lighthouse audit with "Mobile" device emulation and "Accessibility" category,
> Then the Accessibility score is 90 or higher,
> And the audit evidence is attached to the QA report for the sprint demo.

**AC-005-E1 (Dynamic font scaling — large text does not break layout)**
> Given the user has increased the browser / OS font size to 200% (accessibility setting),
> When the balance dashboard is rendered,
> Then no text is clipped, overlapping, or truncated, and the layout adapts (line-wraps gracefully) without losing any information.

**AC-005-E2 (Balance figures — screen reader does not read digit-by-digit)**
> Given an account with balance "45,000.00",
> When a screen reader reads the balance figure,
> Then it reads the full amount as a single unit ("45,000 baht"), not as individual characters ("4", "5", "0", "0", "0"),
> And this is achieved by an explicit `aria-label` attribute on the balance element.

**Out of scope for this story:**
- iOS / Android native app accessibility (Angular web only for this sprint)
- Internationalization beyond Thai/English label mapping (US-BC-002 BR-008)
- WCAG 2.1 AAA compliance (AA is the target)

---

## Deferred Stories

### US-BC-004 — Total Wealth in Home Currency (Multi-Currency Aggregation)

**Status: DEFERRED — follow-up sprint**

**Rationale:** OPEN-001 was resolved as "native currency only" (stakeholder decision 2026-05-21). FX rate source is undecided. Including this story would require an FX gateway ADR, BoT mid-rate integration, and rounding/dispute risk management that are out of scope for a 10-day demo sprint. RISK-001 status: mitigated by scope cut.

This story will be carried forward to the follow-up sprint backlog. Re-open when: (a) FX rate source is decided (BoT mid-rate vs internal treasury vs display native only), (b) SA adds FX gateway to architecture, (c) BA expands this story with AC covering currency conversion, rounding rules, and rate-lock timing.

---

## Out-of-Scope Stories (explicit — do not re-open this sprint)

| Story ID | Title | Disposition |
|---|---|---|
| US-BC-006 | Filter / search accounts | COULD — next sprint |
| US-BC-007 | Sparkline chart of balance over time | COULD — next sprint (needs historical balance store) |
| US-BC-008 | Hide / pin specific accounts | COULD — next sprint (needs user-preference service) |
| US-BC-009 | CSV / PDF export | WON'T — reporting platform scope |
| US-BC-010 | Joint-account / POA support | WON'T — separate compliance epic |
| US-BC-011 | Push notification on balance change | WON'T — notification service scope |

---

## Story Summary

| Story ID | Title | Priority | Happy AC | Edge/Error AC | Total AC |
|---|---|---|---|---|---|
| US-BC-001 | List accounts ranked by balance (desc) | MUST | 4 | 3 | 7 |
| US-BC-002 | Per-account detail row | MUST | 4 | 2 | 6 |
| US-BC-003 | Dashboard meets p95 < 500ms | MUST | 4 | 2 | 6 |
| US-BC-005 | Mobile-first responsive + a11y AA | SHOULD | 4 | 2 | 6 |
| US-BC-004 | Multi-currency total | DEFERRED | — | — | — |

**Total in-scope AC: 25 (16 happy-path + 9 edge/error)**
