# Admin Service

Platform Semantics Service (PSS) and tenant configuration management for Werkflow Enterprise.

## Overview

Provides tenant-scoped configuration, role-to-group mappings, connector registry, process design-time
support (BPMN variable scope, DMN facade), and the Platform Semantics Service (PSS) which aggregates
locale, visibility policy, candidate groups, categories, and FEEL expression catalogs.

## Responsibilities

- Tenant configuration variables (locale, visibility policy, custom FEEL vars)
- Role-to-group mappings (Tier 1 YAML read-through, Tier 2 DB-backed)
- Custody mappings (process custody owner to candidate group routing)
- Connector registry (connector definitions, tenant endpoints, credentials)
- Keycloak realm role proxy (for Admin UI role dropdowns)
- PSS endpoints: candidate groups, departments, locale, visibility policy, FEEL expressions
- BPMN process variable scope analysis (design-time)
- DMN decision variable facade (design-time)

## Technology Stack

- Java 21
- Spring Boot 3.3.x
- PostgreSQL 15 (schema: `admin_service`)
- Caffeine in-memory cache (5-minute TTL for PSS endpoints)
- OAuth2/JWT authentication (Keycloak)

## Port

- **8083** — HTTP REST API

## Key API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/design/platform/candidate-groups` | Aggregated Tier 1 + Tier 2 candidate groups |
| GET | `/api/v1/design/platform/capabilities` | Full PSS capability bundle |
| GET/POST/PUT/DELETE | `/api/v1/config/vars` | Tenant configuration variables |
| GET/POST/DELETE | `/api/v1/config/role-mappings` | Tier 2 role-to-group mappings |
| GET | `/api/v1/design/bpmn/variable-scope` | Process variable scope at activity |
| GET | `/api/v1/design/dmn/variables` | DMN input/output variables |
| GET | `/api/v1/connectors` | Connector definitions |

## Configuration

See `config/env/.env.admin` for environment variables. Key settings:

| Variable | Default | Description |
|----------|---------|-------------|
| `ENGINE_SERVICE_URL` | `http://werkflow-engine:8081` | Engine service (Docker network) |
| `ERP_SERVICE_URL` | `http://werkflow-business:8084` | ERP service URL |
| `KEYCLOAK_URL` | — | Keycloak base URL |
