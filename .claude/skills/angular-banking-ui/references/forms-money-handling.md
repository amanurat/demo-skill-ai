# Angular Forms — Money Handling

Reference loaded on demand by `angular-banking-ui` skill. Patterns for reactive forms in banking flows (transfer, payment, top-up).

## Form Choice

- **Always Reactive Forms** for banking — strong types, predictable validation, testable
- **Never mix** template-driven + reactive in the same feature
- Use typed forms: `FormGroup<{ amount: FormControl<number>; ... }>`

## Required Validators (per field)

| Field | Validators |
|---|---|
| `amount` | `Validators.required`, `Validators.min(0.01)`, custom `maxDailyLimit($limit)`, decimal-places (e.g., 2) |
| `accountNumber` (source / dest) | `Validators.required`, format regex (length + checksum), `notSameAsSource` cross-field |
| `currency` | `Validators.required`, enum allow-list |
| `memo` / `note` | `Validators.maxLength(140)`, sanitize on blur |

### Sketch — money validator

```ts
export function moneyValidator(maxScale = 2): ValidatorFn {
  return (c) => {
    const v = c.value;
    if (v == null || v === '') return null;
    const n = Number(v);
    if (!Number.isFinite(n) || n <= 0) return { money: 'positive' };
    const [, frac = ''] = String(v).split('.');
    return frac.length > maxScale ? { money: 'scale' } : null;
  };
}
```

### Sketch — cross-field "not same account"

```ts
export const notSameAccount: ValidatorFn = (g) => {
  const from = g.get('fromAccount')?.value;
  const to = g.get('toAccount')?.value;
  return from && to && from === to ? { sameAccount: true } : null;
};
```

## Idempotency-Key

- Generate **UUID v4 client-side** when the form opens — not on submit
- Store in form state (e.g., `this.idemKey = crypto.randomUUID()`)
- Send via interceptor as `Idempotency-Key: <uuid>` on POST/PUT
- **Regenerate only after success** (or explicit user "start over")
- Server-side cache TTL ~24h — same key + body = same response

## Optimistic UI — Forbidden for Money

- Money operations must **wait for server 2xx** before showing "success"
- Show progress / pending state during the round-trip; disable submit
- On timeout: do **not** assume failure — show "uncertain" UX and offer "check status" using the same Idempotency-Key

## Confirmation Step (irreversible actions)

Required for: transfer, payment, beneficiary add, limit change.

Pattern: **two-step form**
1. Step 1 — fill + validate inline
2. Step 2 — read-only review screen with full breakdown (amount, fee, ETA, dest)
3. Explicit "Confirm" button (not just Enter)
4. Optional re-auth (PIN / OTP) for high-value transfers

## Input Masking & Sensitive Fields

- **Account numbers** — mask after blur (`****1234`) in display; clipboard sanitized on copy
- **Amount** — locale-aware thousands separator on display; raw number in form value
- **PIN / OTP** — `inputmode="numeric"`, `autocomplete="one-time-code"` for OTP, never logged

## Error UX Mapping

Map server `Problem-Detail` (RFC 7807) → human messages via i18n keys.

| Server code | UX surface | Severity |
|---|---|---|
| `INSUFFICIENT_FUNDS` | Inline under amount | validation-like |
| `DAILY_LIMIT_EXCEEDED` | Banner above form | business |
| `BENEFICIARY_BLOCKED` | Inline under dest account | business |
| `IDEMPOTENCY_CONFLICT` | Modal with "view original result" | business |
| `SERVICE_UNAVAILABLE` | Retry banner with backoff | system |

**Never** show generic "Something went wrong" — always map to a specific i18n key with actionable guidance.

## Distinguishing Error Classes

- **Validation** (`form.invalid`) → inline, next to field, red border + aria-describedby
- **Business** (server says "no") → alert / modal, full context
- **System** (5xx, network) → retry banner; keep form state intact

## Testing Checklist (forms)

- [ ] Each validator has a unit test (valid + invalid)
- [ ] Cross-field validators tested with both fields populated
- [ ] Submit disabled while pending
- [ ] Submit re-enabled on error; form state preserved
- [ ] Idempotency-Key not regenerated on retry
- [ ] Confirmation step cannot be skipped via direct URL / state manipulation
