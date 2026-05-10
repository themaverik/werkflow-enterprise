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
- Tenant datasource registry (JDBC datasource CRUD, connection test with rate limiting, engine-internal resolver)
- Connector generators (OpenAPI 3.1 import — $ref SSRF-safe, JSON Schema import)
- Keycloak realm role proxy (for Admin UI role dropdowns)
- PSS endpoints: candidate groups, departments, locale, visibility policy, FEEL expressions, visible-processes
- BPMN process variable scope analysis (design-time)
- DMN decision variable facade (design-time)
- DTDS connector catalog with schema resolution, field flattening, and 30-min Caffeine cache

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
| GET | `/api/v1/design/platform/visible-processes` | ADR-010 §3 visibility-filtered process key list (any authenticated user) |
| GET/POST/PUT/DELETE | `/api/v1/config/vars` | Tenant configuration variables |
| GET/POST/DELETE | `/api/v1/config/role-mappings` | Tier 2 role-to-group mappings |
| GET/POST/PUT/DELETE | `/api/v1/config/datasources` | Tenant JDBC datasource registry |
| POST | `/api/v1/config/datasources/{ref}/test` | Live connection test (rate-limited: 5/min) |
| GET | `/api/v1/design/bpmn/variable-scope` | Process variable scope at activity |
| GET | `/api/v1/design/dmn/variables` | DMN input/output variables |
| GET | `/api/v1/connectors` | Connector definitions |
| POST | `/api/v1/connectors/generators/openapi` | Generate connector from OpenAPI 3.1 spec |
| POST | `/api/v1/connectors/generators/json-schema` | Generate connector stub from JSON Schema |

## Security Properties

| Property | Enforced at |
|----------|-------------|
| JDBC URL scheme allowlist (7 schemes) | Datasource create/update |
| JDBC host private-range block (RFC-1918 + loopback) | Datasource create/update |
| Driver class allowlist (5 drivers) | Datasource create/update |
| `passwordSecretRef` write-only — never returned in read responses | `TenantDatasourceResponse` |
| Test-connection rate limit: 5 req/min per instance | Resilience4j `datasource-test` |
| Pool `maxSize` capped at 50, connection timeout at 30 s | DTO `@Max` validation |
| OpenAPI `$ref` resolution disabled — no server-side SSRF via content | `OpenApiImportService` |
| SQL comments stripped before DML keyword scan | `ConnectorDefinitionValidator` |

## Configuration

See `config/env/.env.admin` for environment variables. Key settings:

| Variable | Default | Description |
|----------|---------|-------------|
| `ENGINE_SERVICE_URL` | `http://werkflow-engine:8081` | Engine service (Docker network) |
| `ERP_SERVICE_URL` | `http://werkflow-business:8084` | ERP service URL |
| `KEYCLOAK_URL` | — | Keycloak base URL |
