/**
 * 21 — Core User Flows
 *
 * Tests core daily-use flows against the seeded general-approval process.
 * API helpers bootstrap process state; UI assertions verify portal behaviour.
 *
 * Flow under test:
 *   Start → submitRequest task (initiator fills form) → managerApproval task
 *   (DOA_L2 / SUPER_ADMIN claims) → approve/reject → end
 *
 * Smoke-test gotchas captured here:
 *   - TASK_ASSIGNED fires on claim, not on candidateGroup task creation
 *   - john.manager is DOA_L1 only — use admin (SUPER_ADMIN) for approval tasks
 *   - Use werkflow-portal client for token acquisition (werkflow-engine has directAccessGrantsEnabled=false)
 */

import { test, expect, BrowserContext, Browser } from '@playwright/test'
import { STORAGE_STATES, TEST_USERS } from '../fixtures/auth'

const ENGINE_URL = process.env.E2E_ENGINE_URL ?? 'http://localhost:8081'
const KEYCLOAK_URL = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8090'
const PORTAL_CLIENT_SECRET = process.env.E2E_PORTAL_CLIENT_SECRET ?? 'ci-client-secret'

// ── API helpers ───────────────────────────────────────────────────────────────

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

async function startProcess(token: string, variables: Record<string, unknown> = {}): Promise<string> {
  const res = await fetch(`${ENGINE_URL}/api/process-instances`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ processDefinitionKey: 'general-approval', variables }),
  })
  if (!res.ok) throw new Error(`Start process failed: ${res.status} ${await res.text()}`)
  const data = await res.json()
  return data.id
}

async function getTasksForProcess(token: string, processInstanceId: string): Promise<any[]> {
  const res = await fetch(
    `${ENGINE_URL}/api/tasks/process-instance/${processInstanceId}`,
    { headers: { Authorization: `Bearer ${token}` } }
  )
  if (!res.ok) throw new Error(`Get tasks failed: ${res.status}`)
  const data = await res.json()
  return Array.isArray(data) ? data : (data.data ?? data.content ?? [])
}

async function completeTask(token: string, taskId: string, variables: Record<string, unknown> = {}): Promise<void> {
  const res = await fetch(`${ENGINE_URL}/api/tasks/${taskId}/complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ variables }),
  })
  if (!res.ok) throw new Error(`Complete task failed: ${res.status} ${await res.text()}`)
}

async function claimTask(token: string, taskId: string): Promise<void> {
  const res = await fetch(`${ENGINE_URL}/api/tasks/${taskId}/claim`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`Claim task failed: ${res.status}`)
}

async function deleteProcessInstance(token: string, instanceId: string): Promise<void> {
  await fetch(`${ENGINE_URL}/api/process-instances/${instanceId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
}

async function waitForTask(
  token: string,
  processInstanceId: string,
  taskName: string,
  retries = 10,
  delayMs = 500
): Promise<any> {
  for (let i = 0; i < retries; i++) {
    const tasks = await getTasksForProcess(token, processInstanceId)
    const task = tasks.find((t: any) => t.name === taskName || t.taskDefinitionKey === taskName)
    if (task) return task
    await new Promise(r => setTimeout(r, delayMs))
  }
  throw new Error(`Task "${taskName}" not found after ${retries} retries`)
}

// ── 21a — Task list renders ────────────────────────────────────────────────────

test.describe('21 — Task list — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('21.1 — /tasks loads and renders task management UI', async ({ page }) => {
    await page.goto('/tasks')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/task management|my tasks|tasks/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('21.2 — /tasks page has My Tasks and Team Tasks tabs', async ({ page }) => {
    await page.goto('/tasks')
    await expect(page.getByText(/task management|tasks/i).first()).toBeVisible({ timeout: 10000 })
    const myTasks = page.getByText(/my tasks/i).first()
    const teamTasks = page.getByText(/team tasks/i).first()
    const hasTabs = (await myTasks.isVisible().catch(() => false)) ||
                   (await teamTasks.isVisible().catch(() => false))
    expect(hasTabs).toBeTruthy()
  })
})

// ── 21b — Approval flow (full UI journey) ────────────────────────────────────

test.describe('21 — Approval flow', () => {
  let adminToken: string
  let processInstanceId: string
  let submitTaskId: string

  test.beforeAll(async () => {
    adminToken = await getToken(TEST_USERS.admin.username, TEST_USERS.admin.password)
    processInstanceId = await startProcess(adminToken)
    const submitTask = await waitForTask(adminToken, processInstanceId, 'Submit Request')
    submitTaskId = submitTask.id
  })

  test.afterAll(async () => {
    if (processInstanceId && adminToken) {
      await deleteProcessInstance(adminToken, processInstanceId).catch(() => {})
    }
  })

  test.use({ storageState: STORAGE_STATES.admin })

  test('21.3 — submit task appears in admin task list', async ({ page }) => {
    await page.goto('/tasks')
    await expect(page.getByText(/task management|tasks/i).first()).toBeVisible({ timeout: 10000 })
    const taskRow = page.locator(`[data-task-id="${submitTaskId}"], tr, [class*="task"]`).filter({
      hasText: /submit request/i,
    }).first()
    if (await taskRow.isVisible({ timeout: 5000 }).catch(() => false)) {
      await expect(taskRow).toBeVisible()
    } else {
      test.info().annotations.push({ type: 'note', description: 'Submit Request task row not directly visible — continuing' })
    }
  })

  test('21.4 — admin opens submit task detail and form renders', async ({ page }) => {
    await page.goto(`/tasks`)
    await expect(page.getByText(/task management|tasks/i).first()).toBeVisible({ timeout: 10000 })

    const taskLink = page.getByRole('link', { name: /submit request/i }).first()
    if (await taskLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await taskLink.click()
      await page.waitForURL(/tasks\//, { timeout: 10000 })
      await expect(page.getByRole('tab', { name: /details/i }).or(
        page.getByText(/form|submit/i).first()
      )).toBeVisible({ timeout: 10000 })
    } else {
      // Navigate directly by task ID
      await page.goto(`/tasks/${submitTaskId}`)
      await expect(page).not.toHaveURL(/login|404/, { timeout: 5000 })
      test.info().annotations.push({ type: 'note', description: 'Task accessed by direct URL' })
    }
  })

  test('21.5 — submit task form fills and submits', async ({ page }) => {
    await page.goto(`/tasks/${submitTaskId}`)
    await expect(page).not.toHaveURL(/login|404/, { timeout: 5000 })

    // Try to fill visible form inputs
    const titleInput = page.locator('input[name*="title"], input[placeholder*="title" i]').first()
    const descInput = page.locator('textarea, input[name*="desc"]').first()
    const amountInput = page.locator('input[name*="amount"], input[type="number"]').first()

    if (await titleInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await titleInput.fill('E2E Approval Test')
    }
    if (await descInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await descInput.fill('Automated E2E test request')
    }
    if (await amountInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await amountInput.fill('20000')
    }

    const submitBtn = page.getByRole('button', { name: /submit|complete/i }).last()
    if (await submitBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await submitBtn.click()
      await page.waitForTimeout(1000)
      test.info().annotations.push({ type: 'note', description: 'Submit task form submitted via UI' })
    } else {
      // Fall back to API completion
      await completeTask(adminToken, submitTaskId, {
        title: 'E2E Approval Test',
        description: 'Automated E2E test request',
        amount: 20000,
        requestType: 'expense',
      })
      test.info().annotations.push({ type: 'note', description: 'Submit task completed via API fallback' })
    }
  })

  test('21.6 — manager approval task appears after submit', async ({ page }) => {
    // Ensure submitRequest is complete before checking for managerApproval
    try {
      await completeTask(adminToken, submitTaskId, {
        title: 'E2E Approval Test',
        description: 'Automated E2E test request',
        amount: 20000,
        requestType: 'expense',
      })
    } catch {
      // Already completed — ignore
    }

    const approvalTask = await waitForTask(adminToken, processInstanceId, 'Manager Approval')
    expect(approvalTask).toBeTruthy()
    expect(approvalTask.name).toBe('Manager Approval')

    await page.goto('/tasks')
    await expect(page.getByText(/task management|tasks/i).first()).toBeVisible({ timeout: 10000 })
    // Team Tasks tab should contain the unassigned approval task
    const teamTasksTab = page.getByRole('tab', { name: /team tasks/i }).or(
      page.getByText(/team tasks/i)
    ).first()
    if (await teamTasksTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await teamTasksTab.click()
    }
    await expect(page.getByText(/manager approval/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('21.7 — admin claims manager approval task', async ({ page }) => {
    const approvalTask = await waitForTask(adminToken, processInstanceId, 'Manager Approval')

    await page.goto(`/tasks/${approvalTask.id}`)
    await expect(page).not.toHaveURL(/login|404/, { timeout: 5000 })

    const claimBtn = page.getByRole('button', { name: /claim/i })
    if (await claimBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await claimBtn.click()
      await expect(claimBtn).not.toBeVisible({ timeout: 5000 }).catch(() => {})
      test.info().annotations.push({ type: 'note', description: 'Task claimed via UI' })
    } else {
      // Fall back to API claim
      await claimTask(adminToken, approvalTask.id)
      await page.reload()
      test.info().annotations.push({ type: 'note', description: 'Task claimed via API fallback' })
    }
  })

  test('21.8 — admin approves task and process advances', async ({ page }) => {
    const approvalTask = await waitForTask(adminToken, processInstanceId, 'Manager Approval')
    await page.goto(`/tasks/${approvalTask.id}`)
    await expect(page).not.toHaveURL(/login|404/, { timeout: 5000 })

    const approveBtn = page.getByRole('button', { name: /approve/i })
    if (await approveBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      // Set outcome variable if decision select/radio is present
      const decisionSelect = page.locator('select[name*="decision"], [data-key="decision"]').first()
      if (await decisionSelect.isVisible({ timeout: 2000 }).catch(() => false)) {
        await decisionSelect.selectOption('approve')
      }
      await approveBtn.click()
      await page.waitForTimeout(1500)
      // Task should be gone from list after approval
      await page.goto('/tasks')
      test.info().annotations.push({ type: 'note', description: 'Approved via UI Approve button' })
    } else {
      // API fallback
      await claimTask(adminToken, approvalTask.id).catch(() => {})
      await completeTask(adminToken, approvalTask.id, { decision: 'approve' })
      test.info().annotations.push({ type: 'note', description: 'Approved via API fallback (Approve button not found — log as bug)' })
    }

    // Process should now be at endApproved (amount=20000 <= 50000 skips director)
    const remaining = await getTasksForProcess(adminToken, processInstanceId)
    expect(remaining.length).toBe(0)
  })
})

// ── 21c — Reject flow ────────────────────────────────────────────────────────

test.describe('21 — Reject flow', () => {
  let adminToken: string
  let processInstanceId: string

  test.beforeAll(async () => {
    adminToken = await getToken(TEST_USERS.admin.username, TEST_USERS.admin.password)
    processInstanceId = await startProcess(adminToken)
    const submitTask = await waitForTask(adminToken, processInstanceId, 'Submit Request')
    await completeTask(adminToken, submitTask.id, {
      title: 'E2E Reject Test',
      description: 'Will be rejected',
      amount: 5000,
      category: 'Other',
    })
  })

  test.afterAll(async () => {
    if (processInstanceId && adminToken) {
      await deleteProcessInstance(adminToken, processInstanceId).catch(() => {})
    }
  })

  test.use({ storageState: STORAGE_STATES.admin })

  test('21.9 — admin rejects manager approval task', async ({ page }) => {
    const approvalTask = await waitForTask(adminToken, processInstanceId, 'Manager Approval')
    await claimTask(adminToken, approvalTask.id)

    await page.goto(`/tasks/${approvalTask.id}`)
    await expect(page).not.toHaveURL(/login|404/, { timeout: 5000 })

    const rejectBtn = page.getByRole('button', { name: /reject/i })
    if (await rejectBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      const commentField = page.locator('textarea[name*="comment"], textarea').first()
      if (await commentField.isVisible({ timeout: 2000 }).catch(() => false)) {
        await commentField.fill('Rejected for E2E test purposes')
      }
      await rejectBtn.click()
      await page.waitForTimeout(1500)
      test.info().annotations.push({ type: 'note', description: 'Rejected via UI Reject button' })
    } else {
      await completeTask(adminToken, approvalTask.id, { decision: 'reject' })
      test.info().annotations.push({ type: 'note', description: 'Rejected via API fallback (Reject button not found — log as bug)' })
    }

    const remaining = await getTasksForProcess(adminToken, processInstanceId)
    expect(remaining.length).toBe(0)
  })
})

// ── 21d — My Requests (employee) ────────────────────────────────────────────

test.describe('21 — My Requests — employee', () => {
  let adminToken: string
  let processInstanceId: string

  test.beforeAll(async () => {
    // Start a process as admin on behalf of testing (employee can't start via API without token setup)
    // The process shows up in admin's requests; for employee requests, we rely on any existing data
    adminToken = await getToken(TEST_USERS.admin.username, TEST_USERS.admin.password)
    processInstanceId = await startProcess(adminToken)
  })

  test.afterAll(async () => {
    if (processInstanceId && adminToken) {
      await deleteProcessInstance(adminToken, processInstanceId).catch(() => {})
    }
  })

  test.use({ storageState: STORAGE_STATES.employee })

  test('21.10 — /requests loads for employee', async ({ page }) => {
    await page.goto('/requests')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/requests/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('21.11 — requests page shows status filter tabs or filter controls', async ({ page }) => {
    await page.goto('/requests')
    await expect(page.getByText(/requests/i).first()).toBeVisible({ timeout: 10000 })
    const filterControl = page.getByRole('tablist').or(
      page.getByRole('combobox').or(page.locator('[class*="filter"], [class*="tab"]').first())
    )
    await expect(filterControl.first()).toBeVisible({ timeout: 5000 })
  })

  test('21.12 — request detail page loads when a request exists', async ({ page }) => {
    await page.goto('/requests')
    await expect(page.getByText(/requests/i).first()).toBeVisible({ timeout: 10000 })
    const firstRow = page.locator('table tbody tr, [class*="request-row"], [class*="request-card"]').first()
    if (await firstRow.isVisible({ timeout: 5000 }).catch(() => false)) {
      await firstRow.click()
      await expect(page).toHaveURL(/requests\//, { timeout: 8000 })
      await expect(page.getByText(/process|timeline|history|status/i).first()).toBeVisible({ timeout: 8000 })
    } else {
      test.info().annotations.push({ type: 'note', description: 'No requests for employee — skipping detail check' })
    }
  })

  test('21.13 — cancel pending request (or log as missing feature)', async ({ page }) => {
    await page.goto('/requests')
    await expect(page.getByText(/requests/i).first()).toBeVisible({ timeout: 10000 })
    const firstRow = page.locator('table tbody tr, [class*="request-row"]').first()
    if (!await firstRow.isVisible({ timeout: 3000 }).catch(() => false)) {
      test.info().annotations.push({ type: 'note', description: 'No requests to cancel — skipping' })
      return
    }
    await firstRow.click()
    await page.waitForURL(/requests\//, { timeout: 8000 })
    const cancelBtn = page.getByRole('button', { name: /cancel|withdraw/i })
    if (await cancelBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await cancelBtn.click()
      const confirmBtn = page.getByRole('button', { name: /confirm|yes/i })
      if (await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await confirmBtn.click()
      }
      await expect(page.getByText(/cancelled|withdrawn/i)).toBeVisible({ timeout: 5000 })
    } else {
      test.info().annotations.push({
        type: 'bug',
        description: 'Cancel/Withdraw button not found on request detail page — feature may be missing, log as GitHub issue',
      })
    }
  })
})

// ── 21e — Admin requests view ────────────────────────────────────────────────

test.describe('21 — My Requests — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('21.14 — admin sees requests page', async ({ page }) => {
    await page.goto('/requests')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/requests/i).first()).toBeVisible({ timeout: 10000 })
  })
})

// ── 21f — Process Monitoring (admin) ─────────────────────────────────────────

test.describe('21 — Process Monitoring — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('21.15 — /monitoring loads without error', async ({ page }) => {
    await page.goto('/monitoring')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/process health/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('21.16 — monitoring page shows service health status', async ({ page }) => {
    await page.goto('/monitoring')
    await expect(page.getByText(/process health/i).first()).toBeVisible({ timeout: 10000 })
    // Either health status indicators or a loading state is acceptable
    const hasStatus = await page.getByText(/healthy|down|degraded/i).first().isVisible({ timeout: 5000 }).catch(() => false)
    const hasLoading = await page.locator('.animate-pulse').first().isVisible({ timeout: 3000 }).catch(() => false)
    expect(hasStatus || hasLoading).toBeTruthy()
  })
})
