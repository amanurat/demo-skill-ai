# Handoff Artifact Schema

Every inter-agent communication MUST be a single JSON object conforming to the schema below. The Player (orchestrator) validates the envelope; receiving agents validate `payload`.

## Envelope Schema

```json
{
  "artifact_id": "uuid-v4",
  "from_agent": "banking-ba",
  "to_agent": "banking-solution-architect",
  "phase": "DISCOVERY | PLANNING | DESIGN | DEVELOPMENT | REVIEW | SECURITY | TESTING | DEPLOYMENT",
  "feature": "money-transfer",
  "payload": { /* role-specific, see per-agent schemas below */ },
  "metadata": {
    "version": "1.0",
    "timestamp": "2026-05-18T10:30:00Z",
    "quality_gate_passed": true,
    "iteration": 1,
    "previous_artifact_id": "uuid-or-null",
    "notes": "Optional free text"
  }
}
```

## Field Rules

| Field | Required | Rules |
|---|---|---|
| `artifact_id` | yes | UUID v4 |
| `from_agent` | yes | Must match a registered `banking-*` agent |
| `to_agent` | yes | Same as above (or `banking-player` when returning to orchestrator) |
| `phase` | yes | One of the enum values |
| `feature` | yes | Kebab-case feature slug (e.g., `money-transfer`) |
| `payload` | yes | Object — schema depends on `from_agent` |
| `metadata.quality_gate_passed` | yes | If `false`, Player must route to feedback loop |
| `metadata.iteration` | yes | Increments on each retry; max 3 before human escalation |

## Per-Agent `payload` Schemas

### BA → Architect

```json
{
  "user_stories": [
    {
      "id": "US-001",
      "title": "Transfer money to another account",
      "as_a": "authenticated customer",
      "i_want": "to transfer money to a payee account",
      "so_that": "I can pay bills and friends",
      "acceptance_criteria": [
        "Given sufficient balance, transfer succeeds within 5s",
        "Given insufficient balance, transfer is rejected with code 402",
        "Given duplicate Idempotency-Key, second request returns the first result"
      ],
      "priority": "MUST"
    }
  ],
  "process_flows": [
    { "name": "Happy path transfer", "steps": ["..."] }
  ],
  "non_functional": {
    "performance": "p95 < 1s, p99 < 3s",
    "availability": "99.95%",
    "compliance": ["PCI-DSS", "GDPR"]
  },
  "out_of_scope": ["International FX", "Scheduled recurring transfer (v2)"]
}
```

### Solution Architect → Tech Lead

```json
{
  "services": [
    { "name": "transfer-service", "responsibility": "...", "owner": "..." }
  ],
  "events": [
    {
      "name": "TransferRequested",
      "producer": "transfer-service",
      "consumers": ["audit-service", "notification-service"],
      "schema_ref": "avro://transfer.v1.TransferRequested"
    }
  ],
  "decisions": [
    { "id": "ADR-001", "title": "Saga orchestration vs choreography", "decision": "Orchestration", "rationale": "..." }
  ]
}
```

### Tech Lead → Dev

```json
{
  "openapi_spec_path": "backend/transfer-service/api/openapi.yaml",
  "db_schema": {
    "migration_files": ["V001__create_transfers.sql"],
    "tables": ["transfers", "transfer_idempotency"]
  },
  "adrs": ["ADR-001", "ADR-002"],
  "implementation_notes": "Use outbox pattern for TransferCompleted event"
}
```

### Dev → Reviewer

```json
{
  "service": "transfer-service",
  "files_changed": ["src/main/java/..."],
  "tests": { "unit_coverage": 0.85, "integration_added": true },
  "openapi_updated": true,
  "build_status": "success",
  "self_checks_passed": true
}
```

### Reviewer → Dev (feedback) or → Security (pass)

```json
{
  "verdict": "approved | changes_requested",
  "comments": [
    {
      "file": "src/main/java/.../TransferService.java",
      "line": 42,
      "severity": "blocker | major | minor | nit",
      "message": "Anemic domain model — move balance check into Account entity",
      "rule": "anti-pattern: anemic-domain"
    }
  ]
}
```

### Security → Dev (feedback) or → QA (pass)

```json
{
  "verdict": "approved | changes_requested",
  "findings": [
    {
      "severity": "critical | high | medium | low",
      "owasp": "A02-Crypto-Failures",
      "description": "JWT signed with HS256 — should use RS256 with rotated keys",
      "remediation": "..."
    }
  ],
  "sast_passed": true,
  "secrets_scan_passed": true,
  "compliance_check": { "pci_dss": "pass", "gdpr": "pass" }
}
```

### QA → DevOps

```json
{
  "test_plan_path": "docs/test-plans/money-transfer.md",
  "results": {
    "unit": { "passed": 120, "failed": 0, "coverage": 0.87 },
    "integration": { "passed": 25, "failed": 0 },
    "e2e": { "passed": 8, "failed": 0 },
    "performance": { "p95_ms": 420, "p99_ms": 850, "sla_met": true }
  }
}
```

### DevOps → Player (done)

```json
{
  "deployment": {
    "environment": "staging",
    "image_tag": "transfer-service:1.0.0",
    "url": "https://transfer.staging.example.com",
    "smoke_tests": "passed",
    "dashboards": ["https://grafana.../d/transfer-service"]
  },
  "dod_checklist": { /* see definition-of-done.md */ }
}
```

## Validation Rules

1. **No extra fields** in envelope (strict)
2. **`iteration` must increment** when re-emitted after feedback
3. **`previous_artifact_id`** required when `iteration > 1`
4. **`quality_gate_passed: false`** → Player MUST send back to source (not forward)

## Example: Full BA Output

See [workflow.md § E2E walkthrough](workflow.md#end-to-end-walkthrough-money-transfer)
