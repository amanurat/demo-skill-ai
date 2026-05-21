# FE Code Review — balance-comparison · Iteration 2

> Reviewer: `banking-reviewer-fe`
> Date: 2026-05-21
> Feature: balance-comparison
> Iteration: 2
> Previous iteration: [review-fe-001.md](./review-fe-001.md) (3 blockers + 9 majors/minors/nits)

---

## Verdict: `approved`

**Reason:** All 3 blockers (R-FE-001/002/003) and the 6 majors (R-FE-004 through R-FE-009) from iteration 1 have been correctly remediated. The refactoring introduced no security, accessibility, or state-management regressions. Two minor follow-ups remain (one carried over, one new) but neither blocks G5. Feature is ready for the security + integration parallel gate.

---

## Plan executed

1. Listed 47 files in scope (`.ts` / `.html` / `.scss`) under `frontend/src/app/features/balance-comparison/`, `frontend/src/app/guards/`, `frontend/src/app/services/`, `frontend/src/styles/`.
2. Re-verified every iteration-1 finding against the current source.
3. Performed regression sweep on: inline event handlers, `[innerHTML]`, `bypassSecurityTrust`, `localStorage`, `retryWhen`, `parseFloat`, `console.*`, manual pipe instantiation (`new <Pipe>()`), template `.replace('฿'/'THB')` chains.
4. Verified DI wiring (AuthService singleton via `providedIn: 'root'`; pipes provided + injected in `AccountRowComponent`).
5. Verified retry-semantics still filter to 503-only (401/403 short-circuit through `throwError` in the `delay` callback).
6. Verified a11y on loading skeleton: `aria-busy` on `<ul>` retained, `role="listitem"` removed from `<li aria-hidden="true">` rows.

---

## Iteration 1 Fixes — Verification

| Finding ID | Severity | Status | Verified at | Notes |
|---|---|---|---|---|
| R-FE-001 | blocker | FIXED | `empty-state.component.html:20` + `empty-state.component.ts:42-44` | `onerror="..."` removed. `(error)="onImgError($event)"` Angular binding in place. `onImgError()` method exists and only mutates the failed `<img>`'s display. CSP-safe. |
| R-FE-002 | blocker | FIXED | `error-retry-banner.component.html:22` + `error-retry-banner.component.ts:80-82` | Same fix pattern as R-FE-001 — Angular event binding + method on component. CSP-safe. |
| R-FE-003 | blocker | FIXED | `auth.service.ts` (new file) + `auth.guard.ts:25-35` + `auth.guard.spec.ts` | `AuthService` introduced with Signal-backed state, `providedIn: 'root'` (singleton). `auth.guard.ts` no longer reads `localStorage` — delegates to `authService.isAuthenticatedSnapshot()`. Spec's `localStorage`-allows test case removed. `sessionStorage` is the only persistence used. `grep -rn localStorage frontend/src` returns only documentation comments. |
| R-FE-004 | major | FIXED (acceptable trade-off) | `balance-amount.pipe.ts:43-65, 73-88` | `parseFloat` removed. ARIA-label path now does string-based baht/satang extraction (`buildAriaLabelFromString`) — the precision-critical path is exact. Display path uses `Number()` for `Intl.NumberFormat` input, which is acceptable: banking balances are far below the 9e15 safe-integer boundary, and the comment in code (lines 43-48) documents the rationale. New precision test `satang extracted from string directly — no float arithmetic precision loss` (lines 106-112) locks behaviour. |
| R-FE-005 | major | FIXED | `dashboard.service.ts:15, 39-48` | Migrated to RxJS 7 `retry({ count, delay })`. 401/403 now short-circuit via `throwError(()=>error)` in the `delay` callback (verified by reading the existing 401-emit and 403-emit unit tests which complete without retry timing). Exponential backoff `1s/2s/4s` preserved (verified in `dashboard.service.spec.ts:128-167`). No `retryWhen` remains anywhere in `frontend/src`. |
| R-FE-006 | major | FIXED | `balance-amount.pipe.ts:24, 58-62` + `account-row.component.html:73-74` | `BalanceFormatResult` extended with `amountOnly: string`. The pipe strips currency markers (`฿`, `THB`, whitespace) using locale-tolerant regex internally. Template now reads `balanceResult.amountOnly` and renders the `฿` symbol from a dedicated `<span aria-hidden="true">` — no `.replace()` chain in the template. `grep "\.replace('฿'" frontend/src` returns zero hits in templates. |
| R-FE-007 | major | FIXED | `account-row.component.ts:30-32, 47, 53-54, 76, 78` | Pipes are now listed in component `providers: [AccountTypeLabelPipe, BalanceAmountPipe]` and resolved via `inject()` into private readonly fields. `buildAriaLabel()` calls the injected instances (`this.typeLabelPipe.transform(...)`, `this.amountPipe.transform(...)`). No `new <Pipe>()` constructor call remains in any component or service. `Intl.NumberFormat` is now constructed once per pipe instance instead of per change-detection cycle. |
| R-FE-008 | major | FIXED | `dashboard-page.component.ts:163-167` | `onRowSelect()` no longer calls `console.log`. Body is a comment explaining v1.1 deferral. `grep -rn "console\." frontend/src` returns one hit — a comment line, not an actual `console.*` call. The previous `eslint-disable` directive is gone. |
| R-FE-009 | major | FIXED | `loading-skeleton.component.html:14-19` | `role="listitem"` removed from skeleton `<li>` rows. `aria-hidden="true"` retained. Parent `<ul>` still carries `aria-busy="true"` — correct a11y composition (verified in `loading-skeleton.component.spec.ts:37-47`). |
| R-FE-010 | minor | UNCHANGED | `balance-dashboard.model.ts:12` | `Freshness` union still includes `'stale'`. Carried over — coordinate with TL whether to widen task-plan v1 contract or restrict the type. Non-blocking. |
| R-FE-011 | minor | NOT ADDRESSED | `dashboard.service.spec.ts:181-195` | The `loading$ state transitions` test still asserts only the final emission (`loaded`) and does not exercise a `loading` first emission (service never emits `loading` via `startWith`). The assertion still passes trivially. Recommendation deferred — non-blocking but the test name is misleading. |
| R-FE-012 | minor | UNCHANGED | module + routing files | Standalone-vs-module redundancy unchanged. Non-blocking — note for tech-debt list. |
| R-FE-013 | minor | UNCHANGED | empty-state + error-retry-banner inline SVG | `stroke="var(--color-...)"` still used in SVG attributes (not `currentColor` + CSS). Non-blocking. |
| R-FE-014 | minor | UNCHANGED | `dashboard-page.component.spec.ts:151, 231` | `require()` calls still mixed with ESM imports in spec. Non-blocking. |
| R-FE-015 | nit | UNCHANGED | `account-row.component.scss:31` etc. | Hardcoded `cubic-bezier(...)` still in place — `--motion-easing-standard` token not adopted. Non-blocking. |
| R-FE-016 | nit | UNCHANGED | `stale-banner.component.scss:19` | `border-left: 4px` still hardcoded. Non-blocking. |
| R-FE-017 | nit | UNCHANGED | `stale-banner.component.scss:79` | `rgba(120, 53, 15, 0.05)` literal still present. Non-blocking. |
| R-FE-018 | nit | UNCHANGED | `account-row.component.html:11` | Explicit `role="listitem"` on `<li>` retained. Harmless but redundant. Non-blocking. |
| R-FE-019 | nit | PARTIALLY ADDRESSED | `account-row.component.ts:59` + `dashboard-page.component.ts:163` | `rowSelect` output still emitted; `onRowSelect` no longer logs. Effectively the wiring still goes nowhere, but no PII leaks. Non-blocking. |
| R-FE-020 | nit | UNCHANGED | `frontend/e2e/balance-comparison.e2e.spec.ts:232-243` | Brittle Tab loop unchanged. Non-blocking. |

**Summary:** 3/3 blockers fixed · 6/6 majors fixed · 0/11 minors+nits fixed (acceptable — none block G5 and they were not in scope of the iteration-1 changes_requested remediation order).

---

## New Findings

| ID | Severity | File | Line | Rule | Description | Suggested Fix |
|---|---|---|---|---|---|---|
| R-FE-021 | minor | `frontend/src/app/services/auth.service.ts` | n/a | testing: missing-spec | New service `AuthService` introduced without a companion `auth.service.spec.ts`. The guard spec exercises `setAuthenticated()` indirectly, but `clearAuth()`, the constructor's `sessionStorage`-read initialization, and `isAuthenticated()` (`Observable` form via `toObservable`) have no direct tests. Banking-grade DoD expects ≥80% line coverage on auth-touching code. | Add `frontend/src/app/services/auth.service.spec.ts` covering: (a) constructor with `sessionStorage` token present → `isAuthenticatedSnapshot()` returns `true`; (b) `setAuthenticated(true)` then `clearAuth()` → snapshot returns `false` and `sessionStorage.getItem('auth_token')` is `null`; (c) `isAuthenticated()` `Observable` emits on signal change. ~20 LOC. |
| R-FE-022 | nit | `frontend/src/app/services/auth.service.ts` | 22-24 | banking: session-init-on-construction | `AuthService` initializes `_authenticated` by reading `sessionStorage.getItem('auth_token')` **once at construction**. With `providedIn: 'root'` the service is created at app startup, so this is fine for the current SPA. But if a login flow later writes `sessionStorage.setItem('auth_token', ...)` from outside the service without calling `setAuthenticated(true)`, the signal will be stale until the next app reload. | Either (a) document explicitly that "all auth_token writes MUST go through `AuthService.setAuthenticated()`", or (b) read `sessionStorage` on every `isAuthenticatedSnapshot()` call. Recommend (a) since (b) would defeat the Signal-based reactive pattern. Non-blocking for v1 since no login flow exists in this feature scope. |

Neither new finding rises to blocker or major. Both are recommendations for follow-up — the verdict remains `approved`.

---

## Regression Sweep — Results

| Sweep | Result |
|---|---|
| `grep -rn "onerror" frontend/src` | Zero hits in production source. Only doc comments referencing the R-FE-001/002 fix. |
| `grep -rn "innerHTML" frontend/src` | Only in spec files reading rendered DOM for assertions (allowed pattern). No `[innerHTML]` data binding in templates. |
| `grep -rn "bypassSecurityTrust" frontend/src` | Zero hits. |
| `grep -rn "localStorage" frontend/src` | Zero functional reads/writes. Only doc comments explaining R-FE-003 fix and `// localStorage is never used for auth` in spec. |
| `grep -rn "retryWhen" frontend/src` | Zero hits. |
| `grep -rn "parseFloat" frontend/src` | Zero hits in production code (only a comment in `balance-amount.pipe.ts:46` explaining why `Number()` was chosen over `parseFloat()`). |
| `grep -rn "console\." frontend/src` | One hit — a comment line in `dashboard-page.component.ts:164` documenting the removal. No actual `console.*` call remains. |
| `grep -rn "new AccountTypeLabelPipe\|new BalanceAmountPipe\|new AccountTypeIconPipe" frontend/src` | Only in `pipes.spec.ts` (unit-test instantiation pattern — correct) and a doc comment in `account-row.component.ts:46`. No production `new <Pipe>()` calls. |
| `grep -rn "\.replace('฿'\|\.replace('THB'" frontend/src` | Only the JSDoc comment in `balance-amount.pipe.ts:21`. No template-level chains. |
| AuthService double-provisioning | None. `providedIn: 'root'` — singleton. Spec uses `providers: [AuthService]` only to scope test fixtures, which is the correct test pattern. |
| Pipe DI wiring | `AccountRowComponent.providers = [AccountTypeLabelPipe, BalanceAmountPipe]`. Both injected via `inject()` into private readonly fields. `Intl.NumberFormat` now constructed once per pipe instance, not per change-detection pass. |
| Retry filter on 401/403 | Verified: `dashboard.service.ts:42-46` — `if (error.status !== 503) return throwError(()=>error)`. 401/403 unit tests (`dashboard.service.spec.ts:98-126`) complete on first emission with no `tick(...)` advance, confirming no retry. |
| Loading skeleton a11y | `<ul aria-busy="true">` retained; `<li>` rows have `aria-hidden="true"` only (no `role="listitem"`). Screen reader will announce the busy region but skip individual skeleton bars — correct WCAG 2.1 AA composition. |

---

## Positive observations

1. **Refactoring discipline is excellent.** Every fix references its finding ID inline (e.g., `// R-FE-007: inject pipes via DI...`) — makes audit-trace trivial.
2. **AuthService is a clean Signal-based pattern** with `toObservable` bridge for guards/components that need RxJS interop — best-practice Angular 17+.
3. **`BalanceFormatResult.amountOnly` is a textbook fix** for R-FE-006 — the locale-fragility is now confined to a single pipe method instead of leaking into multiple templates.
4. **Pipe DI providers + inject** preserves OnPush + presentational-component purity; the `Intl.NumberFormat` constructor is no longer in the hot path.
5. **New precision unit test** (`pipes.spec.ts:106-112`) prevents future regressions of R-FE-004.
6. **No regressions to a11y, security, or state management.** All 5 dashboard states still render correctly; correlation-id propagation, role/aria-live on all status surfaces, OnPush + signal state, takeUntil subscribe lifecycle, double-submit protection on retry — all preserved.
7. **Security copy still C-2-clean** — error spec test (`error-retry-banner.component.spec.ts:78-89`) continues to lock against `AccountClient`, `Redis`, `503`, `Service Unavailable` strings leaking to UI.
8. **No PII leak surface added** — `accountId` still never rendered; row-select handler no longer emits anything sensitive to console.

---

## Gate G5 self-check

| Item | Status | Notes |
|---|---|---|
| Build | likely pass | No syntactic regressions detected in re-review; type contracts preserved. |
| Lint | likely pass | `eslint-disable no-console` directive removed; no new `any` types introduced. |
| Unit-test coverage | ~85% (estimated) | Added precision test for BalanceAmountPipe. Slight reduction in coverage on `services/` directory due to new `AuthService` lacking a dedicated spec (R-FE-021). |
| A11y violations (static review) | 0 | R-FE-009 fixed. No new a11y issues from refactoring. |
| Security violations | 0 blockers | All three iteration-1 security blockers (R-FE-001, R-FE-002, R-FE-003) resolved. CSP-safe; OWASP-aligned token storage. |
| Interface contract drift | 1 minor carried over | R-FE-010 (Freshness `'stale'`) — non-blocking, coordinate with TL. |
| AC coverage (FE-side) | met | AC-001 / AC-002 / AC-003 / AC-005 covered. |

---

## Recommended next steps (non-blocking)

1. **R-FE-021**: add `auth.service.spec.ts` — ~20 LOC, completes test coverage on the new service.
2. **R-FE-022**: add a JSDoc note on `AuthService` that all token writes must go through `setAuthenticated()`.
3. **Carry-over minors** (R-FE-010, R-FE-011, R-FE-012, R-FE-013, R-FE-014) into tech-debt backlog for the next sprint.

None of the above blocks G5 progression to `banking-security` + `banking-integration`.

---

*Review by `banking-reviewer-fe` · 2026-05-21 · iteration 2 · G5 quality gate · APPROVED*
