#!/usr/bin/env bash
# =============================================================================
# run-local.sh — Start balance-dashboard-service local development environment
#
# Usage:
#   ./run-local.sh infra     Start Redis + Kafka only (run BE/FE from IDE)
#   ./run-local.sh be        Start infra + compile + run Spring Boot (port 8080)
#   ./run-local.sh fe        Start Angular dev server (port 4200)
#   ./run-local.sh all       Start infra + BE + FE
#   ./run-local.sh stop      Stop all docker containers
#   ./run-local.sh status    Show what's running
# =============================================================================

set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[run-local]${NC} $*"; }
warn()  { echo -e "${YELLOW}[run-local]${NC} $*"; }
error() { echo -e "${RED}[run-local]${NC} $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
check_deps() {
  command -v docker  >/dev/null 2>&1 || error "Docker not found"
  command -v java    >/dev/null 2>&1 || error "Java not found (need Java 21+)"
  command -v mvn     >/dev/null 2>&1 || error "Maven not found"
  command -v node    >/dev/null 2>&1 || error "Node.js not found"
}

# ---------------------------------------------------------------------------
start_infra() {
  info "Starting Redis + Kafka..."
  docker compose up -d redis kafka
  info "Waiting for Redis..."
  until docker compose exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; do
    printf '.'; sleep 2
  done
  echo ""
  info "Redis ready ✅"
  info "Waiting for Kafka (up to 60s)..."
  for i in $(seq 1 30); do
    if docker compose exec -T kafka kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
      info "Kafka ready ✅"
      return
    fi
    printf '.'; sleep 2
  done
  warn "Kafka health check timed out — continuing anyway"
}

# ---------------------------------------------------------------------------
start_be() {
  info "Compiling balance-dashboard-service..."
  mvn compile -pl backend/balance-dashboard-service -am -q || error "Compile failed — run 'mvn compile -pl backend/balance-dashboard-service -am' for details"
  info "Compile ✅"

  info "Starting Spring Boot on http://localhost:8080 ..."
  info "(Press Ctrl+C to stop)"
  BALANCE_DASHBOARD_ENABLED=true \
  REDIS_HOST=localhost \
  REDIS_PORT=6379 \
  SPRING_DATA_REDIS_SSL_ENABLED=false \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:9000 \
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://localhost:9000/oauth2/jwks \
    mvn spring-boot:run -pl backend/balance-dashboard-service \
      -Dspring-boot.run.profiles=dev \
      -Dspring-boot.run.jvmArguments="-XX:+UseContainerSupport"
}

# ---------------------------------------------------------------------------
start_fe() {
  info "Installing npm dependencies..."
  cd frontend
  npm install --silent
  info "Starting Angular dev server on http://localhost:4200 ..."
  info "(Press Ctrl+C to stop)"
  npx ng serve --open
}

# ---------------------------------------------------------------------------
case "${1:-help}" in
  infra)
    check_deps
    start_infra
    info "Infra running. Backend: run './run-local.sh be' | Frontend: './run-local.sh fe'"
    ;;
  be)
    check_deps
    start_infra
    start_be
    ;;
  fe)
    check_deps
    start_fe
    ;;
  all)
    check_deps
    start_infra
    start_be &
    BE_PID=$!
    start_fe &
    FE_PID=$!
    info "BE pid=$BE_PID | FE pid=$FE_PID"
    wait
    ;;
  stop)
    info "Stopping all containers..."
    docker compose down
    info "Done"
    ;;
  status)
    echo ""
    docker compose ps
    echo ""
    info "BE health: $(curl -sf http://localhost:8080/actuator/health 2>/dev/null | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("status","?"))' 2>/dev/null || echo 'not running')"
    info "FE:        $(curl -sf http://localhost:4200 >/dev/null 2>&1 && echo 'running' || echo 'not running')"
    ;;
  help|*)
    echo ""
    echo "Usage: ./run-local.sh [infra|be|fe|all|stop|status]"
    echo ""
    echo "  infra   — Start Redis + Kafka only"
    echo "  be      — Start infra + Spring Boot (http://localhost:8080)"
    echo "  fe      — Start Angular dev server  (http://localhost:4200)"
    echo "  all     — Start everything"
    echo "  stop    — Stop all docker containers"
    echo "  status  — Show health of running services"
    echo ""
    ;;
esac
