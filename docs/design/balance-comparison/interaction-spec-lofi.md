# Interaction Spec — Account Balance Comparison Dashboard (LO-FI)

> **Feature slug:** `balance-comparison`
> **Phase:** Phase 1 — LO-FI / Discovery
> **Purpose:** Capture behaviour decisions so SA / TL / FE can validate the interaction model before HI-FI Phase 2.

---

## 1. Tap targets and pointer hygiene

| Rule | Value | Source |
|---|---|---|
| Minimum tap target | **44 × 44 dp** (CSS px equivalent) | WCAG 2.5.5 Target Size, AC-005-H3 |
| Minimum spacing between targets | 8 dp | Conventional |
| `AccountRow` total height | ≥ 64 dp (LO-FI suggestion) — final tokens in HI-FI | Mobile thumb reach |
| Row tap area | Entire row is the tap target — not just the title | Reduces mis-taps |
| `RetrySoftButton` / `PrimaryButton` height | ≥ 44 dp | WCAG 2.5.5 |
| Pull-to-refresh trigger threshold | 80 dp vertical drag from top edge | Platform convention |

**Anti-rule:** Never put two interactive controls inside a single row (e.g., a separate "details" chevron AND a row tap) — keeps gesture intent unambiguous.

---

## 2. Pull-to-refresh

**Recommendation: YES for v1** — with this rationale and the optional drop path.

| Aspect | Decision |
|---|---|
| Enabled? | YES (recommended). |
| Behaviour | Pull from top of list → spinner → refetch `GET /v1/balance-dashboard` → re-render. |
| Audit impact | A refresh IS a new dashboard retrieval → audit event re-emitted per BR-014. UX implication: nothing visible to the customer; logged server-side. |
| Cache impact | Hits warm cache if within 30s TTL; otherwise cold path. The customer sees "Updated just now" if balance_as_of refreshes (when downstream produces a new ledger snapshot). |
| Concurrent guard | If a fetch is in flight, ignore additional pulls (prevent N parallel requests). |
| Reduced motion | If `prefers-reduced-motion: reduce`, suppress bounce animation but keep the gesture functional (no visual spring). |
| Failure | If refetch fails, show inline toast "ดึงข้อมูลใหม่ไม่สำเร็จ ลองอีกครั้ง" — do NOT replace the screen with the error state if the list was already rendered. |
| Drop condition (optional escape valve for FE) | If FE perceives material implementation cost in v1 (e.g., gesture conflict with existing nav-drawer), drop pull-to-refresh and provide a visible refresh icon in the header instead. Flag in Phase 2. |

---

## 3. Scrolling and sticky chrome

| Element | Behaviour |
|---|---|
| `PageHeader` | **NOT sticky in v1** (recommended). Keeps implementation simple; list is short enough that header scrolls out naturally. |
| `StalenessBanner` (Screen 4) | Not sticky in v1. Scrolls with content. Sticky behaviour reconsidered in HI-FI if usability testing flags it. |
| `AccountList` | Vertical scroll only. **No horizontal scroll at any breakpoint** (BR-019, AC-005-H1). |
| Scroll restoration | When customer returns from account-detail (out-of-scope screen), restore scroll position. Angular Router `scrollPositionRestoration: 'enabled'` handles this. |
| Overscroll | Native overscroll (rubber-band) — no custom override. |

---

## 4. Animations (and what is deferred)

| Animation | v1 decision | Reduced-motion override |
|---|---|---|
| Skeleton shimmer | Suppressed (no shimmer) in v1 LO-FI. Static placeholder bars. | N/A |
| Pull-to-refresh spinner | Standard platform spinner | Reduce duration / disable bounce when `prefers-reduced-motion: reduce` |
| Rank-change animation when balances update mid-session | **FUTURE — do NOT design in v1.** Call-out only. Future sprint can use FLIP technique to animate rows reordering. | n/a |
| Stale-badge appearance | Static fade-in (200ms max) | Replace with instant appearance when reduced-motion is set |
| Empty-state illustration entrance | Static (no animation) | n/a |

**Reduced-motion is mandatory** — every animated element queries `prefers-reduced-motion` (WCAG 2.3.3).

---

## 5. Long-press / hover behaviour

| Target | Touch | Mouse / pointer | Keyboard |
|---|---|---|---|
| `LastUpdatedLabel` | Long-press (500ms) reveals absolute timestamp in a tooltip / sheet | Hover reveals tooltip after 300ms | Focus + Enter reveals tooltip (announced via `aria-describedby`) |
| `AccountRow` | Tap → navigate (out of scope target) | Click → navigate | Tab to focus + Enter to activate |
| `StaleBadge` | Long-press optional → toast "ยอดอัปเดตล่าสุด <absolute>"; nice-to-have, can defer | Hover → same toast | Focus + Enter → same toast |

---

## 6. Screen reader (semantic & announcement order)

Reading order MUST follow visual order. The `AccountList` must be a semantic list.

### 6.1 Per-row announcement template (AC-005-H2, BR-021)

```
"Account {rank} of {total}, {accountTypeLabelTH}, ending {last4},
 balance {balanceFullSpokenForm} {currencyCode},
 last updated {relativeTimeTH}{, may be stale (if applicable)}"
```

Example (Thai-first):
> "บัญชีลำดับที่ 1 จาก 3, บัญชีออมทรัพย์, ลงท้ายด้วย 7890, ยอดเงิน 128,540 บาท 25 สตางค์, อัปเดตเมื่อ 2 นาทีที่แล้ว"

English fallback (when locale = EN):
> "Account 1 of 3, Savings account, ending 7890, balance 128,540 baht 25 satang, updated 2 minutes ago."

### 6.2 Aggregate announcement on first render

When list finishes loading (Screen 2 → Screen 1), the list region announces (via `aria-live="polite"` on a status element):
> "บัญชีของฉัน แสดง 3 บัญชีเรียงตามยอดเงินจากมากไปน้อย"
> ("My Accounts — showing 3 accounts ordered by balance from highest to lowest.")

### 6.3 Loading announcement

`AccountList` carries `aria-busy="true"` while loading. Screen readers announce "Loading" without spamming each skeleton row.

### 6.4 Stale-state announcement

`StalenessBanner` uses `role="status"` + `aria-live="polite"` so it is announced once when it appears (not on every focus change).

### 6.5 Error announcement

Screen 5 illustration + heading uses `role="alert"` + `aria-live="assertive"` so the screen reader interrupts gracefully and the customer knows the screen has changed.

### 6.6 Empty-state announcement

Screen 3 heading uses `role="status"`. Avoid `role="alert"` here — empty is informational, not urgent.

---

## 7. Focus order and keyboard

| Step | Focus target | Notes |
|---|---|---|
| 1 | `PageHeader` (programmatic focus on route enter for screen-reader users) | One-time focus shift on lazy route load |
| 2 | `PullToRefreshAffordance` (if present and keyboard-accessible — provide an equivalent "Refresh" button as a fallback for keyboard users) | Pull gesture is not keyboard-reachable; the button is |
| 3 | `StalenessBanner` ⇒ `RetrySoftButton` (when present) | Skip if not rendered |
| 4 | First `AccountRow` | Visible focus ring (contrast ≥ 3:1 against background, AC-005-H3) |
| 5 | Subsequent `AccountRow` rows in rank order | Tab order matches DOM order matches visual rank |
| 6 | (Future) Footer / nav — out of scope for this component |
|   | Shift+Tab reverses correctly | AC-005-H3 |

**Keyboard shortcuts:** None added in v1 (no `j/k` style row navigation). The standard Tab / Shift+Tab + Enter is the only contract.

---

## 8. Touch gestures summary

| Gesture | Action | Where |
|---|---|---|
| Tap | Activate row → navigate to detail (out of scope) | `AccountRow` |
| Long-press | Reveal absolute timestamp | `LastUpdatedLabel` |
| Pull-down from top | Refresh list | List region |
| Swipe-left / swipe-right on row | **NO action in v1.** No quick actions, no delete. | `AccountRow` |
| Pinch-zoom | Allow (do not disable via `user-scalable=no`). WCAG 1.4.4 | Whole page |

---

## 9. Loading-state policy

| Trigger | UI |
|---|---|
| Initial route entry | Screen 2 (skeleton) immediately |
| Pull-to-refresh | Spinner + keep current rows visible (do not flash skeleton) |
| Retry button | Skeleton again — full reset acceptable from explicit retry |
| Background refresh (future) | Out of scope v1 |

No optimistic UI (per `angular-banking-ui` SKILL §security). Balance is canonical data — wait for the server.

---

## 10. Error-handling matrix

| Backend response | UI screen | Notes |
|---|---|---|
| 200 with accounts ≥ 1 | Screen 1 (or 6 if N=1, 7 if N≥7) | Happy |
| 200 with `accounts=[]` | Screen 3 (empty) | AC-001-E1 |
| 200 with `meta.freshness="snapshot"` (proposed) | Screen 4 (global banner) | See open questions for HI-FI |
| 200 with per-account `balance_as_of` older than 60s | Screen 4 (per-row badge) | AC-002-E1 |
| 200 with per-account `balance_as_of=null` | Screen 1/4 with row's last-updated = "—" | AC-002-E2 |
| 401 Unauthorized | Auto-redirect to login (handled outside this component) | AC-001-E3 |
| 403 Forbidden | Screen 5 (generic) — do NOT reveal cause | AC-001-E2 |
| 408 / network timeout | Screen 5 with correlationId | Resilience NFR |
| 503 (AccountClient unavailable after retries) | Screen 5 | NFR §2 |
| 5xx unexpected | Screen 5 | Defensive |

---

## 11. Data freshness UX contract

| Label / state | Source | Display rule |
|---|---|---|
| "Updated just now" | `now() - balance_as_of < 30s` | Default fresh-state |
| "Updated X seconds/minutes/hours/days ago" | Derived from `balance_as_of` | Relative time formatter (FE concern) |
| "Updated —" | `balance_as_of === null` | AC-002-E2 |
| "อาจไม่ใช่ยอดล่าสุด" badge | `now() - balance_as_of > 60s` | BR-013 threshold (configurable feature flag) |
| Header subtitle "Updated just now" | Min `now() - balance_as_of` across all rows | Reflects the freshest row so the header is never more pessimistic than the rows |
| Global stale banner | Server signals `meta.freshness="snapshot"` (proposed) | See open questions for HI-FI / SA |

**Relative-time thresholds (proposed for HI-FI confirmation):**
- < 30s → "just now"
- < 60min → "X minutes ago"
- < 24h → "X hours ago"
- ≥ 24h → "X days ago" (no week / month for v1)

Hover / long-press absolute format: `"DD MMM YYYY HH:mm ICT"` (AC-002-H1).

---

## 12. Internationalisation contract

- All visible strings must come from i18n keys (Thai-primary, English-fallback per locale). FE must not hard-code strings (`angular-banking-ui` SKILL).
- Number formatting uses the Thai locale by default: `Intl.NumberFormat('th-TH', { style: 'currency', currency: 'THB' })` (or the i18n-stable Angular pipe).
- Relative-time formatting respects locale (e.g., "2 นาทีที่แล้ว" vs "2 minutes ago").
- Account type labels: bilingual mapping per BR-008 — list is locked in BA glossary; FE must use a dictionary, not inline strings.

---

## 13. Out-of-scope interactions (do not design)

- Swipe actions (delete, hide, pin) — US-BC-008 next sprint
- Filter / search bar — US-BC-006 next sprint
- Sort override (e.g., by type, by currency) — US-BC-006 next sprint
- Sparkline tap-to-expand — US-BC-007 next sprint
- Multi-select / batch actions — not on roadmap
- Settings cog / preferences — not on roadmap
- Drag-to-reorder — not on roadmap

---

## Cross-references

- Wireframes: `wireframes-lofi.md`
- A11y checklist: `accessibility-notes.md`
- Open questions for HI-FI: `open-questions-for-hifi.md`
- BA AC source: `docs/ba/balance-comparison/user-stories.md`
- BA NFR: `docs/ba/balance-comparison/nfr.md`
