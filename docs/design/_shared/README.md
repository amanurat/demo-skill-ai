# Banking Design System — Shared Tokens (v1.0.0)

> **Scope:** Cross-feature design tokens for the entire banking project.
> **Mode:** Light only (Dark v1.1 deferred — see tokens-rationale.md §9).
> **Source of truth:** [`tokens.json`](./tokens.json) (W3C Design Tokens Community Group format).
> **Consumed by:** `banking-frontend-dev` (Angular), `banking-tech-lead` (OpenAPI metadata), QA (visual regression baseline).

---

## 1. How to consume these tokens (FE-dev playbook)

### 1.1 File paths

| File | Purpose | Generated? |
|---|---|---|
| `docs/design/_shared/tokens.json` | Source of truth (W3C spec). Hand-edited by `banking-designer`. | NO |
| `docs/design/_shared/tokens-rationale.md` | Why each token has its value + WCAG evidence. | NO |
| `src/styles/tokens.scss` | SCSS `$variable` form. **GENERATED** from tokens.json. | YES |
| `src/styles/tokens.css` | CSS `--custom-property` form on `:root`. **GENERATED** from tokens.json. | YES |

The Angular app imports `tokens.scss` for compile-time values (mixin authoring) AND `tokens.css` for runtime values (component custom properties + future dark-mode swap).

### 1.2 Import order in Angular

```scss
// src/styles.scss (global entry)
@import 'styles/tokens';        // .scss — compile-time $color-primary-700, etc.
@import 'styles/tokens-css';    // .css — runtime --color-primary-700, etc. under :root
@import 'styles/reset';
@import 'styles/typography-base';
@import 'styles/utilities';
```

In component styles, prefer CSS custom properties (so dark-mode swap works without recompile):

```scss
// good
.account-row {
  background-color: var(--color-surface-card);
  color: var(--color-text-primary);
  padding: var(--spacing-3) var(--spacing-4);
  border-radius: var(--border-radius-card);
}

// avoid (compile-time only — won't hot-swap with mode)
.account-row {
  background-color: $color-surface-card;
}
```

### 1.3 Font import order

`tokens.json` declares the stack but does NOT load font files. FE-dev MUST add to `index.html` `<head>`:

```html
<!-- Inter (Latin + partial Thai); preload weights actually used -->
<link rel="preconnect" href="https://rsms.me">
<link rel="stylesheet" href="https://rsms.me/inter/inter.css">

<!-- Sarabun (full Thai coverage; loaded for Thai fallback) -->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Sarabun:wght@400;500;600;700&display=swap">
```

The stack `'Inter', 'Sarabun', -apple-system, ...` ensures: Inter for Latin, Sarabun for Thai if Inter Thai is sub-optimal, system fallback if both fail to load.

---

## 2. Generation contract (tokens.json → SCSS + CSS)

The contract is **dual-emit, deterministic, kebab-cased**.

### 2.1 SCSS generation

For each leaf token in `tokens.json` with a `$value` (after alias resolution):

```
group.subgroup.leafName.$value  →  $group-subgroup-leaf-name: <resolved-value>;
```

**Examples:**

| Token path | SCSS variable |
|---|---|
| `color.primary.700.$value = "#1E3A8A"` | `$color-primary-700: #1E3A8A;` |
| `color.surface.card.$value = "{color.neutral.0}"` resolves to `#FFFFFF` | `$color-surface-card: #FFFFFF;` |
| `color.text.primary` (alias to `neutral.900`) → `#0F172A` | `$color-text-primary: #0F172A;` |
| `spacing.4.$value = "16px"` | `$spacing-4: 16px;` |
| `border_radius.card.$value` (alias to `lg` = `12px`) | `$border-radius-card: 12px;` |
| `typography.body` (composite) | emit 4 vars: `$typography-body-font-family`, `$typography-body-font-weight`, `$typography-body-font-size`, `$typography-body-line-height` |
| `typography.amount` (composite, w/ fontFeatureSettings) | emit 5 vars including `$typography-amount-font-feature-settings: "'tnum' 1, 'lnum' 1";` |
| `elevation.1` (composite shadow) | emit single var: `$elevation-1: 0px 1px 2px 0px rgba(15,23,42,0.06);` |

### 2.2 CSS custom properties generation

Same path-flatten-to-kebab-case rule, emitted under `:root { }`:

```css
:root {
  /* color */
  --color-primary-700: #1E3A8A;
  --color-surface-card: #FFFFFF;
  --color-text-primary: #0F172A;
  /* ...all leaf tokens... */

  /* spacing */
  --spacing-4: 16px;

  /* border-radius */
  --border-radius-card: 12px;

  /* typography composite (decomposed) */
  --typography-body-font-family: 'Inter', 'Sarabun', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  --typography-body-font-weight: 400;
  --typography-body-font-size: 16px;
  --typography-body-line-height: 1.5;

  --typography-amount-font-family: 'Inter', 'Sarabun', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  --typography-amount-font-weight: 700;
  --typography-amount-font-size: 20px;
  --typography-amount-line-height: 1.4;
  --typography-amount-font-feature-settings: 'tnum' 1, 'lnum' 1;

  /* elevation (single combined shadow value) */
  --elevation-1: 0px 1px 2px 0px rgba(15,23,42,0.06);
  --elevation-2: 0px 2px 8px 0px rgba(15,23,42,0.08);
}
```

### 2.3 Reduced-motion media query (mandatory)

The generator MUST also emit:

```css
@media (prefers-reduced-motion: reduce) {
  :root {
    --motion-duration-fast:   0ms;
    --motion-duration-normal: 0ms;
    --motion-duration-slow:   0ms;
  }
}
```

This is the single biggest accessibility win — every component using `transition-duration: var(--motion-duration-normal)` automatically becomes instant when the user has reduced motion enabled.

### 2.4 Alias resolution

Aliases use W3C `{group.path}` notation. The generator MUST resolve aliases transitively at build time:

```
color.text.primary = "{color.neutral.900}"
color.neutral.900  = "#0F172A"
                  ↓
--color-text-primary: #0F172A;
```

**Do NOT emit CSS `var(--color-neutral-900)` chains** — flatten at build time. (Future dark-mode v1.1 will use `:root[data-color-mode="dark"]` override blocks; component layer stays unchanged.)

---

## 3. Naming convention rules

| Rule | Detail |
|---|---|
| Kebab-case in CSS/SCSS | `font_weight` (json) → `--font-weight-*` (css) |
| Underscores in JSON | W3C spec uses snake_case at JSON layer for tooling stability |
| Numeric stops keep numerals | `primary.700` → `--color-primary-700` (NOT `seven-hundred`) |
| Composite tokens decomposed | `typography.body` emits 4 vars per CSS, NOT a single shorthand |
| Aliases resolved at build | No `var(--x)` chains in generated CSS |
| `$type` field never emitted | Metadata only — guides validators, not output |
| `$description` never emitted | Documentation only |

---

## 4. Recommended generator tool

`banking-tech-lead` and `banking-frontend-dev` should choose **one** of:

- **Style Dictionary** (Amazon, https://amzn.github.io/style-dictionary/) — battle-tested, W3C-compatible adapters available.
- **Theo** (Salesforce) — older but stable.
- **Custom Node.js script** — acceptable for this scale (single tokens.json, two outputs).

**Recommended:** Style Dictionary with the `style-dictionary-utils` W3C transform pack. CI step runs `style-dictionary build` on every PR that touches `tokens.json`, regenerating `tokens.scss` + `tokens.css` and failing if outputs are out-of-date.

---

## 5. Token versioning placeholder

**v1.0.0 — current.** No formal SemVer policy yet (deferred to v1.1 alongside dark-mode release).

**Implicit policy for v1:**
- New tokens may be added freely (consumers tolerate).
- Existing token values may change ONLY with FE-dev review (visual regression risk).
- Token paths may NOT be renamed in v1.x without major version bump.

A full breaking-change matrix + deprecation pipeline is part of v1.1.

---

## 6. Quick reference — what to use when

| Need | Token | Why |
|---|---|---|
| Page background | `--color-surface-page` | alias resolves to neutral-50 |
| Card / row surface | `--color-surface-card` | alias resolves to neutral-0 (white) |
| Body text | `--color-text-primary` + `--typography-body-*` | AAA contrast |
| Caption / "Last updated" | `--color-text-secondary` + `--typography-caption-*` | AAA contrast |
| Balance figure | `--color-text-primary` + `--typography-amount-*` | tabular nums |
| Primary CTA | `--color-primary-700` bg + `--color-text-inverse` text | AAA contrast (10.36:1) |
| Stale badge / banner | `--color-semantic-warning-tint` bg + `--color-semantic-warning-on-tint` text | AAA contrast (8.05:1) |
| Error toast | `--color-semantic-danger-tint` bg + `--color-semantic-danger-on-tint` text | AAA contrast |
| Focus ring | `outline: 3px solid var(--color-border-focus); outline-offset: 2px;` | AC-005-H3 |
| Tap target floor | `min-height: var(--size-tap-min)` (44px) | WCAG 2.5.5 |
| AccountRow height | `min-height: var(--size-row-min)` (72px) | LO-FI commitment |
| Hairline divider | `border-bottom: var(--border-width-hairline) solid var(--color-border-subtle)` | Decorative; spacing carries semantic |

---

## 7. Cross-references

- [`tokens.json`](./tokens.json) — source of truth
- [`tokens-rationale.md`](./tokens-rationale.md) — why each value + WCAG evidence
- [`../balance-comparison/hifi/component-specs.md`](../balance-comparison/hifi/component-specs.md) — first consumer
- [`../balance-comparison/hifi/screen-specs.md`](../balance-comparison/hifi/screen-specs.md) — token-bound visual specs
- [`../balance-comparison/hifi/accessibility-final.md`](../balance-comparison/hifi/accessibility-final.md) — WCAG evidence per component
- [`docs/architecture/handoff-schema.md`](../../architecture/handoff-schema.md) — Designer Phase 2 payload schema
- W3C Design Tokens Format Module — https://design-tokens.github.io/community-group/format/
