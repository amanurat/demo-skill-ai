# Quality Gates per Phase

The Player enforces these gates **before** allowing an artifact to move forward. Failed gates trigger feedback loops per [workflow.md](workflow.md).

## Gate Matrix

| # | Phase Transition | Gate | Owner Agent | Pass Criteria |
|---|---|---|---|---|
| G1 | Discovery → Planning | **Requirement Completeness** | `banking-ba` | ≥ 90% of asks have AC; no ambiguous terms ("etc.", "and so on"); NFRs are measurable |
| G2 | Planning → Design | **Architecture Soundness** | `banking-solution-architect` | All SUBDEC resolved; ≥ 1 ADR per major choice; no shared-DB anti-pattern |
| G3 | Design → Implementation Planning | **Contract Validated** | `banking-tech-lead` | OpenAPI lints clean; DB schema reviewed (UUID PK, NUMERIC money); threat model done |
| G4 | Implementation Planning → Coding | **Implementation Plan Complete** | `banking-implementation-planner` | Every AC traces to ≥ 1 BE or FE test case; all 6 BE layers + 5 FE steps identified; interface contracts locked; security conditions traced to specific classes |
| G5 | Coding → Review | **Code Health** | `banking-backend-dev` / `banking-frontend-dev` | Build GREEN; lint passes; unit coverage ≥ 80%; critical paths ≥ 95%; OpenAPI in sync with code |
| G6 | Review → Integration+Security | **Best-Practice Compliance** | `banking-reviewer-be` + `banking-reviewer-fe` | Zero blocker findings unresolved; major findings addressed or risk-accepted with owner; both reviewers must approve |
| G7 | Integration → QA+Docs | **Contract Integrity** | `banking-integration` | 100% contract tests pass; zero OpenAPI ↔ BE drift; zero OpenAPI ↔ FE drift; smoke test passes (or staging unavailable note) |
| G8 | Security → QA+Docs | **Vulnerability Floor** | `banking-security` | SAST passes; no critical/high CVEs; no secrets in code; no PII in logs; STRIDE complete per endpoint |
| G9 | QA+Docs → DevOps | **Test Coverage + SLA + Docs** | `banking-qa` + `banking-docs` | All test suites green; perf SLA met (p95 warm <500ms, cold <800ms); mutation ≥ 70% on money paths; API reference generated; CHANGELOG updated |
| G10 | DevOps → Done | **Deployable + Observable** | `banking-devops` | Staging deploy succeeds; smoke tests pass; dashboards live; rollback rehearsed |

> **Note on G7 + G8:** Both `banking-integration` and `banking-security` run **in parallel** after G6 passes. Both must pass before entering QA+Docs phase.

## Banking-Specific Hard Gates

These cannot be bypassed regardless of iteration count:

1. **No PII / card data in logs** — auto-fail at any stage if detected
2. **All financial endpoints accept `Idempotency-Key`** — fail at Tech Lead → Dev
3. **Audit trail emitted** for every state-changing operation — fail at QA
4. **No hardcoded secrets** — fail at Security
5. **Reversible migrations** — fail at DevOps if `DROP TABLE` without backup plan

## Gate Decision Output

When a gate runs, Player emits:

```json
{
  "gate": "code-health",
  "result": "pass | fail",
  "metrics": { "coverage": 0.82, "lint_errors": 0, "build": "success" },
  "violations": []
}
```

On `fail` → enter feedback loop with the failing agent.
