#!/usr/bin/env bash
# =============================================================================
# Smoke test script — transfer-service
# Runs against staging (or locally if BASE_URL overridden)
# Usage: BASE_URL=https://transfer.staging.example.com ./smoke-test.sh
#        BASE_URL=http://localhost:8080 MGMT_URL=http://localhost:9090 ./smoke-test.sh
# =============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-https://transfer.staging.example.com}"
MGMT_URL="${MGMT_URL:-${BASE_URL}}"
JWT_TOKEN="${SMOKE_TEST_JWT:-}"
IDEMPOTENCY_KEY="smoke-$(date +%s%N | sha256sum | head -c 8)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

echo "=== transfer-service smoke tests ==="
echo "  Target: ${BASE_URL}"
echo "  Management: ${MGMT_URL}"
echo ""

# Test 1: Liveness probe
echo "--- Test 1: Liveness probe ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${MGMT_URL}/actuator/health/liveness")
[ "$STATUS" = "200" ] && pass "Liveness: HTTP $STATUS" || fail "Liveness: HTTP $STATUS (expected 200)"

# Test 2: Readiness probe
echo "--- Test 2: Readiness probe ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${MGMT_URL}/actuator/health/readiness")
[ "$STATUS" = "200" ] && pass "Readiness: HTTP $STATUS" || fail "Readiness: HTTP $STATUS (expected 200)"

# Test 3: Actuator health components
echo "--- Test 3: Health components ---"
HEALTH=$(curl -s "${MGMT_URL}/actuator/health")
DB_STATUS=$(echo "$HEALTH" | grep -o '"db":{[^}]*}' | grep -o '"status":"[^"]*"' | head -1 || echo "not_found")
echo "  DB health: $DB_STATUS"

# Test 4: POST /api/v1/transfers (missing Idempotency-Key -> 400)
echo "--- Test 4: POST without Idempotency-Key -> HTTP 400 ---"
AUTH_HEADER=""
[ -n "$JWT_TOKEN" ] && AUTH_HEADER="-H \"Authorization: Bearer ${JWT_TOKEN}\""
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "${BASE_URL}/api/v1/transfers" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId":"00000000-0000-0000-0000-000000000001","destinationAccountId":"00000000-0000-0000-0000-000000000002","amount":"100.00","currency":"THB"}')
[ "$STATUS" = "400" ] && pass "Missing Idempotency-Key: HTTP $STATUS (400 expected)" \
  || warn "Missing Idempotency-Key: HTTP $STATUS (expected 400 — may be 401 if JWT required)"

# Test 5: POST /api/v1/transfers (valid request structure)
echo "--- Test 5: POST with Idempotency-Key ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "${BASE_URL}/api/v1/transfers" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d '{"sourceAccountId":"00000000-0000-0000-0000-000000000001","destinationAccountId":"00000000-0000-0000-0000-000000000002","amount":"100.00","currency":"THB"}')
if [[ "$STATUS" =~ ^(200|201|401|422)$ ]]; then
  pass "POST with key: HTTP $STATUS (live API)"
else
  fail "POST with key: HTTP $STATUS (unexpected)"
fi

# Test 6: Prometheus metrics endpoint
echo "--- Test 6: Prometheus metrics scrape ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${MGMT_URL}/actuator/prometheus")
[ "$STATUS" = "200" ] && pass "Prometheus metrics: HTTP $STATUS" \
  || warn "Prometheus metrics: HTTP $STATUS (may be 401 if management port locked down)"

echo ""
echo "=== Smoke tests complete ==="
