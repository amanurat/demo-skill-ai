# HI-FI Component Specs — Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** Phase 2 — HI-FI / Design
> **Source LO-FI inventory:** [`../wireframes-lofi.md`](../wireframes-lofi.md) §"Component inventory"
> **Token source of truth:** [`../../_shared/tokens.json`](../../_shared/tokens.json)
> **Token rationale + WCAG evidence:** [`../../_shared/tokens-rationale.md`](../../_shared/tokens-rationale.md)
> **Notation:** Tokens are referenced by path (e.g., `color.primary.700`) — NEVER inline hex.

---

## 0. Conventions

- All component names are Angular standalone components (`<balance-XXX>` selector prefix).
- Props use TypeScript-style signatures. **No `any`.** Use unions, literal types, branded types where useful.
- Token references are dotted paths into `tokens.json`. FE-dev resolves to `var(--token-path-kebab)` at consumption time.
- ARIA roles + names are MANDATORY — not optional.
- Every interactive component declares the 5 canonical states: `default`, `hover`, `focus`, `disabled`, `loading` — plus `error` where applicable.
- Copy keys live in i18n bundle `balance-comparison.*` (see [screen-specs.md §6](./screen-specs.md#6-i18n-key-map-locked) for the full key map).

---

## 1. `<balance-dashboard-page>` — top-level page container

**Role:** Page-level orchestrator. Renders header + body. Hosts route-level state machine (`loading | empty | ok | stale | error`).

### Props

```ts
@Input() state: 'loading' | 'empty' | 'ok' | 'stale' | 'error';
@Input() accounts: ReadonlyArray<AccountView>;
@Input() freshness: 'live' | 'snapshot' | 'stale';        // from API meta.freshness (resolves OPEN-D-005)
@Input() correlationId: string;                           // for error footer
@Output() refresh = new EventEmitter<void>();             // pull-to-refresh OR explicit refresh button
@Output() rowSelect = new EventEmitter<AccountView>();    // out-of-scope target

type AccountView = {
  readonly accountId: string;                              // UUID — not displayed
  readonly maskedAccountNumber: string;                    // "****XXXX" — never full
  readonly accountType: 'SAVINGS' | 'CURRENT' | 'FIXED_DEPOSIT';
  readonly balance: string;                                // BigDecimal-as-string (banking convention)
  readonly currency: 'THB';                                // v1 single-currency
  readonly balanceAsOf: string | null;                     // ISO-8601 or null (AC-002-E2)
  readonly isStale: boolean;                               // server-computed per BR-013
  readonly rank: number;                                   // server-side ranking (ADR-004)
};
```

### States

| state | Renders | Tokens used |
|---|---|---|
| `loading` | `<balance-skeleton-list>` with 3 rows | `surface.muted`, `motion.duration.instant` (under reduced-motion) |
| `empty`   | `<balance-empty-state>` | `surface.card`, `text.primary`, `text.secondary` |
| `ok`      | Header + `<balance-account-list>` | per-component below |
| `stale`   | Header + `<balance-staleness-banner>` + `<balance-account-list>` | adds `semantic.warning.tint` for banner |
| `error`   | `<balance-error-state>` | `surface.card`, `semantic.danger.*` |

### Layout

- Grid: single column. Max-width: `breakpoint.mobile` (375px) in v1; gracefully fluid above (no v1 tablet/desktop layouts).
- Page background: `color.surface.page`.
- Bottom safe-area: `spacing.7` (48px).

### Accessibility

- Wrap in `<main>` landmark with `aria-labelledby="balance-page-title"`.
- Lang attribute on `<html lang="th">` (or current locale).
- One `<h1>` per route (the PageHeader title).
- Lazy-route scroll restoration via Angular Router `scrollPositionRestoration: 'enabled'`.

### Banking notes

- The state machine is deterministic from `(state, freshness)` inputs — no local state mutation. Pure component.
- Refresh emitter wired to BOTH pull-to-refresh gesture AND the explicit `<balance-refresh-button>` in the header (per OPEN-D-008 resolution: ship both).

---

## 2. `<balance-page-header>` — title + subtitle + refresh affordance

**Role:** Page title, freshness summary subtitle, explicit refresh button (keyboard-accessible mirror of pull-to-refresh).

### Props

```ts
@Input() accountCount: number;                             // 0..N
@Input() freshestBalanceAsOf: string | null;               // ISO-8601 — OPEN-D-011 rule: FRESHEST row
@Input() loading: boolean;                                 // disables refresh button when true
@Output() refresh = new EventEmitter<void>();
```

### Anatomy

```
+---------------------------------------------+
| <h1> บัญชีของฉัน                            |   ← typography.heading_1, text.primary
| <p> อัปเดตล่าสุด · 3 บัญชี      [↻ refresh]|   ← typography.caption, text.secondary + IconButton
+---------------------------------------------+
```

### States

| state | Visual |
|---|---|
| default | Title visible; subtitle shows freshest relative time + count |
| loading | Refresh icon shows spinner; subtitle replaced by skeleton bar |
| `accountCount=0` | Subtitle hides count, shows only title (matches LO-FI Screen 3) |
| `accountCount=1` | Subtitle shows "1 บัญชี" (no "of N") |

### Layout / tokens

- Padding: `spacing.4` (16px) all sides; `spacing.3` (12px) between title and subtitle.
- Title: `typography.heading_1` (24/29 Bold) + `color.text.primary`.
- Subtitle: `typography.caption` (12/17 Regular) + `color.text.secondary`.
- Refresh icon button: 44 × 44 dp tap target, icon `size.icon_md` (20px), color `color.text.secondary` default / `color.primary.700` focus.

### Refresh button (sub-component)

```ts
selector: '<balance-refresh-button>'
aria-label: 'รีเฟรชยอดเงิน' / 'Refresh balances'   // i18n: balance-comparison.refresh.aria
type: button
states: default | hover | focus | loading (spinner replaces icon) | disabled
icon: Heroicons outline / arrow-path (resolved in OPEN-D-002 — see screen-specs §icon-system)
```

### Accessibility

- `<h1>` set programmatic focus on route entry (one-time, screen-reader users).
- Refresh button has visible focus ring (3px `color.border.focus`, offset 2px).
- `aria-busy="true"` set on button during loading; spinner is decorative (`aria-hidden="true"`).
- Subtitle copy is i18n-keyed; the relative-time formatter uses the locale's date-fns/Intl.RelativeTimeFormat.

### Banking notes

- OPEN-D-011 resolution: subtitle reflects the **FRESHEST** row's `balance_as_of`, never the stalest — header should never be more pessimistic than the rows.
- Header is NOT sticky in v1 (LO-FI commitment).

---

## 3. `<balance-account-list>` — list semantic wrapper

**Role:** Semantic list. Holds N `<balance-account-row>` children.

### Props

```ts
@Input() accounts: ReadonlyArray<AccountView>;
@Input() loading: boolean;                                 // sets aria-busy
@Output() rowSelect = new EventEmitter<AccountView>();
```

### Layout

- `role="list"` (semantic) — explicit because Angular templates can break the implicit `<ul>` role with some style resets.
- `aria-label="รายการบัญชีของคุณ เรียงจากยอดเงินมากไปน้อย"` (i18n: `balance-comparison.list.aria-label`).
- `aria-busy="true"` while `loading`.
- Vertical stack; gap `spacing.3` (12px) between rows.
- Padding: `spacing.4` (16px) horizontal on container.

### Tokens

- Container background: `color.surface.page`.
- No internal background on the list itself — rows carry their own surface.

### Accessibility

- `role="list"` required. Each child `role="listitem"`.
- `aria-label` carries Thai-first description.
- Angular `@for` with `track accountId` for stable re-render.

---

## 4. `<balance-account-row>` — single account row (CENTRAL component)

**Role:** Display one ranked account: icon, type label, masked number, balance, last-updated, optional stale badge.

### Props

```ts
@Input() account: AccountView;
@Input() totalCount: number;                               // for "Account N of M" SR template
@Output() select = new EventEmitter<AccountView>();
```

### Anatomy

```
+--------------------------------------------------------+
| [icon]  บัญชีออมทรัพย์ · ****7890          ฿128,540.25 |
|         อัปเดตเมื่อ 2 นาทีที่แล้ว                  THB |
|         [optional: <balance-staleness-badge>]          |
+--------------------------------------------------------+
   ↑                                                  ↑
   icon column                                        amount column
   width: spacing.6 (32px) + spacing.2 gap            text-align: right
```

### Layout

- **Container:** `<button>` element (NOT `<a>` — out-of-scope target is a future-feature route; using `<button>` lets us defer routing decision).
  - **Why `<button>` not `<a>`?** Per LO-FI interaction-spec §1: "Entire row is the tap target". A `<button>` is more semantically honest when the target is not yet defined. FE-dev wraps in `<a routerLink>` when account-detail route exists.
- Min-height: `size.row_min` (72px).
- Padding: `spacing.3` vertical, `spacing.4` horizontal.
- Background: `color.surface.card`.
- Border: `border_width.hairline` bottom, `color.border.subtle`.
- Border-radius: `border_radius.card` (12px) on first and last row only (rounds the list visually).
- Internal grid: `grid-template-columns: auto 1fr auto` (icon | text-stack | amount-stack).

### States

| state | Visual change |
|---|---|
| default | as above |
| hover | background → `color.neutral.50`; transition `motion.duration.fast` |
| focus | outline 3px `color.border.focus`, offset 2px (does NOT clip — sits outside border) |
| pressed (active) | background → `color.neutral.100`; transition `motion.duration.fast` |
| disabled | N/A in v1 (read-only; rows never disabled) |
| stale (account.isStale = true) | Adds `<balance-staleness-badge>` below the metadata line; no background change |

### Tokens used

| Element | Token |
|---|---|
| Container bg | `color.surface.card` |
| Border bottom | `color.border.subtle`, `border_width.hairline` |
| Icon color | `color.text.secondary` (decorative — neutral) |
| Account-type label | `color.text.primary`, `typography.body_strong` |
| Masked number | `color.text.secondary`, `typography.body` |
| Balance | `color.text.primary`, `typography.amount` (tabular-nums!) |
| Currency code | `color.text.secondary`, `typography.caption` (uppercase, letter-spacing 0.05em) |
| Last-updated | `color.text.secondary`, `typography.caption` |
| Focus ring | `color.border.focus`, `border_width.focus`, outline-offset 2px |
| Hover bg | `color.neutral.50` |

### Sub-components used

- `<balance-account-type-icon type={accountType}>` (§5)
- `<balance-masked-account-label value={maskedAccountNumber}>` (§9)
- `<balance-amount value={balance} currency={currency}>` (§10)
- `<balance-last-updated value={balanceAsOf}>` (§11)
- `<balance-staleness-badge>` (conditional, §6)

### Accessibility

- `role="listitem"` MANDATORY.
- **Accessible name template (composed):**
  ```
  "บัญชีลำดับที่ {rank} จาก {totalCount}, {accountTypeLabelTH}, ลงท้ายด้วย {last4},
   ยอดเงิน {balanceSpoken} {currencyNameTH}, อัปเดตเมื่อ {relativeTimeTH}{, อาจไม่ใช่ยอดล่าสุด}"
  ```
- Implementation: `aria-label` on the row composes from the visible parts; do NOT rely on screen-reader concatenation of inline text.
- Tap target ≥ 44 × 44 dp (entire row).
- Focus ring visible, contrast 10.36:1 against white surface.
- For `account.balanceAsOf === null`: relative time is read as "ไม่ทราบเวลาอัปเดต" (i18n: `balance-comparison.lastUpdated.unknown`).

### Banking notes

- **NEVER displays full account number.** The `maskedAccountNumber` field is the only allowed source.
- **NEVER displays customerId.** Not in DOM, not in `data-*` attrs.
- No swipe actions; no quick-action chevrons. Single tap target (per LO-FI interaction-spec §8).
- Tie-break visualization: NONE (OPEN-D-006 resolution — invisible deterministic order).
- Rank visual: NO badge (OPEN-D-007 resolution — DOM position is the rank).

---

## 5. `<balance-account-type-icon>` — decorative type icon

**Role:** Decorative icon indicating account type (SAVINGS / CURRENT / FIXED_DEPOSIT).

### Props

```ts
@Input() type: 'SAVINGS' | 'CURRENT' | 'FIXED_DEPOSIT';
@Input() size: 'sm' | 'md' | 'lg' = 'md';                  // default md = 20px
```

### Resolution of OPEN-D-002 (LOCKED)

**Icon family chosen: Heroicons outline (https://heroicons.com), v2.x, MIT license.**

Rationale:
- Banking-clean aesthetic (no flourishes, no rounded-friendly stroke).
- MIT — no attribution requirement, safe for commercial banking deploy.
- Single-color SVG — easy to recolor via `currentColor` for state.
- Wide adoption (Tailwind ecosystem) — FE-dev familiarity.

**Icon mapping (LOCKED):**

| Type | Heroicons name | Symbol meaning |
|---|---|---|
| `SAVINGS` | `banknotes` (outline) | Cash bills — universal "savings" signal |
| `CURRENT` | `credit-card` (outline) | Card — current/checking account convention |
| `FIXED_DEPOSIT` | `lock-closed` (outline) | Locked — funds committed for fixed term |

### States

| state | Visual |
|---|---|
| default | Icon stroke = `color.text.secondary` (currentColor inherited) |
| inside hovered row | Stroke = `color.text.primary` (subtle emphasis on row hover) |
| inside focused row | Stroke = `color.primary.700` (subtle emphasis on row focus) |

### Tokens used

- `size.icon_sm` (16px) / `size.icon_md` (20px) / `size.icon_lg` (24px)
- Color via `currentColor` — inherits from parent

### Accessibility

- `aria-hidden="true"` MANDATORY (the visible Thai type label carries semantic meaning per BR-008).
- No `<title>` element; no `role="img"`.
- Icon does NOT carry information not also in text.

### Banking notes

- These icons are NOT brand assets; they are functional indicators only.
- If a customer color-blindness simulation shows confusion between icons, the Thai label (`บัญชีออมทรัพย์` / `บัญชีกระแสรายวัน` / `บัญชีเงินฝากประจำ`) remains the unambiguous source.

---

## 6. `<balance-staleness-badge>` — per-row stale indicator

**Role:** Inline pill displayed under the last-updated line when `account.isStale === true`.

### Props

```ts
@Input() lastUpdatedAbsolute: string;                      // ISO-8601 — used for long-press tooltip
```

### Anatomy

```
+----------------------------------+
| [⚠]  อาจไม่ใช่ยอดล่าสุด          |   ← tap/focus → tooltip with absolute timestamp
+----------------------------------+
```

### Layout

- Display: inline-flex, gap `spacing.1` (4px).
- Padding: `spacing.1` (4px) vertical, `spacing.2` (8px) horizontal.
- Border-radius: `border_radius.badge` (999px / pill).
- Background: `color.semantic.warning.tint` (`#FEF3C7`).
- Icon: Heroicons `exclamation-triangle` outline, `size.icon_sm` (16px), color `color.semantic.warning.on_tint` (`#78350F`).
- Text: `color.semantic.warning.on_tint`, `typography.label` (14/20 Medium).

### States

| state | Visual |
|---|---|
| default | tint background, dark warning text — AAA contrast 8.05:1 |
| hover (desktop) | tint slightly darker (`color.semantic.warning.tint` -2% lightness — not a token; FE-dev uses `filter: brightness(0.97)`) |
| focus (when interactive) | adds 2px focus ring outside the pill |
| (Reduced-motion) | no fade-in; appears instantly |

### Accessibility

- The badge is **non-interactive** by default — it carries text content visible to all users.
- Color-independence honored: icon + Thai text + warning color all signal "stale" — never color alone.
- Long-press or focus + Enter reveals tooltip: "ยอดอัปเดตล่าสุดเมื่อ {absoluteISO}" (i18n: `balance-comparison.stale.tooltip`).

### Banking notes

- The badge has its own text — it does NOT inherit `aria-label` from the parent row. SR users hear it as part of the row's composed accessible name (§4 template).

---

## 7. `<balance-staleness-banner>` — top-of-list global banner

**Role:** Global banner shown when API response carries `meta.freshness === 'snapshot' || 'stale'`. Resolves OPEN-D-005.

### Props

```ts
@Input() freshness: 'snapshot' | 'stale';                  // 'live' = banner not rendered
@Output() retry = new EventEmitter<void>();
@Output() dismiss = new EventEmitter<void>();
```

### Anatomy

```
+--------------------------------------------------+
| [⚠]  ยอดเงินอาจไม่เป็นปัจจุบัน                  |
|      ระบบใช้ข้อมูลล่าสุดที่บันทึกไว้              |
|                                  [ลองอีกครั้ง] [×]|
+--------------------------------------------------+
```

### Layout

- Position: top of `<balance-account-list>`, NOT sticky (per LO-FI interaction-spec §3).
- Margin: `spacing.4` (16px) horizontal, `spacing.3` (12px) bottom.
- Padding: `spacing.3` (12px) vertical, `spacing.4` (16px) horizontal.
- Border-radius: `border_radius.lg` (12px).
- Background: `color.semantic.warning.tint`.
- Border-left: 4px solid `color.semantic.warning.base` (visual emphasis).
- Elevation: `elevation.0` (no shadow — flat is correct here).
- Grid: icon column | text-stack column | actions column.

### States

| state | Visual |
|---|---|
| `freshness=snapshot` | Banner visible; full message |
| `freshness=stale` | Banner visible; stronger message ("กรุณารีเฟรชเพื่อรับข้อมูลล่าสุด") |
| `freshness=live` | Banner NOT rendered |
| dismissed | Banner hidden for session (no DOM); reappears on next fetch if condition persists |

### Tokens used

| Element | Token |
|---|---|
| Background | `color.semantic.warning.tint` |
| Border-left | `color.semantic.warning.base`, 4px |
| Icon | `color.semantic.warning.base`, `size.icon_md` |
| Heading | `color.semantic.warning.on_tint`, `typography.body_strong` |
| Body | `color.semantic.warning.on_tint`, `typography.body` |
| Retry button (soft) | `color.semantic.warning.on_tint` outline; see `<balance-retry-soft-button>` §13 |
| Dismiss button | `color.text.secondary`, 44×44 tap target, X icon (Heroicons `x-mark`) |

### Accessibility

- `role="status"` + `aria-live="polite"` — announced once when it appears, never interrupts.
- `aria-labelledby="staleness-banner-title"`.
- Dismiss button has `aria-label="ปิดข้อความ"` (i18n: `balance-comparison.stale.banner.dismiss`).
- Retry button has visible focus ring.
- Color-independence: icon + heading + body text + warning color — never color alone.

### Banking notes

- **Snapshot/stale signal is server-authoritative.** Per SA event-flows §3.1, the API response includes `meta.freshness` (resolved in SA-001). FE does NOT compute global freshness from per-row data.
- Wording is deliberately non-alarming — "อาจไม่เป็นปัจจุบัน" (may not be current) rather than "ผิดพลาด" (error). The data is correct as-of the snapshot time; it's a freshness advisory, not an error.

---

## 8. `<balance-empty-state>` — zero in-scope accounts

**Role:** Full-page empty state when API returns `accounts: []`.

### Props

```ts
@Input() showOpenAccountCTA: boolean = false;              // OPEN-D-012: v1 placeholder = false → disabled link rendered
```

### Anatomy

```
        +---------------+
        |  [illustration]|
        +---------------+

        ยังไม่มีบัญชีที่ใช้งานอยู่

        บัญชีที่ปิดหรือไม่เคลื่อนไหว
        จะไม่แสดงในหน้านี้

        [เปิดบัญชีใหม่ — placeholder, disabled v1]
```

### Layout

- Centered column, max-width 320px on 375px viewport.
- Vertical spacing: `spacing.7` (48px) above illustration, `spacing.5` (24px) between blocks.
- Illustration size: 120 × 120 (lock as `--illustration-size: 120px` — not a token; one-off).

### Tokens

| Element | Token |
|---|---|
| Background | `color.surface.page` |
| Heading | `color.text.primary`, `typography.heading_2` |
| Body | `color.text.secondary`, `typography.body`, text-align: center |
| Disabled CTA | `color.text.disabled`, `typography.body_strong`; non-interactive `<span>` styled as a button (NOT a real `<button disabled>` — see banking notes) |

### Illustration

- Source: undecided final asset; LO-FI used placeholder. Recommended: simple line-illustration, 1-color (uses `color.primary.300` for stroke).
- `alt="ภาพประกอบไม่มีบัญชี"` (i18n: `balance-comparison.empty.illustration.alt`).

### Accessibility

- `role="status"` (informational, not urgent — per LO-FI a11y §3).
- Programmatic focus on the heading when state transitions to empty.
- The "เปิดบัญชีใหม่" CTA is rendered as a non-interactive `<span aria-disabled="true">` — a `<button disabled>` would still be tab-focusable in some screen readers and confuse users.

### Banking notes

- Empty-state copy explicitly mentions the exclusion rule ("บัญชีที่ปิดหรือไม่เคลื่อนไหว") — sets expectations per OPEN-003 backend decision.

---

## 9. `<balance-masked-account-label>` — `****XXXX` rendering primitive

**Role:** Renders a masked account number consistently. Single source of truth for the mask format.

### Props

```ts
@Input() value: string;                                    // expected: "****XXXX" already-masked from API
@Input() variant: 'inline' | 'block' = 'inline';
```

### Anatomy

```
"****7890"
```

### Tokens

- Color: `color.text.secondary`.
- Typography: `typography.body` (inline) or `typography.caption` (block).
- Font-feature: NO tabular nums — masked numbers don't need column alignment.

### States

| state | Visual |
|---|---|
| default | as above |
| variant=block | wraps to its own line; spacing.1 above |

### Accessibility

- Screen reader announces "ลงท้ายด้วย 7890" via `aria-label="ลงท้ายด้วย {last4}"` (i18n: `account.masked.endingWith` with `{last4}` placeholder).
- The visible `****` is decorative; the meaningful info is the last-4 digits.
- **NEVER** accepts an unmasked input; FE-dev MUST assert in dev mode that `value` matches `^\*+\d{4}$`.

### Banking hard rule

- This is the ONLY component permitted to render account-number-like content. Direct rendering of `accountNumber` strings in any other component is a hard violation.

---

## 10. `<balance-amount>` — formatted currency amount (CRITICAL)

**Role:** Render a balance figure with proper Thai locale formatting, tabular-nums, and a screen-reader-friendly accessible name.

### Props

```ts
@Input() value: string;                                    // BigDecimal-as-string (e.g., "128540.25")
@Input() currency: 'THB';
@Input() size: 'default' | 'small' = 'default';
```

### Anatomy

```
฿128,540.25     <-- "default" (typography.amount, 20/28 Bold, tabular)
        THB     <-- caption uppercase, color.text.secondary
```

### Tokens used

| Element | Token |
|---|---|
| Amount text | `color.text.primary`, `typography.amount` (or `typography.amount_small` for size=small) |
| Currency code | `color.text.secondary`, `typography.caption`, `letter-spacing: 0.05em`, `text-transform: uppercase` |
| Tabular nums | Built into `typography.amount` — `font-feature-settings: 'tnum' 1, 'lnum' 1` |

### Formatting rule

```ts
new Intl.NumberFormat('th-TH', {
  style: 'currency',
  currency: 'THB',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
}).format(parseFloat(value));
// → "฿128,540.25"
```

FE-dev MUST use a stable Angular pipe (`balanceAmount`) wrapping this rule so all balance displays are byte-identical.

### States

| state | Visual |
|---|---|
| default | as above |
| value=`null` | Renders `—` (em dash) — DO NOT show "0.00" (semantically different) |
| Currency in non-THB (future) | Currency code text matches ISO; locale formatting still respects user locale |

### Accessibility (CRITICAL — AC-005-E2)

- `aria-label` MUST be the full spoken form: `"128,540 บาท 25 สตางค์"` — NOT digit-by-digit, NOT "one twenty-eight comma five forty point two five".
- Formatter:
  ```
  const baht = Math.floor(parseFloat(value));
  const satang = Math.round((parseFloat(value) - baht) * 100);
  const aria = satang > 0
    ? `${baht.toLocaleString('th-TH')} บาท ${satang} สตางค์`
    : `${baht.toLocaleString('th-TH')} บาท`;
  ```
- Visible "฿" symbol is decorative; `aria-hidden="true"` on the symbol span.

### Banking notes

- **Security C-2 honored:** Balance values appear ONLY in this component. No balance values in error UI text, no balance values in skeleton placeholders, no balance values in toast messages.
- **NEVER use `float` / `number` for balance math.** The `value` prop is a string (BigDecimal serialized). Parse only for formatting; never compute over it.
- Tabular-nums is non-negotiable — required for digit-column alignment across rows.

---

## 11. `<balance-last-updated>` — relative time label

**Role:** Display "Updated X ago" derived from `balance_as_of`. Long-press / focus reveals absolute timestamp tooltip.

### Props

```ts
@Input() value: string | null;                             // ISO-8601 or null (AC-002-E2)
@Input() locale: 'th' | 'en' = 'th';
```

### Anatomy

```
"อัปเดตเมื่อ 2 นาทีที่แล้ว"     <-- typography.caption, color.text.secondary
```

### Tokens used

- Color: `color.text.secondary` (default), `color.semantic.warning.on_tint` (when parent row.isStale)
- Typography: `typography.caption`

### Relative-time thresholds (OPEN-D-004 LOCKED)

| Age | Label | i18n key |
|---|---|---|
| < 30s | "อัปเดตล่าสุด" / "Updated just now" | `balance-comparison.lastUpdated.justNow` |
| 30s → 60s | "อัปเดตเมื่อ X วินาทีที่แล้ว" | `balance-comparison.lastUpdated.secondsAgo` |
| 1m → 59m | "อัปเดตเมื่อ X นาทีที่แล้ว" | `balance-comparison.lastUpdated.minutesAgo` |
| 1h → 23h | "อัปเดตเมื่อ X ชั่วโมงที่แล้ว" | `balance-comparison.lastUpdated.hoursAgo` |
| 24h → 6d | "อัปเดตเมื่อ X วันที่แล้ว" | `balance-comparison.lastUpdated.daysAgo` |
| ≥ 7d | absolute "DD MMM YYYY HH:mm" | `balance-comparison.lastUpdated.absolute` |
| `value=null` | "ไม่ทราบเวลาอัปเดต" / em dash variant | `balance-comparison.lastUpdated.unknown` |

### States

| state | Visual |
|---|---|
| default | caption, secondary text |
| hover (desktop) | underline appears (indicates tooltip available) |
| focus | tooltip visible (absolute timestamp) |
| long-press (touch) | tooltip visible 1.5s |
| value=null | em dash; tooltip not triggered |

### Accessibility

- `aria-describedby` points to the tooltip container.
- Tooltip dismissable with Esc; persistent while focused.
- Absolute timestamp format: "DD MMM YYYY HH:mm ICT" (i18n locale-aware).

### Banking notes

- 60-second staleness threshold (BR-013) is INDEPENDENT of these labels — a row can read "1 minute ago" AND show the `<balance-staleness-badge>` (the badge condition uses `account.isStale` from server, not parsed from this string).
- Display rule per LO-FI: derive from `balance_as_of`, NEVER `updated_at` (OPEN-002 locked).

---

## 12. `<balance-skeleton-list>` + `<balance-skeleton-row>` — loading state

**Role:** Render shape-matched placeholders during initial load.

### Props

```ts
// list
@Input() rowCount: number = 3;                             // fixed default; do NOT echo cached count (PII timing leak)

// row — no props; pure visual
```

### Anatomy

```
+----------------------------------------+
| ████████                                | ← top placeholder bar (account label width)
| ██████████████                          | ← bottom placeholder bar (number + balance width)
| ██████                                  | ← last-updated bar
+----------------------------------------+
```

### Layout

- Same dimensions as `<balance-account-row>` (avoids CLS).
- Min-height `size.row_min` (72px).
- 3 placeholder bars stacked with `spacing.2` (8px) gap.

### Tokens used

| Element | Token |
|---|---|
| Bar background | `color.neutral.100` |
| Container background | `color.surface.card` |
| Border-bottom | `color.border.subtle` |
| (Reduced-motion) | NO shimmer — bars stay static (already baked in LO-FI decision per OPEN-D-009) |

### Animation (when motion is allowed)

- v1: **NO shimmer.** Static placeholder only (LO-FI interaction-spec §4 + OPEN-D-009 resolution). Simpler, more accessible, less GPU.
- The `<balance-account-list>` carries `aria-busy="true"` — SR announces loading once.

### Accessibility

- Each skeleton row: `aria-hidden="true"` (don't pollute SR output).
- The list region carries the busy state.

---

## 13. `<balance-retry-soft-button>` — inline secondary button (used in StalenessBanner)

**Role:** Secondary CTA in banners. NOT a primary action — outlined or text-button style.

### Props

```ts
@Input() label: string;                                    // i18n-keyed at consumer
@Input() loading: boolean = false;
@Output() click = new EventEmitter<void>();
```

### Tokens / states

| state | Background | Border | Text |
|---|---|---|---|
| default | transparent | 1px `color.semantic.warning.on_tint` | `color.semantic.warning.on_tint`, `typography.body_strong` |
| hover | `rgba(120,53,15,0.05)` | same | same |
| focus | + 3px focus ring (offset 2px) | same | same |
| loading | spinner replaces label-start | same | `aria-busy="true"` |
| disabled | n/a in v1 |

- Padding: `spacing.2` `spacing.3` (8px / 12px).
- Border-radius: `border_radius.button` (8px).
- Min-height: `size.tap_min` (44px).

### Accessibility

- `<button type="button">`.
- Visible focus ring (border.focus color, 3px width).
- `aria-busy="true"` while loading; spinner is `aria-hidden`.

---

## 14. `<balance-primary-button>` — primary CTA (used in ErrorState)

**Role:** Primary action button (e.g., "ลองใหม่" on error screen).

### Props

```ts
@Input() label: string;
@Input() loading: boolean = false;
@Input() disabled: boolean = false;
@Output() click = new EventEmitter<void>();
```

### Tokens / states

| state | Background | Text |
|---|---|---|
| default | `color.primary.700` | `color.text.inverse` (AAA 10.36:1) |
| hover | `color.primary.500` | `color.text.inverse` |
| focus | `color.primary.700` + 3px focus ring (offset 2px) | `color.text.inverse` |
| pressed | `color.primary.800` | `color.text.inverse` |
| loading | `color.primary.700` + spinner | `color.text.inverse`, `aria-busy="true"` |
| disabled | `color.primary.200` | `color.text.disabled` (intentional below-AA — `aria-disabled="true"` carries semantic load) |

- Min-height: `size.tap_min` (44px).
- Padding: `spacing.3` `spacing.4` (12px / 16px).
- Border-radius: `border_radius.button`.
- Typography: `typography.body_strong`.

### Banking notes

- **Double-submit prevention:** When `loading=true`, click handler is suppressed. After `loading` transitions back to `false`, button is enabled again — but consumer (parent) MUST debounce the next click 2 seconds (banking hard rule).

---

## 15. `<balance-error-state>` — hard error screen

**Role:** Full-page error when API exhausted retries (HTTP 503, network down, 5xx unexpected).

### Props

```ts
@Input() correlationId: string;                            // OTel trace ID
@Output() retry = new EventEmitter<void>();
```

### Anatomy

```
        [error-illustration]

        เกิดข้อผิดพลาดในการโหลดข้อมูล

        กรุณาลองใหม่อีกครั้ง
        หรือกลับมาภายในไม่กี่นาที

        [ ลองใหม่ ]   ← <balance-primary-button>

        รหัสอ้างอิง: 7f4e2b...
        (สำหรับติดต่อทีมงาน)
```

### Layout

- Centered column, max-width 320px.
- Vertical spacing: `spacing.7` above illustration, `spacing.5` between blocks.
- correlationId rendered with `typography.caption` + `font-family: monospace` (system mono is acceptable — no token needed).

### Tokens

| Element | Token |
|---|---|
| Background | `color.surface.page` |
| Heading | `color.text.primary`, `typography.heading_2` |
| Body | `color.text.secondary`, `typography.body` |
| correlationId label | `color.text.tertiary`, `typography.caption` |
| Button | see `<balance-primary-button>` §14 |

### Error copy rules (Security C-2)

- **NEVER** mention upstream service names ("AccountClient", "Redis", "503").
- **NEVER** include raw balance values in error copy.
- **NEVER** include account numbers (masked or otherwise) in error copy.
- Generic Thai message only: "ไม่สามารถโหลดข้อมูลได้".
- correlationId is OTel trace ID — does NOT contain PII.

### Accessibility

- `role="alert"` + `aria-live="assertive"` — interrupts politely to inform the customer the page has changed.
- Programmatic focus moves to the heading on entry.
- Retry button has visible focus ring.

### Banking notes

- 401 does NOT land here (auth interceptor redirects to login).
- 403 IDOR attempt DOES fall through to this generic screen — never mention that another customer's data was attempted (Security finding 5.3 honored at UI level).

---

## 16. Component dependency graph

```
<balance-dashboard-page>
├── <balance-page-header>
│   └── <balance-refresh-button>          (icon button)
├── <balance-staleness-banner>            (conditional)
│   └── <balance-retry-soft-button>
├── <balance-account-list>
│   └── <balance-account-row>             (×N)
│       ├── <balance-account-type-icon>
│       ├── <balance-masked-account-label>
│       ├── <balance-amount>
│       ├── <balance-last-updated>
│       └── <balance-staleness-badge>     (conditional)
├── <balance-empty-state>                 (state=empty)
├── <balance-error-state>                 (state=error)
│   └── <balance-primary-button>
└── <balance-skeleton-list>               (state=loading)
    └── <balance-skeleton-row>            (×3)
```

Total: **14 component specs** (15 if counting `<balance-refresh-button>` as a separate sub-component — listed in §2).

---

## 17. Cross-references

- Token source of truth: [`../../_shared/tokens.json`](../../_shared/tokens.json)
- Token rationale + contrast evidence: [`../../_shared/tokens-rationale.md`](../../_shared/tokens-rationale.md)
- Screen specs: [`./screen-specs.md`](./screen-specs.md)
- Accessibility final: [`./accessibility-final.md`](./accessibility-final.md)
- LO-FI wireframes: [`../wireframes-lofi.md`](../wireframes-lofi.md)
- LO-FI interaction spec: [`../interaction-spec-lofi.md`](../interaction-spec-lofi.md)
- LO-FI a11y notes: [`../accessibility-notes.md`](../accessibility-notes.md)
- BA AC: [`../../../ba/balance-comparison/user-stories.md`](../../../ba/balance-comparison/user-stories.md)
- SA event-flows (meta.freshness): [`../../../sa/balance-comparison/event-flows.md`](../../../sa/balance-comparison/event-flows.md)
- Security C-2: [`../../../security/balance-comparison/early-review-consent-coverage.md`](../../../security/balance-comparison/early-review-consent-coverage.md)
