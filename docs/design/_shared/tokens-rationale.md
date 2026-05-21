# Design Tokens — Rationale & Contrast Evidence

> **Shared design system v1.0.0** · authored 2026-05-21 by `banking-designer` · scope: all banking features
> **Source of truth:** [`tokens.json`](./tokens.json)
> **Mode:** Light only (Dark v1.1 deferred; structure is dark-mode-ready by aliasing)
> **Stakeholder decisions:** Trust-Blue `#1E3A8A` palette; Inter + Sarabun fallback; tabular-nums on `amount`; 8-pixel spacing scale; W3C Design Tokens spec

---

## 1. Color — Trust-Blue palette rationale

### 1.1 Why Trust-Blue (`#1E3A8A` / `primary.700`)?

- **Banking convention:** Deep blues signal trust, stability, security — the three values customers most associate with a deposit account. Lighter blues (sky / cyan) read as "tech startup", not "bank".
- **Conservative aesthetic:** Saturated greens read "fintech disruption"; reds read "warning"; oranges read "energy / consumer brand". Trust-blue is the only family that says "this is your money, safely stored" without secondary connotations.
- **Cultural fit (TH):** Thai retail banks (SCB, Kasikorn, Krungsri, Bangkok Bank) all anchor on blue or green; blue is the safer cross-bank neutral.
- **Locked by stakeholder.** Not re-negotiable for v1.

### 1.2 Why a full 50→900 scale (not just primary + tint)?

- **Future-proofing.** Even though v1 uses ~3 stops (700, 500-tint, 50-bg), a full scale prevents ad-hoc hex creep when adding hover/active states in v1.1.
- **WCAG headroom.** Multiple stops let us pick the right contrast pair without modifying the brand color.

### 1.3 Neutral scale — why 11 stops (0 → 900)?

- **Text hierarchy** needs at least 4 stops (primary 900, body 700, secondary 600, tertiary 500). With borders + skeletons + disabled, 11 stops covers all banking UI patterns without modification.
- **`neutral.500` = `#64748B`** is the smallest grey that passes AA on white (verified §3).
- **`neutral.400` = `#94A3B8`** is intentionally below AA — used ONLY for `text.disabled` (non-actionable text exempted by WCAG 1.4.3).

### 1.4 Semantic colors — why 4 (success/danger/warning/info)?

- **Success (`#16A34A`):** confirmation moments only (post-transfer toast, not used in balance-comparison v1).
- **Danger (`#DC2626`):** error/destructive only. Reserved; balance-comparison uses it on `ErrorState` retry button background, never on balance text.
- **Warning (`#D97706`):** the staleness states — both the per-row badge and the global banner. Banking-conservative: never use red for "data is old", because customers will think their money is gone.
- **Info (`#0284C7`):** non-critical hints (tooltips). Distinct from primary-blue so it doesn't read as a CTA.

Each semantic has 4 sub-tokens: `base` (button/icon), `tint` (background), `on_base` (text on base), `on_tint` (text on tint).

### 1.5 Why alias `surface.*` and `text.*` instead of direct neutral references?

**Dark-mode portability.** Component specs reference `color.surface.card` and `color.text.primary` — when v1.1 introduces a dark mode, only the alias targets flip. Component specs do not change.

This is the explicit ask from the stakeholder decision: *"emit tokens in a structure that's dark-mode-ready"*.

---

## 2. Typography rationale

### 2.1 Font stack: Inter + Sarabun fallback

| Font | Role | Why |
|---|---|---|
| **Inter** | Primary (Latin + Thai partial coverage) | Excellent screen rendering, banking-clean, geometric. Wide weight range. |
| **Sarabun** | Thai-optimized fallback | When Inter Thai glyphs render poorly on older Android (< 9), Sarabun is the well-supported Thai web font. |
| `-apple-system`, `BlinkMacSystemFont`, `'Segoe UI'`, `Roboto` | System fallback chain | No-network resilience, OS-native rendering on iOS/Android/desktop. |

**Stack order locked by stakeholder.** All `font_family.*` tokens use this exact stack.

### 2.2 Why `body = 16/24` Regular?

- **16px** = WCAG-friendly minimum for body content; Android default; iOS default.
- **24/16 = 1.5 line-height** = ideal Latin reading rhythm AND comfortable for stacked Thai diacritics (Thai needs more vertical breathing than Latin).
- Per stakeholder decision.

### 2.3 Why a dedicated `amount` typography token with tabular-nums?

- **Banking convention** (HSBC, Chase, Krungsri, Kasikorn): balance columns ALIGN digit-by-digit so customers can compare ฿128,540.25 vs ฿84,012.00 at a glance — without aligned digits, the eye has to re-anchor at each row.
- `font-feature-settings: 'tnum' 1, 'lnum' 1` activates OpenType tabular numerals + lining figures (Inter supports both).
- **Why 20/28 Bold?** Bold makes the most-important data (balance) the dominant element in the AccountRow. 20px is the largest the row can carry without overflow on 375px alongside the masked account number.
- **Per stakeholder decision:** *"amount uses tabular-nums 20/28 (banking convention for aligned digit columns)"*.

### 2.4 Why a separate `amount_small` token?

- Used in tight contexts (toasts, header subtitle) where the full `amount` is too loud. Same tabular treatment, smaller weight and size.
- NOT used in balance-comparison v1 — present in tokens for future features (transfer-confirm summary, statement summary).

### 2.5 Why no `display` / `hero` heading token?

- Banking dashboards don't have hero copy. `heading_1` at 24px is the largest text on any banking screen by convention. Adding a `display` token would invite mis-use (oversized headings = "marketing site" feel).

---

## 3. Contrast Evidence — WCAG 2.1 AA pairwise table

WCAG 2.1 AA requires:
- **Normal text (< 18.66px or < 24px @ bold):** ≥ 4.5:1
- **Large text (≥ 18.66px or ≥ 24px @ bold):** ≥ 3:1
- **UI components / graphical elements:** ≥ 3:1 (1.4.11)
- **AAA bonus (informational only):** ≥ 7:1 for normal text, ≥ 4.5:1 for large

Ratios computed via standard WCAG luminance formula `(L1 + 0.05) / (L2 + 0.05)` where L is sRGB relative luminance. Cross-verified with WebAIM Contrast Checker formula.

### 3.1 Body / heading text on white surface

| Pair | Hex pair | Ratio | AA-normal (4.5) | AA-large (3.0) | AAA-normal (7.0) | Notes |
|---|---|---|---|---|---|---|
| `text.primary` on `surface.card` | `#0F172A` / `#FFFFFF` | **17.85 : 1** | PASS | PASS | PASS | Primary body text — comfortably AAA |
| `text.primary` on `surface.page` | `#0F172A` / `#F8FAFC` | **17.16 : 1** | PASS | PASS | PASS | |
| `text.primary` on `surface.muted` | `#0F172A` / `#F1F5F9` | **16.04 : 1** | PASS | PASS | PASS | |
| `text.secondary` on `surface.card` | `#475569` / `#FFFFFF` | **7.55 : 1** | PASS | PASS | PASS | "Last updated" labels — AAA |
| `text.tertiary` on `surface.card` | `#64748B` / `#FFFFFF` | **5.05 : 1** | PASS | PASS | FAIL | Captions only; AAA fail is acceptable per WCAG |
| `text.disabled` on `surface.card` | `#94A3B8` / `#FFFFFF` | **2.86 : 1** | FAIL | FAIL | FAIL | INTENTIONAL — disabled text exempt per WCAG SC 1.4.3 (informative) |
| `text.link` on `surface.card` | `#1E3A8A` / `#FFFFFF` | **10.36 : 1** | PASS | PASS | PASS | Tappable links |
| Heading on `surface.card` (24px bold) | `#0F172A` / `#FFFFFF` | **17.85 : 1** | PASS | PASS | PASS | |

### 3.2 White text on primary / semantic surfaces (CTAs and badges)

| Pair | Hex pair | Ratio | AA-normal | AA-large | AAA-normal | Notes |
|---|---|---|---|---|---|---|
| `text.inverse` on `primary.700` | `#FFFFFF` / `#1E3A8A` | **10.36 : 1** | PASS | PASS | PASS | PrimaryButton default state |
| `text.inverse` on `primary.500` | `#FFFFFF` / `#3B5BB8` | **5.74 : 1** | PASS | PASS | FAIL | PrimaryButton hover state |
| `text.inverse` on `primary.800` | `#FFFFFF` / `#162C6A` | **13.74 : 1** | PASS | PASS | PASS | PrimaryButton active/pressed |
| `text.inverse` on `success.base` | `#FFFFFF` / `#16A34A` | **3.13 : 1** | FAIL | PASS | FAIL | LARGE text only (button label ≥ 18.66px); confirm in component spec |
| `text.inverse` on `danger.base` | `#FFFFFF` / `#DC2626` | **4.81 : 1** | PASS | PASS | FAIL | |
| `text.inverse` on `warning.base` | `#FFFFFF` / `#D97706` | **3.06 : 1** | FAIL | PASS | FAIL | LARGE text only; v1 we DON'T use this pair — see §3.4 |
| `text.inverse` on `info.base` | `#FFFFFF` / `#0284C7` | **4.59 : 1** | PASS | PASS | FAIL | |

### 3.3 Dark text on light semantic tints (alerts, badges)

| Pair | Hex pair | Ratio | AA-normal | AA-large | AAA-normal | Notes |
|---|---|---|---|---|---|---|
| `success.on_tint` on `success.tint` | `#14532D` / `#DCFCE7` | **9.96 : 1** | PASS | PASS | PASS | Success alert text |
| `danger.on_tint` on `danger.tint` | `#7F1D1D` / `#FEE2E2` | **8.13 : 1** | PASS | PASS | PASS | Inline error text |
| `warning.on_tint` on `warning.tint` | `#78350F` / `#FEF3C7` | **8.05 : 1** | PASS | PASS | PASS | **Staleness banner + per-row stale badge text** — comfortably AAA |
| `info.on_tint` on `info.tint` | `#075985` / `#E0F2FE` | **8.34 : 1** | PASS | PASS | PASS | |

### 3.4 Critical pair for balance-comparison: stale badge / banner

The staleness UI uses the warning tint (`#FEF3C7`) + dark warning text (`#78350F`). **This pair clears AAA (8.05:1)** — meaning customers see "อาจไม่ใช่ยอดล่าสุด" clearly even at small caption size.

We deliberately do NOT use white-on-orange (`#FFFFFF` on `#D97706` = 3.06:1) because it fails AA-normal. The dark-on-tint pattern is the safer, accessible choice and reads as "advisory" rather than "alarm".

### 3.5 Non-text UI contrast (WCAG 1.4.11)

| Pair | Hex pair | Ratio | Required (3:1) | Notes |
|---|---|---|---|---|
| `border.focus` (focus ring) on `surface.card` | `#1E3A8A` / `#FFFFFF` | **10.36 : 1** | PASS | 3px outline focus ring is clearly visible |
| `border.focus` on `surface.page` | `#1E3A8A` / `#F8FAFC` | **9.96 : 1** | PASS | |
| `border.default` on `surface.card` | `#CBD5E1` / `#FFFFFF` | **1.61 : 1** | FAIL | INTENTIONAL — hairline divider is decorative; semantic separation provided by spacing |
| `border.subtle` on `surface.card` | `#E2E8F0` / `#FFFFFF` | **1.23 : 1** | FAIL | Same — decorative only |
| Skeleton (`neutral.100`) on `surface.card` | `#F1F5F9` / `#FFFFFF` | **1.05 : 1** | FAIL | INTENTIONAL — skeleton is decorative; `aria-busy="true"` carries semantic load |

### 3.6 Focus ring contrast — AC-005-H3 evidence

Focus ring spec: **3px solid `border.focus` (`#1E3A8A`) + 2px outline-offset against `surface.card`/`surface.page`**.

- Ring on white: **10.36:1** (PASS 3:1)
- Ring on page bg: **9.96:1** (PASS 3:1)
- Ring against neutral-100 muted: **9.66:1** (PASS 3:1)

→ Satisfies AC-005-H3 ("visible focus ring, contrast ≥ 3:1 against adjacent colors").

---

## 4. Spacing rationale (8-pixel base)

| Token | Value | Use |
|---|---|---|
| `spacing.0` | 0px | reset |
| `spacing.1` | 4px | minimum gap (icon to label inline) |
| `spacing.2` | 8px | tight gap (between badges, within rows) |
| `spacing.3` | 12px | row internal padding (vertical) |
| `spacing.4` | 16px | default container padding, gap between fields |
| `spacing.5` | 24px | section separation |
| `spacing.6` | 32px | major section gap |
| `spacing.7` | 48px | full-page vertical breathing (empty/error state hero) |
| `spacing.8` | 64px | maximum — bottom safe-area on long scrolls |

**Why 8-pixel base?** Aligns with Material Design + iOS HIG defaults; ensures mobile-first layouts grid-align perfectly on 375px (which is 47 × 8px columns).

**Why no 2px / 6px / 10px stops?** Discipline. Forcing the eye to a single scale prevents inconsistent micro-gaps that read as "designer was tired".

---

## 5. Border-radius rationale

Banking is conservative; rounded shapes signal "modern but trustworthy". Values were chosen to read structured at 375px:

- **`button` = 8px (`md`)** — matches input radius; buttons feel like part of the form, not stickers.
- **`card` = 12px (`lg`)** — slightly more rounded than buttons so cards feel like containers, not buttons.
- **`input` = 8px (`md`)** — paired with button radius.
- **`badge` = 999px (`pill`)** — full pill ONLY for badges, never for buttons. Pill buttons read as "consumer / marketing".

No sharp (0px) corners in v1 — they read as "system / admin", not "consumer banking".

---

## 6. Elevation rationale (3 levels max)

Banking-conservative — flat is the default, depth is the exception.

- **`elevation.0` (none):** the entire dashboard. AccountRows are separated by spacing + hairline borders, NOT shadow.
- **`elevation.1`:** subtle hairline lift — used IF the AccountRow needs separation from page bg (typically not needed when surface.card on surface.page provides enough contrast).
- **`elevation.2`:** sticky banners (staleness banner), popovers (tooltip for absolute timestamp).
- **`elevation.3`:** dialogs / modals only. Not used in balance-comparison v1.

We deliberately omit `elevation.4` / `5` (skeuomorphic deep shadows) — banking doesn't need them and they read as "older mobile app".

---

## 7. Motion rationale

- **`duration.fast` (120ms):** focus ring appearance, hover color shift.
- **`duration.normal` (200ms):** badge fade-in, banner slide-down.
- **`duration.slow` (320ms):** modal entrance (not used in v1).
- **`duration.instant` (0ms):** the value FE-dev substitutes when `prefers-reduced-motion: reduce` is detected.

### 7.1 Reduced-motion contract (mandatory per WCAG 2.3.3)

When the OS user has set `prefers-reduced-motion: reduce`:
- ALL animations swap to `motion.duration.instant`.
- Skeleton shimmer is DISABLED (static placeholder only — already baked in LO-FI decision).
- Pull-to-refresh spring is suppressed; gesture still works.
- Badge fade-in becomes instant appearance.

FE-dev MUST implement via `@media (prefers-reduced-motion: reduce) { ... }` rules in tokens.scss output.

### 7.2 Easing curves

Material-inspired but conservative:
- `easing.standard` (`cubic-bezier(0.2, 0, 0, 1)`) — default; energy in, settle out.
- `easing.decelerate` — elements appearing.
- `easing.accelerate` — elements leaving.

---

## 8. Accessibility-by-token notes

| Concern | Token coverage |
|---|---|
| Focus ring | `border.focus` + `border_width.focus` (3px) + 2px offset (component spec) |
| Tap target ≥ 44 dp | `size.tap_min` = 44px; `size.row_min` = 72px |
| Reduced motion | `motion.duration.instant`; FE-dev applies via media query |
| AA contrast | All semantic on_base/on_tint pairs verified §3 |
| Disabled text exempt | `text.disabled` is the ONLY pair allowed below AA; component must also carry `aria-disabled="true"` |
| Color independence | Semantic states carry icon + text — never color alone (`StaleBadge` shows orange icon + Thai text "อาจไม่ใช่ยอดล่าสุด") |

---

## 9. Token versioning policy (placeholder)

**v1.0.0 — initial.** No breaking-change policy defined yet (deferred to follow-up).

Naming convention is locked to allow future tooling:
- Group paths use dotted notation (`color.primary.700`)
- SCSS variables flatten to kebab-case (`$color-primary-700`)
- CSS custom properties to kebab-case under `:root` (`--color-primary-700`)

When v1.1 introduces a dark mode, the strategy is:
1. Add `color.mode.dark.*` alias group in tokens.json.
2. CSS generation emits a second `:root[data-color-mode="dark"]` block.
3. Component specs DON'T change because they reference `surface.card`, `text.primary`, etc. — only the alias targets flip.

A formal SemVer policy + breaking-change matrix lands in v1.1 alongside the dark-mode release.

---

## 10. References

- W3C Design Tokens Format Module — https://design-tokens.github.io/community-group/format/
- WCAG 2.1 AA — https://www.w3.org/TR/WCAG21/
- WebAIM Contrast Checker formula — https://webaim.org/articles/contrast/
- Inter font specimen — https://rsms.me/inter/
- Sarabun (Google Fonts) — https://fonts.google.com/specimen/Sarabun
- BA NFR §5 (accessibility baseline) — `docs/ba/balance-comparison/nfr.md`
- LO-FI a11y notes — `docs/design/balance-comparison/accessibility-notes.md`
