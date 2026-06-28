# Keycloak RBAC Configuration

## Overview

This directory holds the Keycloak realm configuration and management scripts for the
Werkflow platform. It targets **Keycloak 26.x**. The canonical realm is
`realms/werkflow-realm.json` — the single source of truth, imported automatically by
docker-compose via `--import-realm` on a fresh Keycloak volume.

## Files

- `realms/werkflow-realm.json` — canonical realm (roles, clients, mappers, identity providers, demo users)
- `import-realm.sh` — import the realm into a running Keycloak via the admin REST API
- `setup-local-e2e.sh` — local E2E bootstrap (re-enables ROPC for headless test login; dev-only, runs only when `APP_ENVIRONMENT=development`)
- `setup-ci-realm.sh` / `init-realm.sh` — CI / first-boot helpers
- `sample-users.json` — reference-only sample users (not auto-imported)
- `themes/` — custom login theme (FTL)
- `README.md` — this file

> Client secrets and the admin bootstrap password are **not** stored here. The realm JSON
> references them as environment-variable placeholders (see [Client Secrets](#client-secrets)).

## Quick Start

On a fresh stack the realm imports itself — no manual step is needed:

```bash
cd infrastructure/docker
docker compose up -d keycloak   # imports realms/werkflow-realm.json via --import-realm
```

Keycloak is ready when `http://localhost:8090/health/ready` returns 200.

To (re-)import into an already-running Keycloak:

```bash
cd infrastructure/keycloak
./import-realm.sh               # honours KEYCLOAK_URL / KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD
```

### Verify Import

Admin console: `http://localhost:8090/admin` (credentials come from the
`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` env vars). Open the `werkflow` realm and confirm:

- **Realm**: `werkflow`
- **Clients**: 3 (`werkflow-portal`, `werkflow-engine`, `werkflow-admin`) plus the `google` identity provider
- **Realm roles**: 37 (see [Roles](#roles))
- **Groups**: none — authorization is role-based (see [Groups](#groups))
- **Users**: 11 demo users + 3 client service accounts

## Realm Structure

### Clients

| clientId | Type | Flow | Purpose |
|----------|------|------|---------|
| `werkflow-portal` | Confidential | Authorization Code (+ service account) | User-facing Next.js portal (NextAuth). Redirect URIs: `http://localhost:4000/*`, `http://localhost:4000/api/auth/callback/keycloak`. Its service account holds `view-realm` / `query-users` for admin-API reads. |
| `werkflow-engine` | Confidential | Client Credentials (service account) | Backend workflow engine S2S. Standard login flow disabled. |
| `werkflow-admin` | Confidential | Client Credentials (service account) | Backend admin-service S2S. Standard login flow disabled. |

Identity provider: **Google** (alias `google`) — OIDC broker; client id/secret supplied via
`${GOOGLE_CLIENT_ID}` / `${GOOGLE_CLIENT_SECRET}`.

### Roles

The realm defines 37 realm roles:

- **Platform / system**: `admin`, `super_admin`, `user`, `employee`, `workflow-designer`, `process-owner`
- **Service-to-service**: `ENGINE_SERVICE`, `ADMIN_SERVICE`
- **Delegation of Authority**: `doa_approver_level1`, `doa_approver_level2`, `doa_approver_level3`, `doa_approver_level4`
- **Asset workflows**: `asset_request_requester`, `asset_request_approver`, `asset_assignment_requester`, `asset_assignment_approver`
- **Domain approvers**: `procurement_approver`, `transport_approver`, `hub_request_approver`, `leave_request_approver`
- **Department managers / heads**: `hr_manager`, `hr_head`, `it_manager`, `it_head`, `finance_manager`, `finance_head`, `procurement_manager`, `procurement_head`, `logistics_manager`, `transport_head`
- **Inventory / logistics ops**: `inventory_manager`, `hub_manager`, `central_hub_manager`, `driver`, `warehouse_staff`
- **Other**: `department_poc`, `department_head`

### Groups

None. Earlier realm generations used six department groups with sub-groups; the current
realm carries **zero groups** and is entirely role-based. Tenant scoping is carried by the
`tenant_id` user attribute (below), not by group membership.

### Custom Attributes

The realm maps a single custom claim into tokens:

| Attribute | Claim | Description |
|-----------|-------|-------------|
| `tenant_id` | `tenant_id` | Tenant the user belongs to (drives multi-tenant scoping). Every seeded user carries it. |

## Configuration

### Environment Variables

`import-realm.sh` reads (with localhost defaults):

```bash
KEYCLOAK_URL=http://localhost:8090
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<set in your environment>
```

### Client Secrets

Client secrets are sourced from the environment, not committed. The realm JSON references:

- `werkflow-portal` → `${KEYCLOAK_CLIENT_SECRET}`
- `werkflow-engine` → `${ENGINE_CLIENT_SECRET}`
- `werkflow-admin` → `${ADMIN_CLIENT_SECRET}`

These are defined in the gitignored `.env.shared` and consumed by both Keycloak (at import)
and the services. Each secret must match exactly on both sides — a truncated/mismatched
secret causes a silent 401 and an empty realm-roles list.

## Updating the Realm

To regenerate the realm JSON from a running instance, export and review the diff before
committing — keep secrets as `${...}` placeholders:

```bash
docker exec -it werkflow-keycloak /opt/keycloak/bin/kc.sh export \
  --realm werkflow --file /tmp/werkflow-realm-export.json
docker cp werkflow-keycloak:/tmp/werkflow-realm-export.json ./werkflow-realm-backup.json
```

Re-importing (`./import-realm.sh`) prompts before deleting and recreating the realm.

## Production Checklist

The realm already enables brute-force protection, a password policy, and an SMTP server.
Before going live also confirm:

- [ ] All client secrets rotated and supplied via the production secret store
- [ ] Redirect URIs updated to production origins
- [ ] `sslRequired` set to `EXTERNAL` (or `ALL`)
- [ ] SMTP credentials verified for email delivery
- [ ] Token lifespans and SSO session timeouts reviewed
- [ ] Refresh-token rotation enabled (`revokeRefreshToken: true`, `refresh.token.max.reuse: 0`)
- [ ] Admin accounts protected with MFA

## Troubleshooting

- **Keycloak never becomes ready** — check `docker logs werkflow-keycloak` and the database connection.
- **Failed to get access token** — verify `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`.
- **Empty realm roles in a service** — the client secret in `.env.shared` does not match the realm; re-sync and restart.
- **Realm already exists** — `import-realm.sh` prompts to delete and recreate.

## References

- Implementation guide: `docs/Keycloak-Implementation-Guide.md`
- Canonical realm: `realms/werkflow-realm.json`
- RBAC tables migration: `services/engine/src/main/resources/db/migration/V3__create_rbac_tables.sql`
- Keycloak docs: https://www.keycloak.org/documentation
