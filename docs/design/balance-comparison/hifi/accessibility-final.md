# Accessibility — Final (HI-FI) — Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** Phase 2 — HI-FI / Design
> **Standard:** WCAG 2.1 AA (BA NFR §5; AC-005-H1..H4, AC-005-E1..E2)
> **Demo exit criterion:** Lighthouse mobile a11y score ≥ 90 (BR-023)
> **Source LO-FI a11y:** [`../accessibility-notes.md`](../accessibility-notes.md)
> **Token source:** [`../../_shared/tokens.json`](../../_shared/tokens.json) + [`tokens-rationale.md`](../../_shared/tokens-rationale.md) §3 (contrast evidence)
> **Component specs:** [`./component-specs.md`](./component-specs.md)
> **Screen specs:** [`./screen-specs.md`](./screen-specs.md)

This document closes the WCAG 2.1 AA evidence loop for HI-FI: per-component evidence, per-screen focus order, screen-reader templates, touch-target evidence, and the reduced-motion contract.

---

## 1. Pairwise contrast evidence — only token-pairs actually used in screens

> Full table lives in [`tokens-rationale.md` §3](../../_shared/tokens-rationale.md). This section enumerates ONLY the pairs that appear in any HI-FI component/screen spec, with the AC anchor.

### 1.1 Text-on-surface pairs (WCAG 1.4.3 — ≥ 4.5:1 normal, ≥ 3:1 large)

| # | Foreground token | Background token | Hex pair | Ratio | AA-normal | Used in | AC |
|---|---|---|---|---|---|---|---|
| 1 | `color.text.primary` | `color.surface.card` | `#0F172A` / `#FFFFFF` | **17.85:1** | PASS (AAA) | AccountRow type label, balance, error heading, empty heading | AC-005-H4 |
| 2 | `color.text.primary` | `color.surface.page` | `#0F172A` / `#F8FAFC` | **17.16:1** | PASS (AAA) | PageHeader h1 | AC-005-H4 |
| 3 | `color.text.secondary` | `color.surface.card` | `#475569` / `#FFFFFF` | **7.55:1** | PASS (AAA) | Masked number, last-updated, currency code, body copy | AC-005-H4 |
| 4 | `color.text.secondary` | `color.surface.page` | `#475569` / `#F8FAFC` | **7.26:1** | PASS (AAA) | Header subtitle | AC-005-H4 |
| 5 | `color.text.tertiary` | `color.surface.card` | `#64748B` / `#FFFFFF` | **5.05:1** | PASS | Error correlationId label | AC-005-H4 |
| 6 | `color.text.inverse` | `color.primary.700` | `#FFFFFF` / `#1E3A8A` | **10.36:1** | PASS (AAA) | PrimaryButton default state (error retry) | AC-005-H4 |
| 7 | `color.text.inverse` | `color.primary.500` | `#FFFFFF` / `#3B5BB8` | **5.74:1** | PASS | PrimaryButton hover state | AC-005-H4 |
| 8 | `color.text.inverse` | `color.primary.800` | `#FFFFFF` / `#162C6A` | **13.74:1** | PASS (AAA) | PrimaryButton pressed state | AC-005-H4 |
| 9 | `color.semantic.warning.on_tint` | `color.semantic.warning.tint` | `#78350F` / `#FEF3C7` | **8.05:1** | PASS (AAA) | StalenessBanner copy + per-row StalenessBadge text | AC-005-H4 |
| 10 | `color.semantic.danger.on_tint` | `color.semantic.danger.tint` | `#7F1D1D` / `#FEE2E2` | **8.13:1** | PASS (AAA) | (reserved — toast on refresh fail) | AC-005-H4 |

### 1.2 UI-component / focus-ring contrast (WCAG 1.4.11 — ≥ 3:1)

| # | Element | Background | Pair | Ratio | AA-3:1 | Used in |
|---|---|---|---|---|---|---|
| 11 | Focus ring `color.border.focus` | `color.surface.card` | `#1E3A8A` / `#FFFFFF` | **10.36:1** | PASS | AccountRow focus, button focus, refresh-button focus |
| 12 | Focus ring `color.border.focus` | `color.surface.page` | `#1E3A8A` / `#F8FAFC` | **9.96:1** | PASS | PageHeader refresh-button focus, retry-button focus |
| 13 | Focus ring `color.border.focus` | `color.semantic.warning.tint` | `#1E3A8A` / `#FEF3C7` | **9.92:1** | PASS | RetrySoftButton focus inside StalenessBanner |
| 14 | StalenessBanner border-left | `color.semantic.warning.tint` | `#D97706` / `#FEF3C7` | **3.06:1** | PASS | Visual emphasis (decorative — non-text-bearing) |
| 15 | Warning icon (Heroicons) `color.semantic.warning.base` | `color.semantic.warning.tint` | `#D97706` / `#FEF3C7` | **3.06:1** | PASS | StalenessBanner + per-row badge icon |

### 1.3 Intentional-below-AA pairs (documented exemptions)

| # | Element | Background | Pair | Ratio | Status | Rationale |
|---|---|---|---|---|---|---|
| 16 | `color.text.disabled` | `color.surface.card` | `#94A3B8` / `#FFFFFF` | 2.86:1 | INTENTIONAL — exempt per WCAG SC 1.4.3 (informative) | Disabled CTA ("เปิดบัญชีใหม่" placeholder). Carries `aria-disabled="true"` for assistive tech. |
| 17 | `color.border.subtle` | `color.surface.card` | `#E2E8F0` / `#FFFFFF` | 1.23:1 | INTENTIONAL — decorative | Hairline divider between rows. Spacing + DOM order carry the semantic load, not the line. |
| 18 | Skeleton bar `color.neutral.100` | `color.surface.card` | `#F1F5F9` / `#FFFFFF` | 1.05:1 | INTENTIONAL — decorative | Skeleton is `aria-hidden="true"`; `aria-busy` on the list carries the semantic load. |

### 1.4 Total contrast pairs documented

- **Pairs documented in HI-FI scope:** 18 (10 text-on-surface + 5 UI-component + 3 intentional-exempt)
- **AA-passing pairs:** 15 (text + UI elements)
- **AAA-passing pairs:** 10 (subset of AA — all primary text and stale states)
- **Documented exemptions:** 3 (with rationale + aria fallback)

---

## 2. Focus order — per screen

> Per LO-FI interaction-spec §7, focus follows DOM order which matches visual rank order. The lazy route entry sets programmatic focus to the H1 once.

### 2.1 Screen `balance-dashboard-default` (3-5 accounts)

| Step | Focus target | Component | Visible? | Notes |
|---|---|---|---|---|
| 1 (programmatic on entry) | `<h1>` "บัญชีของฉัน" | PageHeader | YES (one-time focus shift) | SR users hear page change |
| 2 | Refresh icon button | RefreshButton in PageHeader | YES | 44×44 tap target |
| 3 | First AccountRow | AccountRow (rank=1) | YES | Focus ring 3px on surface.card |
| 4..N+2 | Subsequent AccountRows in rank order | AccountRow (rank=2..N) | YES | Same focus treatment |
| (Shift+Tab) | Reverses correctly | — | — | AC-005-H3 |

### 2.2 Screen `balance-dashboard-stale` (banner + rows)

| Step | Focus target | Notes |
|---|---|---|
| 1 | `<h1>` (programmatic) | |
| 2 | RefreshButton (header) | |
| 3 | RetrySoftButton (inside StalenessBanner) | Focus ring 3px on warning.tint |
| 4 | DismissButton (banner ×) | 44×44 |
| 5 | AccountRow rank=1 | |
| 6..N+4 | AccountRows rank=2..N | Stale rows do NOT add extra focus stops — badge is non-interactive |

### 2.3 Screen `balance-dashboard-empty`

| Step | Focus target | Notes |
|---|---|---|
| 1 | `<h2>` "ยังไม่มีบัญชีที่ใช้งานอยู่" | Programmatic on state transition |
| 2 | (none — disabled CTA is `<span aria-disabled>`, not focusable) | Avoids confusing focus stop on a no-op |

### 2.4 Screen `balance-dashboard-error`

| Step | Focus target | Notes |
|---|---|---|
| 1 | `<h2>` "เกิดข้อผิดพลาดในการโหลดข้อมูล" | Programmatic on entry; `role="alert"` already announced |
| 2 | PrimaryButton "ลองใหม่" | Visible focus ring on surface.page |
| (no further focus stops) | correlationId is text-only | Not interactive |

### 2.5 Screen `balance-dashboard-loading`

| Step | Focus target | Notes |
|---|---|---|
| 1 | `<h1>` | Programmatic on route enter |
| (no focusable list rows during load — `aria-busy="true"` on list) | | Refresh button still focusable if rendered |

### 2.6 Screen `balance-dashboard-single` (N=1)

Same as §2.1 but only one AccountRow focus stop.

### 2.7 Screen `balance-dashboard-dense` (N=10)

Same as §2.1 with 10 row focus stops. Below-the-fold rows are reached via scroll; focus auto-scrolls into view (browser default + Angular Router scroll-restoration: 'enabled').

---

## 3. Screen-reader announcement templates

### 3.1 Per-row composed `aria-label` (multi-row screens)

Thai-primary template:
```
"บัญชีลำดับที่ {rank} จาก {totalCount}, {accountTypeLabelTH}, ลงท้ายด้วย {last4},
 ยอดเงิน {balanceSpoken} {currencyNameTH}, อัปเดตเมื่อ {relativeTimeTH}{, อาจไม่ใช่ยอดล่าสุด}"
```

**Example outputs:**

- Live row, savings:
  > "บัญชีลำดับที่ 1 จาก 3, บัญชีออมทรัพย์, ลงท้ายด้วย 7890, ยอดเงิน 128,540 บาท 25 สตางค์, อัปเดตเมื่อ 2 นาทีที่แล้ว"
- Stale row, fixed deposit:
  > "บัญชีลำดับที่ 2 จาก 3, บัญชีเงินฝากประจำ, ลงท้ายด้วย 4421, ยอดเงิน 84,012 บาท, อัปเดตเมื่อ 1 วันที่แล้ว, อาจไม่ใช่ยอดล่าสุด"
- Row with null `balanceAsOf`:
  > "บัญชีลำดับที่ 3 จาก 3, บัญชีกระแสรายวัน, ลงท้ายด้วย 0033, ยอดเงิน 12,005 บาท 55 สตางค์, ไม่ทราบเวลาอัปเดต"

English fallback (locale=EN):
> "Account 1 of 3, Savings account, ending 7890, balance 128,540 baht 25 satang, updated 2 minutes ago."

### 3.2 Single-account row (drops rank prefix per LO-FI §6)

```
"{accountTypeLabelTH} ลงท้ายด้วย {last4}, ยอดเงิน {balanceSpoken} บาท, อัปเดตเมื่อ {relativeTimeTH}"
```

Example:
> "บัญชีออมทรัพย์ ลงท้ายด้วย 7890, ยอดเงิน 45,000 บาท, อัปเดตล่าสุด"

### 3.3 Per-screen first-render announcement

| Screen | Region | Announcement |
|---|---|---|
| default (N=3) | `<balance-account-list>` | "รายการบัญชีของคุณ 3 รายการ เรียงจากยอดเงินมากไปน้อย" |
| default (N=10) | same | "รายการบัญชีของคุณ 10 รายการ เรียงจากยอดเงินมากไปน้อย" |
| loading | `<balance-account-list>` (`aria-busy="true"`) | "กำลังโหลดข้อมูล" |
| empty | `<balance-empty-state>` (`role="status"`) | "ยังไม่มีบัญชีที่ใช้งานอยู่ บัญชีที่ปิดหรือไม่เคลื่อนไหวจะไม่แสดงในหน้านี้" |
| stale | `<balance-staleness-banner>` (`role="status"`, `aria-live="polite"`) | "ยอดเงินอาจไม่เป็นปัจจุบัน ระบบใช้ข้อมูลล่าสุดที่บันทึกไว้" |
| error | `<balance-error-state>` (`role="alert"`, `aria-live="assertive"`) | "เกิดข้อผิดพลาดในการโหลดข้อมูล กรุณาลองใหม่อีกครั้ง" |
| single | `<balance-account-list>` | "บัญชีของคุณ {accountTypeLabelTH} ลงท้ายด้วย {last4} ยอดเงิน {balanceSpoken} บาท" |

### 3.4 Balance figure — spoken-form rules (AC-005-E2)

- `aria-label` MUST use full spoken form: `"128,540 บาท 25 สตางค์"` — NEVER digit-by-digit.
- Thai pluralization is invariant for "บาท" / "สตางค์" — no plural form needed.
- When satang = 0, drop the suffix: `"128,540 บาท"`.
- When value = null, `aria-label="ไม่ทราบยอดเงิน"` and visible em-dash.
- Currency symbol `฿` is `aria-hidden="true"` (the spoken form includes "บาท").

### 3.5 Last-updated — spoken templates

| Threshold | Spoken Thai | Spoken English |
|---|---|---|
| < 30s | "อัปเดตล่าสุด" | "Updated just now" |
| 30s..60s | "อัปเดตเมื่อ {n} วินาทีที่แล้ว" | "Updated {n} seconds ago" |
| 1m..59m | "อัปเดตเมื่อ {n} นาทีที่แล้ว" | "Updated {n} minutes ago" |
| 1h..23h | "อัปเดตเมื่อ {n} ชั่วโมงที่แล้ว" | "Updated {n} hours ago" |
| 1d..6d | "อัปเดตเมื่อ {n} วันที่แล้ว" | "Updated {n} days ago" |
| ≥ 7d | "อัปเดตเมื่อ {DD} {MMM} {YYYY} {HH}:{mm} น." | "Updated {DD} {MMM} {YYYY} {HH}:{mm}" |
| null | "ไม่ทราบเวลาอัปเดต" | "Last update unknown" |

---

## 4. Touch-target evidence (WCAG 2.5.5 — ≥ 44 × 44 dp)

| Component | Min size | Token | Evidence |
|---|---|---|---|
| `<balance-account-row>` (entire row) | 72 × 375 px (min-height × full width) | `size.row_min` (72px) | Exceeds 44×44; entire row is tap target |
| `<balance-refresh-button>` | 44 × 44 px | `size.tap_min` | Icon button in header |
| `<balance-primary-button>` | 44 × variable | `size.tap_min` (min-height) | Error retry button |
| `<balance-retry-soft-button>` | 44 × variable | `size.tap_min` | Banner retry |
| Banner dismiss button (×) | 44 × 44 px | `size.tap_min` | Adjacent spacing ≥ 8px to retry button |
| `<balance-staleness-badge>` | Non-interactive | n/a | Decorative text — no tap target required |

**Inter-target spacing:** Minimum `spacing.2` (8px) between any two adjacent interactive targets per LO-FI §1.

---

## 5. Reduced-motion behavior — per component

> Mandatory per WCAG 2.3.3 + tokens.json `motion.duration.instant` swap rule.

| Component | Default motion | `prefers-reduced-motion: reduce` behavior |
|---|---|---|
| `<balance-account-row>` hover transition | `motion.duration.fast` color shift | `0ms` — instant color change |
| `<balance-account-row>` focus ring | `motion.duration.fast` outline appear | `0ms` — instant ring |
| `<balance-skeleton-row>` shimmer | NONE (already disabled in v1 per LO-FI) | NONE — same as default |
| `<balance-staleness-badge>` fade-in | `motion.duration.normal` opacity 0→1 | `0ms` — instant appearance |
| `<balance-staleness-banner>` slide-in | `motion.duration.normal` translateY | `0ms` — instant appearance |
| `<balance-primary-button>` loading spinner | rotation animation | suppress rotation; show static spinner OR text "กำลังโหลด…" |
| Pull-to-refresh spring | platform-default spring | suppress spring; show plain spinner + text "กำลังโหลด…" |
| Page route transition | (handled by Angular Router; out of v1 component scope) | (defer to v1.1) |

**Implementation contract for FE-dev:** All `transition-duration` and `animation-duration` MUST reference `var(--motion-duration-*)` so the global `@media (prefers-reduced-motion: reduce)` rule in tokens.css automatically swaps to 0ms (see `_shared/README.md` §2.3).

---

## 6. ARIA contract — final per component

| Component | role | aria-* attributes | Notes |
|---|---|---|---|
| `<balance-dashboard-page>` | `<main>` landmark | `aria-labelledby="balance-page-title"` | One per route |
| `<balance-page-header>` | implicit `<header>` | `<h1 id="balance-page-title">` | One H1 per page |
| `<balance-refresh-button>` | `<button>` | `aria-label="รีเฟรชยอดเงิน"`, `aria-busy="true"` while loading | Spinner `aria-hidden` |
| `<balance-account-list>` | `role="list"` | `aria-label="..."` (Thai), `aria-busy="true"` while loading | Required (Angular tpl can break implicit role) |
| `<balance-account-row>` | `role="listitem"` + `<button>` | Composed `aria-label` per §3.1 template | One row = one logical button |
| `<balance-account-type-icon>` | inline `<svg>` | `aria-hidden="true"` | Decorative — label carries meaning |
| `<balance-masked-account-label>` | inline `<span>` | `aria-label="ลงท้ายด้วย {last4}"` | Single source of mask format |
| `<balance-amount>` | inline `<span>` | `aria-label="{baht} บาท {satang} สตางค์"` (AC-005-E2) | ฿ symbol `aria-hidden` |
| `<balance-last-updated>` | inline `<span>` | `aria-describedby="{tooltipId}"` | Tooltip dismissable with Esc |
| `<balance-staleness-badge>` | inline `<span>` | (text content visible — no aria override needed) | Non-interactive |
| `<balance-staleness-banner>` | `role="status"` | `aria-live="polite"`, `aria-labelledby="banner-title"` | Announces once on appearance |
| `<balance-retry-soft-button>` | `<button>` | `aria-busy="true"` while loading | Visible focus ring |
| `<balance-primary-button>` | `<button>` | `aria-busy="true"` while loading | Disabled state carries `aria-disabled="true"` |
| `<balance-empty-state>` | `role="status"` | (not `role="alert"` — informational) | Heading receives programmatic focus |
| `<balance-error-state>` | `role="alert"` | `aria-live="assertive"` | Heading receives programmatic focus |
| `<balance-skeleton-list>` | `role="list"` + `aria-busy="true"` | n/a | Children `aria-hidden="true"` |
| `<balance-skeleton-row>` | n/a | `aria-hidden="true"` | Decorative — busy state on parent |

---

## 7. WCAG 2.1 AA Lighthouse exit criterion (AC-005-H4)

| Lighthouse audit category | Target | How HI-FI satisfies it |
|---|---|---|
| Color contrast | 100% | All text pairs documented §1.1 (AA pass); intentional exemptions §1.3 carry aria fallback |
| Image alt text | 100% | Decorative icons `aria-hidden="true"`; illustrations have i18n alt keys |
| ARIA roles + names | 100% | Per-component contract §6; composed names per §3 |
| Focus order | 100% | DOM = visual = rank order; documented §2 |
| Touch targets | 100% | `size.tap_min` (44px) + row min 72px; documented §4 |
| Reduced motion | 100% | CSS media query in tokens.css; per-component behavior §5 |
| Language | 100% | `<html lang="th">` + i18n locale swap |
| Heading hierarchy | 100% | One H1 per route; H2 for section headings |

**Self-assessment:** All inputs to Lighthouse AA are satisfied by HI-FI design. Final score depends on FE-dev implementation fidelity. QA P2 will run actual Lighthouse audit on demo staging URL.

---

## 8. Color independence (WCAG 1.4.1)

Information is NEVER conveyed by color alone:

| Stale state | Carriers |
|---|---|
| StalenessBanner | Icon (`exclamation-triangle`) + Thai heading text + warning color |
| Per-row badge | Icon (`exclamation-triangle`) + Thai text "อาจไม่ใช่ยอดล่าสุด" + warning color |
| Error state | Icon + Thai heading text + danger color |
| Rank order | DOM position + SR announcement ("ลำดับที่ N จาก M") — NOT a color/highlight |
| Empty state | Illustration + Thai heading text + secondary text color |

**Anti-pattern explicitly forbidden:** Using only a red border or only a yellow background to communicate state. Always icon + text + color = three independent channels.

---

## 9. Mobile-first + responsive a11y

| Concern | HI-FI evidence |
|---|---|
| 375px viewport, no horizontal scroll | All 7 screens specified at 375px base; balance figure right-aligned with adequate left-of-screen space (AC-005-H1) |
| 200% font scaling | Row stacks vertically when needed; min-height grows to accommodate; nothing clipped (AC-005-E1) |
| Dynamic OS text-size | Tokens use px (FE-dev choice rem vs px — both acceptable for AA per CSS spec) |
| Landscape orientation | Layout works; rows are wider; no functional change |
| Switch Control / Voice Control | All interactive elements have visible focus + accessible name |
| `prefers-reduced-motion` | Per §5 contract |
| `prefers-color-scheme: dark` | Out of v1 (light only); tokens structured for v1.1 dark swap |

---

## 10. QA handoff — testable a11y checklist (for QA P2)

- [ ] AC-005-H1: Render 3+ accounts at 375px → no horizontal scrollbar (Chrome DevTools + Pixel 5 physical)
- [ ] AC-005-H2: VoiceOver iOS announces per §3.1 template (rank + type + last4 + spoken balance + last-updated)
- [ ] AC-005-H2: TalkBack Android same template (locale-aware)
- [ ] AC-005-H3: Tab from page entry; focus order matches §2 per screen
- [ ] AC-005-H3: Visible focus ring (`color.border.focus` 3px @ 10.36:1 — verify with WebAIM Contrast Checker)
- [ ] AC-005-H3: Shift+Tab reverses correctly
- [ ] AC-005-H4: Lighthouse mobile a11y ≥ 90 on demo staging URL — evidence attached to QA P2 report
- [ ] AC-005-E1: 200% browser font scale → no clipping/overlap on any of 7 screens
- [ ] AC-005-E2: VoiceOver reads balance as "128,540 บาท 25 สตางค์" (NOT digit-by-digit)
- [ ] Reduced-motion: enable system flag → no fade-in on banner/badge; spinner static; skeleton already static
- [ ] StalenessBanner: announced via `aria-live="polite"` once; does NOT steal focus
- [ ] ErrorState: announced via `role="alert"`; heading receives programmatic focus
- [ ] EmptyState: announced via `role="status"`; informational not urgent
- [ ] Tooltip on `<balance-last-updated>` dismissable with Esc; persists while focused
- [ ] All 18 contrast pairs in §1 verified with WebAIM contrast checker (sample 5 randomly)
- [ ] Long-press on stale badge reveals absolute timestamp tooltip
- [ ] Pull-to-refresh gesture works on iOS Safari + Android Chrome
- [ ] Explicit refresh button keyboard-reachable + screen-reader-named

---

## 11. Items deferred to v1.1 (with rationale)

| Item | Why deferred |
|---|---|
| Dark mode | Stakeholder decision (Phase 2: light only). Tokens structured to add `:root[data-color-mode="dark"]` block. |
| Tablet (768px) + desktop (1280px) layouts | Mobile-only v1 (OPEN-D-013). Tokens already declare breakpoints. |
| Sticky PageHeader | LO-FI commitment (no sticky in v1). Revisit if UX testing flags. |
| Rank-change FLIP animation | Out of v1 scope (interaction-spec §4). |
| Sparkline a11y | US-BC-007 next sprint. |
| Color-blindness deuteranopia/protanopia sim | Will run against tokens in QA P2 if Lighthouse flags. Per §8, color is never the only channel. |

---

## 12. Cross-references

- Token contrast evidence (full pair table): [`../../_shared/tokens-rationale.md`](../../_shared/tokens-rationale.md) §3
- Token source: [`../../_shared/tokens.json`](../../_shared/tokens.json)
- Component specs: [`./component-specs.md`](./component-specs.md)
- Screen specs: [`./screen-specs.md`](./screen-specs.md)
- LO-FI a11y notes: [`../accessibility-notes.md`](../accessibility-notes.md)
- LO-FI interaction spec: [`../interaction-spec-lofi.md`](../interaction-spec-lofi.md)
- BA AC anchors: [`../../../ba/balance-comparison/user-stories.md`](../../../ba/balance-comparison/user-stories.md) (US-BC-005)
- BA NFR (a11y baseline): [`../../../ba/balance-comparison/nfr.md`](../../../ba/balance-comparison/nfr.md) §5
- Security C-2 (no raw balance in error UI): [`../../../security/balance-comparison/early-review-consent-coverage.md`](../../../security/balance-comparison/early-review-consent-coverage.md) §7
- WCAG 2.1 — https://www.w3.org/TR/WCAG21/
- WebAIM Contrast Checker — https://webaim.org/resources/contrastchecker/
