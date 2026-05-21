# Redis Cluster Verification Report — balance-dashboard-service

> **Feature:** balance-comparison
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Owner:** banking-devops
> **Target deadline:** D5 (ASSUMPTION-TL-004 — infra inspection by D5)
> **References:** impl-notes §10 (redis-pool-risk), ASSUMPTION-TL-004, SA-RISK-004, db-schema.md §2.3, Security C-4

---

## Purpose

This document is the **verification template** for ASSUMPTION-TL-004 (TL assumptions register). DevOps executes these commands during P1 (by D5) against the shared Redis cluster to confirm:

1. **Headroom:** `maxclients` has sufficient space for BDS pool connections across all deployment scenarios (baseline + HPA peak + existing money-transfer pool).
2. **TLS in-transit:** `ssl.enabled=true` in BDS `application.yml` is actually enforced by the cluster.
3. **At-rest encryption:** Cluster has AES-256-GCM at-rest encryption enabled (Security C-4 requirement). If not, BDS CANNOT ship.

---

## Connection Pool Math

Per impl-notes §10 (`redis-pool-risk`):

| Scenario | BDS pods | Connections from BDS | Money-transfer existing | Total estimate |
|---|---|---|---|---|
| Baseline (min replicas) | 2 | 16 × 2 = **32** | ~64 | ~96 |
| HPA peak | 4 | 16 × 4 = **64** | ~64 | ~128 |
| HPA max | 8 | 16 × 8 = **128** | ~64 | ~192 |

**Pass criterion:** `maxclients - current_connections - 192 > 30% of maxclients`

This ensures at least 30% headroom remains even at HPA max, preventing Lettuce `RedisException` on connection saturation (which degrades every request to cold-cache path, breaking the `performance_p95_lt_500ms_warm` SLA NFR).

---

## Verification Commands

### Step 1 — Connect to the Redis cluster

```bash
# Option A: kubectl exec into a Redis pod
kubectl exec -it redis-shared-0 -n redis -- redis-cli

# Option B: from a jump pod / bastion with redis-cli
redis-cli -h redis-shared.banking-staging.svc.cluster.local -p 6379 \
  --tls \
  --cert /etc/ssl/client.crt \
  --key /etc/ssl/client.key \
  --cacert /etc/ssl/ca.crt
```

### Step 2 — Check `maxclients` limit

```bash
CONFIG GET maxclients
```

**Expected output (healthy):**
```
1) "maxclients"
2) "10000"
```

Record the actual value as `MAXCLIENTS` for the math below.

### Step 3 — Count current connections per shard

Run on **each shard** (Redis cluster mode has multiple nodes):

```bash
CLIENT LIST | wc -l
```

Or for a more detailed breakdown:

```bash
INFO clients
```

Key fields from `INFO clients`:
- `connected_clients` — active connections right now
- `blocked_clients` — clients waiting on blocking commands

**Record:** `CURRENT_CONNECTIONS` = `connected_clients` per shard (sum across all shards for cluster mode).

### Step 4 — Compute headroom

```bash
# Example (substitute actual values):
MAXCLIENTS=10000
CURRENT_CONNECTIONS=128   # sum across all shards
BDS_PEAK_CONNECTIONS=128  # 16 conn/pod × 8 pods (HPA max)
HEADROOM=$(( MAXCLIENTS - CURRENT_CONNECTIONS - BDS_PEAK_CONNECTIONS ))
HEADROOM_PCT=$(( HEADROOM * 100 / MAXCLIENTS ))
echo "Remaining headroom: $HEADROOM connections ($HEADROOM_PCT%)"
```

**Pass criterion:** `HEADROOM_PCT > 30`

### Step 5 — Verify TLS in-transit

```bash
CONFIG GET ssl
```

Expected:
```
1) "ssl"
2) "yes"
```

Or check the cluster's TLS port is active:
```bash
CONFIG GET tls-port
```

Expected: non-zero port (e.g., `6380` if TLS is on a separate port, or `6379` with TLS enabled).

Alternatively, confirm from outside the cluster that a non-TLS connection is rejected:
```bash
redis-cli -h redis-shared.banking-staging.svc.cluster.local -p 6379 PING
# Expected: (error) ERR ... or connection refused (TLS required)
```

**Pass criterion:** `ssl=yes` OR TLS port active AND non-TLS connections rejected.

Note: BDS `application.yml` sets `spring.data.redis.ssl.enabled=true`. If the cluster does not enforce TLS, the BDS client will attempt TLS handshake against a plaintext server and fail with `SSLException` at startup. The connection pool never initializes, causing every request to be cold-cache (fail-open path).

### Step 6 — Verify at-rest encryption (Security C-4)

At-rest encryption is a **cluster-level configuration** — not visible via `redis-cli`. Verification path depends on the Redis deployment:

**Option A: Managed Redis (AWS ElastiCache / GCP Memorystore / Azure Cache for Redis)**
```bash
# AWS ElastiCache
aws elasticache describe-replication-groups \
  --replication-group-id redis-shared-banking \
  --query 'ReplicationGroups[0].AtRestEncryptionEnabled'
# Expected: true

# GCP Memorystore
gcloud redis instances describe redis-shared-banking \
  --region asia-southeast1 \
  --format='value(persistenceIamIdentity,redisConfigs)'
# Check for encryption-related fields
```

**Option B: Self-hosted Redis on Kubernetes**
```bash
# Check if Redis is using LUKS or storage class encryption
kubectl get pvc -n redis -l app=redis -o jsonpath='{.items[*].spec.storageClassName}'
# Expected: a storage class with encryption=true annotation

# Check storage class
kubectl get storageclass <storage-class-name> -o yaml | grep encrypt
```

**Option C: Ask platform team**
If automated verification is not possible, request written confirmation from the platform/infra team that AES-256-GCM at-rest encryption is enabled on the `redis-shared` cluster (ASSUMPTION-TL-004 item a). Store the confirmation as an audit artifact.

**Pass criterion:** Written or API-confirmed evidence that AES-256-GCM at-rest encryption is enabled.

**FAIL action:** If at-rest encryption is NOT enabled, BDS CANNOT ship (Security C-4 violation). DevOps must either:
1. Enable encryption cluster-wide (requires platform team — may need cluster restart in non-managed Redis).
2. Provision a dedicated BDS Redis namespace with encryption enabled.

Escalate to PM immediately if this path is taken — it is a potential sprint blocker.

---

## Pass / Fail Criteria Summary

| Check | Pass criterion | Fail action |
|---|---|---|
| `maxclients` headroom | Headroom > 30% of `maxclients` at HPA max (192 BDS + existing connections) | Reduce BDS pool: `max-active: 8` in application.yml (halves BDS peak to 96) OR request platform to raise `maxclients` |
| TLS in-transit (`ssl=yes`) | `CONFIG GET ssl` returns `yes` OR TLS port active | Verify BDS `ssl.enabled=true` is correct; check cert paths; escalate to platform if cluster lacks TLS |
| At-rest encryption (AES-256-GCM) | API-confirmed or platform-confirmed | Enable cluster-wide OR provision dedicated encrypted namespace. DO NOT ship without this — Security C-4 blocker |

---

## Results Template (fill in during D5 inspection)

```
Date of inspection: _______________
Inspector: _______________
Cluster: redis-shared (banking-staging namespace / <cluster-name>)

STEP 2 — maxclients
  Result: maxclients = _______________

STEP 3 — Current connections
  Shard 0: connected_clients = _______________
  Shard 1: connected_clients = _______________  (if cluster mode)
  Total: _______________

STEP 4 — Headroom
  Headroom at HPA max (192 connections): _______________ (_______________ %)
  PASS / FAIL: _______________

STEP 5 — TLS in-transit
  ssl = _______________  (yes / no)
  Verification method: CONFIG GET / TLS port check / non-TLS reject test
  PASS / FAIL: _______________

STEP 6 — At-rest encryption
  Method: [ ] AWS ElastiCache API  [ ] GCP Memorystore API  [ ] StorageClass  [ ] Platform team confirmation
  Result: _______________
  PASS / FAIL: _______________

OVERALL VERDICT: PASS / FAIL
If FAIL: Fallback taken: _______________
Escalated to PM: YES / NO
```

---

## Fallback Actions (if verification fails)

### Fallback A — maxclients headroom insufficient

Reduce BDS Lettuce pool from 16 → 8 connections per pod:

```yaml
# application.yml — balance-dashboard-service
spring.data.redis.lettuce.pool.max-active: 8
spring.data.redis.lettuce.pool.max-idle: 8
spring.data.redis.lettuce.pool.min-idle: 2
```

Impact analysis:
- 50 peak concurrent users × ~30ms per Redis GET = ~1.7 RPS sustained per pod
- Pool of 8 handles ~8 concurrent Redis calls per pod; 50 concurrent users across 4 pods = ~12.5 concurrent calls per pod (accounting for the full request lifecycle)
- Safe at 50 peak concurrent users (SA NFR `scalability_50_peak_concurrent`)
- Acceptably degrades only if users spike above ~80 concurrent per pod; HPA will scale out before then

### Fallback B — At-rest encryption absent

DevOps provisions a dedicated Redis namespace with encryption:

```yaml
# Helm values override for dedicated Redis namespace
config:
  redis:
    host: "redis-bds-encrypted.banking-staging.svc.cluster.local"
    port: "6379"
```

Track provisioning time in PM risk register. BDS deploy slips until this is ready.
