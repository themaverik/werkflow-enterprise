<p align="center">
  <img src="./frontends/portal/public/werkflow-logo.png" alt="Werkflow" width="200" />
</p>

<h1 align="center">Werkflow</h1>

<p align="center">
  Build and deploy approval flows with a visual BPMN designer and form builder — no code required.
</p>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License: Apache 2.0" /></a>
  <img src="https://img.shields.io/badge/java-21-orange.svg" alt="Java 21" />
  <img src="https://img.shields.io/badge/node-20-green.svg" alt="Node 20" />
  <img src="https://img.shields.io/badge/flowable-7.2.0-brightgreen.svg" alt="Flowable 7.2.0" />
  <img src="https://img.shields.io/badge/docker-required-blue.svg" alt="Docker required" />
</p>

## Services

| Service    | Port | Description                                         |
|------------|------|-----------------------------------------------------|
| Engine     | 8081 | Flowable BPM orchestration                          |
| Admin      | 8083 | User, organisation, connector, and integration management |
| Portal     | 4000 | Web portal (Next.js)                                |
| Keycloak   | 8090 | Authentication and authorisation                    |
| PostgreSQL | 5433 | Primary database                                    |
| OpenBao    | 8200 | Per-tenant credential store                         |
| Mailpit    | 8025 | Email sandbox (dev only)                            |

## Prerequisites

- Docker and Docker Compose
- Java 21+ (for local service development)
- Node.js 20+ (for local portal development)

## Quick Start

```bash
git clone https://github.com/themaverik/werkflow-enterprise.git
cd werkflow-enterprise

# 1. Create the env files from their examples, then fill in secrets.
#    At minimum set KEYCLOAK_ADMIN_PASSWORD and POSTGRES_PASSWORD in
#    config/env/.env.shared before the first start.
cp config/env/.env.shared.example config/env/.env.shared

# 2. Start the stack
cd infrastructure/docker
docker compose up -d
```

Copy any other missing `config/env/.env.*.example` files and fill in secrets before first run.

Portal: http://localhost:4000  
Engine Swagger UI: http://localhost:8081/swagger-ui.html

## Local Credentials

All admin credentials are sourced from `config/env/.env.shared` (gitignored). Copy `.env.shared.example`, set values, then start the stack. Seed users in `werkflow-realm.json` use `temporary: true` — Keycloak forces a password reset on first login.

| Service        | URL                     | Username           | Source                       |
|----------------|-------------------------|--------------------|------------------------------|
| Portal (admin) | http://localhost:4000   | admin              | KC seed (`Werkflow@2026!`)   |
| Keycloak admin | http://localhost:8090   | `${KEYCLOAK_ADMIN}` | `.env.shared`               |
| pgAdmin        | http://localhost:5050   | `${PGADMIN_EMAIL}` | `.env.shared`                |
| PostgreSQL     | localhost:5433          | werkflow_admin     | docker-compose dev defaults  |

## Project Structure

```
werkflow-enterprise/
├── services/
│   ├── engine/       # Flowable BPM orchestration (8081)
│   └── admin/        # Org, connector, and integration management (8083)
├── frontends/
│   └── portal/       # Next.js portal (4000)
├── infrastructure/
│   └── docker/       # Docker Compose and Dockerfiles
└── docs/             # Architecture decisions, guides, ADRs
```

## Roles

| Role             | Access                                      |
|------------------|---------------------------------------------|
| `super_admin`    | Full platform access                        |
| `admin`          | Workflow designer, form builder, all tasks  |
| `workflow_admin` | Workflow designer, form builder             |
| `employee`       | My Tasks, My Requests, Service Catalog      |

## Example Processes

Four example processes auto-deploy on startup (set `WERKFLOW_DEPLOY_EXAMPLES=true`). Each is a complete logical unit (BPMN + start form + optional DMN), seeded by `ProcessExampleDeployer`:

| Process | Forms | DMN |
|---------|-------|-----|
| CapEx Approval | capex-request-form, capex-approval-form | capex-approver-resolution |
| Leave Request | leave-request-form, leave-approval-form | leave-approval |
| Procurement Approval | procurement-request-form, vendor-selection, quotation-review, procurement-approval | procurement-matrix |
| IT Helpdesk Ticket | it-helpdesk-ticket-form, it-helpdesk-resolution-form | — |

Each approval/work task carries a non-interrupting SLA timer (ADR-037): on timeout a token reaches
an "SLA Breached" end event while the task stays active. The start forms expose an optional **SLA
Duration (testing)** preset selector (default 15 min) so the breach scenario can be exercised
without waiting.

## Documentation

- [Quick Start](./docs/QUICKSTART.md)
- [Connector Guide](./docs/CONNECTOR-GUIDE.md)
- [Deployment Configuration](./docs/Deployment-Configuration-Guide.md)
- [Keycloak Setup](./docs/Keycloak-Implementation-Guide.md)
- [Architecture Decisions](./docs/adr/)

## License

Licensed under the [Apache License 2.0](./LICENSE). Third-party components (bpmn-js, form-js, dmn-js) are subject to the [bpmn.io license](./LICENSES/bpmn.io.txt). See [NOTICE](./NOTICE) for full attribution.
