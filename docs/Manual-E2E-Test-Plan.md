# Manual E2E Test Plan

**Werkflow Platform — MVP Release**
**Scope:** 10 curated manual tests, progressively increasing complexity, grounded in real business scenarios
**Relationship to automated suite:** Each manual test is a curated subset of or direct 1:1 map to an existing Playwright spec. This document is the demo-grade surface; the Playwright specs are the single source of truth for flow logic.

---

## Prerequisites

### Stack

Bring the full stack up before executing any test:

```
cd infrastructure/docker
docker compose up
```

Verify all services are healthy before starting:
- Portal: `http://localhost:3000`
- Engine: `http://localhost:8081/actuator/health`
- Admin service: `http://localhost:8082/actuator/health`
- Keycloak: `http://localhost:8090`

### Credentials

All usernames and passwords are sourced from `.env.shared` in the repo root. Do not hardcode credentials in this document (see git-history-rewrite-2026-06-18 policy). Refer to `.env.shared` for:
- `E2E_ADMIN_USERNAME` / `E2E_ADMIN_PASSWORD`
- `E2E_MANAGER_USERNAME` / `E2E_MANAGER_PASSWORD`
- `E2E_EMPLOYEE_USERNAME` / `E2E_EMPLOYEE_PASSWORD`

### Browsers

- **Primary:** Chromium (Playwright default)
- **Secondary:** Firefox (run selected tests for cross-browser coverage)

### Clean state per test

Each test starts from a known clean state:
1. Log out of any active session
2. Clear browser cookies or use a fresh private/incognito window
3. Log in as the role specified in the test prerequisites

### Seed data

The 4 example processes are auto-seeded on engine startup via `ProcessExampleDeployer`. If the process list is empty, restart the engine service:

```
docker compose restart engine
```

---

## Test Sheet

| # | Test Name | Bucket | Symbols Used | Connectors? | DMN? | One-Line Description | Maps to Automated Spec | Pass/Fail | Result Summary |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Simple Finance Approval — Happy Path | A | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None start, user task, exclusive gateway (approve branch), none end | No | No | Employee submits a finance review request; manager approves via exclusive gateway; process completes. | `04-task-approval.spec.ts`, `21-core-user-flows.spec.ts` | Pending | _(to be filled at execution)_ |
| 2 | DMN Reuse — Author New BPMN Referencing Seeded `leave_approval` DMN | A | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None start, service task (`flowable:type="dmn"`), exclusive gateway, none end x2 | No | Yes (`leave-approval.dmn` reused by key) | Admin authors a new BPMN in the designer that references the seeded `leave_approval` DMN by `decisionTableReferenceKey`; supplies `leaveDays=2, leaveType="annual"`; DMN auto-approves; process completes with no user task. Demonstrates seed-artifact reuse. | No current automated equivalent | Pending | _(to be filled at execution)_ |
| 3 | Form Authoring Round-Trip — Author Form, Attach to BPMN, Run | B | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None start, user task (with newly authored form), none end | No | No | Admin authors a 3-field form (text + number + select) at `/forms/new`, then authors a new BPMN with a user task referencing the new form; deploys; starts; claims; fills; completes. End-to-end form designer round-trip. | `27-bpmn-designer-m411-smoke.spec.ts` covers BPMN authoring leg only; no automated coverage of the form-authoring step | Pending | _(to be filled at execution)_ |
| 4 | Notification on Task Assignment | B | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1" fill="currentColor" stroke="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None start, service task (send notification), user task, none end | Notification (email) | No | Admin starts a procurement request; system sends an email notification when the first user task is assigned; manager sees the task in the task list. | `13-action-blocks.spec.ts`, `05-requests.spec.ts` | Pending | _(to be filled at execution)_ |
| 5 | DMN Auto-Routing — Leave Request (2 days, auto-approve) | B | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None start, service task (DMN), exclusive gateway, none end | No | Yes (`leave-approval.dmn`) | Employee submits a 2-day leave request; DMN routes to auto-approve branch; no user task is created; process completes immediately. | `e2e/tests/business/26-workflow-leave-request.spec.ts` (test 26.5) | Pending | _(to be filled at execution)_ |
| 6 | BPMN Designer — Author and Deploy a Process | B | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> Any minimal diagram authored in designer | No | No | Admin opens the BPMN designer, authors a minimal process (start → user task → end), saves as draft, deploys, and confirms it appears in the process list. | `27-bpmn-designer-m411-smoke.spec.ts` | Pending | _(to be filled at execution)_ |
| 7 | Leave Request — Manager Approval Path | C | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked), service task (DMN), exclusive gateway, user task (form), none end | No | Yes (`leave-approval.dmn`) | Employee fills in the leave request form (7 days); DMN routes to manager approval; manager claims the task, reviews the form data, approves; process completes; employee sees completed status in /requests. | `e2e/tests/business/26-workflow-leave-request.spec.ts` (tests 26.1–26.3) | Pending | _(to be filled at execution)_ |
| 8 | Procurement Approval — Multi-Step Sequential Approval | C | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked), user task x3 (vendor select / quotation review / manager approval), none end | No | No | Employee submits a purchase request via form; three sequential user tasks (select vendor, review quotations, manager approval) are claimed and completed in order by appropriate roles; process ends with PO Created. | `05-requests.spec.ts`, `21-core-user-flows.spec.ts` | Pending | _(to be filled at execution)_ |
| 9 | CapEx Approval — Full Stack with DMN and Multi-Tier Escalation | D | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1" fill="currentColor" stroke="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked), service task (SET_VARIABLES), service task (DMN x3), user task x3 (manager / VP / CFO), exclusive gateways, service task (notification), none end | No | Yes (`capex-approver-resolution.dmn`) | Employee submits a CapEx request above the $50K threshold; DMN resolves the manager approver group; manager approves; amount gateway triggers VP escalation; VP approves; CFO threshold not met; system sends approval notification; process completes. | `23-dmn-decisions.spec.ts`, `04-task-approval.spec.ts` | Pending | _(to be filled at execution)_ |
| 10 | Connector REST Call + DMN Routing | D | <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1" fill="currentColor" stroke="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked), service task (DMN), exclusive gateway, service task (connector REST), service task (notification), none end | Yes (REST connector) | Yes (`ticket-routing` DMN inline) | Admin deploys the event-ticket-request process; employee starts with a paid ticket; DMN routes to organiser review; manager approves; notification delegate sends email; process completes with no remaining active tasks. | `e2e/tests/business/25-workflow-event-ticket.spec.ts` (tests 25.1–25.4) | Pending | _(to be filled at execution)_ |

---

## Test Detail Cards

### Test 1 — Simple Finance Approval: Happy Path

**Bucket:** A — Basic
**Prerequisites:** Login as employee (`E2E_EMPLOYEE_USERNAME`)
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None start event, user task (`financeReview`), exclusive gateway (`approvalGateway`), none end event (`endApproved`)
**Process:** `finance-approval-process` (seeded example)
**Lifecycle:** Reuses seeded `finance-approval-process` BPMN (read-only). No test-authored artifacts.

**Steps:**
1. Navigate to `/processes`
2. Confirm `Finance Approval Process` is listed
3. Click "Start Process"
4. Confirm the start form renders (or process starts directly if no start form is attached)
5. Submit the form / start the process
6. Confirm redirect to `/requests`; note the new process instance appears as active
7. Log out; log in as manager (`E2E_MANAGER_USERNAME`)
8. Navigate to `/tasks`
9. Confirm "Finance Review" task is listed
10. Click the task; claim it
11. Select "Approve" (or submit the approval decision)
12. Confirm the task disappears from the active task list
13. Navigate to the process instance in `/requests`; confirm status is "Completed"

**Expected outcome:** Process instance reaches `endApproved`; no remaining active tasks.

---

### Test 2 — DMN Reuse: Author New BPMN Referencing Seeded `leave_approval` DMN

**Bucket:** A — Basic (engine plumbing + seed artifact reuse)
**Prerequisites:** Login as admin (`E2E_ADMIN_USERNAME`); BPMN designer at `/processes/new`; seeded `leave_approval` DMN deployed on engine startup (verified: `services/engine/src/main/resources/examples/tenants/default/dmn/leave-approval.dmn`, decision id `leave_approval`)
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None start event, service task with `flowable:type="dmn"`, exclusive gateway, two none end events
**Process:** Authored fresh; references seeded DMN `leave_approval` by `decisionTableReferenceKey`
**Lifecycle:** **Reuses** seeded `leave_approval` DMN (read-only, by key). **Authors** `e2e-dmn-reuse-test` BPMN — delete after run per Rule 4.
**Maps to:** No current automated equivalent

**Steps:**
1. Navigate to `/processes/new` (BPMN designer)
2. Author a minimal process: start event (none) → service task → exclusive gateway → two none end events
3. On the service task: select DMN action block (`flowable:type="dmn"`); set `decisionTableReferenceKey` to `leave_approval` (exact value — lowercase, underscore)
4. Set service task name to `Route via Leave Approval DMN`
5. Wire the exclusive gateway with two outgoing flows:
   - Condition `${approvalRequired == false}` → end event `Auto Approved`
   - Default flow → end event `Requires Approval`
6. Set process name `DMN Reuse Test`; process key `e2e-dmn-reuse-test`
7. Click "Save Draft"; confirm success indicator
8. Click "Deploy"; confirm no validation errors
9. Navigate to `/processes`; confirm `DMN Reuse Test` appears in the list
10. Click "Start Process"
11. Supply process variables: `leaveDays = 2`, `leaveType = "annual"` (via the portal's start variable input; if the portal requires a start form for variable input, add a minimal start form with these two fields)
12. Confirm redirect to `/requests`; instance is created
13. Wait 3 seconds; refresh `/requests`
14. Confirm the instance status is "Completed" (not "Active")
15. Log in as manager (`E2E_MANAGER_USERNAME`); navigate to `/tasks`; confirm no task exists for this instance

**DMN facts verified from file:**
- Decision key: `leave_approval`
- Inputs: `leaveDays` (number), `leaveType` (string: `sick` / `parental` / `bereavement` / `annual` / `personal`)
- Outputs: `approvalRequired` (boolean), `approverRole` (string)
- Auto-approve rule (rule 6): `leaveDays > 0` AND `leaveType == "annual"` → `approvalRequired=false`, `approverRole="AUTO"`

**Expected outcome:** New BPMN references the seeded `leave_approval` DMN by key. With `leaveDays=2, leaveType="annual"`, the DMN evaluates to `approvalRequired=false`. The exclusive gateway routes to "Auto Approved" end event. Process completes immediately; no user task is created.

**Estimated execution time:** 12–15 minutes

---

### Test 3 — Form Authoring Round-Trip: Author Form, Attach to BPMN, Run

**Bucket:** B — Events + diverse task types (capability round-trip)
**Prerequisites:** Login as admin (`E2E_ADMIN_USERNAME`); requires `WORKFLOW_ADMIN` or `SUPER_ADMIN` to reach `/forms/new` (per `isManagerOrAbove` guard in `FormsPage`); BPMN designer at `/processes/new`
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None start event, user task (with newly authored form on `flowable:formKey`), none end
**Process:** Authored fresh; user task references newly authored form `e2e-round-trip-form`
**Lifecycle:** No seeded artifact reuse. **Authors** `e2e-round-trip-form` form + `e2e-round-trip-process` BPMN — delete both after run per Rule 4.
**Maps to:** `27-bpmn-designer-m411-smoke.spec.ts` covers the BPMN authoring leg only; no automated coverage of the form-authoring step

**Note on logical unit (pre-MVP enforcement):** The form is created in step 1 BEFORE the BPMN that uses it (step 9). This creates a transient orphan window (form exists, not yet referenced). Logical unit enforcement (R1) lands pre-MVP — the BPMN deploy at step 14 will fail loudly if the form key referenced in step 11 was never saved. Confirm the form save at step 7 succeeded before proceeding to BPMN deploy.

**Steps:**
1. Navigate to `/forms`; confirm forms list loads without error
2. Click "Create New Form" (routes to `/forms/new`)
3. Set Form Key `e2e-round-trip-form`; form name `Round Trip Form`
4. In the form-js canvas, add a text field: key `applicantName`, label `Applicant Name`
5. Add a number field: key `requestAmount`, label `Request Amount`
6. Add a select field: key `priority`, label `Priority`; options `low`, `medium`, `high`
7. Click "Save"; confirm success indicator (no error state)
8. Navigate to `/forms`; confirm `Round Trip Form` appears in the list
9. Navigate to `/processes/new` (BPMN designer)
10. Author a minimal process: start event (none, no start form) → user task → none end event
11. On the user task: set `flowable:formKey` to `e2e-round-trip-form` (the key from step 3); set candidate group to `SUPER_ADMIN`
12. Set process name `Round Trip Process`; process key `e2e-round-trip-process`
13. Click "Save Draft"; confirm success
14. Click "Deploy"; confirm validation passes; deploy-success toast appears
15. Navigate to `/processes`; confirm `Round Trip Process` appears
16. Click "Start Process"; confirm the instance is created (no start form — process advances directly to the first user task)
17. Navigate to `/tasks` as admin
18. Confirm the user task is listed; claim it
19. Confirm the task form renders all three fields: `Applicant Name` (text input), `Request Amount` (number input), `Priority` (select dropdown)
20. Fill: Applicant Name = `Test User`; Request Amount = `500`; Priority = `high`
21. Submit the task
22. Navigate to `/requests`; confirm instance status is "Completed"

**Expected outcome:** A form authored from scratch in the portal is saved to the engine's form store, referenced by a BPMN deployed via the designer, and rendered correctly at runtime. Submitted variables (`applicantName`, `requestAmount`, `priority`) are captured by the engine; process completes.

**Field type rationale:** text + number + select covers the three most common form-js field types (string, numeric, enum). Fewer would not demonstrate field-type diversity.

**Estimated execution time:** 15–20 minutes (first run); ~10 minutes once familiar with the designer and form builder.

---

### Test 4 — Notification on Task Assignment

**Bucket:** B — Events + diverse task types
**Prerequisites:** Login as admin; Mailpit running at `http://localhost:8025` (or equivalent mail catcher)
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1" fill="currentColor" stroke="none"/></svg> None start, user task, email notification (via `GlobalTaskNotificationListener`)
**Process:** `finance-approval-process`
**Lifecycle:** Reuses seeded `finance-approval-process` BPMN (read-only). Side-effect tests `GlobalTaskNotificationListener` (engine-wired, no artifact).

**Steps:**
1. Navigate to `/processes`; start `Finance Approval Process`
2. Immediately navigate to Mailpit (`http://localhost:8025`)
3. Confirm an email notification was sent for task assignment (subject should reference the process or task name)
4. Log in as manager; navigate to `/tasks`
5. Confirm "Finance Review" task is present; claim it

**Expected outcome:** Email notification received in Mailpit within 10 seconds of process start; task appears in manager's task list.

**Note if Mailpit is not configured:** Mark this step as "not applicable" and document the absence in Result Summary.

---

### Test 5 — DMN Auto-Routing: Leave Request (2 days, auto-approve)

**Bucket:** B — Events + DMN
**Prerequisites:** Login as employee
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked), service task (`flowable:type="dmn"`), exclusive gateway (auto branch), none end
**Process:** `leave-request`
**Lifecycle:** Reuses seeded `leave-request` BPMN + `leave-approval.dmn` + `leave-request-form` (all read-only).

**Steps:**
1. Navigate to `/processes`; click "Start Process" for `Leave Request`
2. Fill in the leave request form:
   - Start Date: any valid date (e.g., `2026-08-01`)
   - End Date: 2 days later
   - Number of Days: `2`
   - Leave Type: `Annual`
   - Reason: `Short break`
3. Submit the form
4. Confirm redirect to `/requests`
5. Wait 3 seconds; refresh `/requests`
6. Confirm the process instance status is "Completed" (not "Active")
7. Log in as manager; navigate to `/tasks`
8. Confirm NO "Manager Approval" task exists for this instance

**Expected outcome:** DMN routes `leaveDays=2` to `approverLevel=auto`; process completes without creating a user task. Engine has zero remaining active tasks for this instance.

---

### Test 6 — BPMN Designer: Author and Deploy a Simple Process

**Bucket:** B — Designer
**Prerequisites:** Login as admin; BPMN designer accessible at `/designer` or equivalent route
**Lifecycle:** No seeded artifact reuse. **Authors** `e2e-simple-review` BPMN — delete after run per Rule 4.
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> Authoring any minimal BPMN diagram in the designer

**Steps:**
1. Navigate to the BPMN designer
2. Author a minimal process:
   - Start event (none)
   - User task named "Review Item" with `flowable:candidateGroups` set to `SUPER_ADMIN`
   - None end event
   - Connect with sequence flows
3. Set the process name and key (e.g., "[E2E] Simple Review" / `e2e-simple-review`)
4. Click "Save Draft"; confirm success toast/message
5. Click "Deploy"; confirm the process deploys without validation errors
6. Navigate to `/processes`
7. Confirm "[E2E] Simple Review" appears in the process list
8. Click "Start Process"; confirm the process starts
9. Navigate to `/tasks` as the same user (admin)
10. Confirm "Review Item" task is listed; claim and complete it
11. Confirm process instance completes

**Expected outcome:** Author → save → deploy → run round-trip completes without errors. Process runs to completion.

---

### Test 7 — Leave Request: Manager Approval Path

**Bucket:** C — Forms + tasks + events
**Prerequisites:** Login as employee for start; login as manager for approval
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked, form-js), service task (DMN), exclusive gateway (manager branch), user task (form), none end
**Process:** `leave-request`
**Lifecycle:** Reuses seeded `leave-request` BPMN + `leave-approval.dmn` + `leave-request-form` + `leave-approval-form` (all read-only).
**Maps to:** `e2e/tests/business/26-workflow-leave-request.spec.ts` tests 26.1–26.3

**Steps:**
1. As employee: navigate to `/processes`; confirm `Leave Request` is listed
2. Click "Start Process"; confirm form renders with fields: Start Date, End Date, Number of Days, Leave Type, Reason
3. Fill in:
   - Start Date: `2026-08-01`
   - End Date: `2026-08-07`
   - Number of Days: `7`
   - Leave Type: `Annual`
   - Reason: `Annual family leave`
4. Submit the form; confirm redirect to `/requests`; note instance appears as "Active"
5. As manager: navigate to `/tasks`
6. Confirm "Manager Review" task appears
7. Click the task; claim it
8. Confirm task form shows the submitted leave request data
9. Submit the approval decision (approve)
10. As employee: navigate to `/requests`
11. Confirm the instance status changed to "Completed"
12. Check Mailpit for an email notification (soft — mark N/A if not configured)

**Expected outcome:** DMN routes `leaveDays=7` to `approverLevel=manager`; "Manager Review" user task created; manager approves; process completes; employee sees completed status.

---

### Test 8 — Procurement Approval: Multi-Step Sequential Approval

**Bucket:** C — Forms + tasks + events
**Prerequisites:** Credentials sourced from `.env.shared` (`Werkflow@2026!` for all seeded users; expect KC to force a password reset on first login per `temporary: true`). Role-to-user map used in this test:
- `doa_approver_level1` → **`john.manager`** (or `mike.it`)
- `doa_approver_level2` → **`admin`** (SUPER_ADMIN covers all DOA tiers; substitute a dedicated L2 user if one has been added post-seed)
- Process initiator → **`jane.employee`** (or any `employee` role user)
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked), user task x3 (`selectVendor`, `reviewQuotations`, `managerApproval`), none end x2
**Process:** `procurement-approval-process`
**Lifecycle:** Reuses seeded `procurement-approval-process` BPMN + 4 procurement forms (all read-only).

**Steps:**
1. As `jane.employee`: navigate to `/processes`; click "Start Process" for `Procurement Approval Process`
2. Fill in the procurement request form (supplier, item description, estimated cost)
3. Submit the form; confirm redirect to `/requests`; instance appears as "Active"
4. As `john.manager` (doa_approver_level1): navigate to `/tasks`
5. Claim and complete "Select Vendor" task (fill in vendor details if form is present)
6. Claim and complete "Review Quotations" task
7. As `admin` (covering doa_approver_level2): claim and complete "Manager Approval" task
8. Confirm process instance moves to "PO Created" end state
9. As employee: confirm the instance appears as "Completed" in `/requests`

**Expected outcome:** Three sequential user tasks executed in order by appropriate roles; process reaches `endEventApproved`; no remaining active tasks.

---

### Test 9 — CapEx Approval: Full Stack with DMN and Multi-Tier Escalation

**Bucket:** D — Full stack
**Prerequisites:** Login as employee for start; admin for escalation tasks if specific role users not available
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1" fill="currentColor" stroke="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked), service task (SET_VARIABLES), service task (DMN, three instances), user task x3 (manager / VP / CFO), exclusive gateways, service task (notification send), none end
**Process:** `capex-approval-process`
**DMN:** `capex-approver-resolution.dmn` (resolves approver group per tier)
**Lifecycle:** Reuses seeded `capex-approval-process` BPMN + `capex-approver-resolution.dmn` + `capex-request-form` + `capex-approval-form` (all read-only).

**Scenario:** Amount $75,000 — requires manager approval (threshold met), then VP approval (above $50K), CFO not triggered (below $250K).

**Steps:**
1. As employee: navigate to `/processes`; start `CapEx Approval Process`
2. Fill in the CapEx request form:
   - Project name: `Test Capital Project`
   - Amount: `75000`
   - Department / Cost centre: fill in required fields
   - Business justification: `E2E test scenario`
3. Submit; confirm redirect to `/requests`; instance is "Active"
4. Wait for the engine to process the DMN-backed budget check and resolve the manager approver group
5. As manager: navigate to `/tasks`; claim "Manager Review" task
6. Confirm form shows the submitted CapEx data
7. Submit approval decision (approve)
8. Confirm the manager decision gateway routes to VP review (amount > $50K)
9. As VP (or admin): claim "VP Review" task; approve
10. Confirm the VP amount gateway does NOT trigger CFO (amount $75K is below $250K threshold)
11. Confirm system sends approval notification (check Mailpit; soft assertion)
12. Confirm process instance status is "Completed"
13. As employee: confirm completed status visible in `/requests`

**Expected outcome:** Two approval tiers (manager, VP) execute; CFO is not triggered; approval notification sent; process completes with `updateApproved` service task and `sendApprovalNotification` service task having fired.

---

### Test 10 — Connector REST Call + DMN Routing: Event Ticket Request

**Bucket:** D — Full stack
**Prerequisites:** ⚠️ `event-ticket-request` is NOT in the seeded example set (only 4 are: capex, leave, procurement, finance per session 40). Before this test, the admin must deploy the BPMN+DMN manually via the BPMN Designer:
1. Open `e2e/tests/business/25-workflow-event-ticket.spec.ts` and copy the inline BPMN + DMN XML.
2. As `admin`: navigate to `/processes/new`; paste the BPMN; save and deploy.
3. Repeat for the DMN under `/dmn/new`.
4. Confirm both appear in `/processes` and `/dmn` lists before running this test.

**Alternative:** Seed `event-ticket-request` as a 5th classpath example (post-approval decision — would require adding the BPMN/DMN/form to `services/engine/src/main/resources/examples/tenants/default/`).

Login as `jane.employee` for the process start; `john.manager` for organiser review.
**Symbols used:** <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="13" cy="13" r="4"/><circle cx="19" cy="13" r="4"/><circle cx="16" cy="19" r="2"/><text x="28" y="23" font-size="8" fill="currentColor" stroke="none" font-family="monospace">DMN</text></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1" fill="currentColor" stroke="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked), service task (DMN — `ticket-routing`), exclusive gateway, service task (notification), user task, none end
**Connectors:** `${notificationDelegate}` (email)
**DMN:** `ticket-routing` (inline in spec 25 or pre-deployed)
**Lifecycle:** **Manually deploys** `event-ticket-request` BPMN + `ticket-routing` DMN + ticket form (NOT seeded; see prerequisites). Treat these like `e2e-` artifacts — delete after run per Rule 4. (Will be replaced by IT Helpdesk seed once the reshuffle lands; see Seed Library Reshuffle section.)
**Maps to:** `e2e/tests/business/25-workflow-event-ticket.spec.ts` tests 25.1–25.4

**Scenario A (paid ticket, manager approval required):**

1. As employee: navigate to `/processes`; start `Event Ticket Request`
2. Fill in the event ticket form:
   - Attendee Name: your name
   - Email: your email
   - Ticket Type: `Paid`
   - Quantity: `2`
3. Submit; confirm redirect to `/requests`; instance is "Active"
4. Wait for DMN to route (`ticketType=paid` → `approvalRequired=true`)
5. As manager: navigate to `/tasks`; claim "Organiser Review" task
6. Submit approval decision
7. Confirm system fires the `Send Ticket Decision` notification (check Mailpit; soft)
8. As employee: confirm instance is "Completed" in `/requests`
9. Confirm no remaining active tasks via admin API if available

**Scenario B (free ticket, auto-routes):**

1. As employee: start the same `Event Ticket Request` process
2. Select Ticket Type: `Free`; quantity `1`
3. Submit
4. Wait 3 seconds; confirm process is already "Completed" in `/requests`
5. Confirm NO "Organiser Review" task was created (check `/tasks` as manager — it must be absent)
6. Check Mailpit for `Send Ticket Confirmation` notification (soft)

**Expected outcome — Scenario A:** DMN routes paid ticket to organiser review; manager approves; email sent; zero remaining tasks.
**Expected outcome — Scenario B:** DMN routes free ticket directly to `notifyFree` service task; process auto-completes; no user task created.

---

## Seed Library Reshuffle (Approved, Pending Implementation)

Per seed library curation policy (see `BPMN-Symbol-Reference.md` start-event row terminology note and the policy memo in commit history), the following changes are approved for a separate code session:

### REMOVE — `finance-approval-process`

**Reason:** Its userTask + exclusiveGateway pattern is already fully covered by `leave-request` (auto-approve gateway) and `capex-approval-process` (multi-tier gateway). It is in the path/subprocess of procurement/capex approval patterns. No element-family coverage is lost by removal; the seed list goes 4 → 3 momentarily before the IT Helpdesk addition.

**Affected files (when the code session runs):**
- `services/engine/src/main/resources/examples/tenants/default/bpmn/finance-approval-process.bpmn20.xml` (delete)
- `services/engine/src/main/resources/examples/tenants/default/forms/budget-request-form.json` (delete)
- `ProcessExampleDeployer` example registry (deregister)
- New Flyway migration (cleanup deployment + form_schemas rows for fresh deploy; orphan-prune already auto-runs)

### ADD — IT Helpdesk Ticket (replaces `finance-approval` slot)

**Reason:** SendTask is the only element family with zero seed representation. Adding this example brings BPMN element coverage to: userTask, serviceTask (incl. DMN variant), sendTask, exclusiveGateway across the seed set.

**Self-containment requirement (gate before seeding):** Confirm that `NotificationDelegate` (which backs the `SendTask` `SEND_NOTIFICATION` action) does NOT require external SMTP / Slack credentials. The SendTask must either (a) no-op gracefully when Mailpit/SMTP is absent, or (b) write to an in-process log channel. If it requires OpenBao credentials, the example violates inclusion criterion 2 (self-contained on clean tenant) and must NOT be seeded — fall back to a demo-authored test only.

**Proposed BPMN structure:**

- <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="15"/></svg> User-initiated start (form-linked: ticket type, urgency, description)
- <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 3 L33 18 L18 33 L3 18 Z"/><line x1="12" y1="12" x2="24" y2="24" stroke-width="3"/><line x1="24" y1="12" x2="12" y2="24" stroke-width="3"/></svg> Exclusive gateway "Urgent?" with condition `${urgency == 'high'}`
  - True: <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><rect x="7" y="8" width="10" height="8" rx="1" fill="currentColor" stroke="none"/></svg> Send task "Notify On-Call Team" (`SEND_NOTIFICATION`, email channel)
  - Both branches merge to:
- <svg xmlns="http://www.w3.org/2000/svg" width="36" height="24" viewBox="0 0 60 36" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="54" height="30" rx="6"/><circle cx="16" cy="13" r="4" fill="currentColor" stroke="none"/><path d="M8,27 Q16,19 24,27" fill="none"/></svg> User task "Resolve Ticket" (`HUMAN_APPROVAL`, `SUPER_ADMIN`)
- <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="4"><circle cx="18" cy="18" r="15"/></svg> None end "Ticket Resolved"

**Business grounding:** IT helpdesk ticket routing with conditional escalation email for high-urgency tickets.

**Implementation tracking:** See Master Roadmap entry "Seed library reshuffle (finance-approval → IT Helpdesk)".

### DROPPED — Simple PO Approval (no new coverage)

The previously proposed "Simple PO Approval" BPMN has been **dropped**. Per the seed curation policy, its exclusive-gateway-only pattern adds no element-family coverage beyond what capex/leave already demonstrate. Bucket A test slots are now filled by Test 1 (Finance happy path — still using `finance-approval-process` until the reshuffle lands) and Test 2 (DMN Reuse — authored fresh from designer).

---

## Test Artifact Lifecycle

This plan distinguishes between **seeded artifacts** (deployed by `ProcessExampleDeployer` at engine startup) and **test-authored artifacts** (created in the portal during the manual E2E run). The four rules below prevent test artifacts from polluting the seeded example set or conflicting with the logical unit deployment contract (R1 — see `BPMN-Symbol-Reference.md` start-event terminology note).

### Rule 1 — Naming convention: `e2e-` prefix on every test-authored key

Every artifact key authored during this run carries the `e2e-` prefix. Verified: no seeded artifact uses this prefix, so `LIKE 'e2e-%'` cleanly identifies test artifacts via grep or SQL.

| Artifact type | Key pattern | Example |
|---|---|---|
| Form key | `e2e-<purpose>` | `e2e-round-trip-form` |
| Process key | `e2e-<purpose>` | `e2e-dmn-reuse-test`, `e2e-round-trip-process`, `e2e-simple-review` |
| DMN key (if authored) | `e2e-<purpose>` | `e2e-routing-test` |
| Display name | `[E2E] <Purpose>` | `[E2E] Simple Review`, `[E2E] DMN Reuse Test` |

### Rule 2 — Never modify seeded artifacts

Seeded BPMNs (`capex-approval-process`, `leave-request`, `procurement-approval-process`, `finance-approval-process`), seeded DMNs (`leave_approval`, `capex-approver-resolution`), and seeded forms are **read-only** during the E2E run. If a different shape is needed for a test, author a fresh variant under the `e2e-` prefix — do NOT fork a seeded artifact.

### Rule 3 — Reuse seeded DMNs and forms where the test is about composition

When a test is about **composition** (referencing a seeded artifact from a new BPMN) rather than authoring from scratch, reuse the seeded artifact by key. This avoids re-authoring DMN logic and demonstrates seed-as-reference-library semantics.

**Seeded artifact reference table:**

| Seeded artifact | Type | Useful for testing |
|---|---|---|
| `leave_approval` | DMN | Auto-approve decision logic; binary boolean output; multi-input rule routing (Test 2 reuses this) |
| `capex-approver-resolution` | DMN | Multi-tier approval routing; group-name string output |
| `capex-request-form` | Form | Multi-field request form with number + select fields |
| `capex-approval-form` | Form | Multi-field approval form with approve/reject/comments |
| `leave-request-form` | Form | Form with date fields + select + textarea |
| `leave-approval-form` | Form | Minimal approval form |
| `procurement-*` forms (×4) | Form | Sequential form chain across 4 approval steps |
| `budget-request-form` | Form | Single-field minimal form |
| Any seeded BPMN | Process | Read-only inspection in the designer for pattern reference (do NOT save edits) |

**Per-test reuse summary:**

| Test | Reuses (read-only) | Authors (`e2e-` prefix) |
|---|---|---|
| 1 | `finance-approval-process` | — |
| 2 | `leave_approval` DMN | `e2e-dmn-reuse-test` BPMN |
| 3 | — | `e2e-round-trip-form` + `e2e-round-trip-process` |
| 4 | `finance-approval-process` (+ `GlobalTaskNotificationListener`) | — |
| 5 | `leave-request` BPMN + `leave-approval.dmn` + `leave-request-form` | — |
| 6 | — | `e2e-simple-review` BPMN |
| 7 | `leave-request` BPMN + `leave-approval.dmn` + `leave-request-form` + `leave-approval-form` | — |
| 8 | `procurement-approval-process` BPMN + 4 procurement forms | — |
| 9 | `capex-approval-process` BPMN + `capex-approver-resolution.dmn` + `capex-request-form` + `capex-approval-form` | — |
| 10 | — (not seeded — see Test 10 prerequisites for manual deploy steps) | `event-ticket-request` BPMN + `ticket-routing` DMN + form (manual deploy via spec 25 inline XML) |

### Rule 4 — Cleanup after confirmation, not per-test

Cleanup runs **after all 10 tests are complete** and Pass/Fail + Result Summary columns are filled in. Three cleanup methods, pick by situation:

**Method A (default) — Portal UI delete:**
- `/forms` — delete each row whose key starts with `e2e-`
- `/processes` — delete each process whose key starts with `e2e-`
- `/dmn` — delete each DMN whose key starts with `e2e-`

**Method B — SQL targeted (faster batch):**
```sql
-- Preview before deleting
SELECT form_key FROM form_schemas WHERE form_key LIKE 'e2e-%';
SELECT key_, name_ FROM act_re_procdef WHERE key_ LIKE 'e2e-%';
-- Then delete via portal API or Flowable repositoryService.deleteDeployment(cascade=true)
```

**Method C — Engine restart with fresh volumes (nuclear):** only if Methods A/B leave residue. Confirm with the team before running — this wipes ALL tenant data including any uncleaned dev artifacts.
```
docker compose down -v && docker compose up
```

### Cleanup Checklist (run after all 10 tests complete)

```
□ /forms — no e2e- prefixed forms remain
□ /processes — no e2e- prefixed processes remain
□ /dmn — no e2e- prefixed DMNs remain
□ SQL: SELECT key_, name_ FROM act_re_procdef WHERE key_ LIKE 'e2e-%' returns zero rows
□ SQL: SELECT form_key FROM form_schemas WHERE form_key LIKE 'e2e-%' returns zero rows
□ Manually deployed event-ticket-request (Test 10) — delete via Portal /processes
□ Any user-authored process_draft rows abandoned mid-test — delete via /drafts UI (system-created drafts auto-prune on engine restart)
```

---

## What This Plan Does NOT Cover

The following scenarios are explicitly excluded from this manual test plan. They are either deferred to engine-level JUnit tests or outside the scope of CI:

- **Long-duration timers** (> 30 seconds): Timer boundary events and timer catch events with real business durations cannot be run in a demo or CI environment without duration substitution. Use JUnit with `PT2S` ISO 8601 durations for automated coverage.
- **Real escalation timeouts**: Escalation events that trigger only after a defined waiting period (e.g., "if not approved in 24 hours, escalate") are not covered.
- **High concurrency and race conditions on signal / message correlation**: `TenantAwareSignalService` behaviour under concurrent instance creation is covered by `SignalTenantIsolationTest` at engine level only.
- **Parallel gateway join completeness testing**: Verifying that all parallel branches complete before a join gateway proceeds requires controlled multi-token test harnesses, not a manual walkthrough.
- **External worker task polling**: The external task / external worker pattern (ADR-019 through ADR-022) has no portal UI and must be tested via the Flowable REST API or JUnit engine harness.
- **Performance and load testing**: Response times under load, concurrent user handling, and throughput benchmarks are out of scope for this plan.
- **Failure-injection scenarios**: Connector outage (REST connector returning 5xx), database outage, OpenBao unavailable, Keycloak restart mid-session. These belong to chaos engineering runbooks.
- **Script tasks**: Quarantined under ADR-016; cannot be deployed. No manual test is possible until ADR-016 Phase 2 is complete.
- **Business rule tasks**: Hard-rejected by `WerkflowBusinessRuleTaskValidator`; no manual test is possible.
- **Link events**: Hard-rejected by `WerkflowLinkEventValidator`; no manual test is possible.
- **Cross-browser parity testing**: This plan uses Chromium as primary. Firefox is secondary and not covered for every test in this iteration.

---

*Document version: MVP release draft. Scenarios marked "soft" (Mailpit assertions) will not block test pass/fail decisions if the mail catcher is not configured. All other assertions are hard pass/fail.*
