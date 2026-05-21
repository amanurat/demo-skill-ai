# FE Code Review — balance-comparison · Iteration 1

> Reviewer: `banking-reviewer-fe`
> Date: 2026-05-21
> Feature: balance-comparison
> Iteration: 1
> Files reviewed: 41 (`.ts` / `.html` / `.scss` / `.cjs` / `.json` under feature scope)

---

## Verdict: `changes_requested`

**Reason:** 2 blockers (XSS-relevant inline event handler + JWT in `localStorage` for banking app) + 3 majors. Otherwise implementation is solid, design-token pipeline is correct, interface contracts honored, and all 5 dashboard states are covered. After blocker/major remediation this is ready to ship.

---

## Plan executed

1. Listed all 41 files in scope (`.ts`/`.html`/`.scss`/`.cjs`/`.json`).
2. Loaded checklist: interface contracts, design tokens (ADR-005), component structure, Heroicons, error handling, state management, security (XSS/CSRF/PII), routing, testing.
3. High-risk areas identified: balance string vs number precision, JWT storage, XSS surfaces (`onerror`/raw SVG handling), subscribe leaks, double-submit on retry, accountId leak.
4. Review order followed: PII/XSS first → state lifecycle → a11y → tokens → tests.
5. Hard rules verified: subscribe leak prevention, no `[innerHTML]`, no `console.log` with sensitive data, OnPush + mutability.

---

## Findings

| ID | Severity | File | Line | Rule | Description | Suggested Fix |
|---|---|---|---|---|---|---|
| R-FE-001 | blocker | `frontend/src/app/features/balance-comparison/components/empty-state/empty-state.component.html` | 20 | security: inline-event-handler / CSP | `<img onerror="this.style.display='none'">` is an inline JS handler. This will be blocked by a strict Content-Security-Policy (e.g., `script-src 'self'`) — the fallback breaks silently in production. Banking apps run under strict CSP. | Remove `onerror` attribute; use Angular `(error)="onImgError($event)"` event binding, or hide the `<img>` by checking asset availability in the component constructor and rendering only the inline SVG fallback. |
| R-FE-002 | blocker | `frontend/src/app/features/balance-comparison/components/error-retry-banner/error-retry-banner.component.html` | 22 | security: inline-event-handler / CSP | Same `onerror="this.style.display='none'"` inline handler as R-FE-001. Same CSP risk. | Same fix as R-FE-001 — replace with Angular event binding `(error)`. |
| R-FE-003 | blocker | `frontend/src/app/guards/auth.guard.ts` | 25 | security: jwt-in-localstorage | Guard reads JWT from `localStorage` (in addition to `sessionStorage`). For a banking app, storing a bearer/JWT token in `localStorage` is an OWASP anti-pattern — any XSS gains long-lived token access. The token should be in an HttpOnly cookie (preferred) or, at minimum, `sessionStorage` only. The fallback to `localStorage` widens the attack surface and conflicts with the security stance in the rest of the feature (RFC 7807, PII-clean error copy, IBOR filter). | Remove the `localStorage` fallback. If session persistence is required across tabs, route through a refresh-token flow with HttpOnly cookies; do not extend access-token lifetime via `localStorage`. Update `auth.guard.spec.ts` to drop the localStorage-allows case. |
| R-FE-004 | major | `frontend/src/app/features/balance-comparison/pipes/balance-amount.pipe.ts` | 37 | banking: float-precision-on-balance | The contract explicitly types `balance` as `string` to prevent IEEE-754 precision loss, yet the pipe immediately calls `parseFloat(value)` and uses the resulting `number` for both display and aria construction (`Math.floor` / `(value - baht) * 100`). For balances above ~9e15 satang, or for edge fractions, this drops precision. The pipe's own JSDoc says "never parse to number for math operations" but then does exactly that. | Parse the integer baht part and the satang part from the string itself (e.g., split on `.`), then format using `Intl.NumberFormat` on the integer part with manual concatenation of satang. Keep the string contract end-to-end. Add a test with `balance: '9007199254740992.99'` to lock the contract. |
| R-FE-005 | major | `frontend/src/app/features/balance-comparison/services/dashboard.service.ts` | 15, 37 | angular: deprecated-rxjs-operator | `retryWhen` is deprecated as of RxJS 7 and removed in RxJS 8. Combined with the `attempt` counter coming from `mergeMap`'s second arg (the **index** of the inner observable subscription), the retry-count semantics are fragile — the index resets on each emission cycle and currently happens to work only because the upstream emits exactly one error per attempt. | Migrate to `retry({ count: 3, delay: (err, attempt) => err.status === 503 ? timer(RETRY_DELAYS_MS[attempt-1]) : throwError(()=>err) })`. Also explicitly initialize and emit a `{kind:'loading'}` state via `startWith` so the spec assertion in `dashboard.service.spec.ts:181-195` is non-trivial. |
| R-FE-006 | major | `frontend/src/app/features/balance-comparison/components/account-row/account-row.component.html` | 67-74 | banking: fragile-template-string-manipulation | `balanceResult.display.replace('฿', '').replace('THB', '').trim()` is a template-level workaround to strip the currency prefix added by `Intl.NumberFormat`. This is locale-fragile (Intl may emit `THB` for `en` locales, NBSPs, or different positioning) and breaks if the formatter ever returns `THB 128,540.25` or `128,540.25 ฿`. | Have the pipe return a structured object with separate `amount` and `symbol` fields (the pipe already returns a discriminated result — extend `BalanceFormatResult` with `amountOnly: string`). Drop the `.replace()` chain from the template. |
| R-FE-007 | major | `frontend/src/app/features/balance-comparison/components/account-row/account-row.component.ts` | 64-82, 84-107 | angular: anti-pattern / change-detection | `buildAriaLabel()` and `buildRelativeTime()` are invoked from `ngOnChanges`, but `buildRelativeTime()` is also called directly from the template (`{{ buildRelativeTime(account.balanceAsOf) }}`) on every change-detection pass. With `OnPush` this is fine in steady state, but the method instantiates new pipes inside `buildAriaLabel()` (`new AccountTypeLabelPipe()`, `new BalanceAmountPipe()`), recreating `Intl.NumberFormat` every time `account` changes. This is also a DI-violation pattern: pipes should be injected or used in the template, not constructed manually in component code. | Inject the pipes (or move the work to a pure pipe / computed signal). For relative time, derive once in `ngOnChanges` and cache the result on a class field. Bonus: this makes the relative-time test deterministic (today the `2 minutes ago` value depends on `Date.now()` at render time vs at fixture construction). |
| R-FE-008 | major | `frontend/src/app/features/balance-comparison/containers/dashboard-page/dashboard-page.component.ts` | 165-167 | banking: console.log-in-prod | `console.log('[balance-dashboard] row selected:', account.rank)` ships to production. `account.rank` itself is not PII, but (a) this is on a banking page; (b) the eslint-disable comment signals the author knows this is wrong; (c) the next dev to extend the handler will reach for `account` directly (which contains masked number + balance string). Defense in depth: forbid `console.*` in production code, route through a logger service. | Remove the `console.log` entirely. If a placeholder is needed, throw `new Error('Row selection deferred to v1.1')` behind a feature flag, or comment out the body. Add an eslint rule (`no-console: error`) for this feature directory. |
| R-FE-009 | major | `frontend/src/app/features/balance-comparison/components/loading-skeleton/loading-skeleton.component.html` | 18 | a11y: aria-hidden-on-listitem | Skeleton `<li>` elements have both `role="listitem"` AND `aria-hidden="true"`. The parent `<ul>` carries `aria-busy="true"` which is correct, but children should either drop `role="listitem"` (since they are hidden anyway) or drop `aria-hidden`. With both, some screen readers still announce the listitem count (3) while suppressing content — confusing during loading. | Remove `role="listitem"` from skeleton rows. The `aria-busy` parent + `aria-hidden` children is the correct pattern. |
| R-FE-010 | minor | `frontend/src/app/features/balance-comparison/models/balance-dashboard.model.ts` | 12 | contract: type-drift-vs-openapi | `Freshness` union includes `'stale'`, but the task-plan contract §Interface Contracts table says `meta.freshness: 'live' \| 'snapshot'` — v1.1 only for `'stale'`. The component-spec comment in the file acknowledges this. Allowing an extra value here means the FE can silently accept a server response that the contract forbids. | Either (a) restrict `Freshness` to `'live' \| 'snapshot'` for v1 and add a separate type for v1.1, or (b) update task-plan + OpenAPI spec to admit `'stale'` now (preferred — implementation already handles it cleanly). Coordinate with TL. |
| R-FE-011 | minor | `frontend/src/app/features/balance-comparison/services/dashboard.service.spec.ts` | 181-195 | testing: trivially-passing-assertion | The "loading$ state transitions" test pushes emissions to an array but the service never emits a `loading` state (it goes straight to `loaded`). The assertion `expect(states[states.length-1]).toBe('loaded')` passes trivially. The test was clearly written assuming a loading-first emission that the service doesn't produce. | After R-FE-005 (adding `startWith({kind:'loading'})`), this test becomes meaningful. Until then, rename it to reflect reality. |
| R-FE-012 | minor | `frontend/src/app/features/balance-comparison/balance-comparison.module.ts` + `balance-comparison-routing.module.ts` + `frontend/src/app/app.routes.ts` | n/a | angular: redundant-ngmodule-with-standalone | The feature is a standalone component, yet there is also an `NgModule` + `RouterModule.forChild()` wrapper, and `app.routes.ts` uses `loadChildren` on the module while the module uses `loadComponent` inside. This is double-lazy and adds boilerplate. With Angular 18 standalone, you can drop the `NgModule` entirely and have `app.routes.ts` directly `loadChildren: () => import('./features/balance-comparison/balance-comparison.routes').then(m => m.BALANCE_ROUTES)` or even `loadComponent`. | Pick one: either go full standalone (remove `balance-comparison.module.ts`, expose `BALANCE_ROUTES`) or keep the module (then have `app.routes.ts` not nest the lazy load again). Current structure works but is unnecessary indirection. |
| R-FE-013 | minor | `frontend/src/app/features/balance-comparison/components/empty-state/empty-state.component.html` + `.../error-retry-banner/...html` | 30-34, 32-35 | sd: var-in-inline-svg-attribute | The fallback SVGs use `stroke="var(--color-primary-300)"` directly in SVG presentation attributes. This works in modern browsers but is not supported in all SVG renderers / older WebViews and is also less performant than using `stroke="currentColor"` + CSS `color: var(--…)` on the host element. | Use `stroke="currentColor"` and set the color via CSS on the parent illustration container, mirroring the pattern in `account-row.component.html` (Heroicons icons). |
| R-FE-014 | minor | `frontend/src/app/features/balance-comparison/containers/dashboard-page/dashboard-page.component.spec.ts` | 6, 151, 231 | testing: cjs-require-in-typescript-spec | `require('@angular/router').Router` and `require('rxjs').Subject` in spec code mixes CJS `require` with ESM `import` — works under TS but is inconsistent and may trip future strict-mode builds. | Use ESM imports at top of file (`import { Router } from '@angular/router'`; `import { Subject } from 'rxjs'`). |
| R-FE-015 | nit | `frontend/src/app/features/balance-comparison/components/account-row/account-row.component.scss` | 31 | tokens: motion-easing-token-not-used | Hardcoded `cubic-bezier(0.2, 0, 0, 1)` for `transition` — the design tokens already expose `--motion-easing-standard` with this exact value. Replace literal with token. Same issue in stale-banner, stale-badge, error-retry-banner. | Use `var(--motion-easing-standard)`. |
| R-FE-016 | nit | `frontend/src/app/features/balance-comparison/components/stale-banner/stale-banner.component.scss` | 19 | tokens: literal-border-width | `border-left: 4px solid …` hardcodes width. There is no exact `--border-width-4` token, but the design-system uses `--border-width-thick` (2px) + double for the warning bar pattern, or a feature-specific token should be added. | Either add a token (`--border-width-banner-rule: 4px`) and use it, or keep `4px` but document the intentional non-tokenized value in a code comment. |
| R-FE-017 | nit | `frontend/src/app/features/balance-comparison/components/stale-banner/stale-banner.component.scss` | 79 | tokens: rgba-literal | `background: rgba(120, 53, 15, 0.05)` is a hand-derived color from `var(--color-semantic-warning-on-tint)` (#78350F). Loses dark-mode swap. | Use `color-mix(in srgb, var(--color-semantic-warning-on-tint) 5%, transparent)` or define a `--color-warning-hover` token. |
| R-FE-018 | nit | `frontend/src/app/features/balance-comparison/components/account-row/account-row.component.html` | 9-13 | a11y: redundant-role-listitem | `<li role="listitem">` — `<li>` already has implicit `role="listitem"`, the explicit attribute is harmless but redundant. | Remove `role="listitem"` from `<li>` (also applies to skeleton rows after R-FE-009). |
| R-FE-019 | nit | `frontend/src/app/features/balance-comparison/components/account-row/account-row.component.ts` | 47, 51 | angular: unused-output | `@Output() rowSelect` is emitted by `onSelect()`, consumed by the page component, but the page component then logs a `console.log` (R-FE-008) and does nothing. Wire up or remove. | Until v1.1 detail page lands, remove the `rowSelect` output AND remove the `<button>` interactivity (use a plain `<div>` with the row data) — non-interactive rows are clearer than buttons that don't go anywhere. Or keep the button but at least navigate to a placeholder route. |
| R-FE-020 | nit | `frontend/e2e/balance-comparison.e2e.spec.ts` | 232-243 | testing: brittle-keyboard-test | `await page.keyboard.press('Tab'); await page.keyboard.press('Tab');` assumes a fixed number of focusables before the first account row. If anyone adds a skip-link or a header nav button, this test will silently focus the wrong element. | Tab in a loop until `:focus` matches the first `balance-account-row button`, then assert; or use `page.locator('balance-account-row button').first().focus()` and assert order via `evaluate(() => document.activeElement…)`. |

---

## Positive observations

1. **Design-token pipeline is correctly implemented** (ADR-005 §2.2-§2.5).
   - `style-dictionary.config.cjs` uses `outputReferences: false` on both SCSS and CSS platforms — alias-flatten verified.
   - `tokens.css` includes the mandatory `@media (prefers-reduced-motion: reduce)` block.
   - `styles.scss` import order matches the locked order (tokens.scss → tokens.css → reset → typography-base → utilities).
   - `package.json` exposes both `tokens:build` and `tokens:check` scripts.
   - Header comment `/* AUTO-GENERATED FROM tokens.json — DO NOT HAND-EDIT — see ADR-005 */` present in both generated files.

2. **Interface contract honored on the critical path.**
   - `AccountViewDto.balance: string` — locked correctly (R-FE-004 is about preserving precision *downstream*, not the type itself).
   - `accountId` is **not rendered** in DOM — verified by the test `does NOT render accountId in DOM (BA NFR §8)`.
   - `meta.freshness` is a string-literal union (typed correctly, modulo R-FE-010).
   - No FE re-sort — `@for ... track account.accountId` and explicit test case 8 lock the received order.

3. **Component structure is clean.**
   - `AccountRowComponent` is presentational (`@Input` only, no service injection — modulo the manual pipe construction in R-FE-007).
   - `DashboardPageComponent` is the only smart component, injects all 3 services via `inject()`.
   - All components use `ChangeDetectionStrategy.OnPush`.
   - Subscribe lifecycle managed via `takeUntil(destroy$)` — no leaks.

4. **Heroicons mapping locked exactly per spec:** SAVINGS → banknotes, CURRENT → credit-card, FIXED_DEPOSIT → lock-closed. Stroke uses `currentColor` inheriting `var(--color-text-secondary)` from CSS — correct token consumption.

5. **All 5 dashboard states represented and tested:** loading | loaded | empty | error | unauthorized | forbidden. Loading skeleton, empty state, error banner, and stale banner each have `role`/`aria-live` correctly set.

6. **Error handling matches spec:** 401 → router.navigate(['/login']); 403 → forbidden state, no auto-retry; 503 → 1s/2s/4s exponential retry × 3 (modulo R-FE-005 deprecation); empty 200 → empty state.

7. **Double-submit protection on retry** — `isRetrying` signal + `[disabled]="isRetrying()"` + `[attr.aria-busy]` on refresh button. Banking hard rule met.

8. **Security copy is C-2-clean** — error screen has explicit test that no service names ("AccountClient", "Redis", "503", "Service Unavailable") leak into UI.

9. **Accessibility is thorough:**
   - `<main>` landmark with `aria-labelledby`.
   - Stale banner: `role="status"` + `aria-live="polite"` + DOM-removed when `freshness='live'`.
   - Empty state: `role="status"` + heading programmatic focus.
   - Error banner: `role="alert"` + `aria-live="assertive"` + heading programmatic focus.
   - Loading skeleton: `aria-busy="true"` on container (modulo R-FE-009).
   - Account row composes a spoken `aria-label` with rank, type, last-4, balance, last-updated, stale clause.
   - `BalanceAmountPipe` produces non-digit-by-digit `aria-label` ("128,540 บาท 25 สตางค์") per AC-005-E2.
   - `prefers-reduced-motion` is respected at both token level and component level.

10. **No `[innerHTML]` binding, no `bypassSecurityTrust*` calls, no `<any>` types on API response models** — clean of the standard XSS / type-drift footguns.

11. **E2E spec covers AC-001, AC-002, AC-003, AC-005** per the AC→test map.

---

## Gate G5 self-check

| Item | Status | Notes |
|---|---|---|
| Build assumption | likely pass | Angular 18 standalone + signals; no obvious compile errors in code paths reviewed. Cannot verify without `ng build`. |
| Lint status | likely pass with notes | One inline `eslint-disable no-console` in `dashboard-page.component.ts` (R-FE-008). `require()` calls in spec (R-FE-014) may trigger import/no-commonjs depending on rules. |
| Unit-test coverage estimate | ~85% | All 5 components have spec files; service + facade + pipes + guard + page all covered. Some assertions trivial (R-FE-011). |
| A11y violations (static review) | 1 minor | R-FE-009 (`aria-hidden` + `role="listitem"` co-presence in skeleton). |
| Security violations | 2 blockers, 1 major | R-FE-001 + R-FE-002 (CSP-violating inline `onerror`), R-FE-003 (JWT in localStorage). |
| Interface contract drift | 1 minor | R-FE-010 (`Freshness` includes `'stale'` against task-plan v1 contract). |
| AC coverage (FE-side) | met | AC-001/002/003/005 covered in E2E + component specs. |

---

## Recommended remediation order

1. **R-FE-001 + R-FE-002** (CSP) — 5-minute fix per file, replace inline `onerror` with Angular event binding.
2. **R-FE-003** (JWT localStorage) — remove the fallback, update guard spec, confirm with security/SA.
3. **R-FE-004** (parseFloat on balance) — refactor pipe to operate on string directly.
4. **R-FE-005** (deprecated `retryWhen`) — migrate to `retry({count, delay})`; this also unlocks fixing R-FE-011 (loading-state test).
5. **R-FE-006** (template `.replace()` chain) — extend `BalanceFormatResult`.
6. **R-FE-007** (manual pipe construction in component) — inject pipes or move to computed.
7. **R-FE-008** (console.log) — remove.
8. **R-FE-009** (skeleton aria) — drop `role="listitem"` from `<li aria-hidden="true">`.
9. Remaining minors and nits.

Estimated total remediation effort: **half-day** for one FE engineer.

---

*Review by `banking-reviewer-fe` · 2026-05-21 · iteration 1 · G5 quality gate*
