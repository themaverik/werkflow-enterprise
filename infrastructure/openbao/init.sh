#!/bin/sh
# OpenBao bootstrap — applies werkflow-admin / werkflow-engine policies and mints
# scoped service tokens with stable IDs so admin-service + engine-service can
# authenticate via env-supplied tokens.
#
# Idempotent: safe to re-run. Policy writes are upserts; token creation uses
# fixed IDs and is short-circuited if the token already exists.
#
# Dev-mode only. In production the OpenBao deploy is initialised + unsealed by
# ops; policies are still applied via this script (or its equivalent), but the
# stable-ID token shortcut is replaced by short-lived AppRole or JWT auth.

set -e

: "${BAO_ADDR:?BAO_ADDR must be set}"
: "${BAO_TOKEN:?BAO_TOKEN (root token) must be set}"

ADMIN_TOKEN_ID="werkflow-admin-dev-token-do-not-use-in-prod"
ENGINE_TOKEN_ID="werkflow-engine-dev-token-do-not-use-in-prod"

POLICY_DIR="/openbao/policies"

echo "OpenBao bootstrap: ${BAO_ADDR}"

# kv-v2 is enabled at secret/ by default in dev mode. Probe and skip if present.
if bao secrets list -format=json | grep -q '"secret/"'; then
  echo "  kv-v2 already enabled at secret/"
else
  echo "  enabling kv-v2 at secret/"
  bao secrets enable -path=secret kv-v2
fi

echo "  writing werkflow-admin policy"
bao policy write werkflow-admin "${POLICY_DIR}/werkflow-admin.hcl"

echo "  writing werkflow-engine policy"
bao policy write werkflow-engine "${POLICY_DIR}/werkflow-engine.hcl"

create_token_if_missing() {
  TOKEN_ID="$1"
  POLICY="$2"
  if bao token lookup "${TOKEN_ID}" >/dev/null 2>&1; then
    echo "  token already provisioned: ${TOKEN_ID}"
  else
    echo "  minting token: ${TOKEN_ID} (policy=${POLICY})"
    bao token create \
      -id="${TOKEN_ID}" \
      -policy="${POLICY}" \
      -display-name="${POLICY}" \
      -no-default-policy \
      -ttl=0 \
      -orphan \
      >/dev/null
  fi
}

create_token_if_missing "${ADMIN_TOKEN_ID}" "werkflow-admin"
create_token_if_missing "${ENGINE_TOKEN_ID}" "werkflow-engine"

echo "OpenBao bootstrap complete."
