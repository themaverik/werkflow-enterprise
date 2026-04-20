# Deployment Configuration Guide

## Overview

Werkflow runs as 2 backend services + 1 frontend + supporting infrastructure (Keycloak, PostgreSQL, Mailpit). This guide covers local development, Docker Compose deployment, and environment configuration.

---

## Service Ports

| Service    | Port | Description                          |
|------------|------|--------------------------------------|
| Engine     | 8081 | Flowable BPMN engine, REST APIs      |
| Admin      | 8083 | Admin configuration APIs             |
| Portal     | 4000 | Unified Next.js frontend             |
| Keycloak   | 8090 | OAuth2/OIDC identity provider        |
| PostgreSQL | 5433 | Shared database                      |
| Mailpit    | 8025 | Email sandbox (dev only)             |

---

## Local Development Setup

### Prerequisites

- Java 21+
- Node.js 20+
- Docker and Docker Compose

### 1. Start infrastructure (PostgreSQL, Keycloak, Mailpit)

```bash
cd infrastructure/docker
docker compose up postgres keycloak mailpit -d
```

### 2. Copy and configure env files

```bash
cp config/env/.env.engine.example config/env/.env.engine
cp config/env/.env.admin.example  config/env/.env.admin
cp config/env/.env.shared.example config/env/.env.shared
cp frontends/portal/.env.local.example frontends/portal/.env.local
```

Update `KEYCLOAK_CLIENT_SECRET` and `NEXTAUTH_SECRET` in the portal env file.

### 3. Run backend services

```bash
# Terminal 1 — Engine
cd services/engine && mvn spring-boot:run

# Terminal 2 — Admin
cd services/admin && mvn spring-boot:run
```

### 4. Run the portal

```bash
cd frontends/portal
npm install
npm run dev   # http://localhost:4000
```

---

## Full Docker Compose Stack

```bash
cd infrastructure/docker
docker compose up -d
```

All services start together. The portal is available at http://localhost:4000.

---

## Key Environment Variables

### Engine (`config/env/.env.engine`)

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_HOST` | `localhost` | Database host |
| `POSTGRES_PORT` | `5433` | Database port |
| `POSTGRES_DB` | `werkflow` | Database name |
| `POSTGRES_USER` | `werkflow_admin` | Database user |
| `POSTGRES_PASSWORD` | — | Database password |
| `KEYCLOAK_ISSUER` | — | Keycloak realm URL |
| `ADMIN_SERVICE_URL` | `http://localhost:8083` | Admin service base URL |
| `SMTP_HOST` | `mailpit` | SMTP server (Mailpit in dev) |
| `WERKFLOW_BUSINESS_ENABLED` | `false` | Enable business module integration |
| `WERKFLOW_DEPLOY_EXAMPLES` | `false` | Auto-deploy example BPMN processes on startup |

### Admin (`config/env/.env.admin`)

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_HOST` | `localhost` | Database host |
| `KEYCLOAK_ISSUER` | — | Keycloak realm URL |

### Portal (`frontends/portal/.env.local`)

| Variable | Description |
|---|---|
| `NEXTAUTH_URL` | Portal base URL (e.g. `http://localhost:4000`) |
| `NEXTAUTH_SECRET` | Random secret — generate with `openssl rand -base64 32` |
| `KEYCLOAK_CLIENT_ID` | Keycloak client ID (`werkflow-portal`) |
| `KEYCLOAK_CLIENT_SECRET` | Client secret from Keycloak admin console |
| `KEYCLOAK_ISSUER_INTERNAL` | Keycloak URL for server-side use (Docker: `http://keycloak:8080/realms/werkflow`) |
| `KEYCLOAK_ISSUER_BROWSER` | Keycloak URL for browser redirects (host: `http://localhost:8090/realms/werkflow`) |
| `NEXT_PUBLIC_ENGINE_API_URL` | Engine base URL (e.g. `http://localhost:8081`) |
| `NEXT_PUBLIC_ADMIN_API_URL` | Admin base URL (e.g. `http://localhost:8083/api`) |

---

## Keycloak Three-URL Strategy

The portal uses three Keycloak URLs to handle Docker networking:

| Variable | Purpose |
|---|---|
| `KEYCLOAK_ISSUER_INTERNAL` | Server-side token validation — uses Docker hostname (`keycloak:8080`) |
| `KEYCLOAK_ISSUER_BROWSER` | Browser redirects — uses host-accessible URL (`localhost:8090`) |
| `KEYCLOAK_ISSUER_PUBLIC` | Public issuer claim in tokens |

This separation is required because Next.js server-side code runs inside Docker and cannot reach `localhost:8090`, while browser redirects must use the host-accessible address.

---

## Database Migrations

Flyway migrations run automatically on service startup. Migration files are at:

```
services/engine/src/main/resources/db/migration/   # Flowable + engine tables
services/admin/src/main/resources/db/migration/    # Admin config tables
```

---

## Production SMTP

In development, all emails are captured by Mailpit (http://localhost:8025). For production, configure in `config/env/.env.engine`:

| Variable | Description |
|---|---|
| `SMTP_HOST` | SMTP server (e.g. `smtp.sendgrid.net`) |
| `SMTP_PORT` | Port — typically `587` for STARTTLS |
| `SMTP_USERNAME` | Username or API key |
| `SMTP_PASSWORD` | Password or secret |
| `SMTP_AUTH` | `true` |
| `SMTP_STARTTLS_ENABLE` | `true` |
| `MAIL_FROM` | Sender address (e.g. `noreply@yourdomain.com`) |

---

## Health Checks

| Service | Endpoint |
|---|---|
| Engine | `http://localhost:8081/actuator/health` |
| Admin  | `http://localhost:8083/actuator/health` |
| Keycloak | `http://localhost:8090/health/ready` |

---

## Related Documentation

- [Quick Start](QUICKSTART.md)
- [Connector Guide](CONNECTOR-GUIDE.md)
- [Keycloak Implementation Guide](Keycloak-Implementation-Guide.md)
