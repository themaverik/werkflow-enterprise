# Delegate Architecture Analysis

## Summary

Werkflow uses **generic, reusable delegates** for cross-service communication in BPMN workflows. The primary delegate is `RestServiceDelegate`, which can call any REST endpoint without tight coupling to specific services.

**Architectural Rule**: The Engine Service orchestrates workflows but never implements domain business logic. All domain logic lives in the Business service.

---

## Service Topology (Post-Consolidation)

| Service | Port | Role |
|---------|------|------|
| Engine | 8081 | Flowable BPMN engine, delegates, orchestration |
| Business | 8084 | All domain APIs (HR, Finance, Procurement, Inventory) |
| Admin | 8083 | Configuration, route access |

All delegate REST calls from the Engine target `business-service:8084`.

---

## Delegate Patterns

### Pattern 1: Generic RestServiceDelegate (Recommended)

Used for **all cross-service calls** from Engine workflows.

```xml
<serviceTask id="budgetCheck" name="Check Budget Availability"
             flowable:delegateExpression="${restServiceDelegate}">
  <extensionElements>
    <flowable:field name="url">
      <flowable:expression>${businessServiceUrl}/api/finance/budget/check</flowable:expression>
    </flowable:field>
    <flowable:field name="method">
      <flowable:string>POST</flowable:string>
    </flowable:field>
    <flowable:field name="body">
      <flowable:expression>#{{'departmentId': departmentId, 'amount': totalAmount}}</flowable:expression>
    </flowable:field>
    <flowable:field name="responseVariable">
      <flowable:string>budgetCheckResponse</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

**Key properties**:
- `url`: Target endpoint (uses environment variable for base URL)
- `method`: HTTP method (GET, POST, PUT, DELETE)
- `body`: SpEL map literal that serializes to JSON
- `responseVariable`: Process variable to store the response
- `timeoutSeconds`: Optional timeout

**Location**: `/shared/delegates/src/main/java/com/werkflow/delegates/rest/RestServiceDelegate.java`

### Pattern 2: Domain-Specific Delegates (Service-Internal)

Used for operations **within** the Business service where workflows are deployed alongside the service:

```xml
<serviceTask id="approveLeave" name="Approve Leave"
             flowable:delegateExpression="${leaveApprovalDelegate}">
</serviceTask>
```

These delegates live in the Business service, access the database directly, and are never called from the Engine.

### Pattern 3: Notification Delegates

Generic notification delegates for email and alerts:

```xml
<serviceTask id="sendNotification" name="Send Approval Notification"
             flowable:delegateExpression="${restServiceDelegate}">
  <extensionElements>
    <flowable:field name="url">
      <flowable:expression>${engineServiceUrl}/notifications/email</flowable:expression>
    </flowable:field>
    <flowable:field name="body">
      <flowable:expression>#{{'to': requesterEmail, 'subject': 'Request Approved', 'templateName': 'approval', 'variables': #{{'requestId': capexId}}}}</flowable:expression>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

---

## SpEL Map Literal Syntax

BPMN expressions use SpEL (not JSON) for request bodies:

```xml
<!-- Simple -->
<flowable:expression>#{{'amount': requestAmount, 'dept': departmentId}}</flowable:expression>

<!-- Nested -->
<flowable:expression>#{{'request': #{{'amount': amount, 'description': desc}}, 'approver': approverEmail}}</flowable:expression>
```

- `#{}` = SpEL expression evaluator
- `{'key': value}` = Map literal
- Variables like `requestAmount` are resolved from process variables
- The delegate serializes the resulting `Map<String, Object>` to JSON via WebClient

---

## Environment Configuration

All service URLs are configured in `application.yml`:

```yaml
services:
  business:
    url: ${BUSINESS_SERVICE_URL:http://localhost:8084}
```

Docker Compose:
```yaml
engine-service:
  environment:
    BUSINESS_SERVICE_URL: http://business-service:8084
```

BPMN access via Spring bean:
```xml
<flowable:expression>${businessServiceUrl}/api/finance/budget/check</flowable:expression>
```

---

## Anti-Patterns to Avoid

### 1. Tight SpEL Coupling (Broken)

```xml
<!-- DO NOT: Assumes Spring bean exists in Engine -->
<serviceTask flowable:expression="${capexService.createRequest(execution.getVariables())}"/>
```

This fails at runtime because `capexService` is a Business service bean, not an Engine bean.

### 2. Service-Specific Delegates in Engine

```java
// DO NOT: Creates tight coupling between Engine and domain services
@Component("capexServiceDelegate")
public class CapExServiceDelegate implements JavaDelegate { ... }
```

Use `RestServiceDelegate` instead. One generic delegate serves all domains.

---

## Decision Matrix

| Factor | RestServiceDelegate | Service-Specific Delegates |
|--------|---------------------|---------------------------|
| Coupling | LOW | HIGH |
| Reusability | Works for any REST endpoint | One per service |
| No-Code | Configure via BPMN designer | Requires Java code |
| Maintenance | Single implementation | N implementations |

**Decision**: Use `RestServiceDelegate` for all Engine cross-service calls. Domain-specific delegates are permitted only within the Business service for internal operations.

---

## Advanced Patterns

### Conditional Service Calls

```xml
<exclusiveGateway id="needsProcurement" />
<sequenceFlow sourceRef="needsProcurement" targetRef="createPO">
  <conditionExpression>${requestAmount > 100000}</conditionExpression>
</sequenceFlow>
```

### Parallel Service Calls

```xml
<parallelGateway id="fork" />
<serviceTask id="checkBudget" flowable:delegateExpression="${restServiceDelegate}">...</serviceTask>
<serviceTask id="checkEligibility" flowable:delegateExpression="${restServiceDelegate}">...</serviceTask>
<parallelGateway id="join" />
```

### Error Handling

```xml
<boundaryEvent id="serviceError" attachedToRef="callService">
  <errorEventDefinition />
</boundaryEvent>
<sequenceFlow sourceRef="serviceError" targetRef="handleError" />
<userTask id="handleError" name="Manual Intervention Required" />
```

---

## Related Documentation

- [Workflow Architecture Design](./Workflow-Architecture-Design.md)
- [API Path Structure](../API-Path-Structure.md)
- [BPMN Quick Reference](../BPMN-Quick-Reference-Guide.md)
