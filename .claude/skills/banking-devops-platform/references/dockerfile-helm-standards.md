# Dockerfile & Helm Standards

Reference loaded on demand by `banking-devops-platform` skill. Defines the canonical container image and Helm chart shape for every banking JVM microservice.

## Dockerfile — Multi-Stage, Hardened

The standard image for a Spring Boot 3.x / Java 21 service:

```dockerfile
# Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q -DskipTests package

# Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s CMD wget -q -O - http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:+UseG1GC","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
```

### Why each line matters

- **Multi-stage** — build tooling (`jdk` + Maven) stays in the build stage; runtime image carries only `jre` + the fat JAR. Cuts image size 2–3x and shrinks the CVE surface.
- **`addgroup` / `adduser` + `USER app`** — never run as root. K8s `PodSecurity` `restricted` profile rejects root containers.
- **`HEALTHCHECK`** — defensive; K8s probes are authoritative, but this also covers `docker run` and local dev.
- **`-XX:MaxRAMPercentage=75`** — JVM honors the container memory limit instead of guessing host RAM. Prevents OOMKill due to heap overshoot.
- **`-XX:+UseG1GC`** — default GC for typical microservice latency profile. Override to ZGC for low-latency hot paths (see observability reference).
- **Pinning**: in CI, replace `eclipse-temurin:21-jre-alpine` with a digest (`@sha256:...`) for reproducibility.

### Hard rules for every Dockerfile

- No `:latest` base image — always a digest or semver tag.
- No `apk add curl` / `apt-get install` of network tools "just for debugging" — use `kubectl debug` ephemeral containers instead.
- No `COPY . .` of secrets — `.dockerignore` must exclude `.env`, `**/*.pem`, `**/id_rsa*`.
- No writable `VOLUME` for app data — services are stateless; persistence lives in Postgres / Kafka / Redis.

---

## Helm Values — Reference Template

Every chart has `values.yaml` (defaults) plus per-env overrides (`values-staging.yaml`, `values-prod.yaml`). Canonical sketch:

```yaml
replicaCount: 3
resources:
  requests: { cpu: 500m, memory: 1Gi }
  limits:   { cpu: 1500m, memory: 2Gi }
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 12
  targetCPU: 70
podDisruptionBudget:
  minAvailable: 2
service:
  type: ClusterIP
  port: 8080
probes:
  liveness:  { path: /actuator/health/liveness,  initialDelaySeconds: 30 }
  readiness: { path: /actuator/health/readiness, initialDelaySeconds: 10 }
config:
  springProfile: staging
  vault: enabled
  otel:
    endpoint: http://otel-collector:4317
```

### Why each block matters

- **`replicaCount: 3`** — minimum for HA (survives one node drain + one rolling-update pod).
- **`resources.requests` / `limits`** — both set, always. Requests drive scheduling; limits prevent noisy-neighbor. Memory request == limit is recommended for JVM workloads to avoid OOM surprises.
- **`autoscaling`** — HPA on CPU is the baseline. Add custom metrics (RPS, queue depth) via Prometheus Adapter for traffic-sensitive services.
- **`podDisruptionBudget.minAvailable: 2`** — guarantees at least 2 pods stay up during node drain. Prevents zero-replica windows during cluster ops.
- **`service.type: ClusterIP`** — internal only; external exposure goes through the shared Ingress / API gateway with mTLS termination.
- **`probes`** — separate liveness and readiness paths (matches Spring Boot Actuator probe groups). `initialDelaySeconds` is tuned to JVM warm-up time.
- **`config.vault: enabled`** — secrets injected via Vault CSI; never templated into ConfigMaps.
- **`config.otel.endpoint`** — points to the in-cluster OTel collector; traces / metrics / logs all leave via the collector.

### Hard rules for every chart

- Chart name == service name == K8s namespace prefix == Helm release name. No mismatches.
- `values.yaml` has sane defaults; env overrides only override what differs.
- No secrets in any committed values file — secrets are templated from Vault at apply time.
- `replicaCount` ≥ 2 in every non-dev env.
- HPA `minReplicas` ≥ `PodDisruptionBudget.minAvailable + 1` to prevent deadlock during drain.
- Include `NetworkPolicy` (egress allow-list) and `ServiceAccount` (least privilege) templates by default.

---

## When to Use This Reference

- Authoring a new service's `Dockerfile` or Helm chart
- Reviewing image / chart PRs against the platform standard
- Diagnosing OOMKill, slow startup, or probe-flap issues
- Tuning HPA / PDB after a load test
- Onboarding a service into the GitOps / Vault stack
