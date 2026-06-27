# Workflow Architecture Design

## Overview

Werkflow is an enterprise workflow automation platform built on Flowable BPMN. It uses a centralized engine for workflow orchestration, an admin service for connector and configuration management, and a unified portal frontend. External business data (HR, finance, procurement, inventory) is served by an independent ERP backing service (werkflow-erp) that is accessed through the connector abstraction — never via direct in-platform clients.

---

## Service Topology

| Service | Port | Purpose |
|---------|------|---------|
| Engine | 8081 | Flowable BPMN engine, process definitions, task management |
| Admin | 8083 | Admin APIs, connector registry, credential management, route access control |
| Portal | 4000 | Unified Next.js frontend — BPMN designer, form builder, task inbox, admin UI |
| Keycloak | 8090 | OAuth2/OIDC identity provider |
| PostgreSQL | 5432 | Shared database (engine + admin schemas) |
| ERP (external) | — | werkflow-erp: business domain data (HR, finance, procurement, inventory); accessed via registered connector, never via a direct in-platform client |

### Architecture Diagram

```
                    +-----------+
                    |  Keycloak |
                    |   :8090   |
                    +-----+-----+
                          |
              +-----------+-----------+
              |                       |
        +-----+-----+          +------+------+
        |   Engine   |          |    Admin    |
        |   :8081    |          |    :8083    |
        | (Flowable) |          | (Connector  |
        |            |          |  Registry)  |
        +-----+------+          +------+------+
              |                        |
              +----------+-------------+
                         |  connector abstraction
                         |  (SSRF-guarded, credential-resolved,
                         |   audit-logged — ADR-023/ADR-024)
                         v
              +----------+----------+
              |   External ERP      |
              |  (werkflow-erp)     |
              |  + other APIs       |
              +---------------------+

              +--------------------+
              |  PostgreSQL :5432  |
              +--------------------+

   +------------------------------------------------------+
   |               Portal (Next.js :4000)                  |
   |  BPMN designer | Form builder | Task inbox | Admin UI |
   +------------------------------------------------------+
```

---

## Workflow Deployment Model

All BPMN process definitions are deployed through the **Engine Service**. The engine auto-discovers `.bpmn20.xml` files in `services/engine/src/main/resources/processes/` at startup.

Additionally, processes can be deployed at runtime via the Portal's Process Designer (BPMN editor).

### Process Categories

| Category | Examples | Integration Path |
|----------|----------|-----------------|
| HR | Leave Approval, Onboarding, Performance Review | connector → werkflow-erp |
| Finance | CapEx Approval, Budget Check | connector → werkflow-erp |
| Procurement | Purchase Requisition | connector → werkflow-erp |
| Inventory | Stock Requisition | connector → werkflow-erp |
| Cross-Domain | Asset Transfer (HR + Inventory) | connector → werkflow-erp |

External data access routes through the connector abstraction. The engine calls registered connectors via `${externalApiCallDelegate}` (ADR-023); the admin service reads ERP design-time metadata (department lists, custody mappings) via `ErpMetadataReader` on the same connector path. No direct ERP clients exist in the platform services.

---

## Inter-Service Communication

### Action Block authoring model

BPMN authors do not hand-write delegate expressions or service URLs. The BPMN designer presents an **Action Block** picker on each ServiceTask. Selecting an action type auto-morphs the task and the designer writes the correct `flowable:delegateExpression` and extension fields.

| Action Block type | Delegate bean written by designer | Purpose |
|---|---|---|
| `CONNECTOR_OPERATION` | `${externalApiCallDelegate}` | Outbound REST call to a registered connector |
| `SEND_NOTIFICATION` | `${notificationDelegate}` | Email / channel notification via AdapterRegistry |
| `SET_VARIABLES` | `${setVariablesDelegate}` | Computed variable assignments (no external call) |
| `CALL_SUBPROCESS` | native `calledElement` | Start a child process instance |
| `HUMAN_APPROVAL` | UserTask (no service delegate) | Assign task to a candidate group |

### Pattern 1: Connector Operation (`${externalApiCallDelegate}`)

`RestConnectorDelegate` (bean name `externalApiCallDelegate`) makes SSRF-guarded HTTP calls to registered connectors. The connector's base URL and credential are resolved server-side from the connector registry (ADR-023, ADR-024) — no URL or credential appears in the BPMN.

```xml
<serviceTask id="fetchBudget" name="Fetch Budget from ERP"
             flowable:delegateExpression="${externalApiCallDelegate}">
  <extensionElements>
    <flowable:field name="connector">
      <flowable:string>erp-service</flowable:string>
    </flowable:field>
    <flowable:field name="path">
      <flowable:string>/api/finance/budgets/check</flowable:string>
    </flowable:field>
    <flowable:field name="onError">
      <flowable:string>FAIL</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

`onError` values: `FAIL` (default), `CONTINUE` (swallow error, proceed), `THROW_BPMN_ERROR` (trigger error boundary event).

### Pattern 2: Notification (`${notificationDelegate}`)

Routes through `AdapterRegistry` to the configured channel adapter (email, Slack, WhatsApp). Dispatch is `@Async`; the process does not block on delivery.

```xml
<serviceTask id="notifyRequester" name="Notify Requester"
             flowable:delegateExpression="${notificationDelegate}">
  <extensionElements>
    <flowable:field name="recipient">
      <flowable:expression>${requesterEmail}</flowable:expression>
    </flowable:field>
    <flowable:field name="templateKey">
      <flowable:string>request-approved</flowable:string>
    </flowable:field>
    <flowable:field name="channel">
      <flowable:string>email</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

Required fields: `recipient` (validated email expression) and `templateKey`. `channel` defaults to `email` when omitted.

### Pattern 3: Set Variables (`${setVariablesDelegate}`)

Evaluates JUEL expressions and writes process variables atomically. Used for computed fields that require no external call.

```xml
<serviceTask id="setApprovalTier" name="Set Approval Tier"
             flowable:delegateExpression="${setVariablesDelegate}">
  <extensionElements>
    <flowable:field name="var.approvalTier">
      <flowable:expression>${requestAmount > 50000 ? 'VP' : 'MANAGER'}</flowable:expression>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

All `var.*`-prefixed fields are evaluated atomically before any variable is written (no partial commit). Variable names only are audit-logged; values are not.

---

## Form Management

Forms are linked to BPMN user tasks via `flowable:formKey`:

```xml
<userTask id="managerApproval" name="Manager Approval">
    <extensionElements>
        <flowable:formKey>leave-approval</flowable:formKey>
    </extensionElements>
</userTask>
```

The Portal renders forms using form-js. Form definitions are stored in the Flowable database and managed via the Portal's Form Builder.

---

## Database Schema

All services share a single PostgreSQL database (`werkflow_db`):

```
werkflow_db
  +-- Flowable tables (act_re_*, act_ru_*, act_hi_*)
  +-- engine schema (process definitions, task state, connector bindings)
  +-- admin schema (connector registry, credential refs, route config, permissions)
```

Process definitions are visible to the Flowable engine regardless of which service deployed them. Business domain data (HR records, finance entries, procurement orders, inventory) lives in werkflow-erp's own database, which the platform reaches only through registered connectors.

---

## Process Variable Management

| Scope | Usage |
|-------|-------|
| Process variables | Shared across all tasks in a process instance |
| Task variables | Scoped to a single user task |
| Business key | Unique identifier for the process instance |

---

## Error Handling

- **Retries**: Service tasks support `failedJobRetryTimeCycle` (e.g., `R5:PT10S`)
- **Boundary timers**: User tasks can have timeout boundaries (e.g., 3-day escalation)
- **Compensation**: Sub-processes with compensation handlers for rollback scenarios

---

## Frontend Route Groups

The Portal uses Next.js route groups (no URL segment impact):

| Route Group | URL Prefix | Roles |
|-------------|-----------|-------|
| `(platform)` | `/processes`, `/forms`, `/workflows` | ADMIN, WORKFLOW_ADMIN |
| `(hr)` | `/hr/*` | HR_STAFF, HR_ADMIN |
| `(finance)` | `/finance/*` | FINANCE_STAFF, FINANCE_ADMIN |
| `(procurement)` | `/procurement/*` | PROCUREMENT_STAFF, PROCUREMENT_ADMIN |
| `(inventory)` | `/inventory/*` | INVENTORY_ADMIN |

All route groups share the same app-shell layout (sidebar + header).

---

## Scaling Considerations

- **Engine**: Stateless, can be load-balanced horizontally
- **Admin**: Stateless configuration service; scale horizontally
- **Database**: Read replicas for query scaling; connection pooling via HikariCP
- **Portal**: Static export + CDN, or multiple Next.js instances behind LB
- **ERP (external)**: werkflow-erp scales independently of the platform; the connector abstraction decouples its availability from engine uptime

---

## Related Documentation

- ADR-012: Engine Action Delegates Canonical Location (werkflow-platform `docs/adr/`)
- ADR-019: Service Adapter Layer for Connector Operations (werkflow-platform `docs/adr/`)
- ADR-023: All External Access via Connector Abstraction (werkflow-platform `docs/adr/`)
- ADR-024: Connector-Mode Credentials Resolved Server-Side (werkflow-platform `docs/adr/`)
- [API Path Structure](../API-Path-Structure.md)
- [Deployment Configuration Guide](../Deployment-Configuration-Guide.md)
