---
name: banking-security-patterns
description: Banking AppSec + compliance patterns — STRIDE threat modeling, OWASP Top 10 banking lens, banking hard rules (auto-fail), PCI-DSS / GDPR / PDPA / BoT compliance. Use when threat-modeling endpoints, reviewing for security findings, or assessing compliance scope.
---

# Banking Security Patterns — AppSec & Compliance Skill

Reusable security and compliance patterns for banking / fintech systems. Loaded by `banking-security`, `banking-reviewer`, and `banking-backend-dev` agents.

## When to Use

- Threat-modeling a new endpoint (STRIDE)
- Reviewing a PR for security findings (OWASP Top 10 banking lens)
- Assessing whether a change brings code into PCI-DSS / GDPR / PDPA scope
- Checking a change against banking auto-fail hard rules
- Triaging severity of a finding and deciding on verdict (`approved` / `changes_requested`)
- Producing an audit-ready security review

## Quick Reference

| Need | Where to Look |
|---|---|
| STRIDE threat model per endpoint (Spoofing → Elevation) | [references/stride-threat-model.md](references/stride-threat-model.md) |
| OWASP Top 10 (2021) with banking-specific checks | [references/owasp-top10-banking.md](references/owasp-top10-banking.md) |
| Banking hard rules (auto-fail) with "why" + "how to detect" | [references/banking-hard-rules.md](references/banking-hard-rules.md) |
| PCI-DSS, GDPR, PDPA, BoT, AML compliance checklists | [references/pci-gdpr-compliance.md](references/pci-gdpr-compliance.md) |

---

## Banking Hard Rules (inline — auto-fail summary)

These flip a review verdict to `changes_requested` immediately. See [references/banking-hard-rules.md](references/banking-hard-rules.md) for "why" and "how to detect" per rule.

- Secret hardcoded or in version control
- PII / card number in logs (even debug)
- JWT in `localStorage`
- Plaintext password in storage
- Money in `float` / `double`
- Missing audit event for state-changing op
- Missing idempotency on financial POST/PUT
- HS256 JWT in prod
- TLS < 1.2 anywhere
- Unauthenticated endpoint exposing customer data
- Mass-assignment vulnerability (e.g., `bind(*)` to entity)

---

## Severity → Action Decision Matrix (inline)

| Severity Found | Action |
|---|---|
| Critical or High | `changes_requested`, return immediately |
| Medium | `changes_requested` unless mitigations exist + filed risk |
| Low / Info | Note in comments, can approve |
| Structural issue (arch-level) | Loop in `banking-solution-architect` |

---

## Security-Specific Anti-Patterns (inline short list)

- Security-by-obscurity
- Custom crypto
- Rolling own JWT validation (use Spring Security)
- Same secret across environments
- "Will fix later" on critical/high
- Logging on error path that includes raw input
- Missing rate limit on auth endpoints (brute force)

---

## Reference Index

- [stride-threat-model.md](references/stride-threat-model.md) — STRIDE categories with banking examples and when-to-apply notes
- [owasp-top10-banking.md](references/owasp-top10-banking.md) — OWASP Top 10 (2021) with concrete banking code examples / mitigations
- [banking-hard-rules.md](references/banking-hard-rules.md) — 11 auto-fail rules expanded with rationale + detection guidance
- [pci-gdpr-compliance.md](references/pci-gdpr-compliance.md) — PCI-DSS, GDPR, PDPA, BoT, AML consolidated checklist

---

## How This Skill is Loaded

This skill is referenced (not auto-injected) by:
- [`.claude/agents/banking-security.md`](../../agents/banking-security.md) — Read on every security review
- [`.claude/agents/banking-reviewer.md`](../../agents/banking-reviewer.md) — Read when reviewing code for security smells
- [`.claude/agents/banking-backend-dev.md`](../../agents/banking-backend-dev.md) — Read before implementing auth, money, or PII-touching code

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before starting work. The agent persona instructs them to do so.
