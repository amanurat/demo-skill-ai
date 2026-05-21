# ADR-005 — Design Token Consumption Pipeline (Style Dictionary, dual-emit, CI-enforced)

> **Status:** ACCEPTED
> **Date:** 2026-05-21
> **Owner:** `banking-tech-lead`
> **Consumers:** `banking-frontend-dev` (primary), `banking-devops` (CI wiring), `banking-qa` (visual regression baseline)
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Feature:** `balance-comparison`
> **Supersedes:** none
> **Related:**
> - [`docs/design/_shared/README.md`](../../../design/_shared/README.md) — dual-emit contract (Designer-authored)
> - [`docs/design/_shared/tokens.json`](../../../design/_shared/tokens.json) — W3C source of truth
> - [`docs/design/_shared/tokens-rationale.md`](../../../design/_shared/tokens-rationale.md) — WCAG evidence per token
> - [Handoff Designer-002](../../../design/balance-comparison/handoff-designer-002.json) — HI-FI artifact

---

## 1. Context

Designer Phase 2 (HI-FI) produced a W3C-compliant `tokens.json` at `docs/design/_shared/tokens.json` and a dual-emit consumption contract (`tokens.scss` + `tokens.css`) at `docs/design/_shared/README.md`. The contract specifies:

- Source of truth: `tokens.json` (hand-edited by `banking-designer` only).
- Two generated artifacts: `src/styles/tokens.scss` (compile-time SCSS vars) and `src/styles/tokens.css` (`:root` CSS custom properties).
- Alias resolution at build time (no `var(--x)` chains in generated CSS).
- Mandatory `@media (prefers-reduced-motion: reduce)` block in CSS output.
- Recommended generator: **Style Dictionary** with the `style-dictionary-utils` W3C transform pack.

The tech-lead must lock the **tool choice**, **CI enforcement contract**, and **Angular import order** so that:
1. Designer can hand-edit `tokens.json` without touching FE code.
2. FE-dev cannot accidentally hand-edit generated files (CI fails the PR).
3. Visual regression (QA) sees deterministic, alias-flattened CSS.
4. Future dark-mode (v1.1) can swap `:root[data-color-mode]` blocks without touching component layer.

This ADR finalizes choices that the Designer contract left open ("choose one of Style Dictionary / Theo / custom script").

## 2. Decision

We adopt the following design-token consumption pipeline for **all** features in the banking monorepo (this ADR is cross-feature, not balance-comparison-only):

### 2.1 Tool: **Style Dictionary** (locked)

- Package: [`style-dictionary`](https://amzn.github.io/style-dictionary/) v4+ with [`style-dictionary-utils`](https://github.com/lukasoppermann/style-dictionary-utils) W3C transform pack.
- Config file: `tools/design-tokens/style-dictionary.config.cjs` at repo root.
- Source glob: `docs/design/_shared/tokens.json`.
- Output targets (two, both regenerated on every build):
  - `frontend/src/styles/tokens.scss` — SCSS `$variable` form.
  - `frontend/src/styles/tokens.css` — CSS `--custom-property` form on `:root`.

### 2.2 Dual-emit contract (ratifies Designer §2)

- **SCSS emit:** `$<group>-<subgroup>-<leaf>: <resolved-value>;` (kebab-case, alias-flattened).
- **CSS emit:** `--<group>-<subgroup>-<leaf>: <resolved-value>;` under `:root { ... }`, plus the mandatory `@media (prefers-reduced-motion: reduce) { :root { --motion-duration-*: 0ms; } }` block at file tail.
- Composite tokens (`typography.*`, `elevation.*`) decomposed into per-property leaves (e.g., `--typography-amount-font-family`, `--typography-amount-font-feature-settings`).
- **Alias resolution at BUILD time:** generated CSS must contain literal values — `grep -E 'var\(--' frontend/src/styles/tokens.css` must return zero matches. (Future dark mode uses `:root[data-color-mode="dark"]` override blocks, not nested var chains.)

### 2.3 CI enforcement (the load-bearing part)

GitHub Actions / GitLab CI job `design-tokens-up-to-date`:

```yaml
- name: Build design tokens
  run: npx style-dictionary build --config tools/design-tokens/style-dictionary.config.cjs

- name: Fail if generated outputs are out-of-date
  run: |
    git diff --exit-code frontend/src/styles/tokens.scss frontend/src/styles/tokens.css
```

- Triggers on every PR that touches `docs/design/_shared/tokens.json` OR `frontend/src/styles/tokens.{scss,css}` OR the generator config.
- Failure mode: PR cannot merge until the author runs `npm run tokens:build` locally and commits the regenerated files.
- Header comment in generated files: `/* AUTO-GENERATED FROM tokens.json — DO NOT HAND-EDIT — see ADR-005 */`. (Cosmetic deterrent; CI is the real guard.)

### 2.4 Angular import order (locks Designer §1.2)

`frontend/src/styles.scss`:

```scss
@import 'styles/tokens';        // .scss compile-time $vars (mixins, calc)
@import 'styles/tokens.css';    // runtime :root --vars (component custom props, dark-mode swap)
@import 'styles/reset';
@import 'styles/typography-base';
@import 'styles/utilities';
```

**Order matters:** tokens MUST come before reset, typography-base, and utilities, because those files may consume tokens (e.g., `typography-base` sets `body { font-family: var(--typography-body-font-family); }`).

### 2.5 Component-layer rule

Component SCSS **MUST** prefer CSS custom properties over SCSS vars:

```scss
// GOOD — survives runtime mode swap
.account-row {
  background-color: var(--color-surface-card);
  padding: var(--spacing-3) var(--spacing-4);
}

// AVOID — compile-time only; won't hot-swap with dark mode
.account-row {
  background-color: $color-surface-card;
}
```

SCSS vars are reserved for **build-time arithmetic** (e.g., `@media (min-width: $breakpoint-md)` since CSS custom properties cannot be used in media queries pre-`@container` adoption).

### 2.6 Token versioning

- Current: **v1.0.0**.
- Adds are non-breaking; renames or removals are MAJOR bumps (per Designer §5).
- ADR-005 governs the **pipeline**, not the token taxonomy — taxonomy changes are owned by `banking-designer`.

## 3. Consequences

### 3.1 Positive

- **Single source of truth** — designers edit `tokens.json`, never `.scss`/`.css`. Eliminates "the SCSS doesn't match Figma" drift class of bugs.
- **CI-enforced correctness** — `git diff --exit-code` makes it impossible to land a PR where generated files lag the source. No code review burden.
- **Accessibility wins for free** — every component using `transition-duration: var(--motion-duration-normal)` automatically respects `prefers-reduced-motion`.
- **Dark-mode-ready** — `:root[data-color-mode="dark"]` override block is the v1.1 unlock; no component changes needed.
- **Visual regression deterministic** — alias resolution at build time means QA snapshots compare literal RGB/px, not chained `var()` resolution.
- **Cross-feature reuse** — every future feature gets the same pipeline; no per-feature token re-authoring.

### 3.2 Negative / costs

- **Adds a build step** (Style Dictionary CLI) to the FE pipeline. Mitigation: ~150ms on a warm Node cache; trivial.
- **New tool to learn** (one-time onboarding). Mitigation: Style Dictionary docs + Designer §2 are enough; `tools/design-tokens/style-dictionary.config.cjs` is ~40 lines.
- **PR friction** when generated files are forgotten. Mitigation: pre-commit hook (`npm run tokens:build && git add frontend/src/styles/tokens.{scss,css}`) — DevOps P1 to wire.

### 3.3 Risks

- **Style Dictionary W3C support is via community plugin** (`style-dictionary-utils`), not core. Risk: plugin abandonment. Mitigation: pin version in `package.json`; if plugin dies, swap to in-repo Node script (~80 lines) — well-understood replacement.
- **Generator output drift between local Node versions.** Mitigation: pin Node version in `.nvmrc` + CI uses same version (DevOps P1).

## 4. Alternatives considered (rejected)

| Option | Rejected because |
|---|---|
| **Manual copy-paste from Figma to SCSS** | (a) Drift inevitable. (b) No alias resolution → 1000-line color cascade. (c) Designer cannot ship a token update without FE PR. **REJECTED.** |
| **Theo (Salesforce)** | (a) Project effectively unmaintained since 2021 (last release 2021-11). (b) No first-class W3C-format support. **REJECTED.** |
| **Per-component token definitions (Tailwind-style)** | (a) Defeats single-source-of-truth — every component re-declares colors. (b) No central place to enforce WCAG AAA. (c) Designer cannot ship cross-component changes atomically. **REJECTED.** |
| **Custom Node.js script (no library)** | Acceptable fallback, but: (a) Reinvents Style Dictionary's transform/format infrastructure. (b) No community plugins (e.g., for Figma round-trip). (c) Extra maintenance burden. **REJECTED for now**; reconsidered only if Style Dictionary W3C plugin breaks. |
| **CSS-only (no SCSS emit)** | Loses `$breakpoint-*` SCSS vars needed for `@media` queries (CSS custom properties can't be used in media queries until `@container` adoption is universal). **REJECTED.** |
| **Runtime token loading via JS** | Adds FOUC (flash of unstyled content) on every page load. Eliminates compile-time validation. **REJECTED.** |

## 5. Implementation plan (FE-dev hand-off)

1. Add `tools/design-tokens/style-dictionary.config.cjs` (TL or FE-dev — TL provides skeleton in `implementation-notes.md` §8).
2. Add `npm` scripts:
   - `"tokens:build": "style-dictionary build --config tools/design-tokens/style-dictionary.config.cjs"`
   - `"tokens:check": "npm run tokens:build && git diff --exit-code frontend/src/styles/tokens.scss frontend/src/styles/tokens.css"`
3. Wire CI job (`banking-devops` P1).
4. Wire pre-commit hook (`banking-devops` P1) — optional but recommended.
5. FE-dev: edit `frontend/src/styles.scss` import order per §2.4.
6. FE-dev: ensure all component styles use `var(--*)` per §2.5 (lint rule: `stylelint-declaration-strict-value` configurable).

## 6. Acceptance criteria

- [ ] `tools/design-tokens/style-dictionary.config.cjs` exists and produces both outputs.
- [ ] CI job `design-tokens-up-to-date` fails on stale outputs.
- [ ] Generated `tokens.css` contains the `@media (prefers-reduced-motion: reduce)` block.
- [ ] `grep -E 'var\(--' frontend/src/styles/tokens.css` returns zero matches (alias flattening verified).
- [ ] `frontend/src/styles.scss` import order matches §2.4.
- [ ] At least one component in `balance-comparison` consumes a token via `var(--*)` (proof of pipeline end-to-end).

---

*ADR-005 · banking-tech-lead · 2026-05-21*
