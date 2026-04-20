> **Note**: This document reflects pre-S1 architecture and is scheduled for a full rewrite. Use with caution.

# Werkflow - Workflow Management Guide

## Overview

Phase 3 has implemented Flowable BPM integration for automated HR workflow processes. This guide explains how to use the workflow features.

## Available Workflows

### 1. Leave Approval Process (`leaveApprovalProcess`)

Automates the employee leave request approval workflow.

**Process Flow:**
1. Employee submits leave request → System starts workflow
2. Manager reviews request
3. Manager approves or rejects
4. If approved: HR is notified → Employee is notified
5. If rejected: Employee is notified
6. Process ends

**Process Variables:**
- `leaveId`: Leave request ID
- `employeeId`: Employee ID
- `employeeName`: Employee name
- `leaveType`: VACATION, SICK, PERSONAL, UNPAID
- `startDate`: Leave start date
- `endDate`: Leave end date
- `reason`: Leave reason
- `managerId`: Manager ID
- `approved`: Boolean (set by manager)
- `managerComments`: Manager's comments

### 2. Employee Onboarding Process (`employeeOnboardingProcess`)

Automates the new employee onboarding workflow with parallel task execution.

**Process Flow:**
1. New employee hired → System starts workflow
2. **Parallel execution:**
   - IT team: Set up accounts, laptop, system access
   - HR team: Collect documents, contracts, benefits enrollment
   - Manager: Assign buddy, prepare workspace, schedule first week
3. Send welcome email to employee
4. First day orientation by HR
5. Update employee status to ACTIVE
6. Notify all stakeholders
7. Process ends

**Process Variables:**
- `employeeId`: Employee ID
- `employeeName`: Employee name
- `employeeEmail`: Employee email
- `departmentId`: Department ID
- `position`: Job position
- `startDate`: Start date
- `managerId`: Manager ID
- Plus task-specific variables (IT setup, HR docs, manager prep)

### 3. Performance Review Process (`performanceReviewProcess`)

Automates the comprehensive performance review cycle.

**Process Flow:**
1. Review cycle started → System starts workflow
2. Employee completes self-assessment
3. Manager completes evaluation
4. Manager schedules review meeting
5. Conduct review meeting
6. Decision point: Employee agrees?
   - Yes → Proceed to HR approval
   - No → Address concerns → Proceed to HR approval
7. HR reviews and approves
8. Update review record
9. Notify all stakeholders
10. Process ends

**Process Variables:**
- `reviewId`: Performance review ID
- `employeeId`: Employee ID
- `employeeName`: Employee name
- `managerId`: Manager ID
- `reviewPeriodStart`: Review period start date
- `reviewPeriodEnd`: Review period end date
- `reviewType`: ANNUAL, QUARTERLY, PROBATION
- Plus assessment and evaluation details

## REST API Endpoints

All workflow endpoints are under `/api/workflows` and require authentication.

### Process Management

**Start a Process:**
```http
POST /api/workflows/processes/start
Authorization: Bearer {token}
Content-Type: application/json

{
  "processDefinitionKey": "leaveApprovalProcess",
  "businessKey": "LEAVE-2024-001",
  "variables": {
    "leaveId": 1,
    "employeeId": 5,
    "employeeName": "John Doe",
    "leaveType": "VACATION",
    "startDate": "2024-06-01",
    "endDate": "2024-06-10",
    "reason": "Summer vacation",
    "managerId": 2
  }
}
```

**Get Process Instance:**
```http
GET /api/workflows/processes/{processInstanceId}
Authorization: Bearer {token}
```

**Get All Process Instances:**
```http
GET /api/workflows/processes?processDefinitionKey=leaveApprovalProcess
Authorization: Bearer {token}
```

**Delete Process Instance:**
```http
DELETE /api/workflows/processes/{processInstanceId}?deleteReason=Cancelled
Authorization: Bearer {token}
```

**Get Process Variables:**
```http
GET /api/workflows/processes/{processInstanceId}/variables
Authorization: Bearer {token}
```

**Set Process Variables:**
```http
PUT /api/workflows/processes/{processInstanceId}/variables
Authorization: Bearer {token}
Content-Type: application/json

{
  "customVariable": "value",
  "anotherVariable": 123
}
```

### Task Management

**Get Tasks for Assignee:**
```http
GET /api/workflows/tasks/assignee/{userId}
Authorization: Bearer {token}
```

**Get Tasks for Group:**
```http
GET /api/workflows/tasks/group/MANAGER
Authorization: Bearer {token}
```

**Get Tasks for Process:**
```http
GET /api/workflows/tasks/process/{processInstanceId}
Authorization: Bearer {token}
```

**Get Task Details:**
```http
GET /api/workflows/tasks/{taskId}
Authorization: Bearer {token}
```

**Claim a Task:**
```http
POST /api/workflows/tasks/{taskId}/claim?userId={userId}
Authorization: Bearer {token}
```

**Complete a Task:**
```http
POST /api/workflows/tasks/complete
Authorization: Bearer {token}
Content-Type: application/json

{
  "taskId": "12345",
  "variables": {
    "approved": true,
    "managerComments": "Approved. Enjoy your vacation!"
  },
  "comment": "Looks good, approved."
}
```

## Role-Based Access Control

Workflow endpoints are protected by Keycloak roles:

| Role | Permissions |
|------|-------------|
| **HR_ADMIN** | Full access to all workflow operations |
| **HR_MANAGER** | Manage processes, delete instances, handle escalations |
| **MANAGER** | Start processes, complete tasks, view team workflows |
| **EMPLOYEE** | View assigned tasks, complete own tasks |

## Example Workflow Scenarios

### Scenario 1: Employee Requests Leave

1. **Employee submits leave via Leave API**
   ```http
   POST /api/leaves
   {
     "employeeId": 5,
     "type": "VACATION",
     "startDate": "2024-06-01",
     "endDate": "2024-06-10",
     "reason": "Family vacation"
   }
   ```

2. **System starts workflow automatically (in service layer)**
   ```http
   POST /api/workflows/processes/start
   {
     "processDefinitionKey": "leaveApprovalProcess",
     "businessKey": "LEAVE-123",
     "variables": { ... }
   }
   ```

3. **Manager checks pending tasks**
   ```http
   GET /api/workflows/tasks/group/MANAGER
   ```

4. **Manager completes review task**
   ```http
   POST /api/workflows/tasks/complete
   {
     "taskId": "task-456",
     "variables": {
       "approved": true,
       "managerComments": "Approved!"
     }
   }
   ```

5. **System automatically:**
   - Updates leave status to APPROVED
   - Notifies HR
   - Notifies employee
   - Ends process

### Scenario 2: New Employee Onboarding

1. **HR creates employee record**
   ```http
   POST /api/employees
   ```

2. **HR starts onboarding workflow**
   ```http
   POST /api/workflows/processes/start
   {
     "processDefinitionKey": "employeeOnboardingProcess",
     "variables": { ... }
   }
   ```

3. **Parallel tasks created for IT, HR, Manager**
   ```http
   GET /api/workflows/tasks/group/IT
   GET /api/workflows/tasks/group/HR_ADMIN
   GET /api/workflows/tasks/group/MANAGER
   ```

4. **Each team completes their tasks**
   - IT completes account setup
   - HR completes documentation
   - Manager prepares workspace

5. **System automatically:**
   - Sends welcome email
   - Schedules orientation
   - Updates employee status
   - Notifies stakeholders

## Testing Workflows

### Using Swagger UI

1. Start the application and navigate to: `http://localhost:8080/api/swagger-ui.html`
2. Authenticate with Keycloak token
3. Navigate to "Workflow Management" section
4. Test workflow endpoints interactively

### API Explorer

Use the Swagger UI at `http://localhost:8081/swagger-ui.html` to explore and test all Engine API endpoints interactively. Authentication is handled via the Authorize button — paste a valid Bearer token from Keycloak.

### Flowable UI (Optional)

Flowable provides admin UIs for workflow management:
- Process definitions: `http://localhost:8080/api/flowable-ui/`
- Task management: `http://localhost:8080/api/flowable-task/`

(Note: May require additional configuration)

## Monitoring and Debugging

### Logging

Workflow operations are logged at INFO and DEBUG levels:

```yaml
logging:
  level:
    org.flowable: INFO
    com.werkflow.workflow: DEBUG
```

### Check Process Status

```http
GET /api/workflows/processes/{processInstanceId}
```

Response shows:
- Current state
- Start/end times
- Process variables
- Suspension status

### Check Active Tasks

```http
GET /api/workflows/tasks/process/{processInstanceId}
```

Shows all tasks in the process, their assignees, and status.

## Integration with Existing CRUD APIs

Workflow processes integrate seamlessly with existing HR CRUD operations:

1. **Leave Management:**
   - Create leave → Start `leaveApprovalProcess`
   - Manager approves via workflow task
   - Leave status updated automatically by workflow delegate

2. **Employee Management:**
   - Create employee → Start `employeeOnboardingProcess`
   - Teams complete onboarding tasks
   - Employee status updated to ACTIVE by workflow delegate

3. **Performance Reviews:**
   - Create review → Start `performanceReviewProcess`
   - Employee and manager complete assessments
   - Review record updated by workflow delegate

## Best Practices

1. **Always use business keys** when starting processes for easier tracking
2. **Include all required variables** when starting processes
3. **Add comments when completing tasks** for audit trail
4. **Check task assignee** before attempting to complete
5. **Use appropriate roles** - don't give EMPLOYEE role access to management tasks
6. **Monitor long-running processes** using the process instance endpoints
7. **Handle errors gracefully** - workflow errors return standard error responses

## Troubleshooting

### Process doesn't start
- Check all required variables are provided
- Verify user has appropriate role
- Check logs for validation errors

### Task cannot be completed
- Verify task exists and is not already completed
- Check user is assignee or in candidate group
- Ensure all required variables for task completion are provided

### Delegate errors
- Check entity IDs exist in database
- Verify foreign key relationships
- Review delegate logs for specific error messages

## Integrating New Workflows

This section explains how to add new workflows to the Werkflow platform with **minimal code changes** following the established architecture.

### Architecture Principles

The Werkflow platform follows a **decoupled architecture** where:

1. **Workflows are orchestrated by the Engine Service** using BPMN processes
2. **Data is managed by department services** (Finance, Procurement, Inventory, HR, etc.)
3. **Generic delegates** handle cross-service communication via REST APIs
4. **No code changes required** in existing services to add new workflows

**Key Benefits:**
- 90%+ no-code workflow creation
- BPMN processes are configuration, not code
- Department services remain focused on domain logic
- Workflows can be added, modified, or removed without code changes

### Step-by-Step Integration Guide

Follow these steps to integrate a new workflow into the platform:

---

#### Step 1: Design the BPMN Process

Create a BPMN 2.0 XML file for your workflow.

**Location:** `services/engine/src/main/resources/processes/`

**Naming Convention:** `{workflow-name}-process.bpmn20.xml`

**Example:** For a new "Equipment Requisition" workflow:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://werkflow.com/bpmn/equipment">

  <process id="equipment-requisition-process" name="Equipment Requisition Process" isExecutable="true">

    <documentation>Equipment requisition approval workflow</documentation>

    <!-- Start Event with Form -->
    <startEvent id="startEvent" name="Request Submitted"
                flowable:formKey="equipment-request">
    </startEvent>

    <!-- Service Task: Create Request -->
    <serviceTask id="createRequest" name="Create Equipment Request"
                 flowable:expression="${execution.setVariable('requestId', equipmentService.createRequest(execution.getVariables()))}">
    </serviceTask>

    <!-- User Task: Manager Approval -->
    <userTask id="managerApproval" name="Manager Review"
              flowable:candidateGroups="EQUIPMENT_MANAGER"
              flowable:formKey="equipment-approval">
    </userTask>

    <!-- Exclusive Gateway: Approved? -->
    <exclusiveGateway id="decisionGateway" name="Approved?" />

    <!-- Service Task: Update Status -->
    <serviceTask id="updateApproved" name="Update Status: Approved"
                 flowable:expression="${equipmentService.updateStatus(requestId, 'APPROVED')}">
    </serviceTask>

    <serviceTask id="updateRejected" name="Update Status: Rejected"
                 flowable:expression="${equipmentService.updateStatus(requestId, 'REJECTED')}">
    </serviceTask>

    <!-- End Events -->
    <endEvent id="endApproved" name="Approved" />
    <endEvent id="endRejected" name="Rejected" />

    <!-- Sequence Flows -->
    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="createRequest" />
    <sequenceFlow id="flow2" sourceRef="createRequest" targetRef="managerApproval" />
    <sequenceFlow id="flow3" sourceRef="managerApproval" targetRef="decisionGateway" />

    <sequenceFlow id="approved" sourceRef="decisionGateway" targetRef="updateApproved">
      <conditionExpression>${approvalDecision == 'APPROVED'}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="rejected" sourceRef="decisionGateway" targetRef="updateRejected">
      <conditionExpression>${approvalDecision == 'REJECTED'}</conditionExpression>
    </sequenceFlow>

    <sequenceFlow id="flow4" sourceRef="updateApproved" targetRef="endApproved" />
    <sequenceFlow id="flow5" sourceRef="updateRejected" targetRef="endRejected" />

  </process>
</definitions>
```

**That's it!** Flowable automatically loads this process on startup - no code changes needed.

---

#### Step 2: Create Form.io Templates (Optional)

Add dynamic form definitions for workflow forms.

**Location:** `frontends/admin-portal/lib/form-templates.ts`

**Code Change:**

```typescript
// frontends/admin-portal/lib/form-templates.ts

export const formTemplates = {
  // ... existing templates ...

  'equipment-request': {
    title: 'Equipment Requisition Form',
    display: 'form',
    components: [
      {
        type: 'textfield',
        key: 'equipmentName',
        label: 'Equipment Name',
        validate: { required: true },
      },
      {
        type: 'select',
        key: 'category',
        label: 'Category',
        data: {
          values: [
            { label: 'Computers', value: 'COMPUTERS' },
            { label: 'Furniture', value: 'FURNITURE' },
            { label: 'Tools', value: 'TOOLS' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'number',
        key: 'quantity',
        label: 'Quantity',
        validate: { required: true, min: 1 },
      },
      {
        type: 'textarea',
        key: 'justification',
        label: 'Business Justification',
        validate: { required: true, minLength: 20 },
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit Request',
        theme: 'primary',
      },
    ],
  },

  'equipment-approval': {
    title: 'Equipment Approval Form',
    display: 'form',
    components: [
      {
        type: 'select',
        key: 'approvalDecision',
        label: 'Decision',
        data: {
          values: [
            { label: 'Approve', value: 'APPROVED' },
            { label: 'Reject', value: 'REJECTED' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'comments',
        label: 'Comments',
        validate: { required: true },
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit Decision',
        theme: 'primary',
      },
    ],
  },
};
```

**That's it!** Forms are now available for the workflow - no service changes needed.

---

#### Step 3: Implement Service Delegates (If Needed)

If you're calling **existing service APIs**, use Spring Expression Language (SpEL) directly in BPMN - **no code needed**.

If you need **custom logic**, create a reusable delegate.

**Location:** `services/engine/src/main/java/com/werkflow/engine/delegate/`

**Example:** Generic REST service delegate (already exists, reuse it):

```java
// services/engine/src/main/java/com/werkflow/engine/delegate/RestServiceDelegate.java

@Component("restServiceDelegate")
public class RestServiceDelegate implements JavaDelegate {

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void execute(DelegateExecution execution) {
        String serviceUrl = (String) execution.getVariable("serviceUrl");
        String method = (String) execution.getVariable("httpMethod");
        Object requestBody = execution.getVariable("requestBody");

        // Make REST call to department service
        ResponseEntity<?> response = restTemplate.exchange(
            serviceUrl,
            HttpMethod.valueOf(method),
            new HttpEntity<>(requestBody),
            Object.class
        );

        execution.setVariable("responseData", response.getBody());
    }
}
```

**Usage in BPMN:**

```xml
<serviceTask id="callService" name="Call Equipment Service"
             flowable:delegateExpression="${restServiceDelegate}">
  <extensionElements>
    <flowable:field name="serviceUrl">
      <flowable:string>http://equipment-service:8087/api/equipment</flowable:string>
    </flowable:field>
    <flowable:field name="httpMethod">
      <flowable:string>POST</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

**That's it!** Generic delegates handle cross-service calls - no new code needed.

---

#### Step 4: Update Admin Portal Dashboard (Optional)

Add workflow tracking to the centralized dashboard.

**Location:** `frontends/admin-portal/app/(studio)/workflows/page.tsx`

**Code Change:** Add service to the tabs array:

```typescript
// frontends/admin-portal/app/(studio)/workflows/page.tsx

const services = [
  { id: 'all', label: 'All Workflows' },
  { id: 'hr', label: 'HR' },
  { id: 'capex', label: 'CapEx' },
  { id: 'procurement', label: 'Procurement' },
  { id: 'inventory', label: 'Inventory' },
  { id: 'equipment', label: 'Equipment' },  // <-- Add this line
]
```

**That's it!** New workflow appears in the dashboard - minimal code change.

---

### Code Changes Summary

Here's where you need to make changes (all minimal):

| Step | Location | Type | Required? |
|------|----------|------|-----------|
| **1. BPMN Process** | `services/engine/src/main/resources/processes/` | **Configuration** | ✅ Yes |
| **2. Form Templates** | `frontends/admin-portal/lib/form-templates.ts` | Code (1 object) | ⚠️ Optional |
| **3. Service Delegates** | `services/engine/src/main/java/.../delegate/` | Code (Java class) | ⚠️ Only if custom logic needed |
| **4. Dashboard Tab** | `frontends/admin-portal/app/(studio)/workflows/page.tsx` | Code (1 line) | ⚠️ Optional |
| **5. Department Service** | None | None | ✅ No changes needed |

**Key Insight:**
- The **only required change** is the BPMN XML file (configuration, not code)
- Department services remain **completely unchanged**
- Generic delegates handle all service communication

---

### Real-World Examples from Phase 3

Let's look at how Phase 3 workflows were implemented:

#### Example 1: CapEx Approval Workflow

**Files Created:**

1. **BPMN Process** (Configuration only):
   ```
   services/engine/src/main/resources/processes/capex-approval-process.bpmn20.xml
   ```

2. **Form Template** (TypeScript object):
   ```typescript
   // frontends/admin-portal/lib/form-templates.ts
   'capex-request': { /* form definition */ }
   ```

3. **Dashboard Update** (1 line added):
   ```typescript
   // frontends/admin-portal/app/(studio)/workflows/page.tsx
   { id: 'capex', label: 'CapEx' }
   ```

**Finance Service Changes:** ✅ **ZERO** - Service already has CapEx APIs

**Total Code Changes:**
- 1 BPMN XML file (configuration)
- 1 TypeScript form object
- 1 dashboard tab entry

---

#### Example 2: Asset Transfer Workflow

**Files Created:**

1. **BPMN Process**:
   ```
   services/engine/src/main/resources/processes/asset-transfer-approval-process.bpmn20.xml
   ```

2. **Form Template**:
   ```typescript
   'asset-transfer-request': { /* form definition */ }
   ```

**Inventory Service Changes:** ✅ **ZERO** - Service already has transfer APIs

**Delegates Used:** Generic `RestServiceDelegate`, `NotificationDelegate` (already exist)

**Total New Code:** 0 Java files, 0 service changes

---

### Integration Patterns

#### Pattern 1: Direct SpEL Expressions (No Delegates)

Use when calling existing service methods directly:

```xml
<serviceTask id="createRequest" name="Create Request"
             flowable:expression="${financeService.createCapExRequest(execution.getVariables())}">
</serviceTask>
```

**Pros:** No delegate code needed
**Cons:** Service bean must be available in Engine Service context

---

#### Pattern 2: Generic REST Delegate (Recommended)

Use for calling department service REST APIs:

```xml
<serviceTask id="callFinanceAPI" name="Call Finance Service"
             flowable:delegateExpression="${restServiceDelegate}">
  <extensionElements>
    <flowable:field name="serviceUrl">
      <flowable:string>http://finance-service:8084/api/capex</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

**Pros:** True microservice separation, no coupling
**Cons:** Requires HTTP call overhead

---

#### Pattern 3: Custom Delegate (When Needed)

Use for complex orchestration logic:

```java
@Component("customApprovalDelegate")
public class CustomApprovalDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        // Custom logic here
        BigDecimal amount = (BigDecimal) execution.getVariable("amount");

        if (amount.compareTo(new BigDecimal("50000")) > 0) {
            execution.setVariable("requiresVPApproval", true);
        }
    }
}
```

**Pros:** Full control over logic
**Cons:** Requires Java code in Engine Service

---

### Configuration Requirements

#### 1. Engine Service Configuration

If integrating with a **new department service**, add the service URL:

**Location:** `services/engine/src/main/resources/application.yml`

```yaml
app:
  services:
    hr-url: ${HR_SERVICE_URL:http://localhost:8082}
    admin-url: ${ADMIN_SERVICE_URL:http://localhost:8083}
    finance-url: ${FINANCE_SERVICE_URL:http://localhost:8084}
    procurement-url: ${PROCUREMENT_SERVICE_URL:http://localhost:8085}
    inventory-url: ${INVENTORY_SERVICE_URL:http://localhost:8086}
    equipment-url: ${EQUIPMENT_SERVICE_URL:http://localhost:8087}  # <-- Add new service
```

#### 2. Docker Compose (For New Services)

If adding a **new department service**, update `infrastructure/docker/docker-compose.yml`:

```yaml
services:
  equipment-service:
    build:
      context: ../..
      dockerfile: Dockerfile
      target: equipment-service
    container_name: werkflow-equipment
    environment:
      SERVER_PORT: 8087
      SPRING_DATASOURCE_SCHEMA: equipment_service
    ports:
      - "8087:8087"
```

#### 3. Database Schema (For New Services)

Update `infrastructure/docker/init-db.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS equipment_service;
GRANT ALL PRIVILEGES ON SCHEMA equipment_service TO werkflow_admin;
```

---

### Testing New Workflows

#### 1. Start the Workflow

```http
POST http://localhost:8081/api/workflows/processes/start
Authorization: Bearer {token}
Content-Type: application/json

{
  "processDefinitionKey": "equipment-requisition-process",
  "businessKey": "EQ-REQ-2025-001",
  "variables": {
    "equipmentName": "Dell Laptop",
    "category": "COMPUTERS",
    "quantity": 5,
    "requestedBy": "john.doe@werkflow.com"
  }
}
```

#### 2. Check Active Tasks

```http
GET http://localhost:8081/api/workflows/tasks/group/EQUIPMENT_MANAGER
```

#### 3. Complete Task

```http
POST http://localhost:8081/api/workflows/tasks/complete
{
  "taskId": "12345",
  "variables": {
    "approvalDecision": "APPROVED",
    "comments": "Equipment approved for IT department"
  }
}
```

---

### Best Practices for Minimal Code Changes

1. **Reuse Generic Delegates**
   - Use `RestServiceDelegate` for HTTP calls
   - Use `NotificationDelegate` for emails/notifications
   - Use `ApprovalDelegate` for common approval logic

2. **Keep Department Services Independent**
   - Services should not know about workflows
   - Expose REST APIs for data operations
   - Workflows orchestrate via Engine Service

3. **Use Configuration Over Code**
   - BPMN XML is configuration
   - Form.io templates are data, not code
   - Leverage SpEL expressions where possible

4. **Follow Naming Conventions**
   - Process IDs: `{domain}-{action}-process`
   - Form keys: `{domain}-{formtype}`
   - Service tasks: descriptive names

5. **Document Process Variables**
   - Add `<documentation>` tags in BPMN
   - Comment expected variables
   - Define variable types and defaults

---

### Troubleshooting

**Q: Process definition not found after adding BPMN file**

A: Restart Engine Service. Flowable scans `processes/` directory on startup.

```bash
docker restart werkflow-engine
```

---

**Q: Service delegate bean not found**

A: Ensure delegate is annotated with `@Component` and package is scanned:

```java
@Component("myDelegate")
public class MyDelegate implements JavaDelegate { }
```

---

**Q: Form not appearing in workflow**

A: Check `flowable:formKey` in BPMN matches form template key:

```xml
<userTask id="..." flowable:formKey="equipment-request">
```

```typescript
'equipment-request': { /* must match */ }
```

---

**Q: Can't call department service from delegate**

A: Ensure service URL is configured in `application.yml` and accessible from Engine Service container.

---

## Next Steps

Phase 4 will add comprehensive testing:
- Unit tests for workflow services
- Integration tests for workflow APIs
- Process definition tests

---

For more information, see:
- [README.md](README.md) - General project setup
- [TESTING.md](TESTING.md) - API testing guide
- [KEYCLOAK_SETUP.md](KEYCLOAK_SETUP.md) - Authentication setup
- [Enterprise_Workflow_Roadmap.md](Enterprise_Workflow_Roadmap.md) - Platform architecture
