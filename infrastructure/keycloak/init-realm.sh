#!/bin/bash
# Initialize Keycloak realm with all configuration in one command
# Usage: ./init-realm.sh

set -e

KEYCLOAK_URL="http://localhost:8090"
REALM_NAME="werkflow"
ADMIN_USER="admin"
ADMIN_PASS="REDACTED_PASSWORD"

echo "Initializing Keycloak realm: $REALM_NAME"

# Get admin token
echo "Obtaining admin token..."
TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli&grant_type=password&username=$ADMIN_USER&password=$ADMIN_PASS" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "ERROR: Failed to obtain admin token. Is Keycloak running at $KEYCLOAK_URL?"
  exit 1
fi

echo "Token obtained successfully"

# Delete existing realm if it exists                                                                                                                     
  echo "Checking for existing realm..."                                                                                                                  
  if curl -s -f "$KEYCLOAK_URL/admin/realms/$REALM_NAME" -H "Authorization: Bearer $TOKEN" > /dev/null 2>&1; then
    echo "Deleting existing realm: $REALM_NAME"                                                                                                            
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$KEYCLOAK_URL/admin/realms/$REALM_NAME" \
      -H "Authorization: Bearer $TOKEN")                                                                                                                   
    if [ "$HTTP_CODE" = "204" ]; then                                                                                                                    
      echo "Realm deleted successfully"                                                                                                                    
      sleep 3                                                                                                                                              
    else
      echo "WARNING: Delete returned HTTP $HTTP_CODE, continuing anyway..."                                                                                
      sleep 3                                                                                                                                              
    fi
  fi    
  
# Create new realm with full configuration
echo "Creating realm with full configuration..."
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REALM_JSON="$SCRIPT_DIR/realms/werkflow-realm.json"

if [ ! -f "$REALM_JSON" ]; then
  echo "ERROR: Realm JSON not found at $REALM_JSON"
  exit 1
fi

RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/admin/realms" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @"$REALM_JSON" \
  -w "\n%{http_code}")

STATUS=$(echo "$RESPONSE" | tail -n1)

if [ "$STATUS" = "201" ]; then
  echo "✓ Realm created successfully"
  echo ""
  echo "Realm: $REALM_NAME"
  echo "URL: $KEYCLOAK_URL/realms/$REALM_NAME"
  echo ""
  echo "OAuth Client:"
  echo "  Client ID: werkflow-portal"
  echo "  Client Secret: REDACTED_KC_PORTAL_SECRET"
  echo "  Redirect URI: http://localhost:4000/api/auth/callback/keycloak"
  echo ""
  echo "Test Users:"
  echo "  admin / REDACTED_PASSWORD (role: admin)"
  echo "  testuser / password123 (roles: user, workflow-designer, doa_approver_level2)"
else
  echo "ERROR: Failed to create realm (HTTP $STATUS)"
  echo "$RESPONSE" | head -n-1
  exit 1
fi
