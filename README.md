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
| Admin      | 8083 | User, organisation, and service registry management |
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
cd werkflow-enterprise/infrastructure/docker
docker compose up -d
```

Copy any missing `config/env/.env.*.example` files and fill in secrets before first run.

Portal: http://localhost:4000  
Engine Swagger UI: http://localhost:8081/swagger-ui.html

## Local Credentials

| Service        | URL                     | Username           | Password             |
|----------------|-------------------------|--------------------|----------------------|
| Portal (admin) | http://localhost:4000   | admin              | (set in Keycloak)    |
| Keycloak admin | http://localhost:8090   | admin              | REDACTED_PASSWORD             |
| pgAdmin        | http://localhost:5050   | admin@werkflow.com | admin                |
| PostgreSQL     | localhost:5433          | werkflow_admin     | werkflow_secure_pass |

## Project Structure

```
werkflow-enterprise/
├── services/
│   ├── engine/       # Flowable BPM orchestration (8081)
│   └── admin/        # Org and service registry management (8083)
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

Eight example processes auto-deploy on startup (set `WERKFLOW_DEPLOY_EXAMPLES=true`):
Procurement Approval, CapEx Approval, Finance Approval, Leave Request, Event Ticket Request, General Approval, Onboarding Checklist, Asset Request.

## Documentation

- [Quick Start](./docs/QUICKSTART.md)
- [Connector Guide](./docs/CONNECTOR-GUIDE.md)
- [Deployment Configuration](./docs/Deployment-Configuration-Guide.md)
- [Keycloak Setup](./docs/Keycloak-Implementation-Guide.md)
- [Architecture Decisions](./docs/adr/)

## License

Licensed under the [Apache License 2.0](./LICENSE). Third-party components (bpmn-js, form-js, dmn-js) are subject to the [bpmn.io license](./LICENSES/bpmn.io.txt). See [NOTICE](./NOTICE) for full attribution.
