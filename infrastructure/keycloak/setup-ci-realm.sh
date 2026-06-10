#!/bin/bash
# CI-only script: creates/updates the werkflow realm, client, roles, and test users
# via the Keycloak Admin REST API. Idempotent — safe to run against a realm that was
# pre-imported from werkflow-realm.json.
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
    \"serviceAccountsEnabled\": true,
    \"redirectUris\": [\"http://localhost:4000/*\", \"http://localhost:3000/*\"],
    \"webOrigins\": [\"http://localhost:4000\", \"http://localhost:3000\"],
    \"attributes\": {\"post.logout.redirect.uris\": \"http://localhost:4000/*\"}
  }" || echo "  client may already exist"

# Always force-update the client secret via PUT /clients/{id} so it matches CI
# expectations regardless of what was imported from werkflow-realm.json.
# NOTE: POST /clients/{id}/client-secret REGENERATES a random secret (ignores body).
#       The correct approach is GET the representation, patch the secret, then PUT.
echo "Updating 'werkflow-portal' client secret to CI value..."
CLIENT_UUID=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow/clients?clientId=werkflow-portal" \
  -H "$(auth)" | jq -r '.[0].id // empty')
if [ -n "$CLIENT_UUID" ]; then
  CLIENT_REP=$(curl -sf "${KEYCLOAK_URL}/admin/realms/werkflow/clients/${CLIENT_UUID}" \
    -H "$(auth)")
  UPDATED_REP=$(echo "$CLIENT_REP" | jq --arg s "$CLIENT_SECRET" '. + {secret: $s}')
  curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/werkflow/clients/${CLIENT_UUID}" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "$UPDATED_REP" && echo "  client secret set to CI value (uuid=${CLIENT_UUID})"
else
  echo "  WARNING: could not find werkflow-portal client UUID to update secret"
fi

# ── Service account — grant manage-users on realm-management ──────────────────
# Required by TenantProvisioningService to create Keycloak users via the admin API.
echo "Granting manage-users to werkflow-portal service account..."
if [ -n "$CLIENT_UUID" ]; then
  SA_USER_ID=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow/clients/${CLIENT_UUID}/service-account-user" \
    -H "$(auth)" | jq -r '.id // empty')
  REALM_MGMT_UUID=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow/clients?clientId=realm-management" \
    -H "$(auth)" | jq -r '.[0].id // empty')
  if [ -n "$SA_USER_ID" ] && [ -n "$REALM_MGMT_UUID" ]; then
    MANAGE_USERS_ROLE=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow/clients/${REALM_MGMT_UUID}/roles/manage-users" \
      -H "$(auth)")
    curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/werkflow/users/${SA_USER_ID}/role-mappings/clients/${REALM_MGMT_UUID}" \
      -H "$(auth)" -H "Content-Type: application/json" \
      -d "[${MANAGE_USERS_ROLE}]" && echo "  manage-users granted" || echo "  manage-users may already be assigned"
  else
    echo "  WARNING: could not resolve SA user or realm-management client"
  fi
else
  echo "  WARNING: skipping service account grant (client UUID not resolved)"
fi

# ── Helper: create user, assign roles, and reset password ─────────────────────
create_user() {
  local username="$1" password="$2" email="$3" first="$4" last="$5"
  shift 5
  local roles=("$@")

  echo "Creating user '${username}'..."
  local create_resp
  create_resp=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${KEYCLOAK_URL}/admin/realms/werkflow/users" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${username}\",
      \"email\": \"${email}\",
      \"firstName\": \"${first}\",
      \"lastName\": \"${last}\",
      \"enabled\": true,
      \"credentials\": [{\"type\": \"password\", \"value\": \"${password}\", \"temporary\": false}]
    }")
  if [ "$create_resp" = "201" ]; then
    echo "  created (HTTP 201)"
  else
    echo "  creation returned HTTP ${create_resp} (user may already exist or username rejected)"
  fi

  # Fetch user ID — use exact=true to avoid prefix-match ambiguity; -s only (no -f)
  local uid
  uid=$(curl -s "${KEYCLOAK_URL}/admin/realms/werkflow/users?username=${username}&exact=true" \
    -H "$(auth)" | jq -r '.[0].id // empty')

  if [ -z "$uid" ]; then
    echo "  WARNING: could not resolve uid for ${username} — skipping role/password setup"
    return 0
  fi

  # Always reset the password — ensures CI credentials override anything from realm JSON
  curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/werkflow/users/${uid}/reset-password" \
    -H "$(auth)" -H "Content-Type: application/json" \
    -d "{\"type\": \"password\", \"value\": \"${password}\", \"temporary\": false}" \
    && echo "  password set for ${username}" || echo "  WARNING: could not reset password for ${username}"

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

# ── Password policy (applied after users so import passwords are not validated) ─
echo "Applying password policy..."
curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/werkflow" \
  -H "$(auth)" -H "Content-Type: application/json" \
  -d '{"passwordPolicy": "length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1)"}' \
  && echo "  password policy set" || echo "  WARNING: could not set password policy"

echo ""
echo "Realm setup complete. Realm: werkflow, Client: werkflow-portal (secret updated)"
