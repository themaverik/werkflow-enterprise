#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# DEV-ONLY: prepare the LOCAL Keycloak realm so the Playwright E2E suite can run.
#
# The E2E specs authenticate via ROPC (password grant, client_id=werkflow-portal).
# Session-28 security hardening DISABLES ROPC (directAccessGrants) on the realm.
# This script re-enables it on the LOCAL realm only and ensures the 3 test users
# exist with the expected passwords/roles/tenant. It is SURGICAL — it does NOT
# recreate the realm and does NOT change the client secret or redirect URIs.
#
# ⚠️  NEVER run this against a non-local / staging / production Keycloak.
#     To revert: set directAccessGrantsEnabled=false on werkflow-portal.
#
# Usage:
#   KEYCLOAK_ADMIN_PASSWORD=<your-local-kc-master-admin-pw> \
#     infrastructure/keycloak/setup-local-e2e.sh
#
# Env:
#   KEYCLOAK_URL             default http://localhost:8090
#   KEYCLOAK_ADMIN           default admin   (master realm admin user)
#   KEYCLOAK_ADMIN_PASSWORD  REQUIRED        (master realm admin password)
#   E2E_TEST_PASSWORD        default Werkflow@2026!  (must match E2E_*_PASSWORD)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
KC_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KC_ADMIN_PW="${KEYCLOAK_ADMIN_PASSWORD:?Set KEYCLOAK_ADMIN_PASSWORD to your local KC master-admin password}"
E2E_PW="${E2E_TEST_PASSWORD:-Werkflow@2026!}"
REALM="werkflow"

# Safety: refuse anything that is not obviously a local Keycloak.
case "$KEYCLOAK_URL" in
  *localhost*|*127.0.0.1*) ;;
  *) echo "REFUSING: '$KEYCLOAK_URL' is not a localhost Keycloak. This script is DEV-ONLY."; exit 1 ;;
esac

command -v jq >/dev/null || { echo "jq is required (brew install jq)"; exit 1; }

echo "Preparing LOCAL realm '$REALM' at $KEYCLOAK_URL for E2E (DEV-ONLY: re-enables ROPC)..."

TOKEN=$(curl -sf -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=${KC_ADMIN}&password=${KC_ADMIN_PW}" \
  | jq -r '.access_token')
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "Error: bad KC admin credentials."; exit 1; }
AUTH="Authorization: Bearer ${TOKEN}"

# ── 1. Enable ROPC on werkflow-portal (preserve secret + redirect URIs) ─────────
CLIENT_UUID=$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=werkflow-portal" \
  -H "$AUTH" | jq -r '.[0].id // empty')
[ -n "$CLIENT_UUID" ] || { echo "Error: werkflow-portal client not found in realm '$REALM'."; exit 1; }

REP=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}" -H "$AUTH")
UPDATED=$(echo "$REP" | jq '.directAccessGrantsEnabled = true')
curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}" \
  -H "$AUTH" -H "Content-Type: application/json" -d "$UPDATED" >/dev/null
echo "  ✓ ROPC (directAccessGrants) enabled on werkflow-portal (secret + redirects untouched)"

# ── 2. Ensure realm roles exist ─────────────────────────────────────────────────
for role in super_admin admin workflow_admin employee \
            doa_approver_level1 doa_approver_level2 doa_approver_level3 doa_approver_level4; do
  curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/roles" \
    -H "$AUTH" -H "Content-Type: application/json" -d "{\"name\":\"${role}\"}" >/dev/null 2>&1 || true
done

# ── 3. Ensure test users (password + roles + tenant_id=default) ──────────────────
ensure_user() {
  local username="$1" email="$2" first="$3" last="$4"; shift 4; local roles=("$@")
  curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
    -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"username\":\"${username}\",\"email\":\"${email}\",\"firstName\":\"${first}\",\"lastName\":\"${last}\",\"enabled\":true,\"attributes\":{\"tenant_id\":[\"default\"]}}" || true
  local uid
  uid=$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${username}&exact=true" -H "$AUTH" | jq -r '.[0].id // empty')
  [ -n "$uid" ] || { echo "  ! could not resolve uid for ${username}"; return 0; }
  # tenant_id attribute (idempotent — ensure present even if user pre-existed)
  local urep; urep=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${uid}" -H "$AUTH" \
    | jq '.attributes = ((.attributes // {}) + {"tenant_id":["default"]})')
  curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${uid}" -H "$AUTH" -H "Content-Type: application/json" -d "$urep" >/dev/null || true
  curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${uid}/reset-password" \
    -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"type\":\"password\",\"value\":\"${E2E_PW}\",\"temporary\":false}" >/dev/null
  for role in "${roles[@]}"; do
    local rrep; rrep=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${role}" -H "$AUTH" 2>/dev/null || echo "")
    [ -n "$rrep" ] && curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${uid}/role-mappings/realm" \
      -H "$AUTH" -H "Content-Type: application/json" -d "[${rrep}]" >/dev/null 2>&1 || true
  done
  echo "  ✓ ${username} (password set, roles: ${roles[*]}, tenant_id=default)"
}

ensure_user "admin"         "admin@werkflow.com"         "Admin" "User"     super_admin admin doa_approver_level4
ensure_user "john.manager"  "john.manager@werkflow.com"  "John"  "Manager"  doa_approver_level2
ensure_user "jane.employee" "jane.employee@werkflow.com" "Jane"  "Employee" employee

echo ""
echo "Local realm ready for E2E. Next:"
echo "  1) Portal:  cd frontends/portal && npm run dev        # serves :4000 (the e2e baseURL)"
echo "  2) Env:     cp frontends/portal/.env.e2e.example frontends/portal/.env.e2e  # then fill the secret"
echo "  3) Run:     cd frontends/portal && set -a && . .env.e2e && set +a && npm run e2e"
echo ""
echo "Revert ROPC when done (recommended):"
echo "  set directAccessGrantsEnabled=false on werkflow-portal (or re-run with the client patched back)."
