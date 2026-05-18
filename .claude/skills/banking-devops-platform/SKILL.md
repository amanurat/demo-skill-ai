---
name: banking-devops-platform
description: Banking platform engineering patterns — CI/CD pipeline stages, hardened Dockerfile, Helm chart structure, Grafana dashboards, Prometheus alert rules, secrets management. Use when building or reviewing CI/CD, K8s manifests, container images, or observability dashboards.
---

# Banking DevOps Platform — Implementation Skill

Reusable platform engineering patterns for banking microservices on Kubernetes. Loaded by the `banking-devops` agent (and any reviewer touching infra). Covers the CI/CD pipeline standard, container image hardening, Helm chart structure, and the observability stack (Grafana dashboards + Prometheus alerts).

## When to Use

- Building or extending a CI/CD pipeline for a banking service
- Writing or reviewing a `Dockerfile` for a JVM microservice
- Authoring or tuning Helm chart values per environment
- Standing up Grafana dashboards or Prometheus alert rules
- Wiring secrets management (Vault / Secrets Manager) into a deployment
- Reviewing infra PRs against the platform standard

## Quick Reference

| Need | Where to Look |
|---|---|
| 11-stage pipeline definition, fail criteria, tooling per stage | [references/ci-cd-pipeline.md](references/ci-cd-pipeline.md) |
| Hardened multi-stage `Dockerfile`, Helm `values.yaml` template, probes, HPA, PDB | [references/dockerfile-helm-standards.md](references/dockerfile-helm-standards.md) |
| Grafana panels (RED + business), Prometheus alert rules with PromQL | [references/observability-dashboards-alerts.md](references/observability-dashboards-alerts.md) |

---

## Secrets / Config (inline — apply to every deploy)

- **Secrets** in Vault / AWS Secrets Manager — injected via CSI driver or sidecar
- **No secrets in Helm values or ConfigMaps** — values files are committed to git; secrets are not
- **Per-environment overrides** via Helm value files (`values-staging.yaml`, `values-prod.yaml`)
- Rotate short-lived credentials; long-lived static tokens are auto-fail in review
- Use Kubernetes `ServiceAccount` + workload identity (IRSA / GCP WI) for cloud-API access — no static cloud keys mounted in pods

---

## Anti-Patterns (auto-fail in review)

- `:latest` image tag in prod (use semver + digest)
- Running as root in container
- Mounting writable host paths
- Secrets in env vars (use file mount or Vault injection)
- No resource limits → noisy neighbor
- No `PodDisruptionBudget` → outage during node drain
- "Deploy first, observe later" — dashboard + alerts must ship with the service
- Manual changes via `kubectl edit` (causes drift; GitOps only)
- No rollback rehearsal before marking release done
- Single replica in prod

---

## Reference Index

- [references/ci-cd-pipeline.md](references/ci-cd-pipeline.md) — 11-stage pipeline (lint → prod deploy), stage-by-stage tooling, fail criteria
- [references/dockerfile-helm-standards.md](references/dockerfile-helm-standards.md) — multi-stage Dockerfile (non-root, healthcheck), Helm values template (HPA, PDB, probes)
- [references/observability-dashboards-alerts.md](references/observability-dashboards-alerts.md) — Grafana dashboard panels per service, Prometheus alert rules with PromQL examples

---

## How This Skill is Loaded

This skill is referenced (not auto-injected) by:
- [`.claude/agents/banking-devops.md`](../../agents/banking-devops.md) — Read on every deploy / infra task

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before starting work. The agent persona instructs them to do so.
