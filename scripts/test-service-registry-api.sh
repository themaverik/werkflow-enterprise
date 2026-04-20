#!/bin/bash

##############################################################################
# Service Registry API Integration Test Script
#
# Purpose: Test all Service Registry API endpoints
# Usage: ./scripts/test-service-registry-api.sh
# Requirements: Backend must be running at http://localhost:8081
##############################################################################

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# API Base URL
API_BASE="http://localhost:8081/api/services"

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print test header
test_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}TEST: $1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Function to print test result
test_result() {
    TESTS_RUN=$((TESTS_RUN + 1))
    if [ $1 -eq 0 ]; then
        TESTS_PASSED=$((TESTS_PASSED + 1))
        echo -e "${GREEN}PASS${NC}: $2"
    else
        TESTS_FAILED=$((TESTS_FAILED + 1))
        echo -e "${RED}FAIL${NC}: $2"
        if [ ! -z "$3" ]; then
            echo -e "${RED}Error: $3${NC}"
        fi
    fi
}

# Function to print section
section() {
    echo ""
    echo -e "${YELLOW}>>> $1${NC}"
    echo ""
}

##############################################################################
# PRE-FLIGHT CHECKS
##############################################################################

section "Pre-flight Checks"

# Check if backend is running
test_header "Backend Health Check"
if curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
    test_result 0 "Backend is running"
else
    test_result 1 "Backend is NOT running" "Please start backend: cd infrastructure/docker && docker-compose up -d"
    echo ""
    echo -e "${RED}ABORTING: Backend must be running to continue tests${NC}"
    exit 1
fi

# Check if jq is available (for JSON parsing)
if command -v jq > /dev/null 2>&1; then
    test_result 0 "jq JSON parser is available"
else
    test_result 1 "jq JSON parser is NOT available" "Install with: brew install jq (macOS) or apt-get install jq (Ubuntu)"
    echo ""
    echo -e "${YELLOW}WARNING: Some test output may be harder to read without jq${NC}"
fi

##############################################################################
# TEST 1: GET ALL SERVICES
##############################################################################

section "Test 1: Get All Services"

test_header "GET /api/services"
RESPONSE=$(curl -s -w "\n%{http_code}" $API_BASE)
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    test_result 0 "GET /api/services returns 200 OK"

    # Check if response is valid JSON array
    if echo "$BODY" | jq -e 'type == "array"' > /dev/null 2>&1; then
        test_result 0 "Response is a valid JSON array"

        # Count services
        SERVICE_COUNT=$(echo "$BODY" | jq 'length')
        test_result 0 "Found $SERVICE_COUNT services"

        # Check if default services exist
        FINANCE_EXISTS=$(echo "$BODY" | jq '[.[] | select(.serviceName == "finance")] | length')
        if [ "$FINANCE_EXISTS" -gt 0 ]; then
            test_result 0 "Finance service is registered"
        else
            test_result 1 "Finance service is NOT registered"
        fi

        HR_EXISTS=$(echo "$BODY" | jq '[.[] | select(.serviceName == "hr")] | length')
        if [ "$HR_EXISTS" -gt 0 ]; then
            test_result 0 "HR service is registered"
        else
            test_result 1 "HR service is NOT registered"
        fi

        # Print first service as example
        echo ""
        echo -e "${BLUE}Example Service (First in list):${NC}"
        echo "$BODY" | jq '.[0]' 2>/dev/null || echo "$BODY" | head -n 20

    else
        test_result 1 "Response is NOT a valid JSON array"
    fi
else
    test_result 1 "GET /api/services returned HTTP $HTTP_CODE" "$BODY"
fi

##############################################################################
# TEST 2: GET SERVICE BY NAME
##############################################################################

section "Test 2: Get Service by Name"

test_header "GET /api/services/by-name/finance"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_BASE/by-name/finance")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    test_result 0 "GET /api/services/by-name/finance returns 200 OK"

    # Check if serviceName field exists and equals "finance"
    if echo "$BODY" | jq -e '.serviceName == "finance"' > /dev/null 2>&1; then
        test_result 0 "Service name is 'finance'"
    else
        test_result 1 "Service name is NOT 'finance'"
    fi

    # Check required fields
    if echo "$BODY" | jq -e 'has("displayName")' > /dev/null 2>&1; then
        test_result 0 "Service has 'displayName' field"
    else
        test_result 1 "Service is missing 'displayName' field"
    fi

    if echo "$BODY" | jq -e 'has("serviceType")' > /dev/null 2>&1; then
        test_result 0 "Service has 'serviceType' field"
    else
        test_result 1 "Service is missing 'serviceType' field"
    fi

elif [ "$HTTP_CODE" = "404" ]; then
    test_result 1 "Finance service not found (404)" "Service may not be seeded yet"
else
    test_result 1 "GET /api/services/by-name/finance returned HTTP $HTTP_CODE" "$BODY"
fi

##############################################################################
# TEST 3: CREATE SERVICE
##############################################################################

section "Test 3: Create Service"

test_header "POST /api/services"
CREATE_PAYLOAD='{
  "serviceName": "test-service",
  "displayName": "Test Service",
  "description": "Test service for API integration",
  "serviceType": "REST_API",
  "baseUrl": "http://test-service:8090/api"
}'

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE" \
    -H "Content-Type: application/json" \
    -d "$CREATE_PAYLOAD")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | head -n -1)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
    test_result 0 "POST /api/services returns HTTP $HTTP_CODE"

    # Extract service ID for later tests
    SERVICE_ID=$(echo "$BODY" | jq -r '.id' 2>/dev/null || echo "")

    if [ ! -z "$SERVICE_ID" ] && [ "$SERVICE_ID" != "null" ]; then
        test_result 0 "Created service has ID: $SERVICE_ID"
        echo ""
        echo -e "${BLUE}Created Service:${NC}"
        echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    else
        test_result 1 "Created service does not have valid ID"
    fi

elif [ "$HTTP_CODE" = "409" ]; then
    test_result 1 "Service 'test-service' already exists (409)" "This is expected if test ran before. Continuing..."
    # Try to get existing service
    RESPONSE=$(curl -s -w "\n%{http_code}" "$API_BASE/by-name/test-service")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    if [ "$HTTP_CODE" = "200" ]; then
        SERVICE_ID=$(echo "$BODY" | jq -r '.id' 2>/dev/null || echo "")
        echo -e "${YELLOW}Using existing test-service with ID: $SERVICE_ID${NC}"
    fi
else
    test_result 1 "POST /api/services returned HTTP $HTTP_CODE" "$BODY"
fi

##############################################################################
# TEST 4: UPDATE SERVICE
##############################################################################

section "Test 4: Update Service"

if [ ! -z "$SERVICE_ID" ] && [ "$SERVICE_ID" != "null" ]; then
    test_header "PUT /api/services/$SERVICE_ID"
    UPDATE_PAYLOAD='{
      "displayName": "Test Service (Updated)",
      "description": "Updated test service description"
    }'

    RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$API_BASE/$SERVICE_ID" \
        -H "Content-Type: application/json" \
        -d "$UPDATE_PAYLOAD")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)

    if [ "$HTTP_CODE" = "200" ]; then
        test_result 0 "PUT /api/services/$SERVICE_ID returns 200 OK"

        # Check if displayName was updated
        DISPLAY_NAME=$(echo "$BODY" | jq -r '.displayName' 2>/dev/null || echo "")
        if [ "$DISPLAY_NAME" = "Test Service (Updated)" ]; then
            test_result 0 "Service displayName was updated correctly"
        else
            test_result 1 "Service displayName was NOT updated (got: $DISPLAY_NAME)"
        fi
    else
        test_result 1 "PUT /api/services/$SERVICE_ID returned HTTP $HTTP_CODE" "$BODY"
    fi
else
    test_result 1 "Skipping update test (no valid SERVICE_ID)"
fi

##############################################################################
# TEST 5: ADD ENVIRONMENT URL
##############################################################################

section "Test 5: Add Environment URL"

if [ ! -z "$SERVICE_ID" ] && [ "$SERVICE_ID" != "null" ]; then
    test_header "POST /api/services/$SERVICE_ID/urls"
    URL_PAYLOAD='{
      "environment": "development",
      "baseUrl": "http://localhost:8090/api",
      "priority": 1,
      "isActive": true
    }'

    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE/$SERVICE_ID/urls" \
        -H "Content-Type: application/json" \
        -d "$URL_PAYLOAD")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)

    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        test_result 0 "POST /api/services/$SERVICE_ID/urls returns HTTP $HTTP_CODE"

        # Check if environment URL was created
        ENV=$(echo "$BODY" | jq -r '.environment' 2>/dev/null || echo "")
        if [ "$ENV" = "development" ]; then
            test_result 0 "Environment URL created for 'development'"
        else
            test_result 1 "Environment URL was NOT created correctly"
        fi
    else
        test_result 1 "POST /api/services/$SERVICE_ID/urls returned HTTP $HTTP_CODE" "$BODY"
    fi
else
    test_result 1 "Skipping environment URL test (no valid SERVICE_ID)"
fi

##############################################################################
# TEST 6: GET SERVICE URLS
##############################################################################

section "Test 6: Get Service URLs"

if [ ! -z "$SERVICE_ID" ] && [ "$SERVICE_ID" != "null" ]; then
    test_header "GET /api/services/$SERVICE_ID/urls"
    RESPONSE=$(curl -s -w "\n%{http_code}" "$API_BASE/$SERVICE_ID/urls")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)

    if [ "$HTTP_CODE" = "200" ]; then
        test_result 0 "GET /api/services/$SERVICE_ID/urls returns 200 OK"

        # Check if response is array
        if echo "$BODY" | jq -e 'type == "array"' > /dev/null 2>&1; then
            test_result 0 "Response is a valid JSON array"

            URL_COUNT=$(echo "$BODY" | jq 'length')
            test_result 0 "Found $URL_COUNT environment URL(s)"
        else
            test_result 1 "Response is NOT a valid JSON array"
        fi
    else
        test_result 1 "GET /api/services/$SERVICE_ID/urls returned HTTP $HTTP_CODE" "$BODY"
    fi
else
    test_result 1 "Skipping get URLs test (no valid SERVICE_ID)"
fi

##############################################################################
# TEST 7: RESOLVE SERVICE URL
##############################################################################

section "Test 7: Resolve Service URL"

test_header "GET /api/services/resolve/test-service?env=development"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_BASE/resolve/test-service?env=development")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    test_result 0 "GET /api/services/resolve/test-service returns 200 OK"

    # Check if URL field exists
    URL=$(echo "$BODY" | jq -r '.url // .baseUrl' 2>/dev/null || echo "")
    if [ ! -z "$URL" ] && [ "$URL" != "null" ]; then
        test_result 0 "Resolved URL: $URL"
    else
        test_result 1 "Response does not contain URL field"
    fi
elif [ "$HTTP_CODE" = "404" ]; then
    test_result 1 "Service URL not found for test-service/development" "Environment URL may not be configured"
else
    test_result 1 "GET /api/services/resolve/test-service returned HTTP $HTTP_CODE" "$BODY"
fi

##############################################################################
# TEST 8: HEALTH CHECK
##############################################################################

section "Test 8: Health Check"

if [ ! -z "$SERVICE_ID" ] && [ "$SERVICE_ID" != "null" ]; then
    test_header "POST /api/services/$SERVICE_ID/health/check"
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE/$SERVICE_ID/health/check")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)

    if [ "$HTTP_CODE" = "200" ]; then
        test_result 0 "POST /api/services/$SERVICE_ID/health/check returns 200 OK"

        # Check if status field exists
        STATUS=$(echo "$BODY" | jq -r '.status' 2>/dev/null || echo "")
        if [ ! -z "$STATUS" ]; then
            test_result 0 "Health status: $STATUS"
        else
            test_result 1 "Response does not contain status field"
        fi
    else
        test_result 1 "POST /api/services/$SERVICE_ID/health/check returned HTTP $HTTP_CODE" "$BODY"
    fi
else
    test_result 1 "Skipping health check test (no valid SERVICE_ID)"
fi

##############################################################################
# TEST 9: DELETE SERVICE
##############################################################################

section "Test 9: Delete Service"

if [ ! -z "$SERVICE_ID" ] && [ "$SERVICE_ID" != "null" ]; then
    test_header "DELETE /api/services/$SERVICE_ID"
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$API_BASE/$SERVICE_ID")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)

    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "204" ]; then
        test_result 0 "DELETE /api/services/$SERVICE_ID returns HTTP $HTTP_CODE"

        # Verify service is deleted
        RESPONSE=$(curl -s -w "\n%{http_code}" "$API_BASE/$SERVICE_ID")
        HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)

        if [ "$HTTP_CODE" = "404" ]; then
            test_result 0 "Service was successfully deleted (404 on GET)"
        else
            test_result 1 "Service still exists after deletion (HTTP $HTTP_CODE)"
        fi
    else
        test_result 1 "DELETE /api/services/$SERVICE_ID returned HTTP $HTTP_CODE"
    fi
else
    test_result 1 "Skipping delete test (no valid SERVICE_ID)"
fi

##############################################################################
# TEST SUMMARY
##############################################################################

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}TEST SUMMARY${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Total Tests Run:    $TESTS_RUN"
echo -e "${GREEN}Tests Passed:       $TESTS_PASSED${NC}"
echo -e "${RED}Tests Failed:       $TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}ALL TESTS PASSED!${NC}"
    echo ""
    echo -e "${GREEN}Service Registry API is working correctly.${NC}"
    echo -e "${GREEN}Frontend integration can proceed.${NC}"
    exit 0
else
    echo -e "${RED}SOME TESTS FAILED!${NC}"
    echo ""
    echo -e "${YELLOW}Please review the failed tests above and fix any issues.${NC}"
    echo -e "${YELLOW}Check backend logs for errors: docker-compose logs engine${NC}"
    exit 1
fi
