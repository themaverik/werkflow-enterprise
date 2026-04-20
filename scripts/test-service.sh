#!/bin/bash

# Werkflow Service Tester
# Usage: ./scripts/test-service.sh [service-name]
# Example: ./scripts/test-service.sh engine
#          ./scripts/test-service.sh all

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SERVICE=${1:-all}

test_engine() {
  echo -e "${BLUE}Testing Engine Service (8081)${NC}"
  echo "========================================"

  # Health check
  echo -n "Health check... "
  if curl -sf http://localhost:8081/actuator/health > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
    return 1
  fi

  # Flowable REST API
  echo -n "Flowable REST API... "
  if curl -sf -u admin:test http://localhost:8081/flowable-rest/service/repository/process-definitions > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
    return 1
  fi

  # List process definitions
  echo "Process definitions:"
  curl -sf -u admin:test http://localhost:8081/flowable-rest/service/repository/process-definitions | jq -r '.data[] | "  - \(.name) (\(.key)) v\(.version)"' || echo "  (none deployed)"

  echo ""
}

test_hr() {
  echo -e "${BLUE}Testing HR Service (8082)${NC}"
  echo "========================================"

  echo -n "Health check... "
  if curl -sf http://localhost:8082/actuator/health > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
    return 1
  fi

  echo -n "Swagger UI... "
  if curl -sf http://localhost:8082/api/swagger-ui.html > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
  fi

  echo ""
}

test_admin() {
  echo -e "${BLUE}Testing Admin Service (8083)${NC}"
  echo "========================================"

  echo -n "Health check... "
  if curl -sf http://localhost:8083/actuator/health > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${YELLOW}⚠️  Service may not be implemented${NC}"
  fi

  echo ""
}

test_finance() {
  echo -e "${BLUE}Testing Finance Service (8084)${NC}"
  echo "========================================"

  echo -n "Health check... "
  if curl -sf http://localhost:8084/actuator/health > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
    return 1
  fi

  echo -n "Budget categories endpoint... "
  if curl -sf http://localhost:8084/api/finance/budget-categories > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
  fi

  echo ""
}

test_procurement() {
  echo -e "${BLUE}Testing Procurement Service (8085)${NC}"
  echo "========================================"

  echo -n "Health check... "
  if curl -sf http://localhost:8085/actuator/health > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
    return 1
  fi

  echo -n "Vendors endpoint... "
  if curl -sf http://localhost:8085/api/procurement/vendors > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
  fi

  echo ""
}

test_inventory() {
  echo -e "${BLUE}Testing Inventory Service (8086)${NC}"
  echo "========================================"

  echo -n "Health check... "
  if curl -sf http://localhost:8086/actuator/health > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
    return 1
  fi

  echo -n "Asset categories endpoint... "
  if curl -sf http://localhost:8086/api/inventory/categories > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
  fi

  echo ""
}

test_portal() {
  echo -e "${BLUE}Testing Admin Portal (4000)${NC}"
  echo "========================================"

  echo -n "Home page... "
  if curl -sf http://localhost:4000 > /dev/null; then
    echo -e "${GREEN}✅${NC}"
  else
    echo -e "${RED}❌${NC}"
    return 1
  fi

  echo -e "${YELLOW}Manual testing required for:${NC}"
  echo "  - Login/Authentication"
  echo "  - BPMN Designer"
  echo "  - Form Builder"
  echo "  - Multi-Department Dashboard"
  echo "  - Monitoring Dashboard"
  echo "  - Analytics Dashboard"
  echo "  - Task Portal"

  echo ""
}

# Main
case $SERVICE in
  engine)
    test_engine
    ;;
  hr)
    test_hr
    ;;
  admin)
    test_admin
    ;;
  finance)
    test_finance
    ;;
  procurement)
    test_procurement
    ;;
  inventory)
    test_inventory
    ;;
  portal)
    test_portal
    ;;
  all)
    test_engine
    test_hr
    test_admin
    test_finance
    test_procurement
    test_inventory
    test_portal
    ;;
  *)
    echo "Usage: $0 [service-name]"
    echo ""
    echo "Available services:"
    echo "  engine       - Engine Service (8081)"
    echo "  hr           - HR Service (8082)"
    echo "  admin        - Admin Service (8083)"
    echo "  finance      - Finance Service (8084)"
    echo "  procurement  - Procurement Service (8085)"
    echo "  inventory    - Inventory Service (8086)"
    echo "  portal       - Admin Portal (4000)"
    echo "  all          - Test all services"
    exit 1
    ;;
esac

echo -e "${GREEN}✅ Testing complete${NC}"
