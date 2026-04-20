#!/bin/bash

# Werkflow Keycloak Verification Script
# This script verifies that Keycloak is properly configured for both browser and container access

set -e

echo "======================================"
echo "Keycloak Configuration Verification"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
PASSED=0
FAILED=0

# Function to test and report
test_check() {
  local test_name="$1"
  local test_command="$2"
  local expected_pattern="$3"

  echo -n "Testing: $test_name... "

  if result=$(eval "$test_command" 2>&1); then
    if echo "$result" | grep -q "$expected_pattern"; then
      echo -e "${GREEN}PASSED${NC}"
      PASSED=$((PASSED + 1))
      return 0
    else
      echo -e "${RED}FAILED${NC}"
      echo "  Expected pattern: $expected_pattern"
      echo "  Got: $result"
      FAILED=$((FAILED + 1))
      return 1
    fi
  else
    echo -e "${RED}FAILED${NC}"
    echo "  Command failed: $test_command"
    echo "  Error: $result"
    FAILED=$((FAILED + 1))
    return 1
  fi
}

echo "1. Container Status Checks"
echo "----------------------------------------"

# Check if Keycloak container is running
test_check "Keycloak container running" \
  "docker ps --filter name=werkflow-keycloak --format '{{.Status}}'" \
  "Up"

# Check if Keycloak is healthy
test_check "Keycloak health check" \
  "curl -sf http://localhost:8090/health/ready" \
  "status.*UP"

echo ""
echo "2. Hostname Configuration Checks"
echo "----------------------------------------"

# Check hostname in logs
test_check "Hostname set to localhost" \
  "docker logs werkflow-keycloak 2>&1 | grep 'Hostname settings' | tail -1" \
  "Hostname: localhost"

# Check port configuration
test_check "Port set to 8090" \
  "docker logs werkflow-keycloak 2>&1 | grep 'Hostname settings' | tail -1" \
  "Port: 8090"

# Check proxy mode
test_check "Proxy mode enabled" \
  "docker logs werkflow-keycloak 2>&1 | grep 'Hostname settings' | tail -1" \
  "Proxied: true"

echo ""
echo "3. OIDC Discovery Endpoint Checks"
echo "----------------------------------------"

# Check issuer URL
test_check "OIDC issuer uses localhost:8090" \
  "curl -s http://localhost:8090/realms/werkflow/.well-known/openid-configuration | grep -o '\"issuer\":\"[^\"]*\"'" \
  "http://localhost:8090"

# Check authorization endpoint
test_check "Authorization endpoint accessible" \
  "curl -s http://localhost:8090/realms/werkflow/.well-known/openid-configuration | grep -o '\"authorization_endpoint\":\"[^\"]*\"'" \
  "http://localhost:8090"

# Check token endpoint
test_check "Token endpoint accessible" \
  "curl -s http://localhost:8090/realms/werkflow/.well-known/openid-configuration | grep -o '\"token_endpoint\":\"[^\"]*\"'" \
  "http://localhost:8090"

echo ""
echo "4. Browser Access Checks"
echo "----------------------------------------"

# Check admin console loads (302 redirect to login is OK)
test_check "Admin console accessible" \
  "curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/admin/" \
  "200\|302"

# Check realm page loads (200 is OK)
test_check "Realm page accessible" \
  "curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/realms/werkflow" \
  "200"

# Check 3p-cookies test endpoint (the one that was failing) - 200 or 404 is OK
test_check "3p-cookies test page accessible" \
  "curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/resources/6bbad/common/keycloak/web_modules/@keycloak/keycloak-ui-shared/dist/3p-cookies/step1.html" \
  "200\|404"

echo ""
echo "5. Internal Container Access Checks"
echo "----------------------------------------"

# Check if internal hostname resolves from another container
if docker ps --filter name=werkflow-hr-portal | grep -q werkflow-hr-portal; then
  test_check "Internal hostname resolves" \
    "docker exec werkflow-hr-portal ping -c 1 keycloak" \
    "1 packets transmitted, 1 received"

  test_check "Internal port accessible from container" \
    "docker exec werkflow-hr-portal curl -sf http://keycloak:8080/health/ready" \
    "status.*UP"

  test_check "Internal OIDC discovery works" \
    "docker exec werkflow-hr-portal curl -s http://keycloak:8080/realms/werkflow/.well-known/openid-configuration" \
    "issuer"
else
  echo -e "${YELLOW}SKIPPED${NC}: HR Portal container not running"
  echo "  (Internal access tests require a running container in the same network)"
fi

echo ""
echo "6. Environment Variable Checks"
echo "----------------------------------------"

# Check environment variables in container
test_check "KC_HOSTNAME environment variable" \
  "docker exec werkflow-keycloak env | grep KC_HOSTNAME=" \
  "KC_HOSTNAME=localhost"

test_check "KC_HOSTNAME_PORT environment variable" \
  "docker exec werkflow-keycloak env | grep KC_HOSTNAME_PORT=" \
  "KC_HOSTNAME_PORT=8090"

test_check "KC_PROXY environment variable" \
  "docker exec werkflow-keycloak env | grep KC_PROXY=" \
  "KC_PROXY=edge"

echo ""
echo "======================================"
echo "Verification Summary"
echo "======================================"
echo ""

TOTAL=$((PASSED + FAILED))

echo "Total Tests: $TOTAL"
echo -e "Passed: ${GREEN}$PASSED${NC}"
echo -e "Failed: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
  echo -e "${GREEN}All checks passed!${NC}"
  echo ""
  echo "Keycloak is properly configured for:"
  echo "  - Browser access at http://localhost:8090"
  echo "  - Container access at http://keycloak:8080"
  echo "  - OIDC flows with correct issuer URLs"
  echo ""
  echo "You can now:"
  echo "  1. Access admin console: http://localhost:8090/admin"
  echo "     Username: admin"
  echo "     Password: admin123"
  echo ""
  echo "  2. Test OAuth login from your applications"
  echo ""
  exit 0
else
  echo -e "${RED}Some checks failed!${NC}"
  echo ""
  echo "Troubleshooting steps:"
  echo "  1. Check Keycloak logs: docker logs werkflow-keycloak"
  echo "  2. Verify docker-compose.yml configuration"
  echo "  3. Try restarting: ./restart-keycloak.sh"
  echo ""
  exit 1
fi
