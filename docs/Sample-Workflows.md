# Sample Workflows

Werkflow ships with four sample workflows that demonstrate core BPMN patterns. They deploy automatically on startup when `WERKFLOW_DEPLOY_EXAMPLES=true`.

---

## Leave Request

**Process key:** `leave-request`
**Forms:** `leave-request-form`, `leave-approval-form`

### Purpose

An employee leave request workflow where a DMN decision table determines whether the request requires human approval and, if so, which group must approve it. Short casual leave bypasses the approval task entirely; longer or special-category leave is routed to either a line manager or HR.

### Actors

| Actor | Flowable Group | Responsibility |
|---|---|---|
| Employee | any | Submits the leave request |
| Line Manager | `LINE_MANAGER`, `SUPER_ADMIN` | Reviews requests of 4-10 days for non-special leave types |
| HR Manager | `HR_MANAGER`, `SUPER_ADMIN` | Reviews sick, parental, bereavement, or any leave exceeding 10 days |

The approval task uses `candidateGroups="${approverRole},SUPER_ADMIN"`. The DMN writes `approverRole` as either `LINE_MANAGER` or `HR_MANAGER` before the task is created.

### Flow

```
Submit Leave Request (leave-request-form)
  Resolve Leave Policy (DMN: leave_approval)
  Approval Required?
    No  → [end: Auto Approved]
    Yes → Manager Review (${approverRole}, SUPER_ADMIN, leave-approval-form)
            Decision?
              approve → [end: Leave Approved]
              reject  → [end: Leave Rejected]
```

### Modeling Lessons

- **DMN-driven routing** — the `leave_approval` DMN (FIRST hit policy) evaluates `leaveType` and `leaveDays` and writes two output variables: `approvalRequired` (boolean) and `approverRole` (string). The BPMN has no hard-coded group names. Changing who approves a leave category requires only a DMN rule change, not a BPMN edit.
- **Computed form field** — `leaveDays` is derived from `startDate` and `endDate` using a FEEL expression field inside the start form. The value is written into the process context automatically so the DMN can evaluate duration without the submitter entering it manually.
- **FIRST hit policy rule ordering** — sick, parental, and bereavement leave are checked first (regardless of duration) and always route to `HR_MANAGER`. Then long leave (>10 days, any type) is checked, then medium duration (>=4 days), then short type-specific auto-approve cases. Rule order is deliberate: a 2-day sick leave matches rule 1, not rule 7.
- **Auto-approve path** — when `approvalRequired == false` the exclusive gateway routes directly to the "Auto Approved" end event. No task is created. This is the path taken by annual or personal leave of 1-3 days.

---

## IT Helpdesk Ticket

**Process key:** `it-helpdesk-ticket`
**Forms:** `it-helpdesk-ticket-form`, `it-helpdesk-resolution-form`

### Purpose

A simple linear helpdesk workflow. A requester submits a ticket; an acknowledgement email is sent automatically; IT Support resolves the ticket; a resolution email is sent. There are no decision gateways. The process demonstrates `bpmn:sendTask` wired to `notificationDelegate` for automatic email dispatch at two points in the flow.

### Actors

| Actor | Flowable Group | Responsibility |
|---|---|---|
| Requester | any | Submits the ticket via the start form |
| IT Support Agent | `IT_SUPPORT`, `SUPER_ADMIN` | Claims and resolves the ticket |

### Flow

```
Submit Ticket (it-helpdesk-ticket-form)
  Acknowledge Ticket (sendTask → email to ${requesterEmail}, template: ticket-acknowledged)
  Resolve Ticket (IT_SUPPORT, SUPER_ADMIN, it-helpdesk-resolution-form)
  Notify Resolution (sendTask → email to ${requesterEmail}, template: ticket-resolved)
  [end: Ticket Closed]
```

### Modeling Lessons

- **`bpmn:sendTask` wired to `notificationDelegate`** — both notification steps are modeled as BPMN send tasks using `flowable:delegateExpression="${notificationDelegate}"`. The channel (`email`), recipient expression (`${requesterEmail}`), and template key are declared as Flowable extension fields on the task, making them visible in the BPMN without touching Java.
- **`requesterEmail` as a process variable** — the start form collects `requesterEmail` as a plain text field. Both send tasks reference `${requesterEmail}` as the recipient. This allows notifications to reach the original submitter without requiring a Keycloak user account.
- **No gateways** — the flow is intentionally linear. There is no decision point; the ticket is always resolved by `IT_SUPPORT`. Add an exclusive gateway after `Resolve Ticket` if you need escalation paths or re-assignment on a "Won't Fix" status.
- **Two distinct forms** — the start form (`it-helpdesk-ticket-form`) captures the problem (subject, description, category, priority, email). The resolution form (`it-helpdesk-resolution-form`) captures resolution notes and a status (Resolved / Won't Fix / Duplicate). Keeping submitter and agent concerns in separate forms reduces the number of fields shown to each actor.

---

## Capital Expenditure Approval

**Process key:** `capex-approval-process`
**Forms:** `capex-request-form`, `capex-approval-form`

### Purpose

A multi-tier approval workflow for capital expenditure requests. Budget availability is checked via an external API before any human task is created. A DMN service task before each approval tier dynamically resolves the approving group, enabling department-specific DOA rules without hard-coding group names in user tasks. The CFO is the highest tier; there is no escalate route from CFO level.

### Actors

| Actor | Flowable Group | Tier | Threshold |
|---|---|---|---|
| Employee | any | — | Submits the request |
| Manager | `DOA_L2`, `SUPER_ADMIN` | Manager | All requests that pass budget check |
| VP | `DOA_L3`, `SUPER_ADMIN` | VP | Requests above $50,000 or escalated by Manager |
| CFO | `DOA_L4`, `SUPER_ADMIN` | CFO | Requests above $250,000 or escalated by VP |

All three user tasks use `candidateGroups="${approverGroup},SUPER_ADMIN"`. The `approverGroup` variable is written by the DMN service task immediately before each user task.

### Flow

```
Submit CapEx Request (capex-request-form)
  Create CapEx Request (external API, connector: capex-service)
  Check Budget Availability (external API, connector: capex-service)
  Budget Available?
    No  → [end: Insufficient Budget]
    Yes → Resolve Manager Group (DMN: capex_manager_group → ${approverGroup})
            Manager Review (${approverGroup}, SUPER_ADMIN, capex-approval-form)
            Manager Decision:
              reject   → [rejected path] → [end: Request Rejected]
              approve  → Amount > $50,000?
                           No  → [approved path] → [end: Request Approved]
                           Yes → VP level
              escalate → VP level (bypasses amount threshold)

          VP level:
          Resolve VP Group (DMN: capex_vp_group → ${approverGroup})
          VP Review (${approverGroup}, SUPER_ADMIN, capex-approval-form)
          VP Decision:
            reject   → [rejected path] → [end: Request Rejected]
            approve  → Amount > $250,000?
                         No  → [approved path] → [end: Request Approved]
                         Yes → CFO level
            escalate → CFO level (bypasses amount threshold)

          CFO level:
          Resolve CFO Group (DMN: capex_cfo_group → ${approverGroup})
          CFO Review (${approverGroup}, SUPER_ADMIN, capex-approval-form)
          CFO Decision:
            approve → [approved path] → [end: Request Approved]
            reject  → [rejected path] → [end: Request Rejected]
```

### Modeling Lessons

- **DMN-resolved `candidateGroups`** — `capex-approver-resolution.dmn` holds three separate decision tables (`capex_manager_group`, `capex_vp_group`, `capex_cfo_group`), one per tier. Each takes `capexOwner` (department code) as input and writes `approverGroup`. The same output variable name is reused safely because each DMN runs at a different point in the flow. Adding a new department requires only new DMN rules, not a BPMN change.
- **Early-exit on reject** — any tier can reject and the process ends immediately at that level without advancing to the next tier. This is modeled as a separate gateway branch at each level, not a single final gateway, which eliminates the last-write-wins defect present in earlier versions.
- **Escalate vs. approve with amount threshold** — the approval form offers three options: approve, reject, escalate. Approve passes through an amount gateway that may still forward upward if the amount exceeds the tier's threshold. Escalate routes directly to the next tier, bypassing the amount check. CFO has no escalate option.
- **External API pre-checks with structured error handling** — `checkBudget` uses `onError="THROW_BPMN_ERROR"`, which raises a BPMN error if the connector call fails. `createCapExRequest` uses `onError="FAIL"`, halting the instance. The outcome-update service tasks use `onError="CONTINUE"` so a downstream API failure does not block the approved or rejected end states.

---

## Procurement Approval

**Process key:** `procurement-approval-process`
**Forms:** `procurement-request-form`, `vendor-selection`, `quotation-review`, `procurement-approval`

### Purpose

A purchase request approval workflow governed by a procurement matrix DMN. Small purchases are auto-approved as direct purchases. Larger requests proceed through a sequential vendor-selection and quotation-review workflow before manager sign-off. The DMN also calculates whether a procurement committee is required, recording that in a process variable for downstream use.

### Actors

| Actor | Flowable Group | Responsibility |
|---|---|---|
| Requester | any | Submits the purchase request |
| Procurement Officer | `DOA_L1`, `SUPER_ADMIN` | Selects vendor and reviews quotations |
| Manager | `DOA_L2`, `SUPER_ADMIN` | Final approval decision |

### Flow

```
Submit Purchase Request (procurement-request-form)
  Evaluate Procurement Matrix (DMN: procurement_matrix → procurementPath, requiresCommittee)
  Procurement Path?
    DIRECT_PURCHASE → [end: PO Created]
    all other paths → Select Vendor (DOA_L1, SUPER_ADMIN, vendor-selection)
                       Review Quotations (DOA_L1, SUPER_ADMIN, quotation-review)
                       Manager Approval (DOA_L2, SUPER_ADMIN, procurement-approval)
                       Approval Decision?
                         approve   → [end: PO Created]
                         (default) → [end: Request Rejected]
```

### Modeling Lessons

- **DMN path selection** — `procurement_matrix.dmn` (FIRST hit policy) evaluates `amount` and `category` and outputs `procurementPath` (DIRECT_PURCHASE / MANAGER_APPROVAL / COMMITTEE_REVIEW / BOARD_APPROVAL) and `requiresCommittee` (boolean). The BPMN currently branches only on `DIRECT_PURCHASE`; the other path values and `requiresCommittee` are written as process variables and are available for gateway extensions without modifying the DMN.
- **Default gateway branch** — `directPurchaseGateway` uses `flowable:default="flowToVendorDefault"`. Any `procurementPath` that is not `DIRECT_PURCHASE` — including null or an unexpected value — falls through to the vendor workflow. This prevents the instance from dead-locking if the DMN produces an unrecognised path.
- **Multi-step sequential workflow** — the non-direct path passes through three consecutive user tasks, each with a distinct form and a distinct actor group: vendor selection and quotation review belong to `DOA_L1`; the approval decision belongs to `DOA_L2`. This illustrates how to coordinate multiple actors sequentially without parallel gateways.
- **Default reject on the approval gateway** — `approvalDecision` uses `flowable:default="flowRejectDefault"`. The approve branch has an explicit condition (`${decision == 'approve'}`); any other value defaults to rejection. An incomplete task submission cannot accidentally create a PO.

---

## Adding a Custom Workflow

1. In the Portal, navigate to **Processes** and click **New Process**.
2. Use the BPMN Designer to drag tasks, gateways, and events from the left palette.
3. Click a shape to open the Properties Panel and configure form keys, candidate groups, and delegate expressions.
4. Click **Save** to store as a draft, then **Deploy** to make the process available to users.
5. Register any external APIs needed in **Admin > Connectors** before wiring service tasks.

See [CONNECTOR-GUIDE.md](CONNECTOR-GUIDE.md) for wiring external APIs into service tasks.
