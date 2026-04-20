# BPMN Quick Reference Guide

## Approval Workflows in BPMN

This guide provides quick reference for mapping approval concepts to BPMN constructs in Werkflow.

---

## 1. User Tasks (Approval Tasks)

### Basic Approval Task

```xml
<userTask id="managerApproval" name="Manager Approval"
          flowable:candidateGroups="DEPARTMENT_HEAD_${departmentId}"
          flowable:formKey="pr-manager-approval">
  <documentation>Department manager must approve the purchase requisition</documentation>
</userTask>
```

**Key Attributes**:
- `id`: Unique identifier for the task
- `name`: Display name shown to users
- `flowable:candidateGroups`: Role(s) that can claim this task
- `flowable:formKey`: Form definition key for task form

### Approval Task with Listeners

```xml
<userTask id="cfoApproval" name="CFO Approval"
          flowable:candidateGroups="CFO"
          flowable:formKey="capex-cfo-approval">
  <extensionElements>
    <!-- Triggered when task is created -->
    <flowable:taskListener event="create"
                           delegateExpression="${approvalTaskListener}" />

    <!-- Triggered when task is assigned -->
    <flowable:taskListener event="assignment"
                           delegateExpression="${taskAssignmentListener}" />

    <!-- Triggered when task is completed -->
    <flowable:taskListener event="complete"
                           delegateExpression="${approvalCompletionListener}" />
  </extensionElements>
</userTask>
```

**Common Task Listeners**:
- `approvalTaskListener`: Sends notification to candidate group
- `taskAssignmentListener`: Sends notification to assigned user
- `approvalCompletionListener`: Logs approval decision

### Dynamic Candidate Groups

```xml
<!-- Department-specific approval -->
<userTask id="deptHeadApproval" name="Department Head Approval"
          flowable:candidateGroups="DEPARTMENT_HEAD_${departmentId}" />

<!-- Multi-role approval (anyone from these roles can approve) -->
<userTask id="financeApproval" name="Finance Approval"
          flowable:candidateGroups="FINANCE_MANAGER,FINANCE_VP,CFO" />

<!-- Assignment based on expression -->
<userTask id="dynamicApproval" name="Dynamic Approval"
          flowable:assignee="${approverService.getApprover(requestAmount)}" />
```

---

## 2. Service Tasks (System Actions)

### Budget Validation

```xml
<serviceTask id="budgetCheck" name="Check Budget Availability"
             flowable:delegateExpression="${restServiceDelegate}">
  <documentation>Verify budget availability via Finance Service REST API</documentation>
  <extensionElements>
    <flowable:field name="url">
      <flowable:expression>${financeServiceUrl}/budget/check</flowable:expression>
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
    <flowable:field name="timeoutSeconds">
      <flowable:string>15</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

### Notification Service

```xml
<serviceTask id="notifyApproval" name="Notify Approval"
             flowable:delegateExpression="${notificationDelegate}">
  <extensionElements>
    <flowable:field name="notificationType">
      <flowable:string><![CDATA[APPROVAL_GRANTED]]></flowable:string>
    </flowable:field>
    <flowable:field name="recipientVariable">
      <flowable:string>requesterId</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

### Data Update Service

```xml
<serviceTask id="updateStatus" name="Update Request Status"
             flowable:delegateExpression="${dataUpdateDelegate}">
  <extensionElements>
    <flowable:field name="entity">
      <flowable:string>PurchaseRequisition</flowable:string>
    </flowable:field>
    <flowable:field name="entityId">
      <flowable:expression>${prId}</flowable:expression>
    </flowable:field>
    <flowable:field name="updates">
      <flowable:expression>#{{'status': 'APPROVED', 'approvalDate': now()}}</flowable:expression>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

---

## 3. Gateways (Decision Points)

### Exclusive Gateway (XOR) - Single Path Selection

#### Approval Status Gateway

```xml
<!-- Gateway Definition -->
<exclusiveGateway id="approvalDecision" name="Approved?" />

<!-- Outgoing Sequence Flows -->
<sequenceFlow id="approvedFlow"
              sourceRef="approvalDecision"
              targetRef="processApproved">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${approvalStatus == 'APPROVED'}]]>
  </conditionExpression>
</sequenceFlow>

<sequenceFlow id="rejectedFlow"
              sourceRef="approvalDecision"
              targetRef="processRejected">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${approvalStatus == 'REJECTED'}]]>
  </conditionExpression>
</sequenceFlow>

<!-- Default flow (if no condition matches) -->
<sequenceFlow id="defaultFlow"
              sourceRef="approvalDecision"
              targetRef="manualReview" />
```

#### Amount Threshold Gateway

```xml
<exclusiveGateway id="amountDecision" name="Check Amount" />

<sequenceFlow id="lowAmount"
              sourceRef="amountDecision"
              targetRef="managerApproval">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${requestAmount < 50000}]]>
  </conditionExpression>
</sequenceFlow>

<sequenceFlow id="mediumAmount"
              sourceRef="amountDecision"
              targetRef="vpApproval">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${requestAmount >= 50000 && requestAmount < 100000}]]>
  </conditionExpression>
</sequenceFlow>

<sequenceFlow id="highAmount"
              sourceRef="amountDecision"
              targetRef="cfoApproval">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${requestAmount >= 100000}]]>
  </conditionExpression>
</sequenceFlow>
```

#### Budget Availability Gateway

```xml
<exclusiveGateway id="budgetDecision" name="Budget Available?" />

<sequenceFlow id="budgetAvailable"
              sourceRef="budgetDecision"
              targetRef="managerApproval">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${budgetCheckResponse.available == true}]]>
  </conditionExpression>
</sequenceFlow>

<sequenceFlow id="budgetNotAvailable"
              sourceRef="budgetDecision"
              targetRef="notifyBudgetShortfall">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${budgetCheckResponse.available == false}]]>
  </conditionExpression>
</sequenceFlow>
```

### Parallel Gateway (AND) - Multiple Paths Simultaneously

#### Fork (Split)

```xml
<!-- Start parallel execution -->
<parallelGateway id="parallelApprovalStart" name="Parallel Approvals Start" />

<!-- All outgoing flows execute simultaneously -->
<sequenceFlow id="toCFO" sourceRef="parallelApprovalStart" targetRef="cfoApproval" />
<sequenceFlow id="toCEO" sourceRef="parallelApprovalStart" targetRef="ceoApproval" />
<sequenceFlow id="toBoard" sourceRef="parallelApprovalStart" targetRef="boardApproval" />
```

#### Join (Merge)

```xml
<!-- Wait for all incoming flows to complete -->
<parallelGateway id="parallelApprovalEnd" name="All Approvals Complete" />

<!-- All incoming flows must complete before proceeding -->
<sequenceFlow id="fromCFO" sourceRef="cfoApproval" targetRef="parallelApprovalEnd" />
<sequenceFlow id="fromCEO" sourceRef="ceoApproval" targetRef="parallelApprovalEnd" />
<sequenceFlow id="fromBoard" sourceRef="boardApproval" targetRef="parallelApprovalEnd" />

<sequenceFlow id="continue" sourceRef="parallelApprovalEnd" targetRef="finalizeProcess" />
```

### Inclusive Gateway (OR) - Multiple Paths Conditionally

```xml
<!-- Fork: Execute paths where conditions are true -->
<inclusiveGateway id="inclusiveStart" name="Conditional Notifications" />

<sequenceFlow id="notifyRequester"
              sourceRef="inclusiveStart"
              targetRef="emailRequester">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${notifyRequester == true}]]>
  </conditionExpression>
</sequenceFlow>

<sequenceFlow id="notifyManager"
              sourceRef="inclusiveStart"
              targetRef="emailManager">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[${notifyManager == true}]]>
  </conditionExpression>
</sequenceFlow>

<!-- Join: Wait for all active paths to complete -->
<inclusiveGateway id="inclusiveEnd" name="Notifications Complete" />

<sequenceFlow id="fromRequester" sourceRef="emailRequester" targetRef="inclusiveEnd" />
<sequenceFlow id="fromManager" sourceRef="emailManager" targetRef="inclusiveEnd" />
```

---

## 4. Events

### Start Events

#### Simple Start

```xml
<startEvent id="startEvent" name="Process Started">
  <extensionElements>
    <flowable:executionListener event="start"
                                delegateExpression="${processVariableInjector}" />
  </extensionElements>
</startEvent>
```

#### Timer Start (Scheduled Process)

```xml
<startEvent id="timerStart" name="Daily Budget Check">
  <timerEventDefinition>
    <!-- Run every day at 8 AM -->
    <timeCycle>0 0 8 * * ?</timeCycle>
  </timerEventDefinition>
</startEvent>
```

#### Message Start (Triggered by External Event)

```xml
<startEvent id="messageStart" name="Approval Request Received">
  <messageEventDefinition messageRef="approvalRequestMessage" />
</startEvent>

<message id="approvalRequestMessage" name="ApprovalRequest" />
```

### End Events

#### Simple End

```xml
<endEvent id="endApproved" name="Process Approved" />
```

#### Error End

```xml
<endEvent id="endError" name="Process Failed">
  <errorEventDefinition errorRef="processError" />
</endEvent>

<error id="processError" errorCode="PROCESS_ERROR" />
```

#### Terminate End

```xml
<!-- Immediately terminates all active paths -->
<endEvent id="endTerminate" name="Process Cancelled">
  <terminateEventDefinition />
</endEvent>
```

### Intermediate Events

#### Timer Intermediate (Wait)

```xml
<!-- Wait 2 days -->
<intermediateCatchEvent id="waitTwoDays" name="Wait 2 Days">
  <timerEventDefinition>
    <timeDuration>P2D</timeDuration>
  </timerEventDefinition>
</intermediateCatchEvent>
```

#### Message Intermediate (Wait for Signal)

```xml
<intermediateCatchEvent id="waitForDocuments" name="Wait for Documents">
  <messageEventDefinition messageRef="documentsReceivedMessage" />
</intermediateCatchEvent>
```

### Boundary Events (Exception Handling)

#### Timer Boundary (Escalation)

```xml
<userTask id="managerApproval" name="Manager Approval" />

<!-- Escalate if not completed in 24 hours -->
<boundaryEvent id="escalationTimer"
               attachedToRef="managerApproval"
               cancelActivity="false">
  <timerEventDefinition>
    <timeDuration>PT24H</timeDuration>
  </timerEventDefinition>
</boundaryEvent>

<sequenceFlow id="escalate"
              sourceRef="escalationTimer"
              targetRef="notifyEscalation" />
```

#### Error Boundary (Exception Handling)

```xml
<serviceTask id="budgetCheck" name="Check Budget" />

<!-- Handle budget check errors -->
<boundaryEvent id="budgetError"
               attachedToRef="budgetCheck"
               cancelActivity="true">
  <errorEventDefinition errorRef="budgetCheckError" />
</boundaryEvent>

<sequenceFlow id="handleError"
              sourceRef="budgetError"
              targetRef="logError" />
```

---

## 5. Process Variables

### Variable Scopes

| Scope | Setting Method | Access Pattern | Use Case |
|-------|---------------|----------------|----------|
| **Process** | `execution.setVariable("key", value)` | Available to all tasks | Request ID, amounts, status |
| **Task** | `taskService.setVariableLocal(taskId, "key", value)` | Only this task | Task-specific notes |
| **Global** | `runtimeService.setVariable(executionId, "key", value)` | Across process instances | Configuration values |

### Setting Variables in BPMN

#### From Form Submission

```xml
<userTask id="submitRequest" name="Submit Request"
          flowable:formKey="request-form">
  <!-- Form fields automatically become process variables -->
  <!-- Fields: requestAmount, departmentId, projectDescription -->
</userTask>
```

#### From Service Task Response

```xml
<serviceTask id="budgetCheck" name="Check Budget"
             flowable:delegateExpression="${restServiceDelegate}">
  <extensionElements>
    <flowable:field name="responseVariable">
      <!-- Service response stored in this variable -->
      <flowable:string>budgetCheckResponse</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>

<!-- Later use in gateway -->
<exclusiveGateway id="budgetDecision" />
<sequenceFlow sourceRef="budgetDecision" targetRef="approved">
  <conditionExpression>
    ${budgetCheckResponse.available == true}
  </conditionExpression>
</sequenceFlow>
```

#### From Execution Listener

```xml
<startEvent id="start" name="Start">
  <extensionElements>
    <flowable:executionListener event="start"
                                delegateExpression="${processVariableInjector}">
      <!-- Listener can set variables programmatically -->
      <flowable:field name="variables">
        <flowable:expression>#{{'processType': 'APPROVAL', 'priority': 'HIGH'}}</flowable:expression>
      </flowable:field>
    </flowable:executionListener>
  </extensionElements>
</startEvent>
```

### Variable Naming Conventions

| Prefix | Type | Example | Description |
|--------|------|---------|-------------|
| `request*` | Input | `requestAmount`, `requestDate` | User-submitted data |
| `*Response` | Output | `budgetCheckResponse`, `approvalResponse` | Service responses |
| `*Status` | State | `approvalStatus`, `budgetStatus` | Process state |
| `*Id` | Identifier | `requesterId`, `departmentId`, `prId` | Entity references |
| `is*` | Boolean | `isBudgetAvailable`, `isUrgent` | Boolean flags |
| `selected*` | Choice | `selectedVendorId`, `selectedApprover` | User selections |

---

## 6. Common Patterns

### Pattern 1: Single Approver

```
[Start] → [Submit Request] → [Manager Approval] → [Approved?] → [End]
                                                         ↓
                                                    [Rejected] → [End]
```

```xml
<startEvent id="start" />
<sequenceFlow sourceRef="start" targetRef="submitRequest" />

<userTask id="submitRequest" name="Submit Request" formKey="request-form" />
<sequenceFlow sourceRef="submitRequest" targetRef="managerApproval" />

<userTask id="managerApproval" name="Manager Approval"
          candidateGroups="MANAGER" formKey="approval-form" />
<sequenceFlow sourceRef="managerApproval" targetRef="approvalDecision" />

<exclusiveGateway id="approvalDecision" />

<sequenceFlow sourceRef="approvalDecision" targetRef="endApproved">
  <conditionExpression>${approvalStatus == 'APPROVED'}</conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="approvalDecision" targetRef="endRejected">
  <conditionExpression>${approvalStatus == 'REJECTED'}</conditionExpression>
</sequenceFlow>

<endEvent id="endApproved" name="Approved" />
<endEvent id="endRejected" name="Rejected" />
```

### Pattern 2: Multi-Tier Sequential Approval

```
[Start] → [Submit] → [Budget Check] → [Budget OK?] → [Manager] → [VP] → [CFO] → [End]
                                            ↓
                                      [Rejected] → [End]
```

```xml
<startEvent id="start" />
<sequenceFlow sourceRef="start" targetRef="submitRequest" />

<userTask id="submitRequest" name="Submit Request" formKey="request-form" />
<sequenceFlow sourceRef="submitRequest" targetRef="budgetCheck" />

<serviceTask id="budgetCheck" name="Check Budget"
             delegateExpression="${budgetCheckDelegate}" />
<sequenceFlow sourceRef="budgetCheck" targetRef="budgetDecision" />

<exclusiveGateway id="budgetDecision" name="Budget OK?" />

<sequenceFlow sourceRef="budgetDecision" targetRef="managerApproval">
  <conditionExpression>${budgetCheckResponse.available == true}</conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="budgetDecision" targetRef="endRejected">
  <conditionExpression>${budgetCheckResponse.available == false}</conditionExpression>
</sequenceFlow>

<userTask id="managerApproval" name="Manager Approval"
          candidateGroups="MANAGER" />
<sequenceFlow sourceRef="managerApproval" targetRef="vpApproval" />

<userTask id="vpApproval" name="VP Approval"
          candidateGroups="VP" />
<sequenceFlow sourceRef="vpApproval" targetRef="cfoApproval" />

<userTask id="cfoApproval" name="CFO Approval"
          candidateGroups="CFO" />
<sequenceFlow sourceRef="cfoApproval" targetRef="endApproved" />

<endEvent id="endApproved" name="Approved" />
<endEvent id="endRejected" name="Rejected" />
```

### Pattern 3: Amount-Based Routing

```
[Start] → [Submit] → [Amount Check] → [< 50K] → [Manager] → [End]
                           ↓
                      [50K-100K] → [VP] → [End]
                           ↓
                      [> 100K] → [CFO] → [End]
```

```xml
<startEvent id="start" />
<sequenceFlow sourceRef="start" targetRef="submitRequest" />

<userTask id="submitRequest" name="Submit Request" formKey="request-form" />
<sequenceFlow sourceRef="submitRequest" targetRef="amountCheck" />

<exclusiveGateway id="amountCheck" name="Check Amount" />

<sequenceFlow sourceRef="amountCheck" targetRef="managerApproval">
  <conditionExpression>${requestAmount < 50000}</conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="amountCheck" targetRef="vpApproval">
  <conditionExpression>
    ${requestAmount >= 50000 && requestAmount < 100000}
  </conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="amountCheck" targetRef="cfoApproval">
  <conditionExpression>${requestAmount >= 100000}</conditionExpression>
</sequenceFlow>

<userTask id="managerApproval" name="Manager Approval"
          candidateGroups="MANAGER" />
<sequenceFlow sourceRef="managerApproval" targetRef="endApproved" />

<userTask id="vpApproval" name="VP Approval"
          candidateGroups="VP" />
<sequenceFlow sourceRef="vpApproval" targetRef="endApproved" />

<userTask id="cfoApproval" name="CFO Approval"
          candidateGroups="CFO" />
<sequenceFlow sourceRef="cfoApproval" targetRef="endApproved" />

<endEvent id="endApproved" name="Approved" />
```

### Pattern 4: Parallel Approvals

```
[Start] → [Submit] → [Parallel Start] → [CFO] → [Parallel End] → [End]
                           ↓
                         [CEO]
                           ↓
                        [Board]
```

```xml
<startEvent id="start" />
<sequenceFlow sourceRef="start" targetRef="submitRequest" />

<userTask id="submitRequest" name="Submit Request" formKey="request-form" />
<sequenceFlow sourceRef="submitRequest" targetRef="parallelStart" />

<parallelGateway id="parallelStart" name="Parallel Approvals Start" />

<sequenceFlow sourceRef="parallelStart" targetRef="cfoApproval" />
<sequenceFlow sourceRef="parallelStart" targetRef="ceoApproval" />
<sequenceFlow sourceRef="parallelStart" targetRef="boardApproval" />

<userTask id="cfoApproval" name="CFO Approval" candidateGroups="CFO" />
<sequenceFlow sourceRef="cfoApproval" targetRef="parallelEnd" />

<userTask id="ceoApproval" name="CEO Approval" candidateGroups="CEO" />
<sequenceFlow sourceRef="ceoApproval" targetRef="parallelEnd" />

<userTask id="boardApproval" name="Board Approval" candidateGroups="BOARD_EXECUTIVE" />
<sequenceFlow sourceRef="boardApproval" targetRef="parallelEnd" />

<parallelGateway id="parallelEnd" name="All Approvals Complete" />
<sequenceFlow sourceRef="parallelEnd" targetRef="endApproved" />

<endEvent id="endApproved" name="Approved" />
```

### Pattern 5: Approval with Escalation

```
[Start] → [Submit] → [Manager Approval] → [Approved?] → [End]
                           ↓ (24h timer)
                      [Escalate to VP]
```

```xml
<startEvent id="start" />
<sequenceFlow sourceRef="start" targetRef="submitRequest" />

<userTask id="submitRequest" name="Submit Request" formKey="request-form" />
<sequenceFlow sourceRef="submitRequest" targetRef="managerApproval" />

<userTask id="managerApproval" name="Manager Approval"
          candidateGroups="MANAGER" />

<!-- Escalation timer (non-interrupting) -->
<boundaryEvent id="escalationTimer"
               attachedToRef="managerApproval"
               cancelActivity="false">
  <timerEventDefinition>
    <timeDuration>PT24H</timeDuration>
  </timerEventDefinition>
</boundaryEvent>

<sequenceFlow sourceRef="escalationTimer" targetRef="notifyVP" />

<serviceTask id="notifyVP" name="Notify VP of Delay"
             delegateExpression="${notificationDelegate}" />

<sequenceFlow sourceRef="managerApproval" targetRef="approvalDecision" />

<exclusiveGateway id="approvalDecision" />

<sequenceFlow sourceRef="approvalDecision" targetRef="endApproved">
  <conditionExpression>${approvalStatus == 'APPROVED'}</conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="approvalDecision" targetRef="endRejected">
  <conditionExpression>${approvalStatus == 'REJECTED'}</conditionExpression>
</sequenceFlow>

<endEvent id="endApproved" name="Approved" />
<endEvent id="endRejected" name="Rejected" />
```

---

## 7. Expression Language Reference

### Common Expressions

| Expression | Description | Example |
|------------|-------------|---------|
| `${variable}` | Access variable | `${requestAmount}` |
| `${object.property}` | Access object property | `${budgetCheckResponse.available}` |
| `${variable == value}` | Equality check | `${approvalStatus == 'APPROVED'}` |
| `${variable < value}` | Less than | `${requestAmount < 50000}` |
| `${variable >= value}` | Greater or equal | `${requestAmount >= 100000}` |
| `${var1 && var2}` | Logical AND | `${isBudgetOk && isManagerApproved}` |
| `${var1 \|\| var2}` | Logical OR | `${isUrgent \|\| isHighPriority}` |
| `${!variable}` | Logical NOT | `${!isCancelled}` |
| `${condition ? true : false}` | Ternary | `${amount > 100000 ? 'HIGH' : 'LOW'}` |

### Function Calls

```xml
<!-- Call Spring bean method -->
${approverService.getApprover(requestAmount)}

<!-- Call with multiple parameters -->
${budgetService.checkAvailability(departmentId, amount, fiscalYear)}

<!-- String operations -->
${requestDescription.toUpperCase()}
${requestDescription.substring(0, 100)}

<!-- Date operations -->
${now()}
${dateService.addDays(currentDate, 7)}
```

---

## 8. Candidate Groups Reference

### Werkflow Standard Roles

| Role | Candidate Group | Department-Specific | Threshold |
|------|----------------|---------------------|-----------|
| **Department Head** | `DEPARTMENT_HEAD_{departmentId}` | Yes | $0 - $50K |
| **Finance Manager** | `FINANCE_MANAGER` | No | $50K - $100K |
| **Finance VP** | `FINANCE_VP` | No | $100K - $500K |
| **CFO** | `CFO` | No | $500K - $1M |
| **CEO** | `CEO` | No | $1M - $5M |
| **Board Executive** | `BOARD_EXECUTIVE` | No | $5M+ |
| **Procurement Specialist** | `PROCUREMENT_SPECIALIST` | No | N/A |
| **HR Manager** | `HR_MANAGER_{departmentId}` | Yes | N/A |

### Usage Examples

```xml
<!-- Static role -->
<userTask candidateGroups="CFO" />

<!-- Dynamic department-based role -->
<userTask candidateGroups="DEPARTMENT_HEAD_${departmentId}" />

<!-- Multiple roles (any can claim) -->
<userTask candidateGroups="FINANCE_MANAGER,FINANCE_VP,CFO" />

<!-- Assigned to specific user -->
<userTask assignee="${requesterId}" />

<!-- Assigned based on expression -->
<userTask assignee="${approverService.getApprover(requestAmount)}" />
```

---

## Quick Checklist for Creating Approval Workflows

### Design Phase
- [ ] Identify all approval levels and thresholds
- [ ] Define decision points (budget, amount, status)
- [ ] List all system integrations (budget check, notifications)
- [ ] Define process variables (request data, responses)
- [ ] Map roles to candidate groups

### Implementation Phase
- [ ] Add start event with variable injector
- [ ] Add user tasks with correct `candidateGroups`
- [ ] Add service tasks for system integrations
- [ ] Add gateways for decision logic
- [ ] Add sequence flow conditions
- [ ] Add task/execution listeners for notifications
- [ ] Add boundary events for escalations/errors
- [ ] Add end events for all terminal states

### Validation Phase
- [ ] All candidate groups exist in system
- [ ] All variables are defined before use
- [ ] All gateway conditions cover all cases
- [ ] All sequence flows have targets
- [ ] All user tasks have forms defined
- [ ] All service tasks have delegate expressions
- [ ] All error paths handled
- [ ] All notification points configured

### Testing Phase
- [ ] Test happy path (all approvals)
- [ ] Test rejection paths
- [ ] Test escalation timers
- [ ] Test parallel approvals
- [ ] Test error handling
- [ ] Test variable propagation
- [ ] Verify notifications sent

---

This quick reference guide covers the most common BPMN patterns used in Werkflow approval workflows. For more advanced patterns, refer to the Flowable documentation or the BPMN Designer Enhancement Strategy document.
