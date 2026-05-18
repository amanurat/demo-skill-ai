# Angular Banking UI — Anti-Patterns

Reference loaded on demand by `angular-banking-ui` skill. Catalog of frontend patterns to flag in code review and avoid in implementation.

## Severity Legend
- **blocker** — must fix before merge
- **major** — should fix; OK to merge if tracked with follow-up
- **minor** — improve; can defer
- **nit** — style only

## Security & Money

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| **JWT in `localStorage`** | blocker | XSS-readable; tokens forgeable on compromise; persists across tabs forever | httpOnly cookie (preferred) or in-memory with refresh flow |
| **Optimistic UI for money operations** | blocker | User sees "success" before server confirms; on failure leads to double-submit / disputes / wrong balance shown | Wait for server 2xx; show pending state during round-trip |
| Calling `bypassSecurityTrust*` without explicit security review | major | Disables Angular's XSS auto-escape; arbitrary HTML/JS injection risk | Sanitize at source; render via standard binding |

## TypeScript & API

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| `any` in TypeScript | major | Defeats strict-mode safety; silent runtime errors; bug magnet | Use `unknown` + narrow, or generate proper types from OpenAPI |
| Manual `HttpClient` calls bypassing the generated client | major | DTO drift from server; types lie; refactors miss endpoints | Use generated client from OpenAPI; regenerate on contract change |

## Forms

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| Mixing template-driven and reactive forms in the same feature | major | Two mental models; inconsistent validation lifecycle; hard to test | Pick one — **reactive** for banking; refactor stragglers |

## Accessibility

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| Clickable `<div>` / `<span>` as buttons | major | Not keyboard-focusable; no role; screen readers skip; WCAG fail | Use `<button type="button">` |
| Skipping a11y "to ship faster" | major | Regulatory + reputational risk; refactor cost grows; locks out users | a11y is a release gate, not a polish task |

## Styling & UX

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| Inline styles / inline event handlers in templates | minor | Bypass CSP; no theming; harder to test | Use component styles + (event) bindings |
| Hardcoded user-facing strings | minor | Breaks i18n; can't ship to TH locale; QA pain | Use i18n keys (TH + EN both populated) |
| Forgetting loading + error states for async UI | minor | Looks broken; users double-click; support tickets | Every async state: idle / loading / success / error |

## When to Use This Reference

- Reviewing a frontend PR — scan for these patterns
- Onboarding a new Angular dev — assigned reading
- Refactoring a legacy feature — prioritize blockers (JWT storage, optimistic money UI) first
