# Engine Service

Flowable BPM orchestration engine for Werkflow Enterprise.

## Overview

Hosts the Flowable 7.2.0 BPMN/DMN/CMMN engine. Manages process deployment, execution, task assignment,
event correlation (message and signal), webhook inbound correlation, and database connector execution.
Exposes design-time APIs consumed by the Admin Service and Portal.

## Responsibilities

- Process definition deployment and versioning
- Process instance lifecycle (start, suspend, terminate)
- User task management and candidate group resolution
- Message and signal event correlation
- Inbound webhook correlation (HMAC-verified)
- DMN decision deployment and evaluation
- Process and task history
- BPMN variable scope analysis (design-time endpoint)
- Tier 1 YAML role-to-group mapping read-through
- Database connector execution (named queries, keyset pagination, per-tenant HikariCP pools, circuit breaker)

## Technology Stack

- Java 21
- Spring Boot 3.3.x
- Flowable 7.2.0
- PostgreSQL 15 (schema: `flowable`)
- OAuth2/JWT authentication (Keycloak)

## Port

- **8081** — HTTP REST API

## Key API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/config/flowable-role-mappings` | None (internal) | Tier 1 YAML role mappings |
| POST | `/api/v1/webhooks/{connectorKey}` | HMAC signature | Inbound webhook correlation |
| GET | `/api/v1/processes/{id}/bpmn` | JWT | Fetch deployed BPMN XML |
| POST | `/api/v1/processes/{id}/start` | JWT | Start process instance |
| GET | `/api/v1/tasks` | JWT | List user tasks |
| POST | `/api/v1/tasks/{id}/complete` | JWT | Complete a task |

## Connector Delegates

| Delegate | Bean name | Transport |
|----------|-----------|-----------|
| `RestConnectorDelegate` | `externalApiCallDelegate`, `restConnectorDelegate` | HTTP/REST |
| `DatabaseConnectorDelegate` | `databaseConnectorDelegate` | JDBC (named queries) |
| `ConnectorWebhookDelegate` | `connectorWebhookDelegate` | Inbound webhook |

`DatabaseConnectorDelegate` uses per-tenant HikariCP pools managed by `DatasourceRegistry` and wraps each execution in a Resilience4j circuit breaker keyed `{tenantCode}:{connectorKey}`.

## Security Properties

| Property | Enforced at |
|----------|-------------|
| SQL comments stripped before DML keyword rejection | `NamedQueryExecutor.rejectDml()` |
| `DsKey(tenantCode, ref)` record — no string-concat key collision | `DatasourceRegistry` pool cache |
| Database password resolved locally via `SecretsResolver` — never received over the wire | `DatasourceRegistry` |
| Circuit breaker entries evicted on datasource removal | `DatasourceRegistry.evict()` |

## Configuration

See `config/env/.env.engine` for environment variables. Key settings:

| Variable | Default | Description |
|----------|---------|-------------|
| `ADMIN_SERVICE_URL` | `http://admin-service:8083` | Admin service (Docker network) |
| `KEYCLOAK_URL` | — | Keycloak base URL |
| `FLOWABLE_DATABASE_SCHEMA_UPDATE` | `true` | Auto-migrate Flowable schema |
