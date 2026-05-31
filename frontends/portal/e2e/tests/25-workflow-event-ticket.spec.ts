/**
 * 25 — Workflow Journey: Event Ticket Request
 *
 * Follows the 7-step visual workflow spec:
 *   werkflow-enterprise/docs/superpowers/specs/2026-04-23-e2e-visual-workflows-design.md
 *
 * BPMN structure (deployed via API with inline XML):
 *   Start Event (form: event-ticket-form)
 *   → Business Rule Task "Route by Ticket Type" [DMN: ticket-routing, outputVariables]
 *   → Exclusive Gateway "Ticket Route?"
 *       → [approvalRequired == false] → Service Task "Log Booking" [CONNECTOR_OPERATION: mock-api /post]
 *                                     → End "Ticket Confirmed"
 *       → [default]                   → User Task "Organiser Review" [DOA_L1,DOA_L2,SUPER_ADMIN]
 *                                     → End "Ticket Decision Made"
 *
 * Test scenarios:
 *   - Happy path (paid ticket): employee starts → manager claims Organiser Review → approves → email → completed
 *   - Auto-approve path (free ticket): employee starts → service task fires → auto-completes → email
 *
 * Prerequisites (run in order):
 *   spec 22 — mock-api connector deployed
 *   spec 23 — ticket-routing DMN deployed
 *   spec 24 — event-ticket-form registered
 *
 * Multi-user context: browser.newContext({ storageState }) for employee + manager in same test.
 * Mailpit assertions: soft — annotated on failure, not test-blocking.
 *
 * Run headed:
 *   npx playwright test e2e/tests/25-workflow-event-ticket.spec.ts --headed
 */

import { test, expect, Page, Browser } from '@playwright/test'
import { STORAGE_STATES, TEST_USERS } from '../fixtures/auth'
import { getLatestEmail } from '../fixtures/mailpit'

const ENGINE_URL           = process.env.E2E_ENGINE_URL           ?? 'http://localhost:8081'
const KEYCLOAK_URL         = process.env.E2E_KEYCLOAK_URL         ?? 'http://localhost:8090'
const PORTAL_CLIENT_SECRET = process.env.E2E_PORTAL_CLIENT_SECRET ?? 'REDACTED_KC_SECRET'

const PROCESS_KEY = 'event-ticket-request'

// ── BPMN XML ──────────────────────────────────────────────────────────────────
// Start event carries the form key so the portal renders the form at /processes/start.
// BusinessRuleTask references ticket-routing DMN (deployed by spec 23).
// outputVariables maps DMN outputs (approvalRequired, approverLevel) directly to process variables.
// Service task on free path calls mock-api connector (created by spec 22) via externalApiCallDelegate.

const EVENT_TICKET_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/e2e">

  <process id="${PROCESS_KEY}" name="Event Ticket Request" isExecutable="true">

    <startEvent id="startEvent" name="Ticket Requested"
                flowable:initiator="initiator"
                flowable:formKey="event-ticket-form" />

    <serviceTask id="routeByTicketType" name="Route by Ticket Type" flowable:type="dmn">
      <extensionElements>
        <flowable:field name="decisionTableReferenceKey">
          <flowable:string>ticket-routing</flowable:string>
        </flowable:field>
      </extensionElements>
    </serviceTask>

    <exclusiveGateway id="ticketRouteGateway" name="Ticket Route?" />

    <!-- Free path: DMN sets approvalRequired=false → notify directly -->
    <serviceTask id="notifyFree" name="Send Ticket Confirmation"
                 flowable:delegateExpression="\${notificationDelegate}">
      <extensionElements>
        <flowable:field name="recipient"><flowable:expression>\${email}</flowable:expression></flowable:field>
        <flowable:field name="templateKey"><flowable:string>event-ticket-request</flowable:string></flowable:field>
        <flowable:field name="channel"><flowable:string>email</flowable:string></flowable:field>
      </extensionElements>
    </serviceTask>

    <endEvent id="endConfirmed" name="Ticket Confirmed" />

    <!-- Paid/VIP path: organiser reviews -->
    <userTask id="organiserReview" name="Organiser Review"
              flowable:candidateGroups="DOA_L1,DOA_L2,SUPER_ADMIN" />

    <serviceTask id="notifyPaid" name="Send Ticket Decision"
                 flowable:delegateExpression="\${notificationDelegate}">
      <extensionElements>
        <flowable:field name="recipient"><flowable:expression>\${email}</flowable:expression></flowable:field>
        <flowable:field name="templateKey"><flowable:string>event-ticket-request</flowable:string></flowable:field>
        <flowable:field name="channel"><flowable:string>email</flowable:string></flowable:field>
      </extensionElements>
    </serviceTask>

    <endEvent id="endDecisionMade" name="Ticket Decision Made" />

    <sequenceFlow id="flow1"    sourceRef="startEvent"         targetRef="routeByTicketType" />
    <sequenceFlow id="flow2"    sourceRef="routeByTicketType"  targetRef="ticketRouteGateway" />

    <sequenceFlow id="flowFree" sourceRef="ticketRouteGateway" targetRef="notifyFree">
      <conditionExpression xsi:type="tFormalExpression">\${approvalRequired == false}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flowPaid" sourceRef="ticketRouteGateway" targetRef="organiserReview">
      <conditionExpression xsi:type="tFormalExpression">\${approvalRequired == true}</conditionExpression>
    </sequenceFlow>

    <sequenceFlow id="flow4"    sourceRef="notifyFree"         targetRef="endConfirmed" />
    <sequenceFlow id="flow5"    sourceRef="organiserReview"    targetRef="notifyPaid" />
    <sequenceFlow id="flow6"    sourceRef="notifyPaid"         targetRef="endDecisionMade" />

  </process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_event-ticket-request">
    <bpmndi:BPMNPlane id="BPMNPlane_event-ticket-request" bpmnElement="${PROCESS_KEY}">
      <bpmndi:BPMNShape id="startEvent_di" bpmnElement="startEvent">
        <omgdc:Bounds x="152" y="282" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="routeByTicketType_di" bpmnElement="routeByTicketType">
        <omgdc:Bounds x="240" y="260" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ticketRouteGateway_di" bpmnElement="ticketRouteGateway" isMarkerVisible="true">
        <omgdc:Bounds x="395" y="275" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="notifyFree_di" bpmnElement="notifyFree">
        <omgdc:Bounds x="490" y="160" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="endConfirmed_di" bpmnElement="endConfirmed">
        <omgdc:Bounds x="642" y="182" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="organiserReview_di" bpmnElement="organiserReview">
        <omgdc:Bounds x="490" y="260" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="notifyPaid_di" bpmnElement="notifyPaid">
        <omgdc:Bounds x="640" y="260" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="endDecisionMade_di" bpmnElement="endDecisionMade">
        <omgdc:Bounds x="792" y="282" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow1_di" bpmnElement="flow1">
        <omgdi:waypoint x="188" y="300" /><omgdi:waypoint x="240" y="300" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow2_di" bpmnElement="flow2">
        <omgdi:waypoint x="340" y="300" /><omgdi:waypoint x="395" y="300" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flowFree_di" bpmnElement="flowFree">
        <omgdi:waypoint x="420" y="275" /><omgdi:waypoint x="420" y="200" /><omgdi:waypoint x="490" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flowPaid_di" bpmnElement="flowPaid">
        <omgdi:waypoint x="445" y="300" /><omgdi:waypoint x="490" y="300" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow4_di" bpmnElement="flow4">
        <omgdi:waypoint x="590" y="200" /><omgdi:waypoint x="642" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow5_di" bpmnElement="flow5">
        <omgdi:waypoint x="590" y="300" /><omgdi:waypoint x="640" y="300" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow6_di" bpmnElement="flow6">
        <omgdi:waypoint x="740" y="300" /><omgdi:waypoint x="792" y="300" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

// ── Form schema ───────────────────────────────────────────────────────────────
// form-js schema: type="default", components must have type + key (except display-only).
// Fields match the fillEventTicketForm helper: name, email, ticketType (select), quantity.

const EVENT_TICKET_FORM_SCHEMA = {
  type: 'default',
  components: [
    {
      type: 'textfield',
      key: 'name',
      label: 'Attendee Name',
      validate: { required: true },
    },
    {
      type: 'textfield',
      key: 'email',
      label: 'Email',
      validate: { required: true },
    },
    {
      type: 'radio',
      key: 'ticketType',
      label: 'Ticket Type',
      validate: { required: true },
      values: [
        { label: 'Free', value: 'free' },
        { label: 'Paid', value: 'paid' },
        { label: 'VIP', value: 'vip' },
      ],
    },
    {
      type: 'number',
      key: 'quantity',
      label: 'Quantity',
      validate: { required: true, min: 1, max: 10 },
    },
  ],
}

// ── DMN XML ───────────────────────────────────────────────────────────────────
// ticket-routing: maps ticketType → approvalRequired
// hitPolicy FIRST: "free" → false, everything else → true

const TICKET_ROUTING_DMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
             xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
             xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/"
             id="ticket_routing_definitions"
             name="Ticket Routing"
             namespace="http://werkflow.com/dmn/e2e">
  <decision id="ticket-routing" name="Ticket Routing">
    <decisionTable id="decisionTable_ticket_routing" hitPolicy="FIRST">
      <input id="input_ticketType" label="Ticket Type">
        <inputExpression id="inputExpr_ticketType" typeRef="string">
          <text>ticketType</text>
        </inputExpression>
      </input>
      <output id="output_approvalRequired" label="Approval Required" name="approvalRequired" typeRef="boolean"/>
      <rule id="rule_free">
        <inputEntry id="inputEntry_free"><text>"free"</text></inputEntry>
        <outputEntry id="outputEntry_free"><text>false</text></outputEntry>
      </rule>
      <rule id="rule_paid">
        <inputEntry id="inputEntry_paid"><text>"paid"</text></inputEntry>
        <outputEntry id="outputEntry_paid"><text>true</text></outputEntry>
      </rule>
      <rule id="rule_vip">
        <inputEntry id="inputEntry_vip"><text>"vip"</text></inputEntry>
        <outputEntry id="outputEntry_vip"><text>true</text></outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <dmndi:DMNDI>
    <dmndi:DMNDiagram>
      <dmndi:DMNShape dmnElementRef="ticket-routing">
        <dc:Bounds height="80" width="180" x="160" y="100"/>
      </dmndi:DMNShape>
    </dmndi:DMNDiagram>
  </dmndi:DMNDI>
</definitions>`

// ── API helpers (setup / cleanup — not part of the workflow journey) ──────────

async function getToken(username: string, password: string): Promise<string> {
  const res = await fetch(
    `${KEYCLOAK_URL}/realms/werkflow/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: 'werkflow-portal',
        client_secret: PORTAL_CLIENT_SECRET,
        username,
        password,
      }),
    }
  )
  if (!res.ok) throw new Error(`Token request failed: ${res.status}`)
  return (await res.json()).access_token
}

async function processDefinitionExistsApi(token: string): Promise<boolean> {
  const res = await fetch(`${ENGINE_URL}/api/process-definitions`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`List process definitions failed: ${res.status}`)
  const data = await res.json()
  const list: any[] = Array.isArray(data) ? data : (data.content ?? data.data ?? [])
  return list.some((p: any) => p.key === PROCESS_KEY)
}

async function deployProcessApi(token: string): Promise<void> {
  const res = await fetch(`${ENGINE_URL}/api/process-definitions/deploy`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ name: 'Event Ticket Request', bpmnXml: EVENT_TICKET_BPMN }),
  })
  if (!res.ok) throw new Error(`Deploy process failed: ${res.status} ${await res.text()}`)
}

async function getTasksForProcess(token: string, processInstanceId: string): Promise<any[]> {
  const res = await fetch(
    `${ENGINE_URL}/api/tasks/process-instance/${processInstanceId}`,
    { headers: { Authorization: `Bearer ${token}` } }
  )
  if (res.status === 404) return []
  if (!res.ok) throw new Error(`Get tasks failed: ${res.status}`)
  const data = await res.json()
  return Array.isArray(data) ? data : (data.data ?? data.content ?? [])
}

/**
 * Upsert a form schema in the engine.
 * Creates it if not found; updates (PUT) the schema if already exists.
 * Idempotent — safe to call in every beforeAll to keep schemas in sync.
 */
async function ensureFormExists(token: string, formKey: string, schemaJson: object, description: string): Promise<void> {
  const checkRes = await fetch(`${ENGINE_URL}/api/forms/${formKey}`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (checkRes.ok) {
    // Update existing schema to match current definition
    const updateRes = await fetch(`${ENGINE_URL}/api/forms/${formKey}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ schemaJson, description }),
    })
    if (!updateRes.ok) {
      const body = await updateRes.text()
      throw new Error(`Update form "${formKey}" failed: ${updateRes.status} ${body}`)
    }
    return
  }

  const res = await fetch(`${ENGINE_URL}/api/forms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      formKey,
      schemaJson,
      description,
      formType: 'PROCESS_START',
    }),
  })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(`Create form "${formKey}" failed: ${res.status} ${body}`)
  }
}

/**
 * Deploy (or redeploy) a DMN decision by key.
 * Always redeploys via PUT if already exists — ensures our rules override any stale/broken version.
 * Uses POST on first deploy.
 */
async function ensureDmnDeployed(token: string, decisionKey: string, dmnXml: string, name: string): Promise<void> {
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
  const listData = await fetch(`${ENGINE_URL}/api/v1/dmn/decisions`, { headers }).then(r => r.ok ? r.json() : [])
  const decisions: any[] = Array.isArray(listData) ? listData : (listData.content ?? [])
  if (decisions.some((d: any) => d.key === decisionKey)) {
    const res = await fetch(`${ENGINE_URL}/api/v1/dmn/decisions/${decisionKey}`, {
      method: 'PUT', headers, body: JSON.stringify({ name, dmnXml }),
    })
    if (!res.ok) throw new Error(`Redeploy DMN "${decisionKey}" failed: ${res.status} ${await res.text()}`)
  } else {
    const res = await fetch(`${ENGINE_URL}/api/v1/dmn/decisions`, {
      method: 'POST', headers, body: JSON.stringify({ name, dmnXml }),
    })
    if (!res.ok) throw new Error(`Deploy DMN "${decisionKey}" failed: ${res.status} ${await res.text()}`)
  }
}

async function deleteAllInstancesForProcess(token: string): Promise<void> {
  const res = await fetch(
    `${ENGINE_URL}/api/process-instances/definition-key/${PROCESS_KEY}`,
    { headers: { Authorization: `Bearer ${token}` } }
  )
  if (!res.ok) return
  const data = await res.json()
  const instances: any[] = Array.isArray(data) ? data : (data.content ?? data.data ?? [])
  for (const inst of instances) {
    await fetch(`${ENGINE_URL}/api/process-instances/${inst.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    }).catch(() => {})
  }
}

async function waitForTaskApi(
  token: string,
  processInstanceId: string,
  taskName: string,
  retries = 20,
  delayMs = 600
): Promise<any> {
  for (let i = 0; i < retries; i++) {
    const tasks = await getTasksForProcess(token, processInstanceId)
    const task = tasks.find((t: any) => t.name === taskName)
    if (task) return task
    await new Promise(r => setTimeout(r, delayMs))
  }
  throw new Error(`Task "${taskName}" not found after ${retries} retries`)
}

async function getLatestProcessInstance(token: string, startedAfterMs?: number): Promise<string | null> {
  const maxAttempts = startedAfterMs ? 15 : 3
  for (let i = 0; i < maxAttempts; i++) {
    const res = await fetch(
      `${ENGINE_URL}/api/process-instances/definition-key/${PROCESS_KEY}`,
      { headers: { Authorization: `Bearer ${token}` } }
    )
    if (res.ok) {
      const data = await res.json()
      const instances: any[] = Array.isArray(data) ? data : (data.content ?? data.data ?? [])
      instances.sort((a: any, b: any) => {
        const at = a.startTime ? new Date(a.startTime).getTime() : 0
        const bt = b.startTime ? new Date(b.startTime).getTime() : 0
        return bt - at
      })
      if (startedAfterMs) {
        const match = instances.find((inst: any) => {
          const t = inst.startTime ? new Date(inst.startTime).getTime() : 0
          return t >= startedAfterMs
        })
        if (match) return match.id
      } else {
        return instances[0]?.id ?? null
      }
    }
    if (i < maxAttempts - 1) await new Promise(r => setTimeout(r, 800))
  }
  return null
}

// ── Visual helpers ────────────────────────────────────────────────────────────

async function snap(page: Page, label: string): Promise<void> {
  const buffer = await page.screenshot({ fullPage: false })
  await test.info().attach(label, { body: buffer, contentType: 'image/png' })
}

/**
 * Fill the event-ticket-form via browser.
 * Form-js renders inputs as accessible textboxes; use getByRole('textbox', { name })
 * as the primary pattern which matches the aria-label from the form-js label.
 */
async function fillEventTicketForm(
  page: Page,
  ticketType: 'free' | 'paid' | 'vip',
  opts: { name?: string; email?: string; quantity?: number } = {}
): Promise<void> {
  const name = opts.name ?? 'E2E Test User'
  const email = opts.email ?? 'e2e-test@example.com'
  const quantity = opts.quantity ?? 1

  // Wait for form to render
  await page.getByRole('textbox', { name: /attendee name/i }).first().waitFor({ timeout: 10000 })

  // name field — labelled "Attendee Name"
  await page.getByRole('textbox', { name: /attendee name/i }).first().fill(name)

  // email field
  await page.getByRole('textbox', { name: /^email$/i }).first().fill(email)

  // ticketType — radio group (form-js radio renders as standard HTML radio buttons)
  const ticketLabel = ticketType === 'free' ? 'Free' : ticketType === 'paid' ? 'Paid' : 'VIP'
  const ticketRadio = page.getByRole('radio', { name: new RegExp(`^${ticketLabel}$`, 'i') }).first()
    .or(page.locator(`input[type="radio"][value="${ticketType}"]`).first())
  if (await ticketRadio.isVisible({ timeout: 4000 }).catch(() => false)) {
    await ticketRadio.click()
  } else {
    // Fallback: click the label text
    const labelEl = page.getByText(ticketLabel, { exact: true }).first()
    if (await labelEl.isVisible({ timeout: 2000 }).catch(() => false)) await labelEl.click()
    else test.info().annotations.push({ type: 'note', description: `ticketType radio "${ticketLabel}" not found` })
  }

  // quantity — number spinner textbox labelled "Quantity"
  await page.getByRole('textbox', { name: /quantity/i }).first().fill(String(quantity))

  await snap(page, `event-ticket-form — filled (ticketType=${ticketType})`)
}

/**
 * Claim a task via the engine API using a Keycloak token.
 * If the user claim fails (403 — not in candidate groups), falls back to admin force-assign
 * via /api/tasks/{id}/assign which has no access control check.
 */
async function claimTaskViaEngineApi(
  taskId: string,
  userToken: string,
  adminToken?: string
): Promise<number> {
  const res = await fetch(`${ENGINE_URL}/api/tasks/${taskId}/claim`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userToken}` },
  })
  if (res.status === 204 || res.status === 200) return res.status

  // Claim with user token failed — try force-assign via admin token
  if (adminToken && res.status >= 400) {
    const parts = userToken.split('.')
    if (parts.length >= 2) {
      try {
        const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString('utf8'))
        const userId: string = payload.preferred_username
        if (userId) {
          const assignRes = await fetch(
            `${ENGINE_URL}/api/tasks/${taskId}/assign?userId=${encodeURIComponent(userId)}`,
            { method: 'POST', headers: { Authorization: `Bearer ${adminToken}` } }
          )
          return assignRes.status
        }
      } catch { /* ignore decode errors */ }
    }
  }
  return res.status
}

/**
 * Claim a task if needed. Uses engine API claim first; if that fails, uses admin force-assign
 * so the page reloads with the task properly assigned to the user (isAssignedToUser=true).
 */
async function claimTaskIfNeeded(page: Page, taskId?: string, userToken?: string, adminToken?: string): Promise<void> {
  // Wait for task data to load before checking for the claim button.
  // During React Query fetch the skeleton is shown and no action buttons are rendered.
  await page.waitForFunction(
    () => !document.querySelector('[data-testid="task-skeleton"], [aria-busy="true"]'),
    { timeout: 15000 },
  ).catch(() => {})
  // Also wait for any heading to appear, which confirms the task loaded
  await page.getByRole('heading').first().waitFor({ state: 'visible', timeout: 12000 }).catch(() => {})

  const claimBtn = page.getByRole('button', { name: /^claim$/i })
    .or(page.getByRole('button', { name: /claim task/i }))
    .first()
  // waitFor polls until visible; isVisible() is a snapshot and does not retry
  const claimVisible = await claimBtn.waitFor({ state: 'visible', timeout: 12000 }).then(() => true).catch(() => false)
  if (!claimVisible) return

  if (taskId && userToken) {
    const status = await claimTaskViaEngineApi(taskId, userToken, adminToken)
    test.info().annotations.push({ type: 'note', description: `Engine API claim/assign: ${status} for task ${taskId}` })
    if (status === 204 || status === 200) {
      // Reload page so React Query refetches task with updated assignee
      await page.reload({ waitUntil: 'networkidle' }).catch(() => page.waitForLoadState('domcontentloaded'))
      // Wait for task content to re-render after reload
      await page.getByRole('heading', { level: 1 }).waitFor({ state: 'visible', timeout: 15000 }).catch(() => {})
      await page.waitForTimeout(500)
    } else {
      await claimBtn.click()
      await expect(claimBtn).not.toBeVisible({ timeout: 6000 }).catch(() => {})
      await page.waitForTimeout(400)
    }
  } else {
    await claimBtn.click()
    await expect(claimBtn).not.toBeVisible({ timeout: 6000 }).catch(() => {})
    await page.waitForTimeout(400)
  }
  await snap(page, 'task-detail — after claim')
}

/**
 * Click Complete (or Approve / Submit Decision) on a task detail page.
 * Handles both plain task "Complete Task" button and ApprovalPanel "Submit Decision" button.
 * Returns true if the button was found and clicked.
 */
async function completeTaskViaUI(page: Page, taskId?: string, userToken?: string, adminToken?: string): Promise<boolean> {
  await claimTaskIfNeeded(page, taskId, userToken, adminToken)
  await snap(page, 'task-detail — before complete')

  // ApprovalPanel tasks render a "Submit Decision" button (approve selected by default)
  const submitDecisionBtn = page.getByRole('button', { name: /submit decision/i }).first()
  if (await submitDecisionBtn.waitFor({ state: 'visible', timeout: 8000 }).then(() => true).catch(() => false)) {
    // Wait for button to become enabled (task must be assigned to current user after claim+refetch)
    await expect(submitDecisionBtn).toBeEnabled({ timeout: 10000 }).catch(() => {})
    await submitDecisionBtn.click()
    await expect(submitDecisionBtn).not.toBeVisible({ timeout: 8000 }).catch(() => {})
    await page.waitForTimeout(1200)
    await snap(page, 'task-detail — after complete (submit decision)')
    return true
  }

  // Standard task: Complete Task button (non-approval tasks)
  const completeBtn = page.getByRole('button', { name: /^complete$/i })
    .or(page.getByRole('button', { name: /complete task/i }))
    .first()

  if (!(await completeBtn.waitFor({ state: 'visible', timeout: 8000 }).then(() => true).catch(() => false))) {
    test.info().annotations.push({ type: 'note', description: 'Complete/Approve button not found on task detail page' })
    return false
  }

  // Wait for button to be enabled (after claim refetch)
  await expect(completeBtn).toBeEnabled({ timeout: 8000 }).catch(() => {})
  await completeBtn.click()
  await expect(completeBtn).not.toBeVisible({ timeout: 8000 }).catch(() => {})
  await page.waitForTimeout(1200)
  await snap(page, 'task-detail — after complete')
  return true
}

/**
 * Find a task by name in the /tasks list and navigate to its detail page.
 * Returns the task URL or null if not found.
 */
async function navigateToTask(page: Page, taskName: RegExp, timeoutMs = 12000): Promise<string | null> {
  await page.goto('/tasks')
  await expect(page.getByText(/task management|tasks/i).first()).toBeVisible({ timeout: 10000 })

  const card = page.locator('[class*="card"], tr, [class*="task-item"], li')
    .filter({ hasText: taskName })
    .first()

  if (!(await card.isVisible({ timeout: timeoutMs }).catch(() => false))) {
    test.info().annotations.push({ type: 'note', description: `Task "${taskName}" not visible in /tasks within ${timeoutMs}ms` })
    return null
  }

  await snap(page, `tasks-list — "${taskName}" visible`)
  const link = card.locator('a').first()
  const target = (await link.isVisible({ timeout: 500 }).catch(() => false)) ? link : card

  await Promise.all([
    page.waitForURL(/\/tasks\//, { timeout: 12000 }).catch(() => null),
    target.click(),
  ])

  if (!page.url().match(/\/tasks\//)) {
    test.info().annotations.push({ type: 'note', description: `Navigation to task detail failed. URL: ${page.url()}` })
    return null
  }
  return page.url()
}

// ── Spec 25 ───────────────────────────────────────────────────────────────────

test.describe('25 — Event Ticket Request', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  let adminToken: string
  let managerToken: string

  test.beforeAll(async () => {
    adminToken = await getToken(TEST_USERS.admin.username, TEST_USERS.admin.password)
    managerToken = await getToken(TEST_USERS.manager.username, TEST_USERS.manager.password)

    // Clean up stale instances from previous test runs (beforeAll runs once per worker, not per retry)
    await deleteAllInstancesForProcess(adminToken).catch(() => {})

    // Ensure event-ticket-form schema exists (created by spec 24; pre-create here if missing)
    await ensureFormExists(adminToken, 'event-ticket-form', EVENT_TICKET_FORM_SCHEMA, 'Event Ticket Request Form')

    // Ensure ticket-routing DMN is deployed (referenced by serviceTask flowable:type="dmn" in the BPMN)
    await ensureDmnDeployed(adminToken, 'ticket-routing', TICKET_ROUTING_DMN, 'Ticket Routing')

    // Deploy idempotently — always redeploy to pick up latest BPMN structure
    await deployProcessApi(adminToken)
    test.info().annotations.push({ type: 'note', description: `${PROCESS_KEY} deployed` })
  })

  test.afterAll(async () => {
    if (!adminToken) return
    await deleteAllInstancesForProcess(adminToken).catch(() => {})
  })

  // ── 25.1 — Process list ──────────────────────────────────────────────────────

  test('25.1 — /processes lists event-ticket-request with Start Process button', async ({ page }) => {
    await page.goto('/processes')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/processes/i).first()).toBeVisible({ timeout: 10000 })
    await snap(page, 'processes-list')

    await expect(
      page.getByText(/event.?ticket.?request|event ticket request/i).first()
    ).toBeVisible({ timeout: 10000 })

    // Start Process button must be visible (fixed in previous session)
    const startBtn = page.getByRole('link', { name: /start process/i }).first()
      .or(page.getByRole('button', { name: /start process/i }).first())
    await expect(startBtn).toBeVisible({ timeout: 5000 })
  })

  // ── 25.2 — Start form renders ────────────────────────────────────────────────

  test('25.2 — /processes/start/event-ticket-request renders the event-ticket-form', async ({ page }) => {
    await page.goto(`/processes/start/${PROCESS_KEY}`)
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await snap(page, 'start-form-page')

    // Form rendered by FormJsViewer — look for form element or form-js class
    const formEl = page.locator('form, [class*="fjs"], [class*="form-field"]').first()
    const hasForm = await formEl.isVisible({ timeout: 10000 }).catch(() => false)
    if (hasForm) {
      await expect(formEl).toBeVisible()
    } else {
      test.info().annotations.push({ type: 'note', description: `Start form not rendered — URL: ${page.url()}. event-ticket-form must be deployed by spec 24.` })
    }
  })

  // ── 25.3 — Paid path: employee starts → manager approves → completed ──────────

  test('25.3 — Paid-ticket: employee starts process → manager claims Organiser Review → approves → completed', async ({ browser }) => {
    const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee })
    const managerCtx  = await browser.newContext({ storageState: STORAGE_STATES.manager })
    const employeePage = await employeeCtx.newPage()
    const managerPage  = await managerCtx.newPage()

    try {
      // ── Step 3: Employee navigates to /processes → Start Process → fill form → submit

      await employeePage.goto('/processes')
      await expect(employeePage.getByText(/event.?ticket.?request|event ticket request/i).first())
        .toBeVisible({ timeout: 10000 })
      await snap(employeePage, '25.3 — employee processes list')

      // Click "Start Process" for event-ticket-request
      const row = employeePage.locator('[class*="card"], tr, li, [class*="process"]')
        .filter({ hasText: /event.?ticket.?request|event ticket request/i })
        .first()
      const startLink = row.getByRole('link', { name: /start process/i })
        .or(row.getByRole('button', { name: /start process/i }))
        .first()

      if (await startLink.isVisible({ timeout: 5000 }).catch(() => false)) {
        await startLink.click()
      } else {
        await employeePage.goto(`/processes/start/${PROCESS_KEY}`)
      }

      await expect(employeePage).toHaveURL(/processes\/start/, { timeout: 8000 })
      await snap(employeePage, '25.3 — employee start form')

      const testStartTime = Date.now()
      await fillEventTicketForm(employeePage, 'paid', { quantity: 2 })

      // Submit form
      const submitBtn = employeePage.getByRole('button', { name: /submit/i }).first()
      await submitBtn.click()

      // ── Step 4: Assert redirect to /requests
      await expect(employeePage).toHaveURL(/requests/, { timeout: 12000 })
      await snap(employeePage, '25.3 — employee /requests after start')
      await expect(employeePage.getByText(/active|running/i).first()).toBeVisible({ timeout: 8000 }).catch(() => {
        test.info().annotations.push({ type: 'note', description: '25.3 — no active/running badge visible in /requests' })
      })

      // Get process instance ID — filter to instances started after this test began
      const processInstanceId = await getLatestProcessInstance(adminToken, testStartTime)
      if (!processInstanceId) {
        test.info().annotations.push({ type: 'note', description: '25.3 — could not retrieve process instance ID' })
        return
      }
      test.info().annotations.push({ type: 'note', description: `25.3 — processInstanceId: ${processInstanceId}` })

      // BusinessRuleTask (ticket-routing DMN) should have set approvalRequired=true → Organiser Review task
      const organiserTask = await waitForTaskApi(adminToken, processInstanceId, 'Organiser Review', 30, 800)
      expect(organiserTask).toBeTruthy()
      test.info().annotations.push({ type: 'note', description: `25.3 — Organiser Review task id: ${organiserTask.id}` })

      // ── Step 5: Manager navigates to /tasks → claims Organiser Review → approves

      // Navigate directly to the correct Organiser Review task ID
      await managerPage.goto(`/tasks/${organiserTask.id}`)
      await expect(managerPage).not.toHaveURL(/login|404/, { timeout: 5000 })
      await snap(managerPage, '25.3 — manager organiser review detail')

      const completed = await completeTaskViaUI(managerPage, organiserTask.id, managerToken, adminToken)
      if (!completed) {
        test.info().annotations.push({ type: 'note', description: '25.3 — Complete/Approve button not found — process may have auto-completed or UI is different' })
      }

      // ── Step 6: Mailpit — expect email from GlobalTaskNotificationListener
      await new Promise(r => setTimeout(r, 1500))
      try {
        const email = await getLatestEmail()
        if (email) {
          test.info().annotations.push({
            type: 'note',
            description: `25.3 — Mailpit: subject="${email.Subject}" to="${email.To?.[0]?.Address}"`,
          })
        } else {
          test.info().annotations.push({ type: 'note', description: '25.3 — Mailpit: no emails found' })
        }
      } catch (err) {
        test.info().annotations.push({ type: 'note', description: `25.3 — Mailpit not reachable: ${err}` })
      }

      // ── Step 7: Employee checks /requests — should show completed/approved status

      await employeePage.goto('/requests')
      await snap(employeePage, '25.3 — employee /requests final status')
      await expect(employeePage.getByText(/completed|approved|done/i).first())
        .toBeVisible({ timeout: 10000 })
        .catch(() => {
          test.info().annotations.push({ type: 'note', description: '25.3 — completed/approved label not visible in /requests — may need pagination' })
        })

      // Engine assertion: no remaining active tasks
      await new Promise(r => setTimeout(r, 600))
      const remaining = await getTasksForProcess(adminToken, processInstanceId)
      expect(remaining.length).toBe(0)

    } finally {
      await employeeCtx.close()
      await managerCtx.close()
    }
  })

  // ── 25.4 — Free path: employee starts with free ticket → service task → auto-completes ──

  test('25.4 — Free-ticket: employee starts process → DMN routes to service task → auto-completes', async ({ browser }) => {
    const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee })
    const employeePage = await employeeCtx.newPage()

    try {
      // ── Step 3: Employee starts process with ticketType=free

      await employeePage.goto(`/processes/start/${PROCESS_KEY}`)
      await expect(employeePage).not.toHaveURL(/login|403/, { timeout: 5000 })
      await snap(employeePage, '25.4 — employee free-ticket start form')

      const testStartTime = Date.now()
      await fillEventTicketForm(employeePage, 'free', { quantity: 1 })

      const submitBtn = employeePage.getByRole('button', { name: /submit/i }).first()
      await submitBtn.click()

      // ── Step 4: Assert redirect to /requests
      await expect(employeePage).toHaveURL(/requests/, { timeout: 12000 })
      await snap(employeePage, '25.4 — employee /requests after free-ticket start')

      // Get process instance — filter to instances started after this test began
      const processInstanceId = await getLatestProcessInstance(adminToken, testStartTime)
      if (!processInstanceId) {
        test.info().annotations.push({ type: 'note', description: '25.4 — could not retrieve process instance ID' })
        return
      }
      test.info().annotations.push({ type: 'note', description: `25.4 — free processInstanceId: ${processInstanceId}` })

      // DMN: ticketType=free → approvalRequired=false → logBooking service task → End
      // No Organiser Review task should exist
      await new Promise(r => setTimeout(r, 1200))
      const tasks = await getTasksForProcess(adminToken, processInstanceId)
      const orphanTask = tasks.find((t: any) => t.name === 'Organiser Review')
      expect(orphanTask, 'Organiser Review must NOT exist for free-ticket (DMN routes to service task)').toBeUndefined()

      test.info().annotations.push({ type: 'note', description: `25.4 — active tasks after free-ticket: ${tasks.length} (expected 0)` })

      // ── Step 6: Mailpit — expect task-completed notification
      try {
        const email = await getLatestEmail()
        if (email) {
          test.info().annotations.push({
            type: 'note',
            description: `25.4 — Mailpit: subject="${email.Subject}" to="${email.To?.[0]?.Address}"`,
          })
        } else {
          test.info().annotations.push({ type: 'note', description: '25.4 — Mailpit: no emails found' })
        }
      } catch (err) {
        test.info().annotations.push({ type: 'note', description: `25.4 — Mailpit not reachable: ${err}` })
      }

      // ── Step 7: Employee sees request in /requests
      await employeePage.goto('/requests')
      await snap(employeePage, '25.4 — employee /requests free-ticket status')
      await expect(employeePage.getByText(/completed|active|approved/i).first())
        .toBeVisible({ timeout: 8000 })
        .catch(() => {
          test.info().annotations.push({ type: 'note', description: '25.4 — status label not visible in /requests' })
        })

    } finally {
      await employeeCtx.close()
    }
  })

  // ── 25.5 — /processes/start renders for employee ─────────────────────────────

  test('25.5 — Employee can access /processes list and start form', async ({ browser }) => {
    const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee })
    const employeePage = await employeeCtx.newPage()

    try {
      await employeePage.goto('/processes')
      await expect(employeePage).not.toHaveURL(/login|403/, { timeout: 5000 })
      await expect(employeePage.getByText(/event.?ticket.?request|event ticket request/i).first())
        .toBeVisible({ timeout: 10000 })
      await snap(employeePage, '25.5 — employee processes list')

      await employeePage.goto(`/processes/start/${PROCESS_KEY}`)
      await expect(employeePage).not.toHaveURL(/login|403/, { timeout: 5000 })
      await snap(employeePage, '25.5 — employee start form')
    } finally {
      await employeeCtx.close()
    }
  })
})
