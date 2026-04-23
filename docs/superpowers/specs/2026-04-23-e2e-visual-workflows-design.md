# E2E Visual Workflow Test Plan — Enterprise Edition

**Date:** 2026-04-23
**Target:** werkflow-enterprise (portal:4000)
**Gate:** All tests must pass before OSS v1.0.0 is published
**Author:** Design session — approved by user

---

## Objective

Add comprehensive end-to-end tests for the werkflow-enterprise portal that:

1. Verify role-based navigation and access control
2. Verify core daily-use flows (tasks, requests, approvals)
3. Test the full visual creation pipeline — connector → DMN → form → BPMN — entirely through the UI, no backend generation
4. Run 4 real-world workflow journeys end-to-end with multi-user session switching
5. Catch and surface UI bugs in bpmn-js, dmn-js, form-js editors and their properties panels

These tests are the quality gate for the v1.0.0 community release. Only after all pass is the OSS push and GitHub Release permitted.

---

## Test Environment

| Service | Port | Notes |
|---------|------|-------|
| Portal | 4000 | Next.js — enterprise edition |
| Engine | 8081 | Flowable engine service |
| Admin | 8083 | Admin service |
| Keycloak | 8090 | Auth — realm: werkflow |
| Mailpit | 8025 | SMTP sandbox, REST API for assertions |
| PostgreSQL | 5433 | Via docker compose |

**Mock external API:** `https://httpbin.org/post` — used for EXTERNAL_API_CALL tasks. No local mock server required.

**Stack start:** `docker compose -f infrastructure/docker/docker-compose.yml up`

---

## Test Users

| Role | Username | Password | Groups | Capabilities |
|------|----------|----------|--------|-------------|
| admin | admin | REDACTED_PASSWORD | none (super_admin role) | All pages, all admin functions |
| manager | john.manager | TempPassword123! | HR Department/Managers | Tasks, requests, approval tasks, start processes |
| employee | jane.employee | TempPassword123! | HR Department/Specialists | Tasks, requests, start processes |
| it-user | mike.it | TempPassword123! | IT Department/Managers | IT approval tasks (used in C2 equipment workflow) |

Sessions are pre-authenticated via Playwright setup files. A new `it-user.setup.ts` is required for `mike.it`. No re-login in tests.

---

## Architecture: Layered Suite

Tests run in numbered order. Each layer produces artifacts consumed by the next.

```
Layer 0 — Auth Setup         (existing setup files, unchanged)
Layer 1a — RBAC Navigation   (20-rbac-navigation.spec.ts)
Layer 1b — Core User Flows   (21-core-user-flows.spec.ts)
Layer 2a — Connector Setup   (22-connector-setup.spec.ts)
Layer 2b — DMN Decisions     (23-dmn-decisions.spec.ts)
Layer 2c — Forms Visual      (24-forms-visual.spec.ts)
Layer 3  — Workflow Journeys (25–28-workflow-*.spec.ts)
```

Cleanup: every spec has `afterAll` hooks that delete created artifacts via API to leave the environment clean for re-runs.

---

## Layer 1a — RBAC Navigation (`20-rbac-navigation.spec.ts`)

### Admin

- Can access: `/processes`, `/processes/new`, `/decisions`, `/decisions/new`, `/forms`, `/forms/new`, `/services`, `/admin/connectors`, `/admin/doa`, `/admin/departments`
- Sidebar shows: all nav items including admin section
- "Create New Process" button visible on `/processes`

### Manager

- Can access: `/tasks`, `/requests`, `/processes` (view only), `/dashboard`
- Cannot access: `/admin/*` — redirected or 403
- `/processes` page: no "Create New Process" button, no Edit/Delete actions
- Sidebar: no admin section items

### Employee

- Can access: `/tasks`, `/requests`, `/dashboard`
- Cannot access: `/admin/*`, no process creation
- `/processes`: can see list, can click "Start Process" on deployed processes
- No manager-only action buttons visible

---

## Layer 1b — Core User Flows (`21-core-user-flows.spec.ts`)

Tests use the seeded sample processes (general-approval, document-review, onboarding-checklist) that are auto-deployed on engine start.

### My Tasks (employee + manager)

- Task list loads with correct columns (name, process, due date, priority)
- Unassigned task: claim button visible, clicking claim assigns to current user
- Assigned task: open task detail, form renders, fill and submit
- Completed task disappears from open tasks list

### My Requests (employee)

- `/requests` shows all submitted requests with status badges
- Request detail shows: current status, form submission data, history timeline
- Filter by status (active / completed) works correctly
- Cancel a pending request: confirm dialog appears, request moves to cancelled state

### Approval Flow (manager)

- Task detail shows Approve and Reject buttons for human approval tasks
- Approve: task resolves, process advances, request status updates
- Reject: request marked rejected, confirmation shown
- Comment field: manager adds comment, visible in request history

### Process Monitoring (admin)

- `/monitoring` loads without error
- Running process instances listed with correct status
- Click instance → detail view shows active tasks and variables
- Completed instances visible in history

---

## Layer 2a — Connector Setup (`22-connector-setup.spec.ts`)

All actions performed as admin via `/admin/connectors`.

### Tests

1. **Create connector:** click "New Connector" → fill name = "mock-api", base URL = `https://httpbin.org`, description → save → appears in list
2. **Create second connector:** name = "notification-service", base URL = `https://httpbin.org/post` → save
3. **Edit connector:** click edit on "mock-api" → change description → save → change persists on reload
4. **Validation:** submit with empty name → validation error shown, form not submitted
5. **Delete connector:** delete "notification-service" → confirm dialog → removed from list
6. **Re-create:** re-create "notification-service" for use in BPMN tests
7. **BPMN availability:** open `/processes/new` → add Service Task → set action type = EXTERNAL_API_CALL → connector dropdown includes "mock-api" and "notification-service"

### Known Issue Areas

- Connector form save confirmation (toast vs redirect)
- List refresh after create/delete without page reload
- Connector key auto-generation from display name

---

## Layer 2b — DMN Decisions (`23-dmn-decisions.spec.ts`)

All actions performed as admin via `/decisions/new`. Each decision created entirely visually through the dmn-js editor.

### Decision 1: `ticket-routing`

| Input: ticketType (string) | Output: approvalRequired (boolean) | Output: approverLevel (string) |
|---|---|---|
| "free" | false | "none" |
| "paid" | true | "manager" |
| "vip" | true | "manager" |

**Test steps:** Open `/decisions/new` → set name = "ticket-routing" → in dmn-js editor, click input column header to set input expression = `ticketType` → click output column to set output name = `approvalRequired` → add second output `approverLevel` → add 3 rules by clicking "+" → type values in each cell → click Deploy → verify appears on `/decisions` list.

### Decision 2: `leave-routing`

| Input: leaveDays (integer) | Output: approverLevel (string) |
|---|---|
| <= 3 | "auto" |
| > 3, <= 10 | "manager" |
| > 10 | "director" |

**Test steps:** Same flow as above with numeric range expressions in input cells.

### Decision 3: `budget-routing`

| Input: amount (number) | Output: approverLevel (string) |
|---|---|
| < 500 | "auto" |
| >= 500, < 2000 | "manager" |
| >= 2000 | "director" |

### Known Issue Areas

- dmn-js cell click-to-edit activation
- Adding/removing rule rows via "+" button
- Numeric range expression syntax in input cells
- Deploy button state when editor has unsaved changes
- Decision key generation from name

---

## Layer 2c — Forms Visual (`24-forms-visual.spec.ts`)

All actions as admin via `/forms/new`. Each form built entirely through the form-js editor drag-and-drop palette.

### Form 1: `event-ticket-form`

Fields: `name` (text, required), `email` (email, required), `ticketType` (select: free/paid/vip, required), `quantity` (number, min 1, max 10), `notes` (textarea, optional)

### Form 2: `leave-request-form`

Fields: `startDate` (date, required), `endDate` (date, required), `leaveDays` (number, required, min 1 — user enters duration explicitly to keep DMN routing simple), `leaveType` (select: Annual/Sick/Personal, required), `reason` (textarea, required)

### Form 3: `equipment-request-form`

Fields: `employeeName` (text, required), `department` (select: Engineering/Finance/HR/Operations/IT, required), `items` (textarea, required, placeholder "List equipment items"), `urgency` (select: Low/Medium/High, required)

### Form 4: `budget-request-form`

Fields: `title` (text, required), `amount` (number, required, min 1), `category` (select: Software/Hardware/Training/Travel/Other, required), `justification` (textarea, required)

### Test Steps (per form)

1. Navigate to `/forms/new`
2. Add each field via palette (drag or click to add)
3. Configure field label, key, required flag, options (for selects)
4. Click Save → form appears on `/forms` list
5. Click Preview → form renders with all fields, required markers visible
6. Submit preview with empty required fields → validation errors shown

### Known Issue Areas

- form-js palette item drag-and-drop vs click-to-add
- Field configuration panel opening on field click
- Select options editor
- Save/preview round-trip fidelity

---

## Layer 3 — Workflow Journeys

### Shared 7-Step Structure

Every workflow spec follows this sequence:

| Step | Actor | Action |
|------|-------|--------|
| 1 | Admin | Build BPMN visually in `/processes/new` — add all tasks, gateways, connect flows, set properties |
| 2 | Admin | Click Deploy → redirected to `/processes`, new process visible in list |
| 3 | Requester | Navigate to `/processes` → find process by name → click "Start Process" → fill form → submit |
| 4 | Requester | Assert redirect to `/requests`, new request visible with status "Active" |
| 5 | Approver | Navigate to `/tasks` → find approval task → claim → review → approve or reject |
| 6 | System | Assert Mailpit received correct email with correct recipient and subject |
| 7 | Requester | Navigate to `/requests` → assert final status (Approved/Rejected/Completed) |

### Multi-User Session Pattern

```typescript
const requesterCtx = await browser.newContext({ storageState: STORAGE_STATES.employee })
const approverCtx  = await browser.newContext({ storageState: STORAGE_STATES.manager })
const requesterPage = await requesterCtx.newPage()
const approverPage  = await approverCtx.newPage()
// orchestrate actions across both pages in sequence
```

---

### Workflow A: Event Ticket Request (`25-workflow-event-ticket.spec.ts`)

**BPMN structure (built visually):**

```
Start Event
  → User Task "Submit Ticket Request" [form: event-ticket-form]
  → Business Rule Task "Route by Ticket Type" [DMN: ticket-routing]
  → Exclusive Gateway
      → [ticketType == 'free'] → Service Task "Log Booking" [EXTERNAL_API_CALL: mock-api /post]
                               → Email Task "Ticket Confirmed" [NOTIFICATION]
                               → End
      → [default]             → User Task "Organiser Review" [HUMAN_APPROVAL, candidate: manager]
                               → Email Task "Ticket Decision" [NOTIFICATION]
                               → End
```

**Gateway condition (ExpressionBuilder):** `${approvalRequired == false}`

**Test scenarios:**

- **Happy path (paid ticket):** employee submits (ticketType=paid) → manager claims "Organiser Review" task → approves → Mailpit receives approval email to employee email address → employee sees status "Approved"
- **Auto-approve path (free ticket):** employee submits (ticketType=free) → no approval task created → Mailpit receives "Ticket Confirmed" email directly → status "Completed"

---

### Workflow C1: Leave Request (`26-workflow-leave-request.spec.ts`)

**BPMN structure (built visually):**

```
Start Event
  → User Task "Submit Leave" [form: leave-request-form]
  → Business Rule Task "Route Leave" [DMN: leave-routing]
  → Exclusive Gateway
      → [approverLevel == 'auto']    → Email "Leave Auto-Approved" → End
      → [approverLevel == 'manager'] → User Task "Manager Approval" [HUMAN_APPROVAL]
                                     → Email "Leave Decision" → End
      → [approverLevel == 'director']→ User Task "Director Approval" [HUMAN_APPROVAL]
                                     → Email "Leave Decision" → End
```

**Test scenarios:**

- **Manager path (7 days):** employee submits (leaveDays=7) → manager claims approval task → approves with comment → Mailpit receives "Leave approved" email → employee sees "Approved" + comment in request history
- **Rejection path (7 days):** same flow but manager clicks Reject → Mailpit receives rejection email → employee sees "Rejected" + reason
- **Auto-approve path (2 days):** employee submits (leaveDays=2) → no approval task → status "Approved" immediately

---

### Workflow C2: Equipment Request (`27-workflow-equipment-request.spec.ts`)

**BPMN structure (built visually):**

```
Start Event
  → User Task "Submit Equipment List" [form: equipment-request-form]
  → Parallel Gateway (split)
      → User Task "IT Approval" [HUMAN_APPROVAL, candidateGroup: IT Department]
      → User Task "HR Check" [HUMAN_APPROVAL, candidateGroup: HR Department]
  → Parallel Gateway (join)
  → Service Task "Log to Inventory API" [EXTERNAL_API_CALL: mock-api /post]
  → Email Task "Equipment Ready" [NOTIFICATION]
  → End
```

**Test scenarios:**

- **Full parallel path:** manager submits form (Laptop+Mouse, Engineering, High) → `mike.it` (IT Department) navigates to /tasks → claims and approves "IT Approval" task → `john.manager` (HR Department) claims and approves "HR Check" task → parallel join completes → Mailpit receives "Equipment Ready" email → manager sees request "Completed"
- **Verify parallel blocking:** after `mike.it` approves IT task only, request status remains "Active" — join not yet reached, `john.manager` task still pending

---

### Workflow C3: Budget Request (`28-workflow-budget-request.spec.ts`)

**BPMN structure (built visually):**

```
Start Event
  → User Task "Submit Budget Request" [form: budget-request-form]
  → Service Task "Check Budget API" [EXTERNAL_API_CALL: mock-api /post, response → budgetStatus]
  → Business Rule Task "Route by Amount" [DMN: budget-routing]
  → Exclusive Gateway
      → [approverLevel == 'auto']    → Email "Budget Auto-Approved" → End
      → [approverLevel == 'manager'] → User Task "Manager Approval" [HUMAN_APPROVAL]
                                     → Email "Budget Decision" → End
      → [approverLevel == 'director']→ User Task "Director Approval" [HUMAN_APPROVAL]
                                     → Email "Budget Decision" → End
```

**Test scenarios:**

- **Manager path ($1200):** employee submits (amount=1200, Software) → external API call fires (httpbin returns 200, budgetStatus set) → manager approval task created → manager approves → Mailpit receives approval email → employee sees "Approved"
- **Auto-approve path ($200):** employee submits (amount=200) → no approval task → email sent directly → "Approved" immediately
- **Director path ($5000):** employee submits (amount=5000) → admin (acting as director) approves → email sent → "Approved"

---

## Issue Logging Protocol

All UI bugs found during test authoring and execution are logged as GitHub issues with:

- **Label:** `bug`, `e2e-found`
- **Title format:** `[E2E] <spec-file> — <description>`
- **Body:** steps to reproduce, expected vs actual, screenshot if applicable

No workflow spec is marked passing while a blocking UI bug remains open. Non-blocking cosmetic issues are logged but do not block the test suite.

---

## Definition of Done

- [ ] All 9 spec files exist in `werkflow-enterprise/frontends/portal/e2e/tests/`
- [ ] All tests pass on a fresh `docker compose up` (no pre-existing data)
- [ ] All 4 workflow journeys complete end-to-end including Mailpit email assertions
- [ ] All rejection paths tested (leave, budget)
- [ ] All auto-approve paths tested (event ticket free, leave 2-day, budget $200)
- [ ] Parallel gateway join verified (equipment request)
- [ ] All UI bugs found during testing logged as GitHub issues
- [ ] Blocking bugs resolved before v1.0.0 OSS push
- [ ] `afterAll` cleanup verified — re-run on clean state passes

---

## File Structure

```
werkflow-enterprise/frontends/portal/e2e/
├── .auth/                          # pre-authenticated session state
├── setup/
│   ├── admin.setup.ts              # existing — unchanged
│   ├── manager.setup.ts            # existing — unchanged
│   ├── employee.setup.ts           # existing — unchanged
│   └── it-user.setup.ts            # NEW — mike.it session for IT approval tasks
├── fixtures/
│   ├── auth.ts                     # existing — add STORAGE_STATES.itUser + TEST_USERS.itUser
│   ├── mailpit.ts                  # existing — unchanged
│   └── index.ts                    # existing — unchanged
└── tests/
    ├── 20-rbac-navigation.spec.ts        # NEW
    ├── 21-core-user-flows.spec.ts        # NEW
    ├── 22-connector-setup.spec.ts        # NEW
    ├── 23-dmn-decisions.spec.ts          # NEW
    ├── 24-forms-visual.spec.ts           # NEW
    ├── 25-workflow-event-ticket.spec.ts  # NEW
    ├── 26-workflow-leave-request.spec.ts # NEW
    ├── 27-workflow-equipment.spec.ts     # NEW
    └── 28-workflow-budget.spec.ts        # NEW
```
