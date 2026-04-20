# Quick Start

Get Werkflow running locally in about 15 minutes.

---

## Prerequisites

- Docker and Docker Compose
- Git

That's it. You do not need Java or Node installed to run the full stack.

---

## Step 1 — Clone the repository

```bash
git clone https://github.com/themaverik/werkflow.git
cd werkflow
```

---

## Step 2 — Configure environment files

Copy the example env files:

```bash
cp config/env/.env.shared.example config/env/.env.shared
cp config/env/.env.engine.example config/env/.env.engine
cp config/env/.env.admin.example  config/env/.env.admin
cp frontends/portal/.env.local.example frontends/portal/.env.local
```

Open `frontends/portal/.env.local` and set two required values:

```bash
# Generate a random secret
NEXTAUTH_SECRET=$(openssl rand -base64 32)

# After Keycloak starts (Step 3), copy the client secret from:
# Keycloak admin → Clients → werkflow-portal → Credentials tab
KEYCLOAK_CLIENT_SECRET=<paste-from-keycloak>
```

All other defaults work for local development.

---

## Step 3 — Start the stack

```bash
cd infrastructure/docker
docker compose up -d
```

This starts PostgreSQL, Keycloak, the engine, admin service, portal, and Mailpit. First run pulls images and builds the services — this takes a few minutes.

Check that all containers are healthy:

```bash
docker compose ps
```

---

## Step 4 — Get the Keycloak client secret

1. Open http://localhost:8090 (Keycloak admin)
2. Log in: username `admin`, password `admin123`
3. Select realm `werkflow`
4. Go to **Clients** → `werkflow-portal` → **Credentials** tab
5. Copy the **Client secret**
6. Paste it into `frontends/portal/.env.local` as `KEYCLOAK_CLIENT_SECRET`
7. Restart the portal container:

```bash
docker compose restart portal
```

---

## Step 5 — Log in to Werkflow

Open http://localhost:4000

Default credentials (set in Keycloak):

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | Admin |

> To add users or change passwords: Keycloak admin → Realm `werkflow` → Users.

---

## Step 6 — Start your first workflow

1. Go to **Processes** in the sidebar
2. Click **New Process** to open the BPMN designer
3. Draw a simple flow: Start → User Task → End
4. Click **Deploy**
5. Go to **Requests** → **Start New Request** and select your process
6. Submit the form — a task appears in **My Tasks**

---

## Step 7 — Explore further

| Topic | Where to go |
|---|---|
| Build a connector to an external API | [Connector Guide](CONNECTOR-GUIDE.md) |
| Configure for staging or production | [Deployment Configuration](Deployment-Configuration-Guide.md) |
| Keycloak realm setup details | [Keycloak Implementation Guide](Keycloak-Implementation-Guide.md) |

---

## Common Issues

**Portal shows "Unable to connect"**
Engine or admin service is not healthy. Check: `docker compose logs engine-service`

**Login redirects back to login page**
`KEYCLOAK_CLIENT_SECRET` in `.env.local` is wrong or empty. Re-copy from Keycloak and restart the portal.

**Emails not received**
All dev emails go to Mailpit: http://localhost:8025 — no real emails are sent.
