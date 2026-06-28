# Running the E2E Suite Locally

The Playwright suite authenticates via **ROPC (password grant)**, which the
Session-28 security hardening **disables** on the realm. So a local run needs a
one-time, **dev-only** realm prep that re-enables ROPC and seeds the test users.

> The `business/` specs are NOT run in CI (`ci.yml` runs only `--project=chromium`,
> which `testIgnore`s `business/`). They are intended for local/staging runs — this
> doc is how you run them locally.

## Prerequisites

- **This is a local/dev-only flow.** `setup-local-e2e.sh` re-enables ROPC, which is a
  security downgrade, so it is fail-closed: it runs ONLY when `APP_ENVIRONMENT=development`
  (read from the environment, else from `config/env/.env.shared`). On any other instance it
  refuses. A local stack already has `APP_ENVIRONMENT=development` in `.env.shared`, so no
  action is needed locally.
- Docker stack up and healthy (engine `:8081`, Keycloak `:8090`) — see `docker-dev`.
- `jq` installed (`brew install jq`).
- Portal deps installed (`cd frontends/portal && npm ci`).

## 1. Prepare the local realm (dev-only — re-enables ROPC)

```bash
KEYCLOAK_ADMIN_PASSWORD=<your-local-KC-master-admin-password> \
  infrastructure/keycloak/setup-local-e2e.sh
```

This **surgically**: enables `directAccessGrants` (ROPC) on `werkflow-portal`
(secret + redirect URIs untouched), ensures realm roles, and ensures the three
test users with `tenant_id=default` and password `Werkflow@2026!`:

| User | Roles |
|------|-------|
| `admin` | super_admin, admin, doa_approver_level4 |
| `john.manager` | doa_approver_level2 |
| `jane.employee` | employee |

It is guarded by two independent fail-closed checks (both must pass): `APP_ENVIRONMENT`
must be `development`, and `KEYCLOAK_URL` must be a localhost address. **Revert when done**
by setting `directAccessGrantsEnabled=false` on `werkflow-portal` (ROPC should stay off
outside local dev).

## 2. Env

```bash
cp frontends/portal/.env.e2e.example frontends/portal/.env.e2e
# Fill E2E_PORTAL_CLIENT_SECRET — copy KEYCLOAK_CLIENT_SECRET from config/env/.env.shared
```

## 3. Start the portal on :4000 (the e2e `baseURL`)

```bash
cd frontends/portal && npm run dev   # next dev -p 4000
```

## 4. Run

```bash
cd frontends/portal
set -a && . .env.e2e && set +a

npm run e2e                                  # all projects (setup → chromium → business)
npx playwright test --project=chromium       # UI/core suite only (the CI set)
npx playwright test --project=business       # workflow journeys (25/26/28)
npm run e2e:ui                               # interactive
```

The `setup` project logs in via the portal `/login` page and writes session state
to `e2e/.auth/*.json`; `chromium` and `business` depend on it. If logins fail,
re-run step 1 (the users/passwords may have drifted) and confirm the portal is on
`:4000`.

## Notes

- **Notifications are provider-agnostic in the specs** — spec 28 verifies the
  it-helpdesk sendTasks via process flow, not by inspecting Mailpit. (Mailpit at
  `:8025` is available for manual inspection only.)
- **Business specs need seeded examples** — the engine must have started with
  `WERKFLOW_DEPLOY_EXAMPLES=true` so `it-helpdesk-ticket`, `leave-request`, etc.
  are deployed.
