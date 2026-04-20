# Workflow Architecture Design

## Overview

Werkflow is an enterprise workflow automation platform built on Flowable BPMN. It uses a centralized engine for workflow orchestration, a consolidated business service for all domain logic, and a unified portal frontend.

---

## Service Topology

| Service | Port | Purpose |
|---------|------|---------|
| Engine | 8081 | Flowable BPMN engine, process definitions, task management |
| Admin | 8083 | Admin APIs, route access control, configuration |
| Business | 8084 | All domain logic (HR, Finance, Procurement, Inventory) |
| Portal | 4000 | Unified Next.js frontend with module-based route groups |
| Keycloak | 8090 | OAuth2/OIDC identity provider |
| PostgreSQL | 5432 | Shared database |

### Architecture Diagram

```
                    +-----------+
                    |  Keycloak |
                    |   :8090   |
                    +-----+-----+
                          |
         +----------------+----------------+
         |                |                |
   +-----+-----+   +-----+-----+   +------+------+
   |   Engine   |   |   Admin   |   |  Business   |
   |   :8081    |   |   :8083   |   |   :8084     |
   | (Flowable) |   | (Config)  |   | (HR/Fin/    |
   |            |   |           |   |  Proc/Inv)  |
   +-----+------+   +-----------+   +------+------+
         |                                  |
         +----------------------------------+
                          |
                    +-----+-----+
                    | PostgreSQL |
                    |   :5432    |
                    +-----+-----+

   +--------------------------------------------------+
   |              Portal (Next.js :4000)               |
   | (platform) | (hr) | (finance) | (procurement) |  |
   +--------------------------------------------------+
```

---

## Workflow Deployment Model

All BPMN process definitions are deployed through the **Engine Service**. The engine auto-discovers `.bpmn20.xml` files in `services/engine/src/main/resources/processes/` at startup.

Additionally, processes can be deployed at runtime via the Portal's Process Designer (BPMN editor).

### Process Categories

| Category | Examples | Delegate Target |
|----------|----------|-----------------|
| HR | Leave Approval, Onboarding, Performance Review | business:8084 |
| Finance | CapEx Approval, Budget Check | business:8084 |
| Procurement | Purchase Requisition | business:8084 |
| Inventory | Stock Requisition | business:8084 |
| Cross-Domain | Asset Transfer (HR + Inventory) | business:8084 |

All domain-specific logic lives in the Business service. The engine calls it via `RestServiceDelegate` or domain-specific Java delegates.

---

## Inter-Service Communication

### Pattern 1: RestServiceDelegate (Generic REST)

The engine's `RestServiceDelegate` makes HTTP calls to the Business service during workflow execution:

```java
// Configured in BPMN service tasks via field injection
// baseUrl: http://business-service:8084
// endpoint: /api/hr/leave/validate
// method: POST
```

### Pattern 2: Domain-Specific Delegates

Java delegates in the engine that call Business service APIs:

```java
public class FinanceBudgetCheckDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        // Call Business Service (consolidated)
        Budget budget = restTemplate.getForObject(
            "http://business-service:8084/api/finance/budgets/{id}",
            Budget.class
        );
        execution.setVariable("budgetAvailable", budget.isAvailable());
    }
}
```

### Pattern 3: Notification Delegates

```java
public class NotificationDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        String email = (String) execution.getVariable("approverEmail");
        emailService.sendNotification(email, "Action Required");
    }
}
```

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
  +-- business schema (hr, finance, procurement, inventory entities)
  +-- admin schema (route config, permissions)
```

Process definitions are visible to the Flowable engine regardless of which service deployed them. The Business service uses Flyway migrations prefixed by domain (e.g., `V20__hr_leave_types.sql`).

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
- **Business**: Single service handles all domains; scale vertically or horizontally behind LB
- **Database**: Read replicas for query scaling; connection pooling via HikariCP
- **Portal**: Static export + CDN, or multiple Next.js instances behind LB

---

## Related Documentation

- [Delegate Architecture Analysis](./Delegate-Architecture-Analysis.md)
- [API Path Structure](../API-Path-Structure.md)
- [Deployment Configuration Guide](../Deployment-Configuration-Guide.md)
