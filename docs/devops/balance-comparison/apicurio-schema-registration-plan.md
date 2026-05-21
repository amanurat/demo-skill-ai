# Apicurio Schema Registration Plan — AuditEventRecorded v2

> **Feature:** balance-comparison
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Owner:** banking-devops
> **Target deadline:** D6 (BLOCKING for D8 BDS deploy — SA-RISK-002)
> **References:** db-schema.md §5, SA ADR-003, handoff-sa-001.json SA-RISK-002, impl-notes §9.2

---

## 1. Schema to Register

**Fully qualified name:** `com.bank.compliance.audit.v2.AuditEventRecorded`

**Apicurio artifact coordinates:**
- Group ID: `com.bank.compliance.audit`
- Artifact ID: `AuditEventRecorded`
- Version: `2`
- Compatibility mode: `BACKWARD`
- Content type: `application/json` (Avro JSON Schema)

**Why BACKWARD compatibility is the correct setting:**

BACKWARD means new consumers using v2 schema can read v1 records produced by the existing transfer-service. The three new fields (`purpose`, `cacheHit`, `accountCount`) all have `"default": null` — Avro fills them with null when deserializing a v1 record that lacks them. This is the standard Avro evolution contract. Selecting FULL or FORWARD would be overly restrictive and unnecessary for this change.

**Avro v2 schema (canonical — db-schema.md §5):**

```json
{
  "type": "record",
  "name": "AuditEventRecorded",
  "namespace": "com.bank.compliance.audit.v2",
  "doc": "Banking audit event envelope. v2 adds 3 optional fields (purpose, cacheHit, accountCount) — BACKWARD-compatible with v1.",
  "fields": [
    { "name": "eventType",     "type": "string",
      "doc": "Discriminator. Existing: TRANSFER_REQUESTED, etc. NEW: BALANCE_INQUIRY." },
    { "name": "actorId",       "type": "string",
      "doc": "UUID of acting customer (from JWT sub)." },
    { "name": "channel",       "type": { "type": "enum", "name": "Channel",
                                         "symbols": ["MOBILE_BANKING", "WEB", "API"] } },
    { "name": "correlationId", "type": "string",
      "doc": "OTel trace ID (lowercase UUID form)." },
    { "name": "timestamp",     "type": "long",
      "doc": "Epoch millis UTC." },
    { "name": "result",        "type": { "type": "enum", "name": "Result",
                                         "symbols": ["SUCCESS", "FAILURE", "FORBIDDEN", "ERROR"] } },
    { "name": "payload",       "type": ["null", { "type": "map", "values": "string" }],
                               "default": null,
      "doc": "Free-form map for legacy v1 producers. v2 producers SHOULD leave null." },
    { "name": "purpose",       "type": ["null", "string"],   "default": null,
      "doc": "NEW v2. e.g. 'balance-inquiry'." },
    { "name": "cacheHit",      "type": ["null", "boolean"],  "default": null,
      "doc": "NEW v2. True if response served from BDS Redis cache." },
    { "name": "accountCount",  "type": ["null", "int"],      "default": null,
      "doc": "NEW v2. Aggregate account count returned (0 for empty state)." }
  ]
}
```

**Critical namespace constraint:** The Java package in `KafkaAuditEventPublisher` (`com.bank.compliance.audit.v2`) MUST exactly match the Avro `namespace` field. A mismatch causes the Avro deserializer at the consumer to reject records at startup. Verified during D7 smoke test.

---

## 2. Registration Command / Script

### 2.1 Prerequisites

- Apicurio REST API reachable: `http://apicurio.apicurio.svc.cluster.local:8080`
- From outside the cluster (for the CI runner or a DevOps workstation): use port-forward or an external Apicurio URL.
- `curl` + `jq` installed on the runner.
- Save the v2 schema JSON to a file: `tools/schema-registry/audit-event-recorded-v2.avsc`

### 2.2 Step 1 — Verify v1 schema exists (baseline)

```bash
curl -s \
  -H "Accept: application/json" \
  "http://apicurio.apicurio.svc.cluster.local:8080/apis/registry/v2/groups/com.bank.compliance.audit/artifacts/AuditEventRecorded" \
  | jq '{id: .id, version: .version, state: .state}'
```

Expected: returns the v1 artifact. If this 404s, the v1 schema was never registered — escalate to the audit-service team immediately.

### 2.3 Step 2 — Set compatibility rule to BACKWARD (if not already set)

```bash
curl -s -X PUT \
  -H "Content-Type: application/json" \
  -d '{"compatibility": "BACKWARD"}' \
  "http://apicurio.apicurio.svc.cluster.local:8080/apis/registry/v2/groups/com.bank.compliance.audit/artifacts/AuditEventRecorded/rules/COMPATIBILITY" \
  | jq .
```

### 2.4 Step 3 — Register v2 schema

```bash
SCHEMA_FILE="tools/schema-registry/audit-event-recorded-v2.avsc"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: AuditEventRecorded" \
  -H "X-Registry-ArtifactType: AVRO" \
  -H "X-Registry-Version: 2" \
  -d @"${SCHEMA_FILE}" \
  "http://apicurio.apicurio.svc.cluster.local:8080/apis/registry/v2/groups/com.bank.compliance.audit/artifacts?ifExists=UPDATE")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -1)

echo "HTTP: $HTTP_CODE"
echo "Response: $BODY"

if [[ "$HTTP_CODE" != "200" && "$HTTP_CODE" != "201" ]]; then
  echo "ERROR: Schema registration failed (HTTP $HTTP_CODE). Check Apicurio logs."
  echo "Compatibility violation? Response: $BODY"
  exit 1
fi

echo "Schema v2 registered successfully."
```

### 2.5 Step 4 — Confirm registration

```bash
curl -s \
  -H "Accept: application/json" \
  "http://apicurio.apicurio.svc.cluster.local:8080/apis/registry/v2/groups/com.bank.compliance.audit/artifacts/AuditEventRecorded/versions" \
  | jq '[.versions[] | {version: .version, state: .state, createdOn: .createdOn}]'
```

Expected: output includes both version `1` and version `2` with state `ENABLED`.

---

## 3. Verification — Backward Compatibility Smoke Test (D7)

Run this on D7 (day after registration, day before BDS deploy on D8).

### 3.1 Purpose

Prove that:
- A **v1 producer** (transfer-service, unchanged) can still emit to `audit.event-recorded`
- A **v2 consumer** (audit-service with v2 schema) can deserialize v1 records
- A **v2 producer** (BDS, new) can emit records that a v1 consumer can still deserialize

### 3.2 Test procedure (Testcontainers-based — runs on staging or CI)

```bash
# Start Apicurio + Kafka via docker-compose (or use staging cluster)
# Produce a v1-format record (no purpose/cacheHit/accountCount fields)
# Consume with v2 schema — assert: purpose=null, cacheHit=null, accountCount=null

# Produce a v2-format record (with all three new fields populated)
# Consume with v1 schema (audit-service current consumer, schema from registry)
# Assert: consumer reads existing fields correctly; new fields ignored gracefully
```

The integration test class to wire this is:

```
backend/balance-dashboard-service/src/test/java/com/bank/balancedashboard/
  infrastructure/audit/KafkaAuditEventPublisherContractTest.java
```

This test byte-greps the serialized Avro record for forbidden field keys (Security C-2). Backward compatibility is implicitly verified: if the v2 schema were not backward-compatible, the Avro serializer would reject during test setup.

### 3.3 Pass criteria

- [ ] Apicurio returns `200 OK` with both versions (v1 + v2) listed
- [ ] Compatibility rule is `BACKWARD`
- [ ] v1 record produced by transfer-service deserialized by v2 consumer without error
- [ ] v2 record produced by BDS deserialized by v1 consumer without error (v2 fields ignored)
- [ ] `KafkaAuditEventPublisherContractTest` passes (no forbidden field keys in serialized bytes)

---

## 4. Blocker Note and Escalation Path

**SA-RISK-002 (medium severity):** If v2 schema is NOT registered in Apicurio by D6, the BDS deploy on D8 will fail. The `KafkaAuditEventPublisher` uses an Avro serializer that contacts the Apicurio registry at startup to resolve the schema ID. If the schema is absent or the registry is unreachable, the Kafka producer fails to initialize and the Spring context fails to start. The pod enters `CrashLoopBackOff`.

**Escalation rule:**
- If Apicurio registration is at risk by D5, escalate immediately to PM (`banking-pm`).
- PM should assess whether D8 BDS deploy needs to slip to D9 (one-day buffer).
- Do NOT deploy BDS without v2 schema registered and D7 smoke test passed.

**Track in PM risk register:** RISK entry for SA-RISK-002 must be updated on D6 (registered) or escalated (slipped).

**Fallback (emergency only):** If Apicurio is unavailable in the target environment, configure BDS to use `auto.register.schemas=true` on the Kafka producer as a temporary measure (registers the schema automatically on first produce). This should NOT be the normal path — it bypasses the governance workflow. Revert to explicit registration once Apicurio is stabilized.
