# Angular API Integration, Accessibility & i18n

Reference loaded on demand by `angular-banking-ui` skill. HttpClient wiring, interceptors, error UX, a11y, and i18n consolidated.

## HttpClient + Interceptors

Use `provideHttpClient(withInterceptors([...]))`. Order matters — auth first, idempotency last (after body is final).

### Required Interceptors

| Interceptor | Responsibility |
|---|---|
| **AuthInterceptor** | Attach `Authorization: Bearer <jwt>`; refresh on 401 (single-flight) |
| **CorrelationIdInterceptor** | Add `X-Request-Id: <uuid>` per request for traceability |
| **IdempotencyKeyInterceptor** | On POST/PUT to financial endpoints, attach `Idempotency-Key` (from form state) |
| **ErrorMappingInterceptor** | Map server `Problem-Detail` (RFC 7807) → typed app error + log |
| **LoadingInterceptor** (optional) | Increment/decrement global loading counter for UX |

### Sketch — IdempotencyKey interceptor

```ts
export const idempotencyKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const isFinancialWrite =
    ['POST', 'PUT'].includes(req.method) &&
    /\/api\/v\d+\/(transfers|payments|topups)/.test(req.url);
  if (!isFinancialWrite) return next(req);
  const key = req.context.get(IDEM_KEY) ?? crypto.randomUUID();
  return next(req.clone({ setHeaders: { 'Idempotency-Key': key } }));
};
```

## Retry Policy

- Retry **only idempotent calls** (GET, or POST/PUT with `Idempotency-Key`)
- 3 attempts, exponential backoff with jitter (e.g., 200ms, 600ms, 1.5s)
- Skip retry on 4xx (except 408, 429); retry on 5xx + network errors
- Use `rxjs.retry({ count, delay })` or a resilience helper

## Typed API Client

- Generate from OpenAPI on build (`openapi-generator-cli` or `openapi-typescript-codegen`)
- Committed under `libs/data-access-<service>/api/`
- **Never** hand-roll DTOs — drift from server is the #1 source of frontend bugs
- Manual `HttpClient.get/post` bypassing the generated client is an anti-pattern

## Error UX (handler layer)

After interceptors map to typed errors, surface them per the matrix in [forms-money-handling.md](forms-money-handling.md#error-ux-mapping). Three rules:

1. **Specific over generic** — show what failed and what user can do
2. **Preserve user input** — never clear the form on system error
3. **Log with correlation ID** — paired with server logs for triage

---

## Accessibility (WCAG 2.1 AA)

### Semantic HTML

- `<button>` for actions; **never** clickable `<div>` / `<span>` for buttons
- `<a>` for navigation only; `[routerLink]` not `(click)="router.navigate(...)"`
- Form fields wrapped in `<label>` with `for=` linking
- Tables use `<th scope="col|row">`; not `<div class="row">`

### ARIA

- `aria-label` for icon-only buttons
- `aria-describedby` linking error message to input
- `aria-live="polite"` for async status updates; `assertive` only for blocking errors
- `role="alert"` on critical banners
- `role="dialog"` + `aria-modal="true"` + focus trap on modals

### Focus Management

- On dialog open → focus first interactive element; on close → return focus to trigger
- Skip-to-content link on every page
- Visible focus ring (don't `outline: none` without replacement)
- Tab order matches visual order

### Color & Contrast

- Text contrast ≥ 4.5:1 (large text ≥ 3:1)
- Don't convey state by color alone — pair with icon + label
- Test in high-contrast mode (Windows / macOS)

### Keyboard

- Every interactive element reachable + operable by keyboard
- `Enter` submits forms; `Esc` closes modals
- No keyboard traps

### Automated Checks

- `axe-core` integrated into unit tests for components
- `@axe-core/playwright` (or Cypress) in E2E
- Lighthouse a11y = 100 enforced in CI

### Manual Checks (per PR)

- [ ] Tab through the whole flow with keyboard only
- [ ] Test with VoiceOver / NVDA on critical flows
- [ ] Toggle high-contrast / 200% zoom

---

## Internationalization (i18n)

- **All user-facing strings** via i18n keys — never hardcoded
- Support **Thai + English** at minimum (project default)
- Use Angular built-in `@angular/localize` or `ngx-translate` (pick one, document choice)
- Locale-aware formatting: `DatePipe`, `CurrencyPipe`, `DecimalPipe`
- **Money formatting**: respect locale (TH uses `฿1,234.56`, EN `THB 1,234.56`)
- Right-to-left support not required for TH/EN but keep CSS LTR-safe
- Translator-friendly keys: `transfer.form.amount.error.insufficient` not `error1`
- Pluralization via ICU MessageFormat — don't string-concat counts

### i18n Pre-Handoff

- [ ] No raw English/Thai strings in templates or TS (lint rule)
- [ ] All keys present in both `th.json` and `en.json`
- [ ] No missing-key warnings in console
