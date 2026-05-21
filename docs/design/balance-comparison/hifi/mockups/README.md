# Balance Comparison Dashboard — HTML Mockups

> **Feature:** `balance-comparison` · **Sub-feature:** `balance-comparison-ui-mockup`
> **Owner:** `banking-frontend-dev`
> **Status:** Complete — 8 screens implemented
> **Design tokens:** [`docs/design/_shared/tokens.json`](../../../_shared/tokens.json) v1.0.0
> **Viewport:** 375 × 812 px (iPhone SE — Thai mobile baseline)

---

## How to view

Open any screen file directly in a browser. No build step or server required.

```bash
open docs/design/balance-comparison/hifi/mockups/screens/01-default.html
```

Or open all screens sequentially:

```bash
for f in docs/design/balance-comparison/hifi/mockups/screens/*.html; do open "$f"; done
```

The screens depend only on:
- `../styles/tokens.css` — generated CSS custom properties from tokens.json
- `../styles/mockup-base.css` — shared reset + component styles
- Google Fonts CDN (Inter + Sarabun) — requires internet connection for full typography

---

## File index

| File | Story | Priority | Description |
|---|---|---|---|
| `screens/01-default.html` | US-MOCK-001 | MUST | Default dashboard, 3 accounts, all live data |
| `screens/02-stale.html` | US-MOCK-002 | MUST | Stale data state — global banner + row #2 badge |
| `screens/03-loading.html` | US-MOCK-003 | MUST | Loading skeleton, 3 static rows, aria-busy |
| `screens/04-empty.html` | US-MOCK-004 | MUST | Empty state — no active accounts |
| `screens/05-error.html` | US-MOCK-005 | MUST | Hard error state — retry button + correlationId |
| `screens/06-single.html` | US-MOCK-006 | SHOULD | Single account edge case (N=1) |
| `screens/07-dense.html` | US-MOCK-007 | SHOULD | Dense layout — 10 accounts, row #7 stale |
| `screens/08-token-reference.html` | US-MOCK-008 | SHOULD | Design token cross-reference sheet |
| `styles/tokens.css` | — | Foundation | Generated CSS custom properties from tokens.json |
| `styles/mockup-base.css` | — | Foundation | Shared reset + component styles |

---

## Hard rules enforced

1. **NO inline hex** — every color/spacing/typography value is via `var(--token-name)` from `tokens.css`
2. **375px viewport** — `<meta name="viewport" content="width=375, initial-scale=1">`
3. **Thai primary copy** — all user-facing strings in Thai
4. **Security C-2** — error/loading/empty copy does NOT mention Redis, Kafka, AccountClient, HTTP codes, balance values, or account numbers
5. **Heroicons outline v2.x** (inline SVG, MIT license)
6. **WCAG focus rings** — `:focus-visible { outline: 3px solid var(--color-border-focus); outline-offset: 2px; }`
7. **tabular-nums** — all balance amounts use `font-variant-numeric: tabular-nums` + `font-feature-settings: 'tnum' 1`

---

## tokens.css generation contract

`styles/tokens.css` was generated from `docs/design/_shared/tokens.json` following these rules:

- Path flattened to kebab-case: `color.primary.700` → `--color-primary-700`
- Underscores converted to hyphens: `border_radius` → `--border-radius-*`
- Aliases resolved at generation time (no `var()` chains in output)
- Composite `typography.*` tokens decomposed into individual properties
- Composite `elevation.*` shadow tokens emitted as single `box-shadow` values
- `@media (prefers-reduced-motion: reduce)` block overrides motion duration tokens to `0ms`

---

## Validation checklist

Run through this before stakeholder review:

- [ ] Each screen renders at exactly 375px width (no horizontal scroll)
- [ ] Colors: right-click any element → inspect → no hex values in `style` attributes
- [ ] Thai copy visible and readable (requires Sarabun or system Thai fallback)
- [ ] Balance amounts visually align digits (tabular-nums test: compare ฿1,200,000.00 vs ฿12.55 in 07-dense.html)
- [ ] 02-stale.html: warning banner visible + row #2 badge visible
- [ ] 03-loading.html: no shimmer animation — static skeleton only
- [ ] 05-error.html: no service names, no HTTP codes in visible copy
- [ ] 08-token-reference.html: all 10 swatches, contrast table, 9 spacing rulers, 6 radius samples

---

## Known design gaps / flagged for designer review

None blocking. One clarification requested:

1. **`border_radius.button`** token aliases `border_radius.md` (8px) — applied to primary-button. Confirm intended (not `lg`/12px).
2. **`font_size.2xl`** key starts with digit — emitted as `--font-size-2xl` (valid CSS custom property). No issue.
3. **`color.surface.overlay`** uses raw `rgba(15,23,42,0.40)` — not a W3C alias reference. Kept as-is; no dark-mode risk since overlay is modal-scrim only.
4. **`typography.amount` currency label** — spec shows "THB" in caption style but AC-MOCK-001-H2 mentions `letter-spacing: 0.05em` specifically on "THB". Implemented as `.balance-currency` with `letter-spacing: 0.05em`.

---

## Relationship to Angular implementation

These mockups are the **visual validation layer** before `banking-frontend-dev` implements the Angular components. The CSS class names in `mockup-base.css` intentionally mirror the component names that will be implemented:

| Mockup class | Angular component |
|---|---|
| `.account-row` | `AccountRowComponent` |
| `.staleness-banner` | `StalenessBannerComponent` |
| `.stale-badge` | `StaleBadgeComponent` |
| `.skeleton-row` | `AccountRowSkeletonComponent` |
| `.empty-state` | `EmptyStateComponent` |
| `.error-state` | `ErrorStateComponent` |
| `.primary-button` | `PrimaryButtonComponent` |

The token CSS custom properties (`--color-primary-700`, `--spacing-4`, etc.) will be the same variables used in the Angular `styles.scss` / `tokens.css` global stylesheet.
