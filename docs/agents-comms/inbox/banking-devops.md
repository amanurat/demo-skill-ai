# Inbox — banking-devops

> Personal communications view for this agent. What you've received, produced, and what's pending.

## Active Feature: money-transfer

## Received (upstream)

| # | From | Date | Phase | Artifact | Summary |
|---|---|---|---|---|---|
| 1 | banking-qa | 2026-05-18 11:35 UTC | QA → DEPLOYMENT | [S8 `c2f84d17-3b9e-4a06-95c1-8e7d0f2a5b3c`](../../artifacts/S8-qa-money-transfer.md) | Quality gate ⚠️ PASS-with-conditions. 40/40 unit tests pass; money-path coverage ≥ 95%; 6 integration tests compile-clean but Docker-gated (Colima API v1.32 < required v1.44) — **CI must execute them** on Docker Desktop ≥ 4.19. 2 bugs filed back to backend-dev (BUG-QA-001 medium, BUG-QA-002 low). 8 recommended tests for next iteration (T-001..T-008). Hand-off note: configure JaCoCo enforcer ≥ 0.80 on full `mvn verify`; pre-pull `postgres:16-alpine` in CI image; gate staging deploy on Security ITEM-1..ITEM-3 + ITEM-7 + ITEM-8; run Gatling at first staging deploy (100 RPS sustained, p95 < 1s). |

(Context for DevOps — earlier upstream artifacts will be relevant when activated:)

| Ref | From | Date | Artifact | Why it matters for DevOps |
|---|---|---|---|---|
| S7 | banking-security | 2026-05-18 04:05 UTC | [S7 `16792683-0871-4e95-826c-2711bf2a14fc`](../../artifacts/S7-security-money-transfer.md) | **ITEM-9** and **ITEM-10** are explicitly DevOps tasks: actuator on management port 9090, K8s NetworkPolicy for Prometheus scrape, swagger-ui disabled in prod profile, K8s topology constraint pinning pods + DB to Thailand region (PDPA data residency), SCA scan (Dependabot / OWASP-DependencyCheck) wired in CI, SBOM per build, container scan (Trivy / Grype) per image. ITEM-4 (DB_PASSWORD via Vault/CSI) is shared with Backend Dev. |
| S4 | banking-tech-lead | 2026-05-18 13:30 UTC | [S4 `c8d4e9f6-3a72-4b1d-8e5c-1f9d2a6b7c83`](../../artifacts/S4-tech-lead-money-transfer.md) | 5 Flyway migrations to run; 3 Avro schemas to register with Apicurio at deploy time; Kafka topics to create (`transfer.transfer.requested.v1`, `transfer.transfer.completed.v1`, `transfer.transfer.failed.v1`); contract at `backend/transfer-service/api/openapi.yaml` to publish to internal API portal. |
| S3 | banking-solution-architect | 2026-05-18 11:30 UTC | [S3 `b5e2d8a4-4f1c-4a7e-9c3b-7d6f1e2a8c34`](../../artifacts/S3-solution-architect-money-transfer.md) | 9 services to deploy (api-gateway, identity, account, payee, transfer, ledger, audit, notification, compliance); Kafka 3.x cluster (3 brokers, RF=3, min.insync.replicas=2); Apicurio Registry; PostgreSQL 16 with Patroni HA; Redis 7; HashiCorp Vault; OpenTelemetry + Prometheus + Grafana + Loki + Tempo stack. |

## Produced (downstream)

| # | To | Date | Phase | Artifact | Outcome |
|---|---|---|---|---|---|
| 1 | banking-player (close-out) | 2026-05-18 12:30 UTC | DEPLOYMENT → DONE | [S9 `f3a91c82-7d4e-4b15-a6e3-9c8b0d5f2e71`](../../artifacts/S9-devops-money-transfer.md) | ✅ **Quality gate PASS (v1 scaffold).** 24 deliverables: 1 Dockerfile + 1 .dockerignore + 11 Helm files (Chart/values/_helpers/deployment/service/ingress/hpa/pdb/networkpolicy/serviceaccount/configmap) + 7 observability configs (compose, prometheus, grafana datasource/dashboards.yml/dashboard JSON, alerts) + 1 smoke script + 1 GitHub Actions CI workflow (11 stages) + 1 runbook. Docker image `bank/transfer-service:1.0.0-SNAPSHOT` (`sha256:7c55921384f4b04f196aaa6613a83a2fe99719e000f9ef34227034b7282e41eb`, non-root `app:app`). Smoke: liveness 200, readiness 200, Prometheus 200 (14 series). POST 403 (expected — JWT deferred to US-006). **Discovered + fixed 7 schema mismatches** between Flyway DDL and JPA entities during smoke (CHAR→VARCHAR, JSONB→TEXT, SMALLINT→INTEGER) — backend-dev to reconcile entity annotations in US-006. **Verdict:** v1 scaffold complete; staging deploy gated on 5 backend-dev items + CI runner Docker upgrade + Gatling baseline. |

## Quality Gate Record

| Iteration | Phase Transition | Result |
|---|---|---|
| 1 | DEPLOY → DoD | ✅ PASS (v1 scaffold) — image built + smoke 200 (liveness/readiness/Prometheus); CI/CD YAML valid (runtime execution requires CI runner with Docker); Helm chart + observability + runbook complete; 7 schema mismatches surfaced and patched; staging gated on 5 backend items (JWT, stub guard, stub customer ID, MDC.clear filter, tracing). DoD subset for DevOps: complete. |

## Open Items / Action Required

**Awaiting QA S8 artifact.** When QA signs off (or returns with deferred-test-debt acceptable for v1), pick up the following work:

1. **Dockerfile** per service — multi-stage build, base image Eclipse Temurin 21 JRE, non-root user, distroless if compatible; image labels (org.opencontainers.image.source / .revision / .created); HEALTHCHECK against actuator /actuator/health.
2. **docker-compose.yml** for local dev — PostgreSQL 16 + Kafka 3.x + Apicurio + Redis 7 + Vault dev mode + the transfer-service container; named volumes; healthcheck wiring; .env.example with all required vars (no secrets baked in).
3. **Helm chart** per service — values.yaml with profile-per-env (local / staging / prod), ConfigMap for non-secret config, ExternalSecret CRD or Vault Agent Injector for secrets, NetworkPolicy restricting ingress to known sources + egress to known destinations (Kafka, PostgreSQL, Vault), HorizontalPodAutoscaler on CPU + custom `kafka_consumer_lag` metric, PodDisruptionBudget, topology spread constraints (zone + node), Thailand-region constraint per Security ITEM-9.
4. **CI/CD pipeline** (GitHub Actions) per project-structure.md — lint → unit test → SAST (Semgrep / SonarQube) → SCA (Dependabot / OWASP-DependencyCheck) → build (Maven jib / Buildpacks) → container scan (Trivy) → integration test (Testcontainers) → Helm chart lint → deploy preview env → smoke tests → manual gate → deploy staging → manual gate → deploy prod.
5. **Grafana dashboards** per architect's metric list — `transfer_requests_total` (by status), `transfer_duration_histogram` (p50/p95/p99), `saga_compensation_total`, `daily_limit_rejections_total`, `aml_threshold_breach_total`, `kafka_consumer_lag`. Alert rules: error rate > 1% over 5 minutes, p99 latency > 3s over 5 minutes, compensation_failure count > 0, kafka_consumer_lag > 5s sustained.
6. **Observability wiring** — OpenTelemetry Collector → Tempo (traces); Micrometer → Prometheus (metrics); Logback JSON → Fluent Bit → Loki (logs). Confirm `correlation_id` + `transfer_id` indexed in Loki for search.
7. **Vault paths** — design secret paths per service (e.g., `secret/transfer-service/prod/db_password`, `secret/transfer-service/prod/kafka_sasl`); document rotation cadence.
8. **PagerDuty** wiring per architect's RISK-001 (saga COMPENSATION_FAILED double-fault must page within 60s).
9. **Address all Security `must_fix_before_staging` items in DevOps scope**:
   - ITEM-9 (actuator port 9090 + NetworkPolicy + swagger-ui disabled + Thailand topology constraint).
   - ITEM-10 (SCA + SBOM + container scan in CI).
   - Shared ITEM-4 (DB_PASSWORD never via plain env var — Vault/CSI mount only).

## Skills Referenced When Working

- `.claude/skills/banking-devops-platform/SKILL.md` — Dockerfile best practices (multi-stage, non-root, distroless, HEALTHCHECK), Helm chart layout (templates / values per env / NOTES.txt), GitHub Actions reusable workflows pattern, Trivy + Grype container scan gating, Prometheus alert rule conventions (severity labels, runbook annotations), K8s NetworkPolicy least-privilege templates, Vault Agent Injector patterns, OTel Collector pipelines.

## Workflow Hooks

- On S8 from QA → ingest test report + deferred-test-debt; produce Dockerfile + docker-compose + Helm + CI/CD + dashboards; emit S9.
- On Security `must_fix_before_staging` items targeting DevOps → schedule into S9 work; do not promote a release past staging until applicable items closed.
- On deployment incident → page on-call per PagerDuty wiring; capture postmortem; feed lessons back to Tech Lead (for ADR update) and Backend Dev (for code/test fix).
- On QA returning with blocker → halt DevOps work; await re-run.
- On Backend Dev re-iteration (US-006 etc.) → re-build image, re-run scans, redeploy preview env; coordinate staging promotion only after Security re-review closes the affected `must_fix` items.
