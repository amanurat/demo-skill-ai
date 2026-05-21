# =============================================================================
# balance-dashboard-service — Dockerfile
# Feature: balance-comparison · Sprint: SPRINT-2026-Q2-BC-01
#
# Multi-stage build:
#   Stage 1 (builder): Eclipse Temurin 21 JDK — Maven build, fat JAR produced
#   Stage 2 (runtime): Eclipse Temurin 21 JRE — minimal runtime, non-root user
#
# Security constraints honoured:
#   - Non-root user (appuser:appgroup, UID/GID 1001)
#   - WORKDIR is chown'd to appuser:appgroup (not just USER — avoids temp file failures)
#   - No shell in runtime image beyond what Temurin JRE includes
#   - JAVA_OPTS env var for container-aware JVM tuning
#   - Labels: OCI image annotations for traceability
#
# Build:
#   docker build \
#     --build-arg BUILD_VERSION=<semver> \
#     --build-arg GIT_SHA=<sha> \
#     -t ghcr.io/<org>/bank/balance-dashboard-service:<tag> \
#     -f backend/balance-dashboard-service/Dockerfile \
#     .
#
# NOTE: Context is the repo root so that the Maven reactor can resolve
# common-libs/account-client-lib (BDS depends on it via -am reactor).
# If building from within backend/balance-dashboard-service/, the -am reactor
# won't find common-libs; always build with context = repo root.
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1 — Builder
# Use JDK image for compilation. Maven wrapper is already in the repo.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

# Copy Maven wrapper and root pom first for better layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Copy common-libs (BDS depends on account-client-lib — resolved via -am reactor)
COPY common-libs/ common-libs/

# Copy backend service source
COPY backend/balance-dashboard-service/ backend/balance-dashboard-service/

# Grant execute permission to Maven wrapper
RUN chmod +x mvnw

# Build the fat JAR — skip tests (tests run in CI build-and-test stage)
# -pl backend/balance-dashboard-service: only build this module
# -am: also build dependencies (common-libs/account-client-lib)
# -DskipTests: tests already ran in CI stage 1; avoid double-running
# -Dmaven.test.skip=true: also skips compilation of test classes (faster)
RUN ./mvnw clean package \
      -pl backend/balance-dashboard-service \
      -am \
      -DskipTests \
      -Dmaven.test.skip=true \
      --no-transfer-progress \
      -B

# Verify the JAR was built and locate it for the runtime stage
RUN find backend/balance-dashboard-service/target -name "*.jar" \
      -not -name "*-sources.jar" \
      -not -name "*-javadoc.jar" \
      -not -name "original-*.jar" \
      | head -1 \
      > /workspace/.jar_path \
    && echo "JAR path: $(cat /workspace/.jar_path)"

# ---------------------------------------------------------------------------
# Stage 2 — Runtime
# Eclipse Temurin 21 JRE (jammy) — smaller than JDK; no compiler included.
# NOT distroless here because Spring Boot Actuator healthcheck requires curl
# or a JVM-based probe; we use curl healthcheck (HEALTHCHECK directive below).
# If distroless is required by policy, replace HEALTHCHECK with exec-form
# Spring Boot built-in health indicator and use a TCP liveness probe instead.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# OCI image labels — traceability + registry metadata
# BUILD_VERSION and GIT_SHA injected at build time via --build-arg
ARG BUILD_VERSION=0.1.0-SNAPSHOT
ARG GIT_SHA=unknown

LABEL org.opencontainers.image.title="balance-dashboard-service" \
      org.opencontainers.image.description="Account Balance Comparison Dashboard — read-only CQRS aggregator" \
      org.opencontainers.image.vendor="platform-banking" \
      org.opencontainers.image.source="https://github.com/your-org/bank/tree/main/backend/balance-dashboard-service" \
      org.opencontainers.image.version="${BUILD_VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.licenses="UNLICENSED"

# Install curl for the HEALTHCHECK directive.
# curl is the only additional package; do not add shells or build tools.
# Clean apt cache immediately to keep image layer small.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl=7.* \
    && rm -rf /var/lib/apt/lists/*

# Create non-root group and user.
# UID/GID 1001 chosen (1000 is already in use by the temurin base user).
# Shell set to /bin/false — no interactive login allowed.
RUN groupadd --gid 1001 appgroup \
    && useradd --uid 1001 --gid appgroup --shell /bin/false --no-create-home appuser

# Set WORKDIR and chown to appuser:appgroup BEFORE switching to non-root user.
# IMPORTANT: Must chown WORKDIR explicitly — USER 1001 alone does not fix
# existing root-owned directories; app write of temp files would fail at runtime.
WORKDIR /app
RUN chown appuser:appgroup /app

# Copy the fat JAR from builder stage.
# --chown ensures the JAR is owned by appuser:appgroup from the start.
COPY --from=builder --chown=appuser:appgroup /workspace/.jar_path /app/.jar_path
COPY --from=builder --chown=appuser:appgroup \
     /workspace/backend/balance-dashboard-service/target/*.jar \
     /app/app.jar

# Create /tmp owned by appuser so Spring can write temp files
# (readOnlyRootFilesystem=true in K8s mounts /tmp as emptyDir via extraVolumesSystem)
# The directory here is for local Docker runs without K8s volume constraints.
RUN mkdir -p /tmp && chown appuser:appgroup /tmp

# Switch to non-root user for all subsequent RUN, CMD, ENTRYPOINT instructions
USER appuser:appgroup

# Application port (HTTP) — documented; actual binding enforced by NetworkPolicy
EXPOSE 8080

# Management/metrics port — Prometheus scrape + health probes
EXPOSE 8081

# JVM tuning environment variable.
# -XX:+UseContainerSupport: JVM reads cgroup memory limits (prevents OOMKilled)
# -XX:MaxRAMPercentage=75.0: use up to 75% of cgroup memory limit for heap
#   (leaves 25% for non-heap: metaspace, code cache, threads, Netty buffers)
# -XX:+ExitOnOutOfMemoryError: fail-fast on OOM instead of zombie JVM
# Override at deploy time: --set extraEnv[0].name=JAVA_OPTS etc.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Healthcheck — used by Docker (NOT K8s — K8s uses livenessProbe/readinessProbe).
# Checks Spring Boot Actuator /actuator/health endpoint on management port 8081.
# --interval=30s: check every 30 seconds
# --timeout=5s: fail if no response within 5 seconds
# --retries=3: mark unhealthy after 3 consecutive failures
# --start-period=60s: grace period during JVM startup (prevents premature unhealthy)
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# Entrypoint — exec form (no shell wrapper; signals forwarded directly to JVM)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
