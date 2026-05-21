# Accessibility Notes — Account Balance Comparison Dashboard (LO-FI)

> **Feature slug:** `balance-comparison`
> **Phase:** Phase 1 — LO-FI / Discovery
> **Compliance target:** WCAG 2.1 AA (BA NFR §5; AC-005-H1..H4, AC-005-E1..E2)
> **Sprint demo exit criterion:** Lighthouse mobile a11y score ≥ 90 (BR-023)

This document captures a11y intent at the LO-FI stage. Token-level checks (exact colour hex contrast, focus-ring spec, motion easing) are confirmed in Phase 2 HI-FI once tokens are locked.

---

## 1. WCAG 2.1 AA checklist — mapped to wireframes

### 1.1 Perceivable

| WCAG SC | Requirement | LO-FI plan | Wireframe |
|---|---|---|---|
| 1.1.1 Non-text Content | Every meaningful image has a text alternative. | Empty-state and error-state illustrations get `alt` text (Thai + English). Decorative icons use `aria-hidden="true"`. | 3, 5 |
| 1.3.1 Info and Relationships | Semantic structure conveyed in code. | `AccountList role="list"` + `AccountRow role="listitem"`; `PageHeader` is an `<h1>` (or `aria-level=1`). | 1, 4, 6, 7 |
| 1.3.4 Orientation | No restriction to portrait only. | Layout works portrait + landscape at 375px viewport baseline. | All |
| 1.3.5 Identify Input Purpose | (N/A — no inputs in v1 read-only dashboard.) | — | — |
| 1.4.3 Contrast (Minimum) | ≥ 4.5:1 normal text, ≥ 3:1 large text. | Exact tokens deferred to Phase 2 HI-FI; LO-FI commits the rule. | All |
| 1.4.4 Resize Text | Up to 200% without loss of content/function. | Tested at 200% font scale — AC-005-E1; row layout wraps gracefully. | 1, 4, 6, 7 |
| 1.4.5 Images of Text | Use real text, not images. | Balance, masked number, labels are all real text. | All |
| 1.4.10 Reflow | Content must reflow at 320px CSS px equivalent without horizontal scroll. | 375px baseline (with 320px as forward-compat guard). | All |
| 1.4.11 Non-text Contrast | UI components (focus ring, badges) ≥ 3:1. | LO-FI commits the rule; tokens in HI-FI. | All |
| 1.4.12 Text Spacing | No content lost when text spacing increased to spec. | No `overflow:hidden` on row labels in v1. | All |
| 1.4.13 Content on Hover/Focus | Tooltips dismissible, hoverable, persistent. | `LastUpdatedLabel` tooltip can be dismissed with Esc; persists while focused. | 1, 4, 6, 7 |

### 1.2 Operable

| WCAG SC | Requirement | LO-FI plan | Wireframe |
|---|---|---|---|
| 2.1.1 Keyboard | All functionality via keyboard. | Pull-to-refresh has a keyboard-reachable equivalent (refresh button or `RetrySoftButton` on Screen 4). | All |
| 2.1.2 No Keyboard Trap | Focus can move away from any component. | No modal traps in v1. Tooltips dismiss with Esc. | All |
| 2.1.4 Character Key Shortcuts | (N/A — no single-character shortcuts in v1.) | — | — |
| 2.4.1 Bypass Blocks | Skip-link or landmark for main content. | `<main>` landmark wraps the dashboard route. Skip-link inherited from app shell. | All |
| 2.4.3 Focus Order | DOM order matches visual order = rank order. | Confirmed in interaction spec §7. | All |
| 2.4.6 Headings and Labels | Headings describe topic / purpose. | `PageHeader` (H1) = "บัญชีของฉัน"; no other H1 on the page. | All |
| 2.4.7 Focus Visible | Keyboard focus indicator is visible. | LO-FI commits visible focus ring on `AccountRow`, buttons, tooltip targets. | All |
| 2.5.3 Label in Name | Accessible name contains the visible label. | `AccountRow` accessible name includes the visible Thai label. | 1, 4, 6, 7 |
| 2.5.5 Target Size | ≥ 44 × 44 dp interactive targets. | Confirmed in interaction spec §1. | 1, 4, 5, 6, 7 |

### 1.3 Understandable

| WCAG SC | Requirement | LO-FI plan | Wireframe |
|---|---|---|---|
| 3.1.1 Language of Page | `<html lang>` set. | `lang="th"` (with `lang="en"` switch via i18n). | All |
| 3.2.1 On Focus | Focus does not trigger context change. | No auto-navigation on focus. | All |
| 3.2.2 On Input | (N/A — no inputs in v1.) | — | — |
| 3.3.1 Error Identification | Errors identified in text. | Screen 5 explicit Thai error copy + `correlationId`. | 5 |
| 3.3.2 Labels or Instructions | (N/A — no inputs in v1.) | — | — |

### 1.4 Robust

| WCAG SC | Requirement | LO-FI plan | Wireframe |
|---|---|---|---|
| 4.1.1 Parsing | Valid HTML / no duplicate IDs. | FE concern; Angular standalone components prevent ID clash via component-scoped templates. | All |
| 4.1.2 Name, Role, Value | All UI controls expose name + role + state. | LO-FI commits ARIA contract per component (see §3 below). | All |
| 4.1.3 Status Messages | Status messages reach AT without focus shift. | `aria-live="polite"` on staleness banner; `role="status"` on empty / loaded announce; `role="alert"` on error. | 2, 3, 4, 5 |

---

## 2. Mobile-first / responsive a11y

| Concern | Plan |
|---|---|
| 375px viewport, no horizontal scroll | Row internal stack adapts; balance value never truncated (BR-019, AC-005-H1). |
| 200% font scaling | Row height grows; line-wrap allowed; nothing clipped (AC-005-E1). |
| Dynamic OS text-size (iOS / Android) | `rem` / fluid typography respects OS settings; tested in HI-FI. |
| Landscape orientation | Layout works; rows wider; no functional change. |
| Touch + assistive | Switch Control / Voice Control reach all interactive targets. |
| `prefers-reduced-motion` | All animations gated; see interaction spec §4. |

---

## 3. ARIA contract per component (LO-FI commitment)

| Component | Role | Accessible name | State attrs | Notes |
|---|---|---|---|---|
| `PageHeader` | Implicit `<h1>` | "บัญชีของฉัน" | n/a | One per route |
| `AccountList` | `role="list"` | `aria-label="ranked list of accounts, highest balance first"` (Thai equivalent) | `aria-busy` during load | Children must be `role="listitem"` |
| `AccountRow` | `role="listitem"` + tappable via `<a>` or `<button>` | Composed accessible name per template in interaction spec §6.1 | `aria-current` (optional) | One row = one logical link/button |
| `MaskedAccountLabel` | Inline text | "ลงท้ายด้วย 7890" | n/a | Last-4 only |
| `BalanceAmount` | Inline text | `aria-label="128,540 บาท 25 สตางค์"` | n/a | AC-005-E2; full spoken form |
| `LastUpdatedLabel` | Inline text + button-like trigger for tooltip | "อัปเดตเมื่อ 2 นาทีที่แล้ว" | `aria-describedby` → tooltip id | Tooltip dismissable with Esc |
| `StaleBadge` | `<span>` with explicit text | "อาจไม่ใช่ยอดล่าสุด" | n/a | Not interactive |
| `StalenessBanner` | `role="status"` + `aria-live="polite"` | Banner copy | n/a | Dismissible button has its own `aria-label="ปิดข้อความ"` |
| `SkeletonRow` | Decorative — `aria-hidden="true"` (list as a whole has `aria-busy`) | n/a | n/a | Don't pollute AT output |
| `EmptyState` | `role="status"` | "ยังไม่มีบัญชีที่ใช้งานอยู่" | n/a | Not `role="alert"` |
| `ErrorState` | `role="alert"` | "เกิดข้อผิดพลาดในการโหลดข้อมูล" | n/a | Interrupts politely |
| `PrimaryButton` / `RetrySoftButton` | `<button>` | "ลองใหม่" / "ลองอีกครั้ง" | `aria-busy="true"` while in-flight | Disable + spinner while loading |
| `PullToRefreshAffordance` | Non-interactive hint | "ดึงลงเพื่อรีเฟรช" | n/a | A keyboard-reachable button mirrors the gesture |

---

## 4. Screen-reader behaviour summary

| Screen | First announcement on entry |
|---|---|
| 1 — Default | "บัญชีของฉัน หน้าหลัก. แสดง 3 บัญชีเรียงตามยอดเงินจากมากไปน้อย." |
| 2 — Loading | "บัญชีของฉัน กำลังโหลดข้อมูล." (via `aria-busy`) |
| 3 — Empty | "บัญชีของฉัน. ยังไม่มีบัญชีที่ใช้งานอยู่." |
| 4 — Stale | Banner announced via `aria-live="polite"`: "ยอดเงินอาจไม่เป็นปัจจุบัน" then list announces normally. |
| 5 — Error | `role="alert"`: "เกิดข้อผิดพลาดในการโหลดข้อมูล กรุณาลองใหม่อีกครั้ง." |
| 6 — Single | "บัญชีของฉัน. บัญชีออมทรัพย์ของคุณ ลงท้ายด้วย 7890 ยอดเงิน 45,000 บาท อัปเดตเมื่อสักครู่." |
| 7 — Dense | "บัญชีของฉัน. แสดง 10 บัญชี." then list. |

---

## 5. Reduced-motion plan (WCAG 2.3.3)

- Honour `prefers-reduced-motion: reduce` everywhere — see interaction spec §4.
- No essential information conveyed by motion alone.
- No auto-scrolling, no carousel, no parallax in v1.

---

## 6. Colour-independence (WCAG 1.4.1)

- Information NEVER conveyed by colour alone. `StaleBadge` carries explicit Thai text "อาจไม่ใช่ยอดล่าสุด" — not just an icon or colour swatch.
- Rank order is conveyed by DOM position (top-to-bottom) and announced numerically — not solely by visual styling.
- Errors carry explicit text, not just a red icon.

---

## 7. Tested-against checklist (Phase 1 deliverables to QA)

Hand QA P1 the following test cases (mirroring AC-005):

- [ ] AC-005-H1: render 3+ accounts at 375px → no horizontal scrollbar
- [ ] AC-005-H2: VoiceOver iOS announces rank + type + last4 + spoken balance + last-updated
- [ ] AC-005-H2: TalkBack Android same template
- [ ] AC-005-H3: Tab through rows in rank order; visible focus ring
- [ ] AC-005-H3: Shift+Tab reverses correctly
- [ ] AC-005-H4: Lighthouse mobile a11y ≥ 90 on demo staging URL
- [ ] AC-005-E1: 200% browser font scale → no clipping / overlap on any screen state
- [ ] AC-005-E2: balance read as "45,000 baht" not "4-5-0-0-0"
- [ ] Reduced-motion: no shimmer / spring animations when system flag is set
- [ ] Stale banner announces via `aria-live="polite"` without stealing focus
- [ ] Error state announces via `role="alert"` and traps no focus

---

## 8. Items deferred to Phase 2 HI-FI (with rationale)

| Item | Why deferred |
|---|---|
| Exact contrast ratios per token | Tokens not yet locked — needs Designer P2 + design-system reuse decision |
| Focus-ring exact spec (offset, width, colour) | Token-dependent |
| Final icon set + alt-text inventory | Iconography chosen in HI-FI |
| Final micro-copy (Thai + English i18n key map) | Copy review in HI-FI with content owner |
| Specific colour-blindness sims (deuteranopia, protanopia) | Run against HI-FI palette |
| Sparkline / chart a11y | OUT OF SCOPE (US-BC-007 next sprint) |

---

## Cross-references

- Wireframes: `wireframes-lofi.md`
- Interaction spec: `interaction-spec-lofi.md`
- Open questions for HI-FI: `open-questions-for-hifi.md`
- BA NFR (a11y baseline): `docs/ba/balance-comparison/nfr.md`
- BA AC for a11y: `docs/ba/balance-comparison/user-stories.md` (US-BC-005)
