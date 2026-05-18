# CI/CD Pipeline Standard — 11 Stages

Reference loaded on demand by `banking-devops-platform` skill. Defines the canonical pipeline every banking service must run on every commit to a release branch. Stages run sequentially unless explicitly marked parallel; any failure halts promotion.

## Pipeline Overview

```yaml
stages:
  - lint                  # parallel: java + ts
  - unit-test             # parallel per module
  - sast-sca              # Semgrep + OWASP DC + Trivy (fs)
  - build                 # Buildx, multi-arch
  - integration-test      # Testcontainers (real Postgres + Kafka)
  - container-scan        # Trivy on image
  - push-registry         # signed image (cosign) + SBOM (syft)
  - deploy-staging        # Helm upgrade --install
  - smoke-tests           # automated probe of key endpoints
  - dast                  # ZAP / Burp baseline against staging
  - manual-gate           # approval for prod
  - deploy-prod           # Helm, canary if configured
```

---

## Stage-by-Stage

### 1. `lint` (parallel: java + ts)

- **What it does**: Static style / formatting checks; fast feedback before tests.
- **Tooling**: `mvn spotless:check` + `mvn checkstyle:check` for Java; `eslint` + `prettier --check` for Angular.
- **Fail criteria**: any rule violation. No warnings allowed in CI.
- **Typical duration**: < 1 min.

### 2. `unit-test` (parallel per module)

- **What it does**: Runs JUnit 5 / Jasmine suites with coverage reporters.
- **Tooling**: `mvn test` (with JaCoCo); `ng test --watch=false --code-coverage`.
- **Fail criteria**: any test failure, OR coverage below threshold (≥ 80% overall, ≥ 95% for money-handling paths).
- **Artifacts**: JaCoCo / Istanbul HTML reports uploaded as CI artifacts.

### 3. `sast-sca` (Semgrep + OWASP DC + Trivy)

- **What it does**: Static application security testing (SAST) on source, software-composition analysis (SCA) on dependencies, secret scanning on the workspace.
- **Tooling**:
  - SAST: `semgrep --config=p/security-audit --config=p/owasp-top-ten`
  - SCA: OWASP Dependency-Check (`dependency-check.sh --scan .`)
  - Filesystem scan: `trivy fs --severity HIGH,CRITICAL .`
- **Fail criteria**: any HIGH or CRITICAL finding without an approved waiver.
- **Output**: SARIF uploaded to the security dashboard.

### 4. `build` (Buildx, multi-arch)

- **What it does**: Produces the application JAR / Angular bundle, then builds an OCI image.
- **Tooling**: `mvn -DskipTests package`, then `docker buildx build --platform linux/amd64,linux/arm64 -t <registry>/<service>:<git-sha> .`
- **Fail criteria**: non-zero build exit, or image > 300 MB without justification.
- **Note**: Image is tagged with both git SHA and semver (`1.4.0`); never `:latest`.

### 5. `integration-test` (Testcontainers)

- **What it does**: Runs full integration tests against real Postgres + Kafka brought up by Testcontainers — no H2, no embedded brokers.
- **Tooling**: `mvn verify -P integration` with `org.testcontainers:postgresql,kafka`.
- **Fail criteria**: any failing IT, or flake rate > 1% (quarantined separately).
- **Typical duration**: 3–8 min.

### 6. `container-scan` (Trivy on image)

- **What it does**: Scans the built image for OS / library CVEs.
- **Tooling**: `trivy image --severity HIGH,CRITICAL --exit-code 1 <image>`
- **Fail criteria**: any HIGH / CRITICAL CVE without a documented waiver and expiry date.
- **Tip**: Pin base images by digest, not tag, to keep scans reproducible.

### 7. `push-registry` (signed image + SBOM)

- **What it does**: Pushes the image to the private registry, generates and attaches an SBOM, and signs the image.
- **Tooling**:
  - SBOM: `syft <image> -o spdx-json > sbom.spdx.json`
  - Signing: `cosign sign --key <kms-ref> <image>`
  - Attach: `cosign attach sbom --sbom sbom.spdx.json <image>`
- **Fail criteria**: push rejected, signature failed, or SBOM upload failed.

### 8. `deploy-staging` (Helm)

- **What it does**: Deploys the new image to the staging cluster using the service's Helm chart.
- **Tooling**: `helm upgrade --install <release> ./chart -f values-staging.yaml --set image.tag=<git-sha> --atomic --wait`
- **Fail criteria**: Helm release fails to reach Ready state within 5 min (auto-rollback via `--atomic`).

### 9. `smoke-tests`

- **What it does**: Hits the staging URL with a curated set of probe requests (health endpoint, one read, one write) using a synthetic test account.
- **Tooling**: a small `k6` or `curl` script in `infra/smoke/`.
- **Fail criteria**: any non-2xx on liveness/readiness; any business probe failure → auto-rollback.

### 10. `dast` (against staging)

- **What it does**: Dynamic application security testing — black-box scan of the running staging instance.
- **Tooling**: `zap-baseline.py -t https://<service>.staging.example.com` (OWASP ZAP).
- **Fail criteria**: any HIGH alert; MEDIUMs reviewed and either fixed or waived with expiry.

### 11. `manual-gate` → `deploy-prod`

- **What it does**: Human approval (release manager) gates production rollout. Production deploy uses Helm with canary or blue/green per the service's configuration.
- **Tooling**: `helm upgrade --install <release> ./chart -f values-prod.yaml --set image.tag=<semver> --atomic --wait` (canary handled by Argo Rollouts or Flagger if configured).
- **Fail criteria**: canary error budget exceeded → automated rollback; manual rollback command also documented in the handoff artifact.

---

## Pipeline Conventions

- **Concurrency**: One pipeline per ref; superseded runs cancelled.
- **Caching**: Maven (`~/.m2`) and npm (`~/.npm`) caches keyed by lockfile hash.
- **Secrets in CI**: only via the CI provider's secret store (GitHub Actions OIDC → cloud IAM preferred). Never in plain YAML.
- **Idempotency**: re-running a pipeline on the same SHA must be safe (no destructive migrations triggered).
- **Observability of the pipeline itself**: emit duration + result metrics to the same Prometheus / Grafana stack used by services.

---

## When to Use This Reference

- Authoring a new service's pipeline file (`.github/workflows/ci.yml` or equivalent)
- Adding a new stage (e.g., contract tests, mutation tests) — find the right slot
- Diagnosing a slow / flaky pipeline
- Reviewing a pipeline PR against the platform standard
