# Agent Communication Sequence — Money Transfer v1

Mermaid sequence diagram showing every inter-agent handoff for the Money Transfer SDLC run.
Render this in any Mermaid-compatible viewer (GitHub, VS Code extension, mermaid.live).

```mermaid
sequenceDiagram
    autonumber
    actor PO as Product Owner
    participant P  as Player (Orchestrator)
    participant BA as banking-ba
    participant SA as banking-solution-architect
    participant TL as banking-tech-lead
    participant BD as banking-backend-dev
    participant RV as banking-reviewer
    participant SC as banking-security
    participant QA as banking-qa
    participant DO as banking-devops

    PO->>P: "Build Money Transfer feature"
    note over P: Read CLAUDE.md<br/>Plan 8-phase chain

    rect rgb(230, 245, 255)
        note over P,BA: Phase S2 — DISCOVERY
        P->>BA: Delegate: analyze raw requirement
        activate BA
        BA->>BA: Decompose stories, map entities,<br/>quantify NFRs, flag OQs
        BA-->>P: Handoff artifact S2<br/>(11 stories · 43 AC · 6 entities · 8 OQs)
        deactivate BA
        P->>P: Validate quality gate ✅<br/>Requirement Completeness PASSED
    end

    rect rgb(230, 255, 230)
        note over P,SA: Phase S3 — PLANNING
        P->>SA: Delegate: design system architecture
        activate SA
        SA->>SA: Service decomposition, event flows,<br/>C4 diagram, 12 ADRs, risk register
        SA-->>P: Handoff artifact S3<br/>(9 services · 14 events · 12 ADRs · 6 risks)
        deactivate SA
        P->>P: Validate quality gate ✅<br/>Architecture Soundness PASSED
    end

    rect rgb(255, 250, 220)
        note over P,TL: Phase S4 — DESIGN
        P->>TL: Delegate: produce API contract + DB schema
        activate TL
        TL->>TL: Write OpenAPI 3.x spec,<br/>5 Flyway migrations, 3 Avro schemas,<br/>STRIDE walk-through, 4 new ADRs
        TL-->>P: Handoff artifact S4<br/>(22KB OpenAPI · 5 migrations · ADR-013..016)
        deactivate TL
        P->>P: Validate quality gate ✅<br/>Contract Validated PASSED
    end

    rect rgb(255, 235, 220)
        note over P,BD: Phase S5 — DEVELOPMENT
        P->>BD: Delegate: implement US-001 + US-003
        activate BD
        BD->>BD: Read skills: spring-boot-banking,<br/>banking-security-patterns
        BD->>BD: Implement hexagonal layout<br/>(domain → application → infra → interfaces)
        BD->>BD: Write 54 Java files:<br/>Money VO, Transfer entity, Saga,<br/>Outbox, Idempotency, Controller
        BD->>BD: Run mvn test → 40/40 pass<br/>Coverage: Money 98%, Transfer 95.2%
        BD-->>P: Handoff artifact S5<br/>(40/40 unit tests · cov 0.85 · 6 IT compile-clean)
        deactivate BD
        P->>P: Validate quality gate ✅<br/>Code Health PASSED
    end

    rect rgb(245, 230, 255)
        note over P,RV: Phase S6 — REVIEW
        P->>RV: Delegate: review all 54 files
        activate RV
        RV->>RV: Read skill: code-review-checklists
        RV->>RV: Check anti-patterns, layering,<br/>money handling, idempotency, security posture
        RV-->>P: Handoff artifact S6<br/>(0 blocker · 5 major · 13 minor · APPROVED)
        deactivate RV
        P->>P: Validate quality gate ✅<br/>Best-Practice Compliance PASSED
    end

    rect rgb(255, 220, 220)
        note over P,SC: Phase S7 — SECURITY
        P->>SC: Delegate: security review + compliance
        activate SC
        SC->>SC: Read skill: banking-security-patterns
        SC->>SC: STRIDE per endpoint,<br/>OWASP Top 10 table,<br/>PCI-DSS + PDPA + GDPR check
        SC-->>P: Handoff artifact S7<br/>(0 critical · 0 high · 3 medium · 7 low · APPROVED)
        deactivate SC
        P->>P: Validate quality gate ⚠️<br/>Vulnerability Floor PASSED-WITH-CONDITIONS
    end

    rect rgb(220, 255, 245)
        note over P,QA: Phase S8 — TESTING
        P->>QA: Delegate: test plan + execution
        activate QA
        QA->>QA: Read skill: banking-test-automation
        QA->>QA: Run actual: mvn test<br/>Verify JaCoCo thresholds
        QA->>QA: Write integration tests (Testcontainers)
        QA-->>BD: BUG-QA-001 (idempotency REJECTED replay)<br/>BUG-QA-002 (TTL expiry path)
        QA-->>P: Handoff artifact S8<br/>(40/40 pass · 95%+ coverage · 6 IT compile-clean)
        deactivate QA
        P->>P: Validate quality gate ⚠️<br/>Test Coverage + SLA PASSED-WITH-CONDITIONS
    end

    rect rgb(220, 235, 255)
        note over P,DO: Phase S9 — DEPLOYMENT
        P->>DO: Delegate: CI/CD + containerisation
        activate DO
        DO->>DO: Read skill: banking-devops-platform
        DO->>DO: Write multi-stage Dockerfile,<br/>9-template Helm chart,<br/>10-panel Grafana dashboard,<br/>6 Prometheus alert rules,<br/>11-stage GitHub Actions pipeline
        DO->>DO: docker build + smoke test<br/>liveness/readiness/prometheus → 200 ✅
        DO-->>P: Handoff artifact S9<br/>(23 infra files · HTTP 200 · schema fixes applied)
        deactivate DO
        P->>P: Validate quality gate ✅<br/>Deployable + Observable PASSED (v1 scaffold)
    end

    P-->>PO: v1 Scaffold COMPLETE ✅<br/>40/40 tests · 0 sec critical/high · Docker HTTP 200<br/>Staging gated on 5 backend hardening items (US-006)
```

---

## Summary Statistics

| Metric | Value |
|---|---|
| Total handoffs | 8 |
| Feedback loops triggered | 0 (all gates passed on iteration 1) |
| Quality gate results | 6 × ✅ pass + 2 × ⚠️ pass-with-conditions |
| Bugs filed by QA back to Dev | 2 (BUG-QA-001, BUG-QA-002) |
| Schema fixes applied by DevOps | 7 (Flyway V001/V002/V004) |

## How to Render

**GitHub / GitLab:** This file renders automatically in the browser.

**VS Code:** Install the *Markdown Preview Mermaid Support* extension, then open preview (`Cmd+Shift+V`).

**Online:** Paste the diagram block at [mermaid.live](https://mermaid.live).
