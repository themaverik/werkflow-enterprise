#!/bin/bash

# Werkflow Service Health Check Script
# Usage: ./scripts/health-check.sh

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "============================================"
echo "  Werkflow Service Health Check"
echo "============================================"
echo ""

# Function to check service health
check_service() {
  local name=$1
  local port=$2
  local check_command=$3

  printf "%-25s [Port %-5s] " "$name" "$port"

  if eval "$check_command" 2>/dev/null; then
    echo -e "${GREEN}✅ UP${NC}"
    return 0
  else
    echo -e "${RED}❌ DOWN${NC}"
    return 1
  fi
}

# Track failures
failed=0
total=0

# Infrastructure Services
echo "📦 Infrastructure Services"
echo "----------------------------------------"

((total++))
check_service "PostgreSQL" "5433" "nc -z localhost 5433" || ((failed++))

((total++))
check_service "Keycloak" "8090" "curl -sf -m 5 http://localhost:8090/health > /dev/null" || ((failed++))

((total++))
check_service "pgAdmin" "5050" "curl -sf -m 5 http://localhost:5050 > /dev/null" || ((failed++))

echo ""

# Backend Services
echo "🔧 Backend Services"
echo "----------------------------------------"

((total++))
check_service "Engine Service" "8081" "curl -sf -m 5 http://localhost:8081/actuator/health > /dev/null" || ((failed++))

((total++))
check_service "HR Service" "8082" "curl -sf -m 5 http://localhost:8082/actuator/health > /dev/null" || ((failed++))

((total++))
check_service "Admin Service" "8083" "curl -sf -m 5 http://localhost:8083/actuator/health > /dev/null" || ((failed++))

((total++))
check_service "Finance Service" "8084" "curl -sf -m 5 http://localhost:8084/actuator/health > /dev/null" || ((failed++))

((total++))
check_service "Procurement Service" "8085" "curl -sf -m 5 http://localhost:8085/actuator/health > /dev/null" || ((failed++))

((total++))
check_service "Inventory Service" "8086" "curl -sf -m 5 http://localhost:8086/actuator/health > /dev/null" || ((failed++))

echo ""

# Frontend Applications
echo "🎨 Frontend Applications"
echo "----------------------------------------"

((total++))
check_service "Admin Portal" "4000" "curl -sf -m 5 http://localhost:4000 > /dev/null" || ((failed++))

((total++))
check_service "HR Portal" "4001" "curl -sf -m 5 http://localhost:4001 > /dev/null" || ((failed++))

echo ""
echo "============================================"
echo "  Summary"
echo "============================================"
echo ""

passed=$((total - failed))

echo "Total Services: $total"
echo -e "Passed: ${GREEN}$passed${NC}"
if [ $failed -gt 0 ]; then
  echo -e "Failed: ${RED}$failed${NC}"
else
  echo -e "Failed: $failed"
fi

echo ""

if [ $failed -eq 0 ]; then
  echo -e "${GREEN}✅ All services are healthy${NC}"
  exit 0
else
  echo -e "${YELLOW}⚠️  Some services are down. Check logs for details.${NC}"
  echo ""
  echo "Troubleshooting tips:"
  echo "  1. Check Docker containers: docker-compose ps"
  echo "  2. View service logs: docker logs <container-name>"
  echo "  3. Check port conflicts: lsof -i :<port>"
  echo "  4. Restart services: docker-compose restart <service>"
  exit 1
fi
