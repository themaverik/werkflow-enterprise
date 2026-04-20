#!/bin/bash
# CI-only script: creates the werkflow realm, client, roles, and test users
# from scratch via the Keycloak Admin REST API.
#
# Usage: ./setup-ci-realm.sh
# Env:
#   KEYCLOAK_URL        defaults to http://localhost:8090
#   KEYCLOAK_CLIENT_SECRET  the portal client secret to set (defaults to ci-client-secret)

set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-ci-client-secret}"

echo "Setting up werkflow realm at ${KEYCLOAK_URL}..."

# ── Wait for Keycloak ──────────────────────────────────────────────────────────
echo "Waiting for Keycloak health..."
for i in $(seq 1 40); do
  if curl -sf "${KEYCLOAK_URL}/health/ready" > /dev/null 2>&1; then
    echo "Keycloak ready."
    break
  fi
  echo "  attempt ${i}/40..."
  sleep 3
  if [ "$i" -eq 40 ]; then
    echo "Error: Keycloak did not become ready in time."
    exit 1
  fi
done

# ── Admin token ────────────────────────────────────────────────────────────────
TOKEN=$(curl -sf -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin123" \
  | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Error: could not obtain admin token."
  exit 1
fi
echo "Admin token obtained."

auth() { echo "Authorization: Bearer ${TOKEN}"; }

# ── Create realm ───────────────────────────────────────────────────────────────
echo "Creating realm 'werkflow'..."
curl -sf -X POST "${KEYCLOAK_URL}/admin/realms" \
  -H "$(auth)" -H "Content-Type: application/json" \
  -d '{
    "realm": "werkflow",
    "enabled": true,
    "registrationAllowed": false,
    "loginWithEmailAllowed": true,
    "duplicateEmailsAllowed": false,
    "sslRequired": "none",
    "accessTokenLifespan": 300,
    "ssoSessionMaxLifespan": 36000
  }' || echo "  (realm may already exist, continuing)"

# ── Realm roles ────────────────────────────────────────────────────────────────
echo "Creating realm roles..."
for role in super_admin admin workflow_admin employee \
            doa_approver_level1 doa_approver_level2 \
            doa_approver_level3 doa_approver_level4; do
  curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/werkflow/roles" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "{\"name\": \"${role}\"}" || echo "  role ${role} may already exist"
done

# ── Portal client ──────────────────────────────────────────────────────────────
echo "Creating 'werkflow-portal' client..."
curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/werkflow/clients" \
  -H "$(auth)" -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"werkflow-portal\",
    \"enabled\": true,
    \"protocol\": \"openid-connect\",
    \"publicClient\": false,
    \"secret\": \"${CLIENT_SECRET}\",
    \"standardFlowEnabled\": true,
    \"directAccessGrantsEnabled\": true,
    \"redirectUris\": [\"http://localhost:4000/*\", \"http://localhost:3000/*\"],
    \"webOrigins\": [\"http://localhost:4000\", \"http://localhost:3000\"],
    \"attributes\": {\"post.logout.redirect.uris\": \"http://localhost:4000/*\"}
  }" || echo "  client may already exist"

# ── Helper: create user and assign roles ──────────────────────────────────────
create_user() {
  local username="$1" password="$2" email="$3" first="$4" last="$5"
  shift 5
  local roles=("$@")

  echo "Creating user '${username}'..."
  curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/werkflow/users" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${username}\",
      \"email\": \"${email}\",
      \"firstName\": \"${first}\",
      \"lastName\": \"${last}\",
      \"enabled\": true,
      \"credentials\": [{\"type\": \"password\", \"value\": \"${password}\", \"temporary\": false}]
    }" || echo "  user ${username} may already exist"

  # Fetch user ID
  local uid
  uid=$(curl -sf "${KEYCLOAK_URL}/admin/realms/werkflow/users?username=${username}" \
    -H "$(auth)" | jq -r '.[0].id')

  # Fetch and assign roles
  for role in "${roles[@]}"; do
    local role_rep
    role_rep=$(curl -sf "${KEYCLOAK_URL}/admin/realms/werkflow/roles/${role}" \
      -H "$(auth)")
    curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/werkflow/users/${uid}/role-mappings/realm" \
      -H "$(auth)" -H "Content-Type: application/json" \
      -d "[${role_rep}]" || echo "  role ${role} may already be assigned"
  done
}

# ── Test users ─────────────────────────────────────────────────────────────────
create_user "admin"        "admin123"   "admin@werkflow.com"       "Admin"  "User"    "super_admin" "admin" "doa_approver_level4"
create_user "john.manager" "manager123" "john.manager@werkflow.com" "John"  "Manager" "doa_approver_level2"
create_user "jane.employee" "employee123" "jane.employee@werkflow.com" "Jane" "Employee" "employee"

echo ""
echo "Realm setup complete. Realm: werkflow, Client: werkflow-portal"
