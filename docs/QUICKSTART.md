# Quickstart — 5-Minute Demo

> Run the AI-generated Money Transfer service end-to-end in under 5 minutes.

---

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | Desktop ≥ 4.19 | `docker version` |
| curl | any | `curl --version` |

---

## Step 1 — Unit Tests (60 seconds)

```bash
cd backend/transfer-service
mvn test -q
```

**Expected output:**
```
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Open the coverage report to see money-path coverage at 95-98%:
```bash
open target/site/jacoco/index.html   # macOS
xdg-open target/site/jacoco/index.html  # Linux
```

---

## Step 2 — Build Docker Image (60-90 seconds)

```bash
# From the repo root
docker build -t bank/transfer-service:1.0.0-SNAPSHOT backend/transfer-service/
```

**Expected output:**
```
Successfully built <image-id>
Successfully tagged bank/transfer-service:1.0.0-SNAPSHOT
```

The Dockerfile uses a multi-stage build (`eclipse-temurin:21-jdk-alpine` → `jre-alpine`) and runs as a non-root `app` user — wired by the DevOps agent per the security checklist.

---

## Step 3 — Start Service + Dependencies (30 seconds)

```bash
docker compose -f infra/docker-compose.yml up -d
```

This starts:
- `transfer-service` on port 8080
- PostgreSQL (Flyway auto-runs all 5 migrations on startup)
- Redis (idempotency cache)

Wait ~10 seconds for startup, then check readiness:
```bash
curl -s http://localhost:8080/actuator/health/readiness | python3 -m json.tool
```

---

## Step 4 — Smoke Test (10 seconds)

```bash
bash infra/smoke/smoke-test.sh
```

**Expected output:**
```
[SMOKE] GET /actuator/health/liveness  → 200 ✅
[SMOKE] GET /actuator/health/readiness → 200 ✅
[SMOKE] GET /actuator/prometheus       → 200 ✅
[SMOKE] All checks passed.
```

---

## Step 5 — Send a Transfer

```bash
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "sourceAccountId": "ACCT-001",
    "destinationAccountId": "ACCT-002",
    "amount": "500.00",
    "currency": "THB",
    "description": "Rent payment"
  }' | python3 -m json.tool
```

> **v1 note:** `SecurityConfig` is `permitAll` so the service accepts the request. JWT enforcement ships in US-006. The `AccountClientStub` returns a canned 10M THB ACTIVE account for any ID.

**Try idempotency** — send the exact same request with the same `Idempotency-Key`. The second call should return `idempotency_status: "IDEMPOTENT_REPLAY"` without creating a new transfer.

---

## Bonus — Browse the Artifacts

The agents produced structured handoff artifacts at every phase. Explore them:

```bash
# Human-readable summaries (Markdown)
ls docs/artifacts/*.md

# Machine-validated envelopes (JSON)
ls docs/artifacts/*.json

# Agent communication log
open docs/agents-comms/dashboard.md

# All 16 architecture decisions
open docs/agents-comms/decisions-log.md
```

Or open the visual dashboard:
```bash
open docs/demo/index.html
```

---

## Teardown

```bash
docker compose -f infra/docker-compose.yml down -v
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `mvn test` fails — missing dependency | Run `mvn dependency:resolve` first |
| Readiness returns `DOWN` | Wait 15s; PostgreSQL may still be migrating |
| Port 8080 already in use | `lsof -ti:8080 \| xargs kill` then retry |
| Docker build fails on `COPY` | Ensure you ran `mvn package -DskipTests` first OR use the `Dockerfile`'s multi-stage build which includes the Maven build step |
| Integration tests skip | Requires Docker API ≥ 1.44. Run `docker version` to verify. |
