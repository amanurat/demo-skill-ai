# Quality Gates per Phase

The Player enforces these gates **before** allowing an artifact to move forward. Failed gates trigger feedback loops per [workflow.md](workflow.md).

## Gate Matrix

| Phase Transition | Gate | Pass Criteria |
|---|---|---|
| Discovery → Planning | **Requirement Completeness** | ≥ 90% of asks have AC; no ambiguous terms ("etc.", "and so on") |
| Planning → Design | **Architecture Soundness** | All NFRs traced to a design decision; ≥ 1 ADR per major choice |
| Design → Development | **Contract Validated** | OpenAPI spec lints clean; DB schema reviewed; threat model done |
| Development → Review | **Code Health** | Unit coverage ≥ 80%; lint passes; build green; OpenAPI in sync |
| Review → Security | **Best-Practice Compliance** | No blocker/major review comments unresolved; SOLID upheld |
| Security → QA | **Vulnerability Floor** | SAST passes; no critical/high CVEs; no secrets in code |
| QA → DevOps | **Test Coverage + SLA** | All test suites green; perf SLA met; critical-path coverage ≥ 95% |
| DevOps → Done | **Deployable + Observable** | Deploy succeeds; smoke tests pass; dashboards live; rollback rehearsed |

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
