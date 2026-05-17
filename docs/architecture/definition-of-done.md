# Definition of Done (DoD)

A feature is "Done" only when **all** boxes below are checked. The Player verifies this list before reporting completion.

## Universal Checklist

### Code
- [ ] All code merged to main branch with green CI
- [ ] No TODO/FIXME comments in production paths
- [ ] OpenAPI spec(s) updated and published
- [ ] CHANGELOG entry added

### Tests
- [ ] Unit test coverage ≥ 80% (≥ 95% for critical financial paths)
- [ ] Integration tests pass (with real Postgres + Kafka via Testcontainers)
- [ ] Contract tests pass (Pact / Spring Cloud Contract)
- [ ] E2E happy path automated
- [ ] Performance baseline established (p95, p99 within SLA)

### Security
- [ ] SAST scan: no new high/critical
- [ ] DAST scan: no high/critical (against staging)
- [ ] SCA scan: no high/critical CVEs
- [ ] Secrets scan: clean
- [ ] Threat model reviewed (for new features)
- [ ] OWASP Top 10 reviewed

### Banking-Specific
- [ ] Audit trail recorded for every state-changing op
- [ ] Idempotency keys honored on financial endpoints
- [ ] PII handling reviewed (encryption at rest for sensitive cols)
- [ ] PCI-DSS scope unchanged or expansion approved
- [ ] GDPR data-subject rights (export / erasure) supported

### Observability
- [ ] Structured logs with correlation IDs
- [ ] Metrics (RED — Rate / Errors / Duration) exposed
- [ ] Distributed tracing spans added
- [ ] Health endpoints (`/actuator/health`) wired
- [ ] Grafana dashboard updated
- [ ] Alert rules defined (latency, error rate, saturation)

### Deployment
- [ ] Deployed to staging — smoke tests pass
- [ ] Rollback plan documented and rehearsed
- [ ] Feature flag in place (if risky)
- [ ] DB migrations versioned + reversible
- [ ] Load test executed against staging

### Documentation
- [ ] ADRs filed for non-trivial decisions
- [ ] Runbook updated for on-call
- [ ] User-facing docs / release notes updated

### Approvals
- [ ] `banking-reviewer` approved
- [ ] `banking-security` approved
- [ ] `banking-qa` signed off
- [ ] Product owner (human) approved release

## Quick-Reference: Per-Agent DoD Subset

| Agent | Their own DoD subset |
|---|---|
| BA | Stories have AC, NFRs listed, scope edges marked |
| Solution Architect | Service map drawn, events specified, ADRs ≥ 1 per major choice |
| Tech Lead | OpenAPI lints clean, DB schema reviewed, threat model documented |
| Backend Dev | Build green, coverage met, OpenAPI in sync, no anti-patterns |
| Frontend Dev | a11y AA, responsive, tests green, no console errors |
| Reviewer | All major/blocker comments resolved |
| Security | SAST/DAST/SCA clean, secrets clean, threat model addressed |
| QA | All suites green, SLA met, critical-path coverage ≥ 95% |
| DevOps | Deploy + smoke pass, dashboards + alerts live, rollback ready |
