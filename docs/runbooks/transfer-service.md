# Runbook — transfer-service

> **Version:** 1.0.0  
> **Last updated:** 2026-05-18  
> **Maintained by:** #platform-banking (Slack)  
> **PagerDuty service:** transfer-service  
> **Escalation contacts:** See [On-Call Escalation](#on-call-escalation) section

---

## Overview

`transfer-service` is the core intra-bank THB money transfer microservice. It implements the saga pattern for debit/credit coordination and writes to a transactional outbox for downstream event relay (US-007, not yet wired in v1).

### Architecture

```
Client -> API Gateway -> transfer-service (port 8080)
                             |
                             +-- PostgreSQL (transfer_db)
                             +-- account-service (Resilience4j-decorated, US-006)
                             +-- Kafka outbox relay (US-007, future)
                             +-- OTel Collector -> Grafana/Tempo
```

### Key Dependencies

| Dependency | Endpoint / Config | Impact if down |
|---|---|---|
| PostgreSQL `transfer_db` | `transfer-postgres:5432` | Total outage — all operations fail |
| account-service | `account-service:8080` | Circuit breaker trips at 50% errors / 10s; transfer saga fails with 503 |
| OTel Collector | `otel-collector:4317` | Trace export drops; app continues serving |
| Vault CSI | SecretProviderClass `transfer-service-vault-secrets` | Pod cannot start without DB_PASSWORD |
| Prometheus | scrapes `management:9090/actuator/prometheus` | Metrics loss; alerts stop firing |

### URLs

| Environment | URL |
|---|---|
| Staging | https://transfer.staging.example.com/api/v1/transfers |
| Production | https://transfer.example.com/api/v1/transfers |
| Grafana Dashboard | https://grafana.staging.example.com/d/transfer-service-v1 |
| Alert Rules | `infra/observability/prometheus/alerts/transfer-service.yml` |

---

## Common Alerts and Diagnosis

### HighErrorRate

**Alert:** `HighErrorRate` — 5xx error rate > 1% for 5 minutes  
**Severity:** Critical (PagerDuty)

**Diagnosis steps:**

1. Check pod logs for exceptions:
   ```bash
   kubectl logs -n banking-staging -l app=transfer-service --since=10m | grep -i "ERROR\|Exception" | tail -50
   ```

2. Check if the DB is healthy:
   ```bash
   kubectl exec -n banking-staging deployment/transfer-service -- \
     wget -q -O- http://localhost:9090/actuator/health | jq '.components.db'
   ```

3. Check account-service circuit breaker state:
   ```bash
   kubectl exec -n banking-staging deployment/transfer-service -- \
     wget -q -O- http://localhost:9090/actuator/health | jq '.components.circuitBreakers'
   ```

4. Check for recent deploy:
   - Look for deploy annotation lines on the Grafana dashboard
   - `helm history transfer-service-staging --namespace banking-staging`

5. If caused by a bad deploy: **rollback immediately** (see Rollback section).

---

### HighLatencyP95

**Alert:** `HighLatencyP95` — p95 latency > 1000ms for 5 minutes  
**Severity:** Critical (PagerDuty)

**Diagnosis steps:**

1. Check GC pauses — may be the root cause:
   ```bash
   kubectl exec -n banking-staging deployment/transfer-service -- \
     wget -q -O- http://localhost:9090/actuator/metrics/jvm.gc.pause | jq '.measurements'
   ```

2. Check HikariCP pool — are connections exhausted?
   ```bash
   kubectl exec -n banking-staging deployment/transfer-service -- \
     wget -q -O- "http://localhost:9090/actuator/metrics/hikaricp.connections.active"
   ```

3. Check account-service call latency — is the upstream slow?
   - Look at the p95 latency panel filtered by URI `/api/v1/transfers` POST only
   - If account-service is the bottleneck, check its dashboard

4. Check if HPA has scaled — is there enough capacity?
   ```bash
   kubectl get hpa -n banking-staging transfer-service
   ```

5. If DB slow queries are the cause, check Postgres `pg_stat_activity`:
   ```bash
   kubectl exec -n banking-staging deployment/transfer-postgres -- \
     psql -U transfer_user -d transfer_db -c \
     "SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state \
      FROM pg_stat_activity WHERE state <> 'idle' ORDER BY duration DESC LIMIT 10;"
   ```

---

### HighGcPauses

**Alert:** `HighGcPauses` — average GC pause > 500ms for 1 minute  
**Severity:** Warning (Slack)

**Diagnosis steps:**

1. Check heap usage — is it near the limit?
   ```bash
   kubectl exec -n banking-staging deployment/transfer-service -- \
     wget -q -O- "http://localhost:9090/actuator/metrics/jvm.memory.used?tag=area:heap"
   ```

2. If heap is consistently > 80% of max, request a memory limit increase:
   - Loop back to `banking-backend-dev` for memory profiling
   - Temporary fix: `helm upgrade transfer-service-staging ... --set resources.limits.memory=3Gi`

3. Check if G1GC is appropriate — for sub-10ms SLA hotpaths, consider ZGC:
   - Coordinate with backend-dev to add `-XX:+UseZGC` to ENTRYPOINT flags

---

### PodCrashLooping

**Alert:** `PodCrashLooping` — pod restarted > 3 times in 10 minutes  
**Severity:** Critical (PagerDuty)

**Diagnosis steps:**

1. Identify crashing pod:
   ```bash
   kubectl get pods -n banking-staging -l app=transfer-service
   ```

2. Get termination reason:
   ```bash
   kubectl describe pod <pod-name> -n banking-staging | grep -A 10 "Last State"
   ```

3. **OOMKill** (exit code 137): Memory limit too low → increase `resources.limits.memory`
   ```bash
   helm upgrade transfer-service-staging ./infra/helm/transfer-service \
     --namespace banking-staging \
     --reuse-values \
     --set resources.limits.memory=3Gi
   ```

4. **Startup failure** (exit code 1): Check if Vault secrets are mounted correctly:
   ```bash
   kubectl exec -n banking-staging <pod-name> -- ls /vault/secrets/
   ```

5. **Liveness probe failure**: Check if Postgres is reachable and Flyway migration completed:
   ```bash
   kubectl logs <pod-name> -n banking-staging --previous | tail -100
   ```

---

### TransferFailedRatioHigh

**Alert:** `TransferFailedRatioHigh` — business transfer failure ratio > 5% for 5 minutes  
**Severity:** Critical (PagerDuty)

This alert fires even when HTTP metrics look healthy — a saga may complete successfully (HTTP 200) but record a business failure (INSUFFICIENT_FUNDS, account frozen, etc.).

**Diagnosis steps:**

1. Check what failure reasons are dominating:
   ```bash
   kubectl logs -n banking-staging -l app=transfer-service --since=10m \
     | jq -r 'select(.message) | .message' \
     | grep -i "FAILED\|COMPENSATION\|INSUFFICIENT\|FROZEN" | sort | uniq -c | sort -rn
   ```

2. Check `saga_state` table for stuck sagas:
   ```bash
   kubectl exec -n banking-staging deployment/transfer-postgres -- \
     psql -U transfer_user -d transfer_db -c \
     "SELECT status, count(*) FROM saga_state WHERE updated_at > now() - interval '1 hour' GROUP BY status;"
   ```

3. If `COMPENSATION_FAILED` count is non-zero: see **Compensation Incident** section below.

4. If failures are INSUFFICIENT_FUNDS spikes: likely a flood of low-balance requests — check rate-limit status and customer communication channels.

---

### HikariPoolExhaustion

**Alert:** `HikariPoolExhaustion` — all connections active for 2 minutes  
**Severity:** Warning (Slack)

**Diagnosis steps:**

1. Look for long-running transactions:
   ```bash
   kubectl exec -n banking-staging deployment/transfer-postgres -- \
     psql -U transfer_user -d transfer_db -c \
     "SELECT pid, now() - xact_start AS txn_duration, query FROM pg_stat_activity \
      WHERE xact_start IS NOT NULL ORDER BY txn_duration DESC LIMIT 5;"
   ```

2. Temporarily increase pool size as a bandage (fix root cause after):
   ```bash
   kubectl set env deployment/transfer-service DB_POOL_MAX=30 -n banking-staging
   ```

3. Coordinate with backend-dev to investigate slow queries or missing transaction close.

---

## Rollback Procedure

### Staging rollback

```bash
# List release history
helm history transfer-service-staging --namespace banking-staging

# Rollback to previous release (revision N-1)
helm rollback transfer-service-staging --namespace banking-staging

# Rollback to a specific revision
helm rollback transfer-service-staging <revision> --namespace banking-staging

# Verify rollback completed
kubectl rollout status deployment/transfer-service -n banking-staging --timeout=5m
```

Rollback time target: **under 1 minute** (Helm atomic rollback is near-instant for image changes; Flyway schema rollbacks require down migrations — see below).

### Production rollback

```bash
helm rollback transfer-service-prod --namespace banking-prod
kubectl rollout status deployment/transfer-service -n banking-prod --timeout=5m
```

### Database schema rollback

Flyway migrations are in `backend/transfer-service/src/main/resources/db/migration/`.  
**All migrations must have reversible down scripts (V*__undo_*.sql) before staging.**

If a rollback is blocked by a non-reversible migration:
1. Stop: do not rollback the application — data loss is worse than downtime.
2. Escalate to `banking-tech-lead` for a forward-fix migration.
3. Apply the forward-fix as a new migration version (V006__fix_...sql).

---

## Compensation Incident Handling (COMPENSATION_FAILED)

`COMPENSATION_FAILED` means both the original transfer operation AND its compensating transaction failed. Funds may be in an inconsistent state.

### Detection

```bash
# Find all COMPENSATION_FAILED sagas in the last 24 hours
kubectl exec -n banking-staging deployment/transfer-postgres -- \
  psql -U transfer_user -d transfer_db -c \
  "SELECT transfer_id, created_at, updated_at, error_message \
   FROM saga_state WHERE status = 'COMPENSATION_FAILED' \
   ORDER BY updated_at DESC LIMIT 20;"
```

### Response procedure

1. **Do not retry automatically** — manual Ops intervention required.
2. Identify the affected `transfer_id` and `reference_number` from `saga_state`.
3. Check the associated `transfers` record status:
   ```sql
   SELECT id, reference_number, status, source_account_id, destination_account_id, amount
   FROM transfers WHERE id = '<transfer_id>';
   ```
4. Cross-reference with `transfer_outbox` to determine what events were published:
   ```sql
   SELECT event_type, dispatched, created_at FROM transfer_outbox
   WHERE transfer_id = '<transfer_id>' ORDER BY created_at;
   ```
5. Contact Ops team with transfer_id, reference_number, amount, and saga_state error_message.
6. Ops team coordinates with ledger-service reconciliation to correct the balance.
7. Regulatory SLA for resolution: **OQ-006 open — confirm with Ops/Compliance** (see open-issues.md).
8. After resolution, update `saga_state.status = 'COMPENSATION_MANUALLY_RESOLVED'` with resolution notes.

---

## Daily Accumulator Hot Row Contention

`DailyTransferAccumulator` uses `INSERT ... ON CONFLICT DO UPDATE` on `(account_id, accumulation_date)`. At midnight Bangkok time (UTC+7 = 17:00 UTC), first-transfer requests of the new day race to insert a new row.

### Symptoms

- Elevated latency on `POST /api/v1/transfers` at 17:00 UTC (midnight Bangkok)
- Postgres `pg_stat_activity` shows concurrent `INSERT ON CONFLICT` queries waiting on same row lock
- `RISK-002` from architect risk register

### Mitigation (already implemented)

The `INSERT ... ON CONFLICT DO UPDATE` pattern resolves the race — only one insert wins; others fall into the update path with optimistic locking. This should self-resolve within 1-2 seconds.

### If contention persists beyond 30 seconds

1. Check for lock waits:
   ```sql
   SELECT blocked.pid, blocked.query, blocking.pid AS blocking_pid, blocking.query AS blocking_query
   FROM pg_stat_activity blocked
   JOIN pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
   WHERE blocked.query ILIKE '%daily_transfer_accumulator%';
   ```
2. If deadlocked: `SELECT pg_cancel_backend(<blocked_pid>);` to unblock.
3. Escalate to `banking-tech-lead` if pattern repeats — may need to introduce a retry with exponential backoff in `DailyTransferAccumulatorService`.

---

## On-Call Escalation

| Level | Contact | When |
|---|---|---|
| L1 — On-call engineer | PagerDuty on-call rotation `#platform-banking` | All critical alerts; first responder |
| L2 — Platform Lead | `@platform-lead` on Slack / PD escalation | If L1 cannot resolve in 30 min; COMPENSATION_FAILED; data integrity issues |
| L3 — Tech Lead | `@banking-tech-lead` | Non-reversible schema issues; saga design questions |
| L4 — Security | `@security-team` | Any suspected data breach, PII exposure, or auth bypass |
| L5 — Ops (Compliance) | `ops-team@example.com` | COMPENSATION_FAILED requiring manual fund adjustment; regulatory timeline questions |

**Placeholder — replace with real contacts before go-live.**

---

## Reference Links

- Grafana Dashboard: https://grafana.staging.example.com/d/transfer-service-v1
- Alert Rules: `infra/observability/prometheus/alerts/transfer-service.yml`
- Helm Chart: `infra/helm/transfer-service/`
- Architecture Overview: `docs/architecture/overview.md`
- Open Issues: `docs/agents-comms/open-issues.md`
- Security Must-Fix: `docs/artifacts/S7-security-money-transfer.md`
- QA Test Plan: `docs/test-plans/money-transfer.md`
