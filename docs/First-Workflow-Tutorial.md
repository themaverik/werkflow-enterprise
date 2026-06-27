# First Workflow Tutorial

Build and complete your first approval workflow in about 30 minutes using the Leave Request sample process that ships with Werkflow.

## Prerequisites

- Werkflow running locally. See [QUICKSTART.md](QUICKSTART.md) if you haven't started it yet.
- Two browser tabs or incognito windows — you will log in as two different users.

## Sample Users

The following demo users are pre-loaded in the default Keycloak realm. All share the password `Werkflow@2026!`, which is marked temporary — Keycloak will prompt you to set a new password on first login.

| Username | Password | Group | Role in this tutorial |
|---|---|---|---|
| `demo.employee` | `Werkflow@2026!` | — | Submits the leave request |
| `demo.admin` | `Werkflow@2026!` | `SUPER_ADMIN` | Approves the request (`SUPER_ADMIN` can claim any task) |

> **About the approver:** The leave process routes its approval task to a `LINE_MANAGER` or `HR_MANAGER` group resolved by the DMN. Those department groups are not pre-provisioned for the bundled demo users, so this tutorial approves as `demo.admin` — `SUPER_ADMIN` is always included alongside the resolved group in the task's `candidateGroups`, so it can claim every task. In a real tenant you would map your manager/HR users into those groups (see [Admin-Setup-Guide.md](Admin-Setup-Guide.md)).

---

## Part 1: Enable Sample Workflows

The example processes deploy on startup when `WERKFLOW_DEPLOY_EXAMPLES=true`.

In `config/env/.env.engine`, set:

```
WERKFLOW_DEPLOY_EXAMPLES=true
```

Restart the engine service:

```bash
docker compose restart engine
```

You should see log lines like:

```
Deployed example process: leave-request.bpmn20.xml
Deployed example process: it-helpdesk-ticket.bpmn20.xml
Deployed example process: capex-approval-process.bpmn20.xml
Deployed example process: procurement-approval-process.bpmn20.xml
```

---

## Part 2: Submit a Leave Request (as demo.employee)

1. Open http://localhost:4000 and log in as `demo.employee`.
2. In the sidebar, click **Processes**.
3. Find **Leave Request** and click **Start**.
4. Fill in the form:
   - Leave Type: `Annual Leave`
   - Start Date: pick a date five or more working days from today
   - End Date: set this to four days after your Start Date (five calendar days total)
   - Reason: `Annual vacation`
5. The form will compute and display `Total leave days: 5` automatically.
6. Click **Submit**.

You will be redirected to the requests list. Behind the scenes the engine ran the `leave_approval` DMN: five days of annual leave matched the "medium duration" rule (>=4 days, any type), so `approvalRequired=true` and `approverRole=LINE_MANAGER`. A Manager Review task has been created with `candidateGroups="LINE_MANAGER,SUPER_ADMIN"` — among the demo users, `demo.admin` (a `SUPER_ADMIN`) can claim it.

---

## Part 3: Approve the Request (as demo.admin)

1. Open a new tab and log in as `demo.admin`.
2. Click **My Tasks** in the sidebar.
3. You should see a **Manager Review** task for the leave request you just submitted.
4. Open the task. The request details are shown in the task context.
5. Set **Decision** to `Approve` and add a comment (optional).
6. Click **Complete**.

The process moves to the "Leave Approved" end event. The instance is now complete.

---

## Part 4: Try the Auto-Approve Path

Annual leave of fewer than four days is auto-approved by the DMN — no task is created and no one needs to act.

1. Log back in as `demo.employee` and start another Leave Request.
2. Fill in:
   - Leave Type: `Annual Leave`
   - Start Date: any Monday
   - End Date: the following Tuesday (two calendar days total)
   - Reason: `Long weekend`
3. The form will compute `Total leave days: 2`.
4. Click **Submit**.

The process completes immediately. The `leave_approval` DMN matched the "short annual leave" rule (days > 0, type == annual) and set `approvalRequired=false`. The exclusive gateway routed directly to the "Auto Approved" end event. No task appeared in any My Tasks list.

---

## Part 5: Try Different Leave Types

Submit requests with other leave types to see different DMN routing outcomes.

**Sick leave — always routed to HR regardless of duration:**

1. Log in as `demo.employee` and start another Leave Request.
2. Set Leave Type to `Sick Leave`, dates for one day, and a reason.
3. Submit.
4. Log in as `demo.admin` (who is in `SUPER_ADMIN`, which is always included in the task's candidate groups).
5. Open **My Tasks**. You will see a Manager Review task. The DMN set `approverRole=HR_MANAGER`. Because `demo.admin` is in `SUPER_ADMIN` — included alongside `${approverRole}` in the task's `candidateGroups` — they can claim and complete it.
6. Approve or reject the task to end the process.

**Rejection:**

1. Log in as `demo.employee` and submit a five-day annual leave request.
2. Log in as `demo.admin` and open the Manager Review task.
3. Set **Decision** to `Reject` and add a comment.
4. Click **Complete**.

The process ends at the "Leave Rejected" end event.

---

## Part 6: View the Process in the Designer

1. Log in as `demo.admin`.
2. Click **Processes** then select **Leave Request**.
3. Click **View Designer** to see the BPMN diagram.
4. Observe the DMN service task (Resolve Leave Policy) before the first gateway. This is what evaluates `leave_approval.dmn` and writes `approvalRequired` and `approverRole` into the process context.
5. Click the exclusive gateway labeled "Approval Required?" to see the two outgoing conditions: `${approvalRequired == false}` routes to Auto Approved; `${approvalRequired == true}` routes to Manager Review.
6. Click the Manager Review user task and open its Properties Panel. Notice `candidateGroups="${approverRole},SUPER_ADMIN"` — the group is not hard-coded; it is resolved at runtime from the variable set by the DMN.

---

## Next Steps

- Try the **IT Helpdesk Ticket**, **Capital Expenditure Approval**, and **Procurement Approval** sample workflows — see [Sample-Workflows.md](Sample-Workflows.md) for what each demonstrates.
- Build your own workflow: click **New Process** in the BPMN Designer, drag shapes from the palette, and deploy.
- Register an external API connector in **Admin > Connectors** and wire it to a service task.
