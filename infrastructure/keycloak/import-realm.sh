#!/bin/bash

# ================================================================
# Keycloak Realm Import Script
# ================================================================
# Imports werkflow-platform realm into Keycloak
# Usage: ./import-realm.sh
# ================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REALM_FILE="${SCRIPT_DIR}/werkflow-realm.json"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin123}"

echo "================================================================"
echo "Keycloak Realm Import"
echo "================================================================"
echo "Keycloak URL: ${KEYCLOAK_URL}"
echo "Realm File: ${REALM_FILE}"
echo ""

# Check if realm file exists
if [ ! -f "${REALM_FILE}" ]; then
    echo "Error: Realm file not found: ${REALM_FILE}"
    exit 1
fi

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
MAX_RETRIES=30
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -sf "${KEYCLOAK_URL}/health/ready" > /dev/null 2>&1; then
        echo "Keycloak is ready!"
        break
    fi
    echo "Waiting for Keycloak... (attempt $((RETRY_COUNT + 1))/${MAX_RETRIES})"
    sleep 2
    RETRY_COUNT=$((RETRY_COUNT + 1))
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo "Error: Keycloak did not become ready in time"
    exit 1
fi

echo ""
echo "Getting admin access token..."

# Get admin token
TOKEN_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASSWORD}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli")

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
    echo "Error: Failed to get access token"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
fi

echo "Access token obtained successfully"
echo ""

# Check if realm already exists
echo "Checking if realm already exists..."
REALM_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${KEYCLOAK_URL}/admin/realms/werkflow-platform" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

if [ "$REALM_EXISTS" == "200" ]; then
    echo "Realm 'werkflow-platform' already exists"
    read -p "Do you want to delete and recreate it? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Deleting existing realm..."
        curl -s -X DELETE "${KEYCLOAK_URL}/admin/realms/werkflow-platform" \
          -H "Authorization: Bearer ${ACCESS_TOKEN}"
        echo "Realm deleted"
    else
        echo "Import cancelled"
        exit 0
    fi
fi

echo ""
echo "Importing realm from ${REALM_FILE}..."

# Import realm
IMPORT_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/admin/realms" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${REALM_FILE}")

if [ $? -eq 0 ]; then
    echo "Realm imported successfully!"
else
    echo "Error importing realm"
    echo "Response: $IMPORT_RESPONSE"
    exit 1
fi

echo ""
echo "================================================================"
echo "Verifying realm configuration..."
echo "================================================================"

# Verify realm
REALM_INFO=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow-platform" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

echo "Realm: $(echo $REALM_INFO | jq -r '.realm')"
echo "Enabled: $(echo $REALM_INFO | jq -r '.enabled')"

# Count roles
ROLES=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow-platform/roles" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")
ROLE_COUNT=$(echo $ROLES | jq '. | length')
echo "Realm Roles: ${ROLE_COUNT}"

# Count groups
GROUPS=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow-platform/groups" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")
GROUP_COUNT=$(echo $GROUPS | jq '. | length')
echo "Groups: ${GROUP_COUNT}"

# Count clients
CLIENTS=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow-platform/clients" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")
CLIENT_COUNT=$(echo $CLIENTS | jq '. | length')
echo "Clients: ${CLIENT_COUNT}"

# Count users
USERS=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow-platform/users" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")
USER_COUNT=$(echo $USERS | jq '. | length')
echo "Users: ${USER_COUNT}"

echo ""
echo "================================================================"
echo "Import Complete!"
echo "================================================================"
echo ""
echo "Keycloak Admin Console: ${KEYCLOAK_URL}/admin"
echo "Realm: werkflow-platform"
echo ""
echo "Default admin user: admin@werkflow.com / admin123"
echo ""
echo "Next steps:"
echo "1. Login to Keycloak Admin Console"
echo "2. Review realm configuration"
echo "3. Update client secrets (do not use default secrets in production!)"
echo "4. Create additional users"
echo "5. Configure SMTP for email notifications (optional)"
echo ""
