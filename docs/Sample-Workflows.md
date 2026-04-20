# Sample Workflows

Werkflow ships with three sample workflows that demonstrate core BPMN patterns. They deploy automatically on startup when `WERKFLOW_DEPLOY_EXAMPLES=true`.

---

## General Approval

**Process key:** `general-approval`
**Form:** `general-approval-form`

### Purpose

A general-purpose approval workflow suitable for budget requests, operational decisions, and ad-hoc approvals. Demonstrates DOA (Delegation of Authority) routing based on monetary amount.

### Actors

| Actor | Keycloak Role | Flowable Group | Approves amounts |
|---|---|---|---|
| Requester | any | — | Submits the request |
| Manager | `doa_approver_level2` | `DOA_L2` | Up to $50K |
| Director | `doa_approver_level3` | `DOA_L3` | Over $50K |

### Flow

```
Submit Request
  Manager Approval (all requests)
  Amount > $50K?
    Yes → Director Approval → Notify Outcome
    No  → Notify Outcome
```

### Modeling Lessons

- **Exclusive gateway after approval task** — the manager decision (`approve`/`reject`) is captured as a process variable named `decision`; the gateway reads this to route to the Director path or the notification.
- **DOA candidateGroups** — `DOA_L2` and `DOA_L3` map to Keycloak roles via `app.flowable.role-mappings` in `application.yml`. Users with `doa_approver_level2` automatically inherit both `DOA_L1` and `DOA_L2` groups.
- **Amount gateway** — after Manager approval, a second exclusive gateway checks `${amount > 50000}`. The Director approval task uses `DOA_L3,DOA_L4,SUPER_ADMIN` so both directors and admins can see it.
- **Email delegate** — `emailActionDelegate` sends emails using notification templates (`request-approved`, `request-rejected`). The recipient is set to `${initiator}` — the Keycloak username of the person who started the process.

---

## Document Review

**Process key:** `document-review`
**Form:** `document-review-form`

### Purpose

A document review workflow with a revision loop. Useful for policy approvals, contract reviews, and specification sign-offs. Demonstrates the revise-and-resubmit cycle.

### Actors

| Actor | Role | Notes |
|---|---|---|
| Requester | any | Submits document and handles revisions |
| Reviewer | selected in start form | Any user in the chosen group |

### Flow

```
Submit Document (requester selects reviewer group)
  Review Document (reviewer)
  Decision?
    Approve → Notify Approved → End
    Reject  → Notify Rejected → End
    Revise  → Notify Revision → Revise Document (requester) → Review Document (loop)
```

### Modeling Lessons

- **Dynamic candidateGroups** — the reviewer group is set by the requester in the start form (`reviewerGroup` field). The Review Document user task uses `candidateGroups="${reviewerGroup},SUPER_ADMIN"` — Flowable resolves the EL expression at runtime.
- **Three-way gateway** — the review decision has three outcomes (approve/reject/revise), each routing to a different path. This is modeled as a single exclusive gateway with three outgoing conditional flows.
- **Loop pattern** — the "revise" path leads back to the Review Document task. The requester fills in the start form again (with their changes) before the reviewer sees a new task. There is no explicit counter; the loop continues until the reviewer approves or rejects.
- **Assignee on revision** — the Revise Document task uses `flowable:assignee="${initiator}"` so it goes directly to the original requester's task list rather than a candidate group.

---

## Onboarding Checklist

**Process key:** `onboarding-checklist`
**Form:** `onboarding-checklist-form`

### Purpose

An employee onboarding workflow where IT and Facilities work happens in parallel. Demonstrates parallel gateway usage and shows how independent tasks can proceed simultaneously.

### Actors

| Actor | Task |
|---|---|
| HR | Submits new hire details |
| IT team | Provisions equipment and system access |
| Facilities team | Prepares desk, access card, workspace |
| Manager | Assigns a buddy |

All tasks use `SUPER_ADMIN` as the candidate group in the sample configuration so that demo users can complete every step.

### Flow

```
HR: Submit Onboarding
  Parallel Fork
    IT: System Setup  ─┐
    Facilities Setup  ─┤  (both must complete)
  Parallel Join ────────┘
  Assign Manager Buddy
  Send Welcome Email
  End
```

### Modeling Lessons

- **Parallel gateway** — unlike an exclusive gateway, a parallel fork creates all outgoing tokens simultaneously. Both IT Setup and Facilities Setup tasks appear in the task list at the same time. The parallel join waits for all incoming tokens before continuing.
- **No conditions on parallel flows** — the sequence flows from the fork gateway have no condition expressions. Flowable activates all outgoing flows unconditionally.
- **Email to new hire** — the welcome email uses `${employeeEmail}` (a form field from the start form) as the recipient rather than `${initiator}`. This shows how any process variable can be used as a recipient.
- **Buddy assignment gate** — in a real workflow you would add a gateway before `assignBuddy` gated on `${buddyRequired == true}` (a checkbox in the start form). The sample omits this for clarity but the `buddyRequired` variable is available in the process context.

---

## Adding a Custom Workflow

1. In the Portal, navigate to **Processes** and click **New Process**.
2. Use the BPMN Designer to drag tasks, gateways, and events from the left palette.
3. Click a shape to open the Properties Panel and configure form keys, candidate groups, and delegate expressions.
4. Click **Save** to store as a draft, then **Deploy** to make the process available to users.
5. Register any external APIs needed in **Admin > Connectors** before wiring service tasks.

See [CONNECTOR-GUIDE.md](CONNECTOR-GUIDE.md) for wiring external APIs into service tasks.
