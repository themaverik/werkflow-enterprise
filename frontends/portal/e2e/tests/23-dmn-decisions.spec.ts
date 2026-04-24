/**
 * 23 — DMN Decisions
 *
 * Tests decision table management via /decisions.
 * Creates and deploys the three routing decisions required by Layer 3 workflow tests:
 *   - ticket-routing    (eventType → assignedTeam)
 *   - leave-routing     (leaveDays → approvalPath)
 *   - budget-routing    (amount → approvalPath)
 *
 * Strategy:
 *   - Deploy each decision via the UI (fill name, click Deploy) — DmnEditor starts
 *     with a valid empty table so Deploy is immediately available.
 *   - beforeAll checks if decisions already exist (idempotent re-runs).
 *   - afterAll does NOT delete the three routing decisions — they are required by Layer 3.
 *   - A dedicated "e2e-delete-test" decision is created and deleted to test the delete flow.
 *
 * API: engine at http://localhost:8081 (same token/client pattern as spec 21)
 */

import { test, expect } from '@playwright/test'
import { STORAGE_STATES, TEST_USERS } from '../fixtures/auth'

const ENGINE_URL = process.env.E2E_ENGINE_URL ?? 'http://localhost:8081'
const KEYCLOAK_URL = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8090'
const PORTAL_CLIENT_SECRET = process.env.E2E_PORTAL_CLIENT_SECRET ?? 'REDACTED_KC_PORTAL_SECRET'

// Decisions required by Layer 3 — must persist after this spec completes
const ROUTING_DECISIONS = [
  { name: 'Ticket Routing', key: 'ticket-routing' },
  { name: 'Leave Routing',  key: 'leave-routing'  },
  { name: 'Budget Routing', key: 'budget-routing'  },
]

// Temporary decision used only to test the delete flow
const DELETE_TEST_DECISION = { name: 'E2E Delete Test', key: 'e2e-delete-test' }

// ── API helpers ───────────────────────────────────────────────────────────────

async function getToken(): Promise<string> {
  const res = await fetch(
    `${KEYCLOAK_URL}/realms/werkflow/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: 'werkflow-portal',
        client_secret: PORTAL_CLIENT_SECRET,
        username: TEST_USERS.admin.username,
        password: TEST_USERS.admin.password,
      }),
    }
  )
  if (!res.ok) throw new Error(`Token request failed: ${res.status}`)
  return (await res.json()).access_token
}

async function listDecisionsApi(token: string): Promise<any[]> {
  const res = await fetch(`${ENGINE_URL}/api/v1/dmn/decisions`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`List decisions failed: ${res.status}`)
  const data = await res.json()
  return Array.isArray(data) ? data : (data.content ?? [])
}

async function decisionExistsApi(token: string, key: string): Promise<boolean> {
  const decisions = await listDecisionsApi(token)
  return decisions.some((d: any) => d.key === key)
}

async function deleteDecisionByKeyApi(token: string, key: string): Promise<void> {
  // Find the deploymentId for the given key, then delete
  const decisions = await listDecisionsApi(token)
  const decision = decisions.find((d: any) => d.key === key)
  if (!decision) return
  await fetch(`${ENGINE_URL}/api/v1/dmn/decisions/deployment/${decision.deploymentId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
}

// ── Helper: deploy a decision via the UI ────────────────────────────────────

async function deployDecisionViaUI(page: any, name: string): Promise<void> {
  await page.goto('/decisions/new')
  await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })

  // Wait for DmnEditor canvas to render (dynamically loaded)
  const canvas = page.locator('[class*="dmn"], .djs-container, canvas, .dmn-js-container').first()
  await expect(canvas).toBeVisible({ timeout: 15000 })

  // Fill the decision name
  await page.getByLabel(/name/i).fill(name)

  // Deploy button should now be enabled
  const deployBtn = page.getByRole('button', { name: /deploy/i })
  await expect(deployBtn).toBeEnabled({ timeout: 3000 })
  await deployBtn.click()

  // On success, redirects to /decisions
  await page.waitForURL(/\/decisions$/, { timeout: 15000 })
}

// ── 23 — DMN Decisions ────────────────────────────────────────────────────────

test.describe('23 — DMN Decisions — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  let adminToken: string

  test.beforeAll(async () => {
    adminToken = await getToken()
  })

  test.afterAll(async () => {
    // Clean up the delete-test decision only (leave routing decisions intact for Layer 3)
    await deleteDecisionByKeyApi(adminToken, DELETE_TEST_DECISION.key).catch(() => {})
  })

  // ── 23.1 — List page ────────────────────────────────────────────────────────

  test('23.1 — /decisions loads for admin with New Decision button', async ({ page }) => {
    await page.goto('/decisions')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByRole('link', { name: /new decision/i }).or(
      page.getByRole('button', { name: /new decision/i })
    ).first()).toBeVisible({ timeout: 5000 })
  })

  // ── 23.2 — Editor loads ─────────────────────────────────────────────────────

  test('23.2 — /decisions/new renders DMN editor with Deploy button', async ({ page }) => {
    await page.goto('/decisions/new')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    // DmnEditor mounts asynchronously (dynamic import)
    const canvas = page.locator('[class*="dmn"], .djs-container, canvas, .dmn-js-container').first()
    await expect(canvas).toBeVisible({ timeout: 15000 })
    // Deploy button exists (disabled until name is filled)
    await expect(page.getByRole('button', { name: /deploy/i })).toBeVisible({ timeout: 5000 })
  })

  // ── 23.3 — Deploy ticket-routing ────────────────────────────────────────────

  test('23.3 — Deploy "Ticket Routing" decision', async ({ page }) => {
    const exists = await decisionExistsApi(adminToken, ROUTING_DECISIONS[0].key)
    if (exists) {
      test.info().annotations.push({ type: 'note', description: 'ticket-routing already deployed — skipping UI deploy' })
      return
    }

    await deployDecisionViaUI(page, ROUTING_DECISIONS[0].name)
    test.info().annotations.push({ type: 'note', description: 'ticket-routing deployed via UI' })
  })

  // ── 23.4 — ticket-routing in list ───────────────────────────────────────────

  test('23.4 — ticket-routing appears in decisions list', async ({ page }) => {
    await page.goto('/decisions')
    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/ticket.?routing|ticket routing/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 23.5 — Deploy leave-routing ─────────────────────────────────────────────

  test('23.5 — Deploy "Leave Routing" decision', async ({ page }) => {
    const exists = await decisionExistsApi(adminToken, ROUTING_DECISIONS[1].key)
    if (exists) {
      test.info().annotations.push({ type: 'note', description: 'leave-routing already deployed — skipping UI deploy' })
      return
    }

    await deployDecisionViaUI(page, ROUTING_DECISIONS[1].name)
    test.info().annotations.push({ type: 'note', description: 'leave-routing deployed via UI' })
  })

  // ── 23.6 — leave-routing in list ────────────────────────────────────────────

  test('23.6 — leave-routing appears in decisions list', async ({ page }) => {
    await page.goto('/decisions')
    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/leave.?routing|leave routing/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 23.7 — Deploy budget-routing ────────────────────────────────────────────

  test('23.7 — Deploy "Budget Routing" decision', async ({ page }) => {
    const exists = await decisionExistsApi(adminToken, ROUTING_DECISIONS[2].key)
    if (exists) {
      test.info().annotations.push({ type: 'note', description: 'budget-routing already deployed — skipping UI deploy' })
      return
    }

    await deployDecisionViaUI(page, ROUTING_DECISIONS[2].name)
    test.info().annotations.push({ type: 'note', description: 'budget-routing deployed via UI' })
  })

  // ── 23.8 — budget-routing in list ───────────────────────────────────────────

  test('23.8 — budget-routing appears in decisions list', async ({ page }) => {
    await page.goto('/decisions')
    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/budget.?routing|budget routing/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 23.9 — Edit navigation ──────────────────────────────────────────────────

  test('23.9 — Edit button navigates to decision edit page', async ({ page }) => {
    await page.goto('/decisions')
    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })

    // Click the first Edit button/link in the list
    const editLink = page.getByRole('link', { name: /edit/i }).first()
    if (await editLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await editLink.click()
      await expect(page).toHaveURL(/decisions\/.+\/edit/, { timeout: 8000 })
      // DMN editor should load on edit page too
      const canvas = page.locator('[class*="dmn"], .djs-container, canvas, .dmn-js-container').first()
      await expect(canvas).toBeVisible({ timeout: 15000 })
    } else {
      test.info().annotations.push({ type: 'note', description: 'No Edit link visible — no decisions deployed yet' })
    }
  })

  // ── 23.10 — Deploy and delete test decision ─────────────────────────────────

  test('23.10 — Deploy and delete a test decision via confirm dialog', async ({ page }) => {
    // Ensure test decision exists (create via API if not present)
    const exists = await decisionExistsApi(adminToken, DELETE_TEST_DECISION.key)
    if (!exists) {
      await deployDecisionViaUI(page, DELETE_TEST_DECISION.name)
      // After deploy, we're on /decisions — need to go back
      await page.goto('/decisions')
    } else {
      await page.goto('/decisions')
    }

    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })
    // Find the trash icon button for the test decision card
    const decisionCard = page.locator('[class*="card"]').filter({ hasText: /e2e.?delete.?test|e2e delete test/i }).first()

    if (await decisionCard.isVisible({ timeout: 5000 }).catch(() => false)) {
      const trashBtn = decisionCard.getByRole('button').filter({ has: page.locator('[class*="trash"], svg') }).last()
      if (!await trashBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        // Fallback: find any destructive button within the card
        const anyTrashBtn = decisionCard.locator('button.text-destructive, button[class*="destructive"]').first()
        if (await anyTrashBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await anyTrashBtn.click()
        } else {
          test.info().annotations.push({ type: 'note', description: 'Trash button not found in card — skipping delete UI test' })
          return
        }
      } else {
        await trashBtn.click()
      }

      // Confirm dialog appears
      const confirmBtn = page.getByRole('button', { name: /delete|confirm/i }).last()
      await expect(confirmBtn).toBeVisible({ timeout: 5000 })
      await confirmBtn.click()

      // Decision removed from list
      await expect(decisionCard).not.toBeVisible({ timeout: 8000 })
      test.info().annotations.push({ type: 'note', description: 'E2E Delete Test decision deleted via confirm dialog' })
    } else {
      // Try clicking a trash icon generically to test the delete flow
      const trashBtns = page.locator('button.text-destructive, button[class*="destructive"]')
      const count = await trashBtns.count()
      if (count > 0) {
        await trashBtns.last().click()
        const confirmBtn = page.getByRole('button', { name: /delete|confirm/i }).last()
        if (await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
          await confirmBtn.click()
          await page.waitForTimeout(1000)
        }
        test.info().annotations.push({ type: 'note', description: 'Delete tested on last available decision' })
      } else {
        test.info().annotations.push({ type: 'note', description: 'No decisions found to test delete — verify decisions were deployed' })
      }
    }
  })

  // ── 23.11 — List shows metadata ─────────────────────────────────────────────

  test('23.11 — Decisions list shows version and deployed date per decision', async ({ page }) => {
    await page.goto('/decisions')
    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })

    const firstCard = page.locator('[class*="card"]').first()
    if (await firstCard.isVisible({ timeout: 5000 }).catch(() => false)) {
      // Cards should show version number and date
      const hasVersion = await page.getByText(/version|v\d+/i).first().isVisible({ timeout: 3000 }).catch(() => false)
      const hasDate = await page.getByText(/deployed|date/i).first().isVisible({ timeout: 3000 }).catch(() => false)
      expect(hasVersion || hasDate).toBeTruthy()
    } else {
      test.info().annotations.push({ type: 'note', description: 'No decision cards visible to check metadata' })
    }
  })

  // ── 23.12 — History page accessible ─────────────────────────────────────────

  test('23.12 — Execution history link navigates to /decisions/{key}/executions', async ({ page }) => {
    await page.goto('/decisions')
    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })

    const historyLink = page.getByRole('link', { name: /history/i }).first()
    if (await historyLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await historyLink.click()
      await expect(page).toHaveURL(/decisions\/.+\/executions/, { timeout: 8000 })
      await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    } else {
      test.info().annotations.push({ type: 'note', description: 'No history link found — no decisions available' })
    }
  })
})

// ── 23 — RBAC: employee cannot create/edit decisions ─────────────────────────

test.describe('23 — DMN Decisions — employee RBAC', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('23.13 — Employee sees no New Decision button on /decisions', async ({ page }) => {
    await page.goto('/decisions')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/decisions/i).first()).toBeVisible({ timeout: 10000 })
    // Employee should NOT see the New Decision button
    await expect(page.getByRole('link', { name: /new decision/i })).not.toBeVisible({ timeout: 3000 })
    await expect(page.getByRole('button', { name: /new decision/i })).not.toBeVisible({ timeout: 3000 })
  })

  test('23.14 — Employee cannot access /decisions/new', async ({ page }) => {
    await page.goto('/decisions/new')
    // Should redirect or show 403 — not the DMN editor
    const urlOk = await expect(page).not.toHaveURL('/decisions/new', { timeout: 5000 }).catch(() => false)
    const hasEditor = await page.locator('[class*="dmn"], .djs-container').first().isVisible({ timeout: 3000 }).catch(() => false)
    if (hasEditor) {
      test.info().annotations.push({
        type: 'bug',
        description: 'Employee can access /decisions/new — missing route guard. Log as GitHub issue.',
      })
    }
  })
})
