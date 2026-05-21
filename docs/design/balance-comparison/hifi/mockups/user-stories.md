# User Stories — Visual Mockup Sub-Feature (HI-FI Renders)

> **Sub-feature slug:** `balance-comparison-ui-mockup`
> **Parent feature:** `balance-comparison`
> **Owner:** `banking-designer` (Phase 2 follow-up proposal)
> **Status:** PROPOSED — pending PM scope-change approval before implementation
> **Purpose:** ผลิต visual mockup ที่ render เป็น image จริง (PNG/HTML preview) ก่อน FE implementation เต็มรูปแบบ เพื่อให้ stakeholder เห็นภาพชัดเจน
> **Reference design system:** [`docs/design/_shared/tokens.json`](../../../_shared/tokens.json) + [`tokens-rationale.md`](../../../_shared/tokens-rationale.md)
> **Reference HI-FI specs:** [`docs/design/balance-comparison/hifi/screen-specs.md`](../screen-specs.md) + [`component-specs.md`](../component-specs.md)

---

## Scope Note (Important)

This is **a NEW sub-feature outside the PM-001 sprint backlog** for `balance-comparison`. The original 5 stories (US-BC-001..005) cover the production Angular implementation — these MOCK stories cover an interim visual-validation deliverable.

**Decision required from PM before implementation:**
1. Approve as in-sprint addition (will compress timeline for US-BC-001..003)
2. Approve as sprint-extension (push demo date)
3. Reject — proceed straight to FE Angular implementation (skip mockup interstitial)
4. Defer mockup to a 1-day pre-implementation spike

---

## Story Summary

| Story ID | Title | Priority | Effort | Output |
|---|---|---|---|---|
| US-MOCK-001 | Render default dashboard (3 accounts) | MUST | S | `01-default.html` + PNG |
| US-MOCK-002 | Render stale data state | MUST | S | `02-stale.html` + PNG |
| US-MOCK-003 | Render loading skeleton | MUST | S | `03-loading.html` + PNG |
| US-MOCK-004 | Render empty state | MUST | S | `04-empty.html` + PNG |
| US-MOCK-005 | Render hard error | MUST | S | `05-error.html` + PNG |
| US-MOCK-006 | Render single-account edge | SHOULD | XS | `06-single.html` + PNG |
| US-MOCK-007 | Render 10-account dense | SHOULD | XS | `07-dense.html` + PNG |
| US-MOCK-008 | Token reference sheet | SHOULD | S | `08-token-reference.html` + PNG |

**Total: 8 stories — 5 MUST + 3 SHOULD**

---

## US-MOCK-001 — Render Default Dashboard Screen (3 accounts, all live)

**As a** stakeholder reviewing the HI-FI design
**I want** to see a rendered image of the default balance dashboard with 3 active accounts
**So that** I can validate the visual hierarchy, color usage, and typography before frontend implementation begins

- **Priority:** MUST
- **Effort:** S (1 mockup file)
- **Reference:** [`screen-specs.md` §1](../screen-specs.md)

### Acceptance Criteria

**AC-MOCK-001-H1 — All tokens applied correctly**
- Given the mockup renders the default state with 3 accounts
- When I inspect each element
- Then every color value MUST come from `tokens.json`:
  - Page background = `color.surface.page` (`#F8FAFC`)
  - Card surface = `color.surface.card` (`#FFFFFF`)
  - Primary text = `color.text.primary` (`#0F172A`)
  - Secondary text = `color.text.secondary` (`#475569`)
  - Balance amount = `color.text.primary` with `typography.amount` (20/28 Bold, tabular-nums)
- And NO inline hex outside the documented token values is present in the markup

**AC-MOCK-001-H2 — Typography hierarchy visible**
- `<h1>` "บัญชีของฉัน" uses `typography.heading_1` (24/29 Bold Inter)
- Balance figures use `typography.amount` with `font-variant-numeric: tabular-nums`
- Captions use `typography.caption` (12/17 Regular)
- Currency code "THB" appears uppercase with `letter-spacing: 0.05em`

**AC-MOCK-001-H3 — Spacing follows 8-pixel scale**
- All gaps use `spacing.*` tokens (no arbitrary px like 7px, 13px)
- Row internal padding = `spacing.3` / `spacing.4` (12px / 16px)
- Gap between rows = `spacing.3` (12px)
- Page horizontal padding = `spacing.4` (16px)

**AC-MOCK-001-H4 — Mobile viewport 375px**
- Canvas exactly 375 × 812 px (iPhone SE / Thai mobile baseline)
- No horizontal scroll
- Balance column right-aligns within viewport

**AC-MOCK-001-H5 — Sample data follows banking conventions**
- Masked numbers as `****7890` / `****4421` / `****0033` (last 4 only)
- Balances format as Thai locale: `฿128,540.25`, `฿84,012.00`, `฿12,005.55`
- NO full account numbers or customerIds anywhere

---

## US-MOCK-002 — Render Stale Data State Screen

**As a** stakeholder reviewing degraded-state UX
**I want** to see the stale data screen with global banner + per-row stale badge
**So that** I can validate that the warning color treatment is non-alarming yet noticeable

- **Priority:** MUST
- **Effort:** S
- **Reference:** [`screen-specs.md` §4](../screen-specs.md)

### Acceptance Criteria

**AC-MOCK-002-H1 — StalenessBanner uses warning palette**
- Background = `color.semantic.warning.tint` (`#FEF3C7`)
- Border-left = 4px solid `color.semantic.warning.base` (`#D97706`)
- Heading text = `color.semantic.warning.on_tint` (`#78350F`) — contrast 8.05:1 AAA
- Icon = Heroicons outline `exclamation-triangle` in warning.base color

**AC-MOCK-002-H2 — Per-row badge below stale row**
- Row #2 (`isStale=true`) shows pill badge below last-updated line
- Background = `color.semantic.warning.tint`
- Text "อาจไม่ใช่ยอดล่าสุด" using `typography.label` in `color.semantic.warning.on_tint`
- Border-radius = `border_radius.badge` (pill, 999px)
- Icon + text + color = 3 channels (color-independence honored)

**AC-MOCK-002-H3 — Other rows remain neutral**
- Rows #1 and #3 (not stale) render identically to default state — no warning treatment

---

## US-MOCK-003 — Render Loading Skeleton Screen

**As a** stakeholder reviewing loading UX
**I want** to see the skeleton placeholder screen
**So that** I can validate that the skeleton shape matches the final row (no CLS) and uses correct muted tokens

- **Priority:** MUST
- **Effort:** S
- **Reference:** [`screen-specs.md` §2](../screen-specs.md)

### Acceptance Criteria

**AC-MOCK-003-H1 — 3 skeleton rows, same dimensions as AccountRow**
- Exactly 3 skeleton rows (do NOT echo cached count — PII timing leak)
- Each row has `min-height: 72px` (`size.row_min`) matching real AccountRow
- 3 placeholder bars per row (label / number+balance / last-updated)

**AC-MOCK-003-H2 — Muted color tokens only**
- Bar background = `color.neutral.100` (`#F1F5F9`)
- Row container background = `color.surface.card` (`#FFFFFF`)
- NO shimmer animation in v1 (static placeholder per OPEN-D-009)

**AC-MOCK-003-H3 — Header subtitle also skeleton-ized**
- Header `<h1>` "บัญชีของฉัน" visible
- Subtitle replaced by 14px-tall skeleton bar at 50% width

---

## US-MOCK-004 — Render Empty State Screen

**As a** stakeholder reviewing zero-account UX
**I want** to see the empty state with illustration + Thai copy + disabled CTA
**So that** I can validate the centered layout and confirm the exclusion-rule message is clear

- **Priority:** MUST
- **Effort:** S
- **Reference:** [`screen-specs.md` §3](../screen-specs.md)

### Acceptance Criteria

**AC-MOCK-004-H1 — Centered layout, max-width 320px**
- Content centered with `max-width: 320px`
- Vertical rhythm: `spacing.7` (48px) above illustration, `spacing.5` (24px) between blocks

**AC-MOCK-004-H2 — Illustration + Thai copy**
- Illustration placeholder (120 × 120 px) with stroke color `color.primary.300`
- Heading "ยังไม่มีบัญชีที่ใช้งานอยู่" uses `typography.heading_2` in `color.text.primary`
- Subtitle "บัญชีที่ปิดหรือไม่เคลื่อนไหวจะไม่แสดงในหน้านี้" uses `typography.body` in `color.text.secondary`, center-aligned

**AC-MOCK-004-H3 — Disabled CTA placeholder**
- "เปิดบัญชีใหม่" renders as non-interactive `<span aria-disabled="true">` per OPEN-D-012
- Uses `color.text.disabled` (`#94A3B8`) + `typography.body_strong`
- Visibly disabled (not a real button)

---

## US-MOCK-005 — Render Hard Error Screen

**As a** stakeholder reviewing error UX
**I want** to see the hard error screen with retry CTA + correlationId footer
**So that** I can validate that Security C-2 is honored (no balance/account/upstream service mention in copy)

- **Priority:** MUST
- **Effort:** S
- **Reference:** [`screen-specs.md` §5](../screen-specs.md)

### Acceptance Criteria

**AC-MOCK-005-H1 — PrimaryButton uses brand color**
- Background = `color.primary.700` (`#1E3A8A` — Trust-Blue)
- Text = `color.text.inverse` (`#FFFFFF`) — contrast 10.36:1 AAA
- Border-radius = `border_radius.button` (8px)
- Min-height = `size.tap_min` (44px)

**AC-MOCK-005-H2 — Error copy is generic — Security C-2**
- Heading = "เกิดข้อผิดพลาดในการโหลดข้อมูล"
- Body = "กรุณาลองใหม่อีกครั้ง หรือกลับมาภายในไม่กี่นาที"
- Copy MUST NOT mention: balance values, account numbers (masked or full), "AccountClient", "Redis", "503", "Kafka", or any upstream service name

**AC-MOCK-005-H3 — correlationId footer present**
- Footer shows "รหัสอ้างอิง: 7f4e2b..." (truncated trace ID)
- Uses `typography.caption` in `color.text.tertiary` (`#64748B`)
- Uses `font-family: monospace` (system mono acceptable, no token)

---

## US-MOCK-006 — Render Single-Account Edge Case

**As a** stakeholder reviewing edge cases
**I want** to see the N=1 account screen
**So that** I can validate that no rank chrome appears and the layout doesn't look awkward with a single row

- **Priority:** SHOULD
- **Effort:** XS (variant of default)
- **Reference:** [`screen-specs.md` §6](../screen-specs.md)

### Acceptance Criteria

**AC-MOCK-006-H1 — Header subtitle says "1 บัญชี" not "1 of 1"**
- Subtitle reads "อัปเดตล่าสุด · 1 บัญชี"
- NO "1 of 1" / "ลำดับที่ 1 จาก 1" appears visually

**AC-MOCK-006-H2 — No visual rank chrome**
- Single row renders identically to default rows (per OPEN-D-007)
- NO rank number badge (DOM position carries rank)

---

## US-MOCK-007 — Render Dense Layout (10 accounts)

**As a** stakeholder reviewing maximum density
**I want** to see the 10-account screen to validate scroll behavior + AC-005-H1
**So that** I can confirm no horizontal scroll appears even at maximum density

- **Priority:** SHOULD
- **Effort:** XS
- **Reference:** [`screen-specs.md` §7](../screen-specs.md)

### Acceptance Criteria

**AC-MOCK-007-H1 — 10 rows at 375px, no horizontal scroll**
- NO horizontal scrollbar (AC-005-H1)
- Rows below the fold reachable via vertical scroll
- Row #7 carries a stale badge (per LO-FI annotation)

**AC-MOCK-007-H2 — Balances align via tabular-nums**
- 10 balance figures with varying magnitudes (`฿1,200,000.00` down to `฿12.55`)
- Digits align vertically column-by-column (tabular-nums enforced)
- Comma/period positions visually stacked

---

## US-MOCK-008 — Design Token Cross-Reference Sheet

**As a** stakeholder + developer reviewing the design system
**I want** a single visual reference sheet showing all tokens with their hex values + WCAG ratings
**So that** I can verify design intent at a glance without opening JSON

- **Priority:** SHOULD
- **Effort:** S
- **Reference:** [`tokens.json`](../../../_shared/tokens.json) + [`tokens-rationale.md`](../../../_shared/tokens-rationale.md) §3

### Acceptance Criteria

**AC-MOCK-008-H1 — Color swatches with contrast pairs**
- Primary scale 50→900 as labeled swatches
- Neutral scale 0→900 as labeled swatches
- 4 semantic families (success/danger/warning/info) with on_base + on_tint
- Each swatch labeled with token path (`color.primary.700`) + hex (`#1E3A8A`)
- Top 10 contrast pairs from `tokens-rationale.md` §3 with their ratio + WCAG verdict

**AC-MOCK-008-H2 — Typography specimen**
- 9 typography tokens render with sample Thai + English text
- `typography.amount` shows digit-alignment demo with 3 stacked balances

**AC-MOCK-008-H3 — Spacing + radius rulers**
- 9 spacing stops (0→64px) as visual rulers
- 6 border-radius values as labeled squares

---

## Implementation Notes (deferred — for the agent that builds these mockups)

### Recommended deliverable format

**HTML + CSS prototype → screenshot to PNG.** Tokens enforced via `var(--color-*)` from generated `tokens.css`; live-inspectable + static images for stakeholder review.

### File structure proposal

```
docs/design/balance-comparison/hifi/mockups/
├── README.md                          # how to view / regenerate
├── styles/
│   ├── tokens.css                     # generated from tokens.json
│   └── mockup-base.css                # minimal layout/reset
├── screens/
│   ├── 01-default.html                # US-MOCK-001
│   ├── 02-stale.html                  # US-MOCK-002
│   ├── 03-loading.html                # US-MOCK-003
│   ├── 04-empty.html                  # US-MOCK-004
│   ├── 05-error.html                  # US-MOCK-005
│   ├── 06-single.html                 # US-MOCK-006
│   ├── 07-dense.html                  # US-MOCK-007
│   └── 08-token-reference.html        # US-MOCK-008
└── screenshots/                       # generated PNG outputs (375×812 each)
```

### Hard rules (binding for all mockups)

1. **NO inline hex** — every color/font/spacing value via `var(--token-name)` from `tokens.css`
2. **375 × 812 px viewport**
3. **Sample data realism** — exact data from LO-FI Screen 1 (`****7890` Savings ฿128,540.25, `****4421` Fixed Deposit ฿84,012.00, `****0033` Current ฿12,005.55)
4. **Thai-primary copy** — strings from `screen-specs.md` §8 i18n key map
5. **Security C-2** — error/loading/empty copy MUST NOT contain balance values, unmasked account numbers, or upstream service names
6. **Heroicons outline v2.x** (MIT)
7. **WCAG focus rings** — 3px `var(--color-border-focus)` on `:focus-visible`

### Validation checklist (per mockup)

- [ ] Renders correctly at 375 × 812 px
- [ ] All colors traceable to `tokens.json` (no inline hex)
- [ ] All spacing from 8-pixel scale
- [ ] All typography from documented composites
- [ ] Sample masked numbers use `****XXXX` format
- [ ] Balance figures use tabular-nums
- [ ] Thai copy matches `screen-specs.md` §8
- [ ] Security C-2 honored
- [ ] Heroicons outline icons used
- [ ] Focus rings visible
- [ ] Screenshot exported to `screenshots/` folder

---

## Recommended Owner for Implementation

| Option | Owner agent | Trade-off |
|---|---|---|
| 1 (Recommended) | `banking-frontend-dev` | Real token consumption practice; aligns with future Angular implementation |
| 2 | `banking-designer` | Faster, but Designer doesn't normally implement code |
| 3 | Shared (Designer specs → FE-dev implements) | Cleanest separation, slightly slower |

---

## Decision Pending

> ⏸ **This document is a PROPOSAL.** User stories are committed for record/discussion, but no mockups are produced yet. PM scope-change approval (or user direction) required before `banking-frontend-dev` is invoked to implement these stories.
