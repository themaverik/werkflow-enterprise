# Production Deploy Checklist — Werkflow Enterprise

**Scope:** First deploy + every subsequent release  
**Environments:** Docker Compose on a DigitalOcean Droplet (engine, admin, portal, Keycloak, Postgres, OpenBao)  

---

## Pre-Deploy Gates (block deploy if any fail)

### Secrets & Credentials

- [ ] `AUTH_SECRET` / `NEXTAUTH_SECRET` generated: `openssl rand -base64 32`
- [ ] `KEYCLOAK_CLIENT_SECRET` matches the value in Keycloak admin console (truncation → silent 401; must be exactly 32 chars)
- [ ] All Spring Boot `application*.yml` secrets overridden via environment variables — no hardcoded values in committed files
- [ ] OpenBao unsealed and reachable from engine and admin services before startup
- [ ] `BAO_DEV_ROOT_TOKEN_ID` (dev only) replaced with a production token — never ship the dev root token
- [ ] `POSTGRES_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`, `NEXTAUTH_SECRET` rotated from any values used in local `.env.local`

### Infrastructure

- [ ] PostgreSQL 15 accessible; `init-db.sql` run on fresh deploy
- [ ] Keycloak import: `realm.json` loaded with all clients, roles, and users
- [ ] Flyway migrations run cleanly (`flyway:info` shows all applied; no pending)
- [ ] `flyway:repair` run if any checksum mismatch (non-dev DBs only — never edit applied migration files)
- [ ] Docker healthchecks green for all services before routing traffic:
  - `werkflow-postgres` — pg_isready
  - `werkflow-keycloak` — Keycloak admin endpoint
  - `werkflow-engine` — `/actuator/health`
  - `werkflow-admin` — `/actuator/health`

### Portal (Docker — DigitalOcean Droplet)

- [ ] `DOCKER_BUILD=true` set in the container env (enables `output: 'standalone'`)
- [ ] `ENGINE_BASE_URL` and `ADMIN_BASE_URL` point to the backend services reachable from the portal container
- [ ] `NEXTAUTH_URL` set to the public portal URL (no trailing slash)
- [ ] Keycloak redirect URIs updated to include the public portal domain:
  - Valid redirect URI: `https://<portal-domain>/api/auth/callback/keycloak`
  - Valid post-logout URI: `https://<portal-domain>`
- [ ] Portal container builds successfully from `Dockerfile`
- [ ] Portal build succeeds: `npm run build` clean with no TypeScript errors

### Security Baseline

- [ ] Security headers active: verify with curl or browser devtools
  - `X-Frame-Options: DENY`
  - `X-Content-Type-Options: nosniff`
  - `Strict-Transport-Security` (HTTPS only)
  - `Referrer-Policy: strict-origin-when-cross-origin`
- [ ] TLS / HTTPS enforced (HSTS header is `max-age=63072000` — only enable after HTTPS confirmed)
- [ ] `npm audit signatures` passes for portal dependencies
- [ ] OWASP security report reviewed (see CI artifacts from `security.yml` workflow) — no unacknowledged CRITICAL findings
- [ ] `npm audit` report reviewed — no unacknowledged HIGH/CRITICAL without a mitigation note in `compliance-checklist.md`

---

## Post-Deploy Smoke Checks

Run these immediately after deploy:

- [ ] `GET /actuator/health` → 200 on engine and admin
- [ ] `GET /api/health` → 200 on portal
- [ ] Portal login page loads at `/login`
- [ ] Keycloak SSO login completes (redirected back to `/dashboard`)
- [ ] Dashboard renders without JS console errors
- [ ] `/legal/privacy`, `/legal/terms`, `/legal/cookies` accessible without login
- [ ] Cookie consent banner appears on first visit (cleared localStorage)
- [ ] One example process deploys and starts successfully (e.g., leave-request)
- [ ] Admin tenant page loads and shows correct tenant data

---

## Release-to-Release Checklist (repeat on every new deployment)

- [ ] Run `git log <prev-tag>..HEAD --oneline` — review all commits shipping
- [ ] Database migrations: Flyway `INFO` output shows only new migrations pending (no gaps, no missing)
- [ ] Connector credentials: any new credential types added? → test binding end-to-end
- [ ] OpenBao: new secrets paths provisioned if new services added
- [ ] Environment variables: any new `NEXT_PUBLIC_*` or Spring Boot vars required? → documented in `.env.local.example`
- [ ] BPMN/DMN examples: `ExampleBpmnDeployTest` suite passes (prevents version climbing on restart)
- [ ] Keycloak realm: any new roles or clients required? → `realm.json` updated

---

## npm min-release-age Policy (supply-chain protection)

The portal enforces `min-release-age=7` (7 days) via `frontends/portal/.npmrc`.

- **`npm ci`** in CI respects this — packages too new for the lockfile will be flagged.
- **`npm audit fix`** is a **developer action only** — never run in CI, because it can silently break dependent packages. Run locally after reviewing the audit output.
- **Bypassing** `min-release-age` (e.g., `--no-min-release-age`) is blocked by the `cooldown-guard.sh` pre-tool-use hook.
- When a lockfile PR is opened that updates a package, CI verifies the `npm audit signatures` step passes.

---

## M7 Runbook References (Docker prod-compose)

- Main compose: `infrastructure/docker/docker-compose.yml`
- Enterprise overlay: `infrastructure/docker/docker-compose.enterprise.yml`
- Command: `docker compose -f docker-compose.yml -f docker-compose.enterprise.yml up -d`
- Keycloak verification: `infrastructure/docker/verify-keycloak.sh`
- Keycloak restart (after config change): `infrastructure/docker/restart-keycloak.sh`

---

## Open Items (from `compliance-checklist.md`)

| Priority | Item | Owner |
|----------|------|-------|
| HIGH | Next.js 14→16 upgrade — closes critical CVEs (middleware bypass, cache poisoning) | Dev team |
| HIGH | CSP header implementation — requires `unsafe-eval` budget analysis for bpmn-js/form-js | Dev team |
| MEDIUM | Admin-service + portal proxy rate limiting | Infra |
| MEDIUM | Automated data erasure API (GDPR Art.17) | Dev team |
| LOW | Confirm cloud infrastructure provider → finalise DPA/SCCs | Legal/Infra |
