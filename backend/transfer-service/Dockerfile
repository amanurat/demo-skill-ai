# =============================================================================
# transfer-service — Multi-stage Dockerfile
# Standard: banking-devops-platform/references/dockerfile-helm-standards.md
#
# Build stage: eclipse-temurin:21-jdk-alpine (full JDK + Maven wrapper)
# Runtime stage: eclipse-temurin:21-jre-alpine (JRE only — ~200 MB smaller)
# Non-root user: app / app (UID auto-assigned by adduser -S)
# Healthcheck: Spring Boot Actuator liveness probe
# JVM flags: G1GC + MaxRAMPercentage=75 (honors container memory limit)
# No secrets: DB creds injected at runtime via env vars or Vault CSI mount
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1 — Build
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build

LABEL stage=build

WORKDIR /app

# Install system Maven as the build tool.
# In production CI, generate a Maven wrapper with:
#   mvn -N io.takari:maven:wrapper
# and commit .mvn/ + mvnw so the build stage can switch to:
#   COPY .mvn/ .mvn/
#   COPY mvnw ./
#   RUN chmod +x mvnw && ./mvnw -q -DskipTests package
RUN apk add --no-cache maven

# Copy POM first — dependency layer cached until pom.xml changes
COPY pom.xml ./

# Download dependencies offline — cached layer (invalidated only on pom.xml change)
RUN mvn -q dependency:go-offline -B

# Copy source and build the fat JAR (tests run separately in CI unit-test stage)
COPY src/ ./src/
RUN mvn -q -B -DskipTests package && ls -lh target/*.jar

# ---------------------------------------------------------------------------
# Stage 2 — Runtime (minimal JRE, no build tooling)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Add non-root group and user; -S = system account (no home dir, no login shell)
RUN addgroup -S app && adduser -S app -G app

# App directory owned by app user
WORKDIR /app
RUN chown app:app /app

# Copy the fat JAR from the build stage
COPY --from=build --chown=app:app /app/target/*.jar app.jar

# Drop to non-root user — K8s restricted PodSecurity profile requires this
USER app

# App port (HTTP)
EXPOSE 8080
# Management / actuator port (separate from app traffic — Security ITEM-9)
EXPOSE 9090

# Health check for local docker run / docker compose (K8s probes are authoritative in cluster)
# Uses /actuator/health/liveness — Spring Boot Actuator liveness group
HEALTHCHECK \
    --interval=15s \
    --timeout=5s \
    --start-period=60s \
    --retries=3 \
    CMD wget -q --spider http://localhost:9090/actuator/health/liveness || exit 1

# JVM flags:
#   -XX:+UseG1GC            — G1 garbage collector (default for microservice latency profile)
#   -XX:MaxRAMPercentage=75 — JVM heap = 75% of container memory limit (prevents OOMKill)
#   -XX:+ExitOnOutOfMemoryError — fail fast rather than thrash in degraded state
#   -Djava.security.egd=...   — faster SecureRandom in containers (no blocking /dev/random)
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
