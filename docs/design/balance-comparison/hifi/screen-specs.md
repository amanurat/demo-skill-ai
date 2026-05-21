# HI-FI Screen Specs — Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** Phase 2 — HI-FI / Design
> **Viewport:** 375 × 812 (mobile-first; LO-FI commitment locked)
> **Token source:** [`../../_shared/tokens.json`](../../_shared/tokens.json) — refer by token path, never inline hex
> **Component refs:** [`./component-specs.md`](./component-specs.md)
> **LO-FI source:** [`../wireframes-lofi.md`](../wireframes-lofi.md) (7 screen states)

---

## 0. Common layout primitives

| Concern | Token / value |
|---|---|
| Page background | `color.surface.page` (`#F8FAFC`) |
| Page max-width | `breakpoint.mobile` (375px); v1 mobile-only |
| Page horizontal padding | `spacing.4` (16px) |
| Vertical rhythm | base unit `spacing.2` (8px); section gap `spacing.5` (24px) |
| Safe-area top | iOS notch — respect `env(safe-area-inset-top)` |
| Safe-area bottom | `spacing.7` (48px) + `env(safe-area-inset-bottom)` |
| Lang attribute | `<html lang="th">` (or current locale via i18n) |

### Resolved icon family (OPEN-D-002 LOCKED)

**Heroicons outline v2.x, MIT license.** See [`./component-specs.md` §5](./component-specs.md#5-balance-account-type-icon--decorative-type-icon) for the per-type icon map.

### Resolved freshness signal (OPEN-D-005 LOCKED)

Server returns `meta.freshness: 'live' | 'snapshot' | 'stale'` in the JSON payload (per SA event-flows §3.1). UI consumption rule:

| `meta.freshness` | Global banner | Per-row badge driver |
|---|---|---|
| `live` | NOT shown | independent — per `account.isStale` |
| `snapshot` | `<balance-staleness-banner>` shown, copy = banner.snapshot.* | independent — per `account.isStale` |
| `stale` | `<balance-staleness-banner>` shown, copy = banner.stale.* (stronger urgency) | independent — per `account.isStale` |

Per-row `account.isStale` (server-computed via `now() - balanceAsOf > 60s` per BR-013) is INDEPENDENT of `meta.freshness` — a single row can be stale while the response as a whole is "live".

---

## 1. Screen — `balance-dashboard-default` (3-5 accounts, all live, none stale)

> **State:** `state="ok"`, `meta.freshness="live"`, all rows `isStale=false`
> **AC anchors:** AC-001-H1/H2/H4, AC-002-H1/H2/H4, AC-005-H1

### Layout grid (375px)

```
[safe-area-top]
[PageHeader]                            padding: spacing.4
[AccountList]                           padding: spacing.4
  [AccountRow × N]                      gap: spacing.3 between rows
[safe-area-bottom]
```

### Visual spec per region

| Region | Tokens |
|---|---|
| Page bg | `color.surface.page` |
| Header — h1 "บัญชีของฉัน" | `typography.heading_1`, `color.text.primary` |
| Header — subtitle "อัปเดตล่าสุด · 3 บัญชี" | `typography.caption`, `color.text.secondary` |
| Header — refresh icon button | `size.tap_min`, icon `size.icon_md`, color `color.text.secondary` → `color.primary.700` on focus |
| AccountRow bg | `color.surface.card` |
| AccountRow border-bottom | `border_width.hairline`, `color.border.subtle` |
| AccountRow min-height | `size.row_min` (72px) |
| Type icon | `color.text.secondary`, `size.icon_md` |
| Type label "บัญชีออมทรัพย์" | `typography.body_strong`, `color.text.primary` |
| Masked number "****7890" | `typography.body`, `color.text.secondary` |
| Balance "฿128,540.25" | `typography.amount` (tabular-nums!), `color.text.primary`, text-align: right |
| Currency code "THB" | `typography.caption` uppercase, `color.text.secondary` |
| Last-updated "อัปเดตเมื่อ 2 นาทีที่แล้ว" | `typography.caption`, `color.text.secondary` |
| Row hover | bg → `color.neutral.50`, transition `motion.duration.fast` standard easing |
| Row focus | outline 3px `color.border.focus`, offset 2px |

### Interaction states

| Trigger | Result |
|---|---|
| Tap row | `rowSelect` event (target route out-of-scope; for now no-op + console log in dev) |
| Long-press on last-updated | tooltip with absolute time |
| Pull from top (≥ 80px drag) | refresh emit |
| Refresh button tap | refresh emit |
| Keyboard Tab | focus moves header → refresh-button → first row → row 2 → row 3 |

### Accessibility on this screen

- `<h1>` receives programmatic focus on route enter (once).
- AccountList carries `aria-label="รายการบัญชีของคุณ เรียงจากยอดเงินมากไปน้อย"`.
- Each row's composed `aria-label` is the SR template (see [`./accessibility-final.md` §SR templates](./accessibility-final.md)).
- Lighthouse target: a11y ≥ 90.

---

## 2. Screen — `balance-dashboard-loading` (skeleton)

> **State:** `state="loading"`
> **AC anchors:** NFR §1 (perf), AC-005-H1

### Layout

Same shell as default; AccountList replaced by `<balance-skeleton-list>` with 3 rows.

### Visual spec

| Region | Tokens |
|---|---|
| Skeleton row container | `color.surface.card` |
| Skeleton bars (×3 per row) | `color.neutral.100` |
| Skeleton bar heights | top 16px (label), middle 24px (number/balance), bottom 14px (last-updated) |
| Bar widths | top 40%, middle 70%, bottom 30% (visual rhythm — not tokens) |
| Subtitle skeleton (in header) | `color.neutral.100` bar, 14px tall, 50% width |
| Animation | NONE in v1 (LO-FI commitment + OPEN-D-009) — static placeholder only |

### A11y

- `<balance-account-list>` carries `aria-busy="true"`.
- Skeleton rows individually `aria-hidden="true"`.
- SR announces "บัญชีของฉัน กำลังโหลดข้อมูล" via the busy region.
- If load > 800ms, KEEP skeleton (do not flicker to error).

---

## 3. Screen — `balance-dashboard-empty` (0 accounts)

> **State:** `state="empty"`
> **AC anchors:** AC-001-E1

### Layout

Centered column, max-width 320px on the 375px viewport.

```
        [illustration 120×120]      margin-top: spacing.7
                                    margin-bottom: spacing.5
        ยังไม่มีบัญชีที่ใช้งานอยู่     typography.heading_2
                                    margin-bottom: spacing.4
        บัญชีที่ปิดหรือไม่เคลื่อนไหว   typography.body
        จะไม่แสดงในหน้านี้           color.text.secondary
                                    text-align: center
        [เปิดบัญชีใหม่]              ← disabled placeholder (OPEN-D-012)
```

### Visual spec

| Region | Tokens |
|---|---|
| Background | `color.surface.page` |
| Illustration stroke | `color.primary.300` (subtle, non-alarming) |
| Heading | `typography.heading_2`, `color.text.primary` |
| Subtitle | `typography.body`, `color.text.secondary`, text-align: center |
| Disabled CTA | `typography.body_strong`, `color.text.disabled`; rendered as `<span aria-disabled="true">` |

### A11y

- `role="status"` on the EmptyState region (informational, not urgent).
- Heading receives programmatic focus on state transition.
- Illustration `alt="ภาพประกอบไม่มีบัญชี"` (i18n key `balance-comparison.empty.illustration.alt`).

### Banking note

- Subtitle copy EXPLICITLY references OPEN-003 exclusion rule — sets expectation that dormant/closed don't appear here.

---

## 4. Screen — `balance-dashboard-stale` (snapshot served + 1 per-row stale)

> **State:** `state="stale"` (or `state="ok"` with `meta.freshness="snapshot"`)
> **AC anchors:** AC-002-E1, AC-003-E1

### Layout

Same as default, with `<balance-staleness-banner>` inserted above the list, AND `<balance-staleness-badge>` rendered on the row(s) where `account.isStale=true`.

```
[PageHeader]
[StalenessBanner]                    padding: spacing.3 vert / spacing.4 horiz
                                     margin: spacing.4 horiz, spacing.3 bottom
[AccountList]
  [AccountRow rank=1]                live row
  [AccountRow rank=2]  + badge       isStale=true
  [AccountRow rank=3]                live row
```

### Visual spec — banner

| Element | Token |
|---|---|
| Banner bg | `color.semantic.warning.tint` (`#FEF3C7`) |
| Border-left (4px) | `color.semantic.warning.base` (`#D97706`) |
| Border-radius | `border_radius.lg` (12px) |
| Icon (exclamation-triangle outline) | `color.semantic.warning.base`, `size.icon_md` |
| Heading copy | `typography.body_strong`, `color.semantic.warning.on_tint` (`#78350F`) — AAA 8.05:1 |
| Body copy | `typography.body`, `color.semantic.warning.on_tint` |
| Retry soft button | see component spec §13 — text/border `color.semantic.warning.on_tint` |
| Dismiss button (×) | icon `color.text.secondary`, 44×44 tap target |

### Visual spec — per-row staleness badge

| Element | Token |
|---|---|
| Pill bg | `color.semantic.warning.tint` |
| Pill text + icon | `color.semantic.warning.on_tint` |
| Border-radius | `border_radius.badge` (pill, 999px) |
| Padding | `spacing.1` vert / `spacing.2` horiz |
| Typography | `typography.label` (14/20 Medium) |
| Position | new line below last-updated; gap `spacing.1` |

### A11y

- Banner `role="status"` + `aria-live="polite"` — announced once on appearance, doesn't interrupt.
- Banner dismiss has `aria-label="ปิดข้อความ"`.
- Per-row badge is non-interactive text — visible part of the row's composed `aria-label`.

### Banking notes

- Banner is dismissible per session (closes for the session); reappears on next fetch if `meta.freshness` still indicates snapshot/stale.
- Banner copy is non-alarming ("อาจไม่เป็นปัจจุบัน") — the data is correct as-of snapshot, not erroneous.

---

## 5. Screen — `balance-dashboard-error` (hard error / retry)

> **State:** `state="error"`
> **AC anchors:** AC-001 fall-back, NFR §2 resilience

### Layout

```
[safe-area-top]
                                    (no PageHeader on error — keeps focus on resolution)
        [error-illustration]        margin-top: spacing.7
                                    size 120×120
                                    margin-bottom: spacing.5
        เกิดข้อผิดพลาดในการโหลดข้อมูล  typography.heading_2
                                    text-align: center
                                    margin-bottom: spacing.4
        กรุณาลองใหม่อีกครั้ง           typography.body
        หรือกลับมาภายในไม่กี่นาที     color.text.secondary
                                    margin-bottom: spacing.5
        [ ลองใหม่ ]                  ← <balance-primary-button>
                                    margin-bottom: spacing.5
        รหัสอ้างอิง: 7f4e2b...      typography.caption
        (สำหรับติดต่อทีมงาน)         color.text.tertiary
                                    font-family: monospace
```

### Visual spec

| Element | Token |
|---|---|
| Background | `color.surface.page` |
| Illustration stroke | `color.semantic.danger.base` (subtle — line illustration only) |
| Heading | `typography.heading_2`, `color.text.primary` |
| Body | `typography.body`, `color.text.secondary` |
| Primary button | bg `color.primary.700`, text `color.text.inverse` — 10.36:1 AAA |
| correlationId | `typography.caption`, `color.text.tertiary`, system monospace |

### Error copy rules (Security C-2 honored)

- Heading: "เกิดข้อผิดพลาดในการโหลดข้อมูล" (NEVER include balance values or account numbers).
- Body: "กรุณาลองใหม่อีกครั้ง หรือกลับมาภายในไม่กี่นาที" (generic).
- correlationId: "รหัสอ้างอิง: {trace}" — OTel trace ID only, NO PII.

### A11y

- `role="alert"` + `aria-live="assertive"` — interrupts politely.
- Heading receives programmatic focus.
- Retry button has visible focus ring + `aria-busy` while retrying.
- 401 does NOT route here (auth interceptor); 403 IDOR DOES route here but copy NEVER reveals cross-customer attempt (Security finding 5.3 honored).

---

## 6. Screen — `balance-dashboard-single` (1 account)

> **State:** `state="ok"`, accounts.length === 1
> **AC anchors:** Edge of AC-001-H1 (N=1)

### Layout

Same as default but:
- Header subtitle: "1 บัญชี" (NOT "1 of 1").
- AccountList contains a single row; visual rank chrome is NOT shown (OPEN-D-007 — no rank badge).
- SR template for the single row uses softer phrasing: drops "ลำดับที่ 1 จาก 1" prefix.

### Visual spec

Identical to §1 except single row.

### A11y

- AccountList `aria-label="บัญชีของคุณ"` (singular).
- Single row's `aria-label` template: "{accountTypeLabelTH} ลงท้ายด้วย {last4}, ยอดเงิน {balanceSpoken} บาท, อัปเดตเมื่อ {relativeTimeTH}" (NO "ลำดับที่ N จาก M" prefix).

---

## 7. Screen — `balance-dashboard-dense` (10 accounts)

> **State:** `state="ok"`, accounts.length === 10
> **AC anchors:** AC-005-H1 (no horizontal scroll), perf

### Layout

Same row template as default — NO compression, NO truncation. Rows simply repeat 10 times. Scroll boundary is at viewport bottom; rows below the fold are revealed via scroll.

### Performance considerations

- Angular `@for` with `track accountId`.
- No virtualization in v1 (N ≤ 10 is small enough; virtualization adds a11y complexity).
- LCP target: < 2.5s (BA NFR §1).

### Layout-specific check

- All 10 rows verified at 375px → no horizontal scroll (AC-005-H1).
- 200% font scale verified → rows reflow gracefully (AC-005-E1).
- Tested with mixed `isStale` flags (per LO-FI annotation): row #7 in the example carries a stale badge.

### A11y

- AccountList `aria-label="รายการบัญชีของคุณ 10 รายการ เรียงจากยอดเงินมากไปน้อย"`.
- Header subtitle: "อัปเดตล่าสุด · 10 บัญชี" (uses i18n with plurals via Intl.PluralRules).

---

## 8. i18n key map (LOCKED — resolves OPEN-D-003)

Namespace: `balance-comparison.*` (with shared `account.*` keys carried over from money-transfer if present).

### v1 minimum keys (12 keys)

| Key | Thai (primary) | English (fallback) |
|---|---|---|
| `balance-comparison.page.title` | บัญชีของฉัน | My Accounts |
| `balance-comparison.list.aria-label` | รายการบัญชีของคุณ เรียงจากยอดเงินมากไปน้อย | Your accounts, ranked highest to lowest balance |
| `balance-comparison.list.aria-label.single` | บัญชีของคุณ | Your account |
| `balance-comparison.list.aria-label.count` | รายการบัญชีของคุณ {count} รายการ เรียงจากยอดเงินมากไปน้อย | Your {count} accounts, ranked highest to lowest balance |
| `balance-comparison.lastUpdated.justNow` | อัปเดตล่าสุด | Updated just now |
| `balance-comparison.lastUpdated.secondsAgo` | อัปเดตเมื่อ {n} วินาทีที่แล้ว | Updated {n} seconds ago |
| `balance-comparison.lastUpdated.minutesAgo` | อัปเดตเมื่อ {n} นาทีที่แล้ว | Updated {n} minutes ago |
| `balance-comparison.lastUpdated.hoursAgo` | อัปเดตเมื่อ {n} ชั่วโมงที่แล้ว | Updated {n} hours ago |
| `balance-comparison.lastUpdated.daysAgo` | อัปเดตเมื่อ {n} วันที่แล้ว | Updated {n} days ago |
| `balance-comparison.lastUpdated.absolute` | อัปเดตเมื่อ {DD} {MMM} {YYYY} {HH}:{mm} น. | Updated {DD} {MMM} {YYYY} {HH}:{mm} |
| `balance-comparison.lastUpdated.unknown` | ไม่ทราบเวลาอัปเดต | Last update unknown |
| `balance-comparison.lastUpdated.tooltip.aria` | กดค้างเพื่อดูเวลาอัปเดตแบบเต็ม | Long-press for full timestamp |

### Stale state (3 keys)

| Key | Thai (primary) | English |
|---|---|---|
| `balance-comparison.stale.badge.label` | อาจไม่ใช่ยอดล่าสุด | May not be the latest balance |
| `balance-comparison.stale.banner.snapshot.title` | ยอดเงินอาจไม่เป็นปัจจุบัน | Balances may not be current |
| `balance-comparison.stale.banner.snapshot.body` | ระบบใช้ข้อมูลล่าสุดที่บันทึกไว้ ลองรีเฟรชอีกครั้งในอีกสักครู่ | We're showing the last saved data. Try refreshing in a moment. |
| `balance-comparison.stale.banner.stale.title` | ข้อมูลอาจค้างนาน | Balances may be outdated |
| `balance-comparison.stale.banner.stale.body` | กรุณารีเฟรชเพื่อรับข้อมูลล่าสุด | Please refresh to get the latest data. |
| `balance-comparison.stale.banner.retry` | ลองอีกครั้ง | Try again |
| `balance-comparison.stale.banner.dismiss` | ปิดข้อความ | Dismiss |
| `balance-comparison.stale.tooltip.aria` | ยอดอัปเดตล่าสุดเมื่อ {absoluteISO} | Last balance update {absoluteISO} |

### Empty state (3 keys)

| Key | Thai | English |
|---|---|---|
| `balance-comparison.empty.title` | ยังไม่มีบัญชีที่ใช้งานอยู่ | No active accounts to display |
| `balance-comparison.empty.subtitle` | บัญชีที่ปิดหรือไม่เคลื่อนไหวจะไม่แสดงในหน้านี้ | Closed or dormant accounts are not shown here. |
| `balance-comparison.empty.cta.disabled` | เปิดบัญชีใหม่ | Open a new account |
| `balance-comparison.empty.illustration.alt` | ภาพประกอบไม่มีบัญชี | No-accounts illustration |

### Error state (4 keys)

| Key | Thai | English |
|---|---|---|
| `balance-comparison.error.title` | เกิดข้อผิดพลาดในการโหลดข้อมูล | Could not load your accounts |
| `balance-comparison.error.body` | กรุณาลองใหม่อีกครั้ง หรือกลับมาภายในไม่กี่นาที | Please try again, or come back in a few minutes. |
| `balance-comparison.error.cta.retry` | ลองใหม่ | Try again |
| `balance-comparison.error.correlationLabel` | รหัสอ้างอิง: {trace} (สำหรับติดต่อทีมงาน) | Reference: {trace} (for support contact) |
| `balance-comparison.error.illustration.alt` | ภาพประกอบเกิดข้อผิดพลาด | Error illustration |

### Refresh affordance (3 keys)

| Key | Thai | English |
|---|---|---|
| `balance-comparison.refresh.aria` | รีเฟรชยอดเงิน | Refresh balances |
| `balance-comparison.refresh.pullHint` | ดึงลงเพื่อรีเฟรช | Pull to refresh |
| `balance-comparison.refresh.loading` | กำลังโหลด… | Loading… |

### Reused from money-transfer (`account.*` namespace — 4 keys)

These should already exist; if not, FE-dev creates them in the same bundle.

| Key | Thai | English |
|---|---|---|
| `account.type.savings` | บัญชีออมทรัพย์ | Savings Account |
| `account.type.current` | บัญชีกระแสรายวัน | Current Account |
| `account.type.fixedDeposit` | บัญชีเงินฝากประจำ | Fixed Deposit Account |
| `account.masked.endingWith` | ลงท้ายด้วย {last4} | ending {last4} |

**Total v1 keys: ~32 (12 + 8 + 4 + 5 + 3 + 4 carried-over).**

---

## 9. Backend / freshness signal — final UI consumption contract

Per SA event-flows §3.1 and architecture.md §7, the API response carries:

```json
{
  "accounts": [ { "rank":1, "accountId":"...", "maskedAccountNumber":"****7890",
                  "accountType":"SAVINGS", "balance":"128540.25", "currency":"THB",
                  "balanceAsOf":"2026-05-21T08:00:00Z", "isStale": false } ],
  "meta": {
    "freshness": "live",                    // "live" | "snapshot" | "stale"
    "cacheHit": true,
    "correlationId": "..."
  }
}
```

### UI consumption rules (LOCKED)

1. `meta.freshness` drives the global `<balance-staleness-banner>` visibility (rendered when value is `snapshot` or `stale`).
2. `account.isStale` drives the per-row `<balance-staleness-badge>` (independent of `meta.freshness`).
3. `meta.cacheHit` is OBSERVABILITY-ONLY — never displayed to the customer.
4. `meta.correlationId` is shown ONLY in `<balance-error-state>` for support contact.
5. `account.rank` is the server's authoritative ranking — FE does NOT re-sort (ADR-004).

---

## 10. Banking-conservative visual decisions (locked, do-not-revisit)

| Decision | Rationale |
|---|---|
| NO gradients on cards | Banking should feel solid, not iridescent |
| NO neon / saturated accent colors beyond brand | Conservative trust signal |
| NO skeuomorphic shadows | `elevation.0`–`elevation.3` only; cards mostly flat |
| NO playful illustrations (smiley face, balloons) | Empty/error illustrations are line-only, single color |
| NO progress bars on per-account "goal" | Goal-tracking is out of scope |
| NO color-only stale state | Always icon + Thai text + warning color |
| NO rank number visible | DOM position + SR announcement carry rank (OPEN-D-007) |
| NO tie-break visualization | Invisible deterministic ordering (OPEN-D-006) |
| Currency code ALWAYS shown | Forward-compat for multi-currency (OPEN-D-010) |
| NO sticky header in v1 | LO-FI interaction §3 — defer to v1.1 if UX testing flags |
| Pull-to-refresh AND refresh button | OPEN-D-008 — ship both for a11y |
| Mobile-first 375px ONLY | v1 scope; tablet/desktop deferred (OPEN-D-013) |

---

## 11. Cross-references

- Component specs: [`./component-specs.md`](./component-specs.md)
- Accessibility final: [`./accessibility-final.md`](./accessibility-final.md)
- Token source: [`../../_shared/tokens.json`](../../_shared/tokens.json)
- Token rationale: [`../../_shared/tokens-rationale.md`](../../_shared/tokens-rationale.md)
- LO-FI wireframes: [`../wireframes-lofi.md`](../wireframes-lofi.md)
- LO-FI a11y: [`../accessibility-notes.md`](../accessibility-notes.md)
- LO-FI interaction: [`../interaction-spec-lofi.md`](../interaction-spec-lofi.md)
- SA event-flows: [`../../../sa/balance-comparison/event-flows.md`](../../../sa/balance-comparison/event-flows.md)
- SA architecture: [`../../../sa/balance-comparison/architecture.md`](../../../sa/balance-comparison/architecture.md)
- BA user stories: [`../../../ba/balance-comparison/user-stories.md`](../../../ba/balance-comparison/user-stories.md)
- BA NFR: [`../../../ba/balance-comparison/nfr.md`](../../../ba/balance-comparison/nfr.md)
- Security early review (C-2): [`../../../security/balance-comparison/early-review-consent-coverage.md`](../../../security/balance-comparison/early-review-consent-coverage.md)
