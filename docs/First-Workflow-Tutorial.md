# First Workflow Tutorial

Build and complete your first approval workflow in about 30 minutes using the General Approval sample process that ships with Werkflow.

## Prerequisites

- Werkflow running locally. See [QUICKSTART.md](QUICKSTART.md) if you haven't started it yet.
- Three browser tabs or incognito windows — you will log in as three different users.

## Sample Users

The following demo users are pre-loaded in the default Keycloak realm.

| Username | Password | DOA Level | Role in this tutorial |
|---|---|---|---|
| `demo.employee` | `Demo1234!` | L1 | Submits the request |
| `demo.manager` | `Demo1234!` | L2 | Approves requests up to $50K |
| `demo.admin` | `Demo1234!` | L4 | Approves requests over $50K |

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
Deployed example process: general-approval.bpmn20.xml
Deployed example process: document-review.bpmn20.xml
Deployed example process: onboarding-checklist.bpmn20.xml
```

---

## Part 2: Submit a Request (as demo.employee)

1. Open http://localhost:4000 and log in as `demo.employee`.
2. In the sidebar, click **Processes**.
3. Find **General Approval** and click **Start**.
4. Fill in the form:
   - Request Title: `Office chair replacement`
   - Description: `Ergonomic chair needed for home office setup`
   - Amount: `3500`
   - Category: `Operational`
5. Click **Submit**.

You will be redirected to the requests list. The process instance is now running and a Manager Approval task has been created.

---

## Part 3: Approve the Request (as demo.manager)

1. Open a new tab and log in as `demo.manager`.
2. Click **My Tasks** in the sidebar.
3. You should see **Manager Approval** for the request you just submitted.
4. Open the task. The request details are shown read-only.
5. Set **Decision** to `Approve` and add a comment (optional).
6. Click **Complete**.

Because the amount ($3,500) is below the $50K Director threshold, the process moves directly to the notification step. The requester receives an email notification (visible in Mailpit at http://localhost:8025).

---

## Part 4: Try the Director Path

Submit a second request with a high amount to trigger the Director approval step.

1. Log back in as `demo.employee` and start another General Approval.
2. Set Amount to `75000` (over $50K).
3. Submit.
4. Log in as `demo.manager` and approve the Manager task.
5. Log in as `demo.admin` and open **My Tasks** — you will see a **Director Approval** task.
6. Approve it. The requester receives the final approval email.

---

## Part 5: Try Rejection

1. Submit another request as `demo.employee`.
2. Log in as `demo.manager` and set Decision to `Reject`.
3. The process ends with the "Request Rejected" end event. A rejection email is sent.

---

## Part 6: View the Process in the Designer

1. Log in as `demo.admin`.
2. Click **Processes** then select **General Approval**.
3. Click **View Designer** to see the BPMN diagram.
4. Notice the DOA gateway that routes low-amount requests to Manager only and high-amount requests through Manager then Director.

---

## Next Steps

- Try the **Document Review** and **Onboarding Checklist** sample workflows — see [Sample-Workflows.md](Sample-Workflows.md) for what each demonstrates.
- Build your own workflow: click **New Process** in the BPMN Designer, drag shapes from the palette, and deploy.
- Register an external API connector in **Admin > Connectors** and wire it to a service task.
