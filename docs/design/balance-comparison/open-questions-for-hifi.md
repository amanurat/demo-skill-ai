# Open Questions for HI-FI (Phase 2) — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** Phase 1 — LO-FI / Discovery
> **Purpose:** Catalog questions the Designer must resolve in Phase 2 HI-FI, after Solution Architect (SA-001) and any prior Designer artifacts (none exist yet) are available. Each item carries a working assumption so parallel tracks are not blocked.

---

## OPEN-D-001 — Design token / brand palette source

**Question:** Does an existing design token system from the money-transfer feature (or a global app shell) exist to reuse, or does this sprint need to author tokens from scratch?

**Working assumption (Phase 1):** No prior Designer artifacts exist (verified — `docs/design/`, `docs/ui/`, `docs/**/designer*` all empty). Phase 2 HI-FI will:
1. Define a minimal token set: colours (primary, surface, text, success, warning, danger), typography (h1, body, caption, amount-display), spacing (4/8/12/16/24/32), border-radius (button, card, badge).
2. Bias toward conservative banking aesthetic (trust-blue or neutral) — final colour deferred to HI-FI.

**Resolution owner:** Designer Phase 2, in consultation with banking-tech-lead.
**Resolution gate:** Before HI-FI starts.
**AC anchor:** AC-005-H4 (Lighthouse a11y depends on contrast ratios).

---

## OPEN-D-002 — Iconography for account types

**Question:** What icons represent SAVINGS vs CURRENT vs FIXED_DEPOSIT for Thai retail customers? Use existing icon library or commission new?

**Working assumption (Phase 1):**
- LO-FI uses a generic placeholder square.
- HI-FI proposal: line icons, two-tone optional. Candidates — piggy-bank / leaf / coin (SAVINGS), wallet / receipt (CURRENT), lock / safe / clock (FIXED_DEPOSIT).
- Constraint: icons must be decorative (`aria-hidden="true"`) — the human-readable Thai label carries semantic meaning (BR-008).

**Resolution owner:** Designer Phase 2.
**Resolution gate:** Before HI-FI mockups are finalised.
**AC anchor:** BR-008 (label is the source of truth, not the icon).

---

## OPEN-D-003 — Exact micro-copy + i18n key map (Thai-primary)

**Question:** What is the final Thai (and English) copy for every visible string, and what are the i18n keys?

**Working assumption (Phase 1):** LO-FI uses illustrative Thai strings; final copy is content-owner-approved in HI-FI. Proposed key namespace: `balance-comparison.*`.

**Strings to lock in HI-FI:**

| Phase 1 illustrative Thai | Proposed i18n key | Notes |
|---|---|---|
| "บัญชีของฉัน" | `balance-comparison.page.title` | Page header |
| "Updated just now" / "อัปเดตล่าสุด" | `balance-comparison.lastUpdated.justNow` | Relative-time variant |
| "อัปเดตเมื่อ X นาทีที่แล้ว" | `balance-comparison.lastUpdated.minutesAgo` | Pluralisation in HI-FI |
| "ยอดเงินอาจไม่เป็นปัจจุบัน" | `balance-comparison.stale.banner.title` | Stale banner |
| "อาจไม่ใช่ยอดล่าสุด" | `balance-comparison.stale.badge` | Per-row badge |
| "ลองอีกครั้ง" / "ลองใหม่" | `balance-comparison.retry.soft` / `.primary` | Two button variants |
| "ยังไม่มีบัญชีที่ใช้งานอยู่" | `balance-comparison.empty.title` | Empty state |
| "บัญชีที่ปิดหรือไม่เคลื่อนไหวจะไม่แสดงในหน้านี้" | `balance-comparison.empty.subtitle` | Explains OPEN-003 |
| "เกิดข้อผิดพลาดในการโหลดข้อมูล" | `balance-comparison.error.title` | Error state |
| "รหัสอ้างอิง" | `balance-comparison.error.correlationLabel` | Correlation id label |
| "บัญชีออมทรัพย์" / "Savings Account" | `account.type.savings` | Reuse if existing key from money-transfer |
| "บัญชีกระแสรายวัน" / "Current Account" | `account.type.current` | Same |
| "บัญชีเงินฝากประจำ" / "Fixed Deposit Account" | `account.type.fixedDeposit` | Same |
| "ลงท้ายด้วย XXXX" | `account.masked.endingWith` | Used in screen-reader template |

**Resolution owner:** Designer Phase 2 + content owner (escalate to PM if no content owner is named).
**Resolution gate:** Before FE implementation (HI-FI handoff to FE).

---

## OPEN-D-004 — Relative-time vs absolute-time switch threshold

**Question:** At what age does "X minutes ago" become "X hours ago", and when do we show absolute date instead of relative time?

**Working assumption (Phase 1) — proposed for HI-FI ratification:**
- < 30s → "just now" / "อัปเดตล่าสุด"
- < 60min → "X minutes ago"
- < 24h → "X hours ago"
- ≥ 24h → "X days ago"
- ≥ 7d → switch to absolute "DD MMM YYYY HH:mm ICT" (v1 cap; no week/month rollups for clarity)

The 60-second staleness threshold (BR-013, triggers stale badge) is INDEPENDENT of these labels — a row can read "1 minute ago" AND show the stale badge.

**Resolution owner:** Designer Phase 2 + FE (formatter contract).
**Resolution gate:** Before FE implementation.
**AC anchor:** AC-002-H1, AC-002-E1.

---

## OPEN-D-005 — Global "snapshot served" signal from server

**Question:** How does the server tell the UI that the entire response is a degraded snapshot (e.g., AccountClient returned cached/snapshot data) so the global staleness banner appears?

**Working assumption (Phase 1):** Server adds either:
- (a) HTTP response header `X-Data-Freshness: live | snapshot | stale`, or
- (b) `meta.freshness: "live" | "snapshot" | "stale"` in the JSON payload.

LO-FI Screen 4 assumes (b) JSON payload — easier for FE to parse and observable in OpenAPI contract.

**Resolution owner:** Solution Architect (ADR) + Tech Lead (OpenAPI spec). Designer Phase 2 ratifies the UI consumption rule.
**Resolution gate:** Must be resolved by SA-001 before HI-FI Phase 2 starts. (Hard dependency.)
**AC anchor:** Implicit in resilience NFR §2; not in any explicit AC — flag for AC enhancement if SA introduces the field.

---

## OPEN-D-006 — Per-row tie-break visualisation

**Question:** When two rows have identical balance (and thus identical-looking rank chrome), do we add a visual tie indicator, or keep ordering invisible?

**Working assumption (Phase 1):** Keep ordering INVISIBLE (deterministic accountId ASC sort is a backend contract — AC-001-H2 — but customer-visible chrome stays minimal). HI-FI may revisit if user testing flags confusion.

**Resolution owner:** Designer Phase 2.
**Resolution gate:** HI-FI mockups.

---

## OPEN-D-007 — Rank visual treatment (number badge vs implicit order)

**Question:** Do we show the rank number visually (e.g., "1", "2", "3" badges) or rely solely on DOM/visual order?

**Working assumption (Phase 1):** No visible rank badges. Order is communicated by:
- Visual stacking (top = highest balance);
- Screen-reader announcement "Account N of M" (AC-005-H2).

Rationale: matches the BA's "compare at a glance" goal without adding chrome that competes with the balance figure (the most important data).

**Resolution owner:** Designer Phase 2.
**Resolution gate:** HI-FI mockups.

---

## OPEN-D-008 — Pull-to-refresh vs explicit refresh button

**Question:** Do we ship pull-to-refresh in v1, or just a refresh icon in the header? (See interaction spec §2.)

**Working assumption (Phase 1):** Ship BOTH for accessibility — pull-to-refresh as the primary gesture for touch users, AND a keyboard-reachable refresh button (could be a small icon button in the header or the same as `RetrySoftButton`). If FE flags effort risk, drop pull-to-refresh and keep only the button.

**Resolution owner:** Designer Phase 2 + Frontend Dev.
**Resolution gate:** HI-FI handoff.

---

## OPEN-D-009 — Reduced-motion fallback patterns

**Question:** When `prefers-reduced-motion: reduce` is set, what is the visual substitute for skeleton shimmer, pull-to-refresh spring, and badge fade-in?

**Working assumption (Phase 1):**
- Skeleton: static placeholder (no shimmer at all — easiest).
- Pull-to-refresh: gesture still works; spring animation suppressed; spinner does not rotate (use a textual "กำลังโหลด…").
- Badge fade-in: instant appearance.

**Resolution owner:** Designer Phase 2 + Frontend Dev.
**Resolution gate:** HI-FI mockups + FE implementation.

---

## OPEN-D-010 — Currency-only display when only THB exists

**Question:** When only THB accounts exist (the v1 reality, per OPEN-001 native-currency-only), do we still display the "THB" code beside every row, or omit it when single-currency?

**Working assumption (Phase 1):** **Always show the currency code** beside the balance. Rationale:
- Forward-compat for when multi-currency accounts appear (US-BC-004 deferred).
- Removes ambiguity for customers travelling abroad / holding foreign holdings later.
- Aligns with AC-001-H4 "each row shows its own `currency` code".

**Resolution owner:** Designer Phase 2.
**Resolution gate:** HI-FI mockups.

---

## OPEN-D-011 — Header subtitle freshness reflection rule

**Question:** What is the rule for the header "Updated…" subtitle when rows have varied freshness?

**Working assumption (Phase 1):** Use the FRESHEST row's `balance_as_of` for the header subtitle. Rationale: header is a summary; a customer should never see the header report something more pessimistic than what's in the list. Per-row stale badges remain authoritative for stale-row warnings.

Alternative considered and rejected: use the STALEST row → too alarming, would imply the entire list is stale when only one row is.

**Resolution owner:** Designer Phase 2.
**Resolution gate:** HI-FI mockups.

---

## OPEN-D-012 — Empty-state CTA fate

**Question:** Does the "เปิดบัญชีใหม่" CTA on the empty state link out to a real flow, or stay as a static placeholder?

**Working assumption (Phase 1):** Static placeholder (disabled / no-op) for v1. Linking to "open new account" is out of scope for balance-comparison; need product owner sign-off + cross-feature integration before activating.

**Resolution owner:** Designer Phase 2 + PM (escalate if product owner is not identified).
**Resolution gate:** HI-FI mockups; can be revisited in a follow-up sprint without blocking demo.

---

## OPEN-D-013 — Mobile vs tablet/desktop responsiveness for v1 demo

**Question:** Is tablet (768px) and desktop (1024px+) layout in scope for the demo, or is mobile-only sufficient?

**Working assumption (Phase 1):** Mobile-first design at 375px is the deliverable. Tablet/desktop layouts are a stretch — if trivially responsive (single column scales gracefully), we ship; otherwise deferred. AC-005 only requires 375px baseline, so demo is safe.

**Resolution owner:** Designer Phase 2 + PM.
**Resolution gate:** HI-FI mockups.

---

## OPEN-D-014 — Dependence on SA decisions for HI-FI gating

Phase 2 HI-FI **cannot start until SA-001 lands**, because the following SA decisions materially shape components:

| SA / TL decision needed | Affects | LO-FI working assumption |
|---|---|---|
| `meta.freshness` field in response (OPEN-D-005) | Screen 4 global banner trigger | Assume present |
| OpenAPI shape (final field names) | All rows | Assume BA AccountInfo shape |
| `balance_as_of` semantics confirmed in ADR | All rows + per-row badge | Assume OPEN-002 baked |
| Staleness threshold (60s) confirmation in NFR | Per-row badge | Assume 60s |
| Service boundary (SUBDEC-004) | None — UI-agnostic | n/a |
| AccountClient batch confirmation (SUBDEC-002) | None — UI-agnostic | n/a |
| Audit event schema (SUBDEC-003) | None — UI-agnostic | n/a |
| FIXED_DEPOSIT balance composition (SUBDEC-001) | Row balance display | Assume principal + accrued interest |

**Process:** Orchestrator MUST pause Designer Phase 2 invocation until SA-001 envelope lands; then re-invoke Designer Phase 2.

---

## Resolution priority for HI-FI Phase 2 kick-off

| Priority | Item | Blocker for HI-FI? |
|---|---|---|
| P0 | OPEN-D-005 (snapshot signal) | YES — needs SA |
| P0 | OPEN-D-014 (SA gating) | YES |
| P1 | OPEN-D-001 (tokens) | YES — HI-FI cannot mock without |
| P1 | OPEN-D-003 (micro-copy) | YES — for HI-FI fidelity |
| P2 | OPEN-D-002 (icons) | NO — placeholder acceptable for initial HI-FI |
| P2 | OPEN-D-004 (time thresholds) | NO — FE concern; proposed defaults usable |
| P2 | OPEN-D-008 (pull-to-refresh) | NO — proposed default usable |
| P3 | OPEN-D-006, 007, 009, 010, 011, 012, 013 | NO — design polish |

---

## Cross-references

- Wireframes: `wireframes-lofi.md`
- Interaction spec: `interaction-spec-lofi.md`
- A11y notes: `accessibility-notes.md`
- BA glossary: `docs/ba/balance-comparison/glossary.md`
