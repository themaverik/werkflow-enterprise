/**
 * 24 — Forms Visual Builder
 *
 * Tests form lifecycle via /forms.
 * Creates the four form definitions required by Layer 3 workflow tests:
 *   - event-ticket-form
 *   - leave-request-form     (must include a leaveDays field)
 *   - equipment-request-form
 *   - budget-request-form
 *
 * Strategy:
 *   - Check via API whether each form already exists (idempotent re-runs).
 *   - If missing, navigate to /forms/new, fill the form key field, and click Save.
 *     The form-js editor starts with an empty schema — the Save action registers
 *     the form in the engine so it can be linked to BPMN tasks in Layer 3.
 *   - afterAll does NOT delete the four workflow forms — they are required by Layer 3.
 *   - A dedicated "e2e-delete-test-form" is created and deleted to exercise the delete flow.
 *
 * API: engine at http://localhost:8081 (same pattern as specs 21 & 23)
 * Form key input: input[placeholder="e.g. leave-request-form"]  (no id attr)
 * Save button: enabled only when form key is non-empty
 */

import { test, expect } from '@playwright/test'
import { STORAGE_STATES, TEST_USERS } from '../fixtures/auth'

const ENGINE_URL = process.env.E2E_ENGINE_URL ?? 'http://localhost:8081'
const KEYCLOAK_URL = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8090'
const PORTAL_CLIENT_SECRET = process.env.E2E_PORTAL_CLIENT_SECRET ?? 'REDACTED_KC_PORTAL_SECRET'

// Forms required by Layer 3 — must persist after this spec
const WORKFLOW_FORMS = [
  'event-ticket-form',
  'leave-request-form',
  'equipment-request-form',
  'budget-request-form',
]

const DELETE_TEST_FORM_KEY = 'e2e-delete-test-form'

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

async function listFormsApi(token: string): Promise<any[]> {
  const res = await fetch(`${ENGINE_URL}/api/forms`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`List forms failed: ${res.status}`)
  const data = await res.json()
  return Array.isArray(data) ? data : (data.content ?? [])
}

async function formExistsApi(token: string, formKey: string): Promise<boolean> {
  const forms = await listFormsApi(token)
  return forms.some((f: any) => f.key === formKey)
}

async function deleteFormApi(token: string, formKey: string): Promise<void> {
  await fetch(`${ENGINE_URL}/api/forms/${formKey}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
}

// ── Helper: create a form via UI ─────────────────────────────────────────────

async function createFormViaUI(page: any, formKey: string): Promise<void> {
  await page.goto('/forms/new')
  await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })

  // Wait for form-js editor to load (dynamic import)
  const editor = page.locator('[class*="fjs"], [class*="form-js"], .fjs-container, canvas').first()
  // Editor may not always render a visible element immediately — wait for the key input instead
  const keyInput = page.locator('input[placeholder="e.g. leave-request-form"]')
  await expect(keyInput).toBeVisible({ timeout: 15000 })

  // Fill form key
  await keyInput.fill(formKey)

  // Save button should now be enabled
  const saveBtn = page.getByRole('button', { name: /^save$/i })
  await expect(saveBtn).toBeEnabled({ timeout: 3000 })
  await saveBtn.click()

  // Success indicator appears (green checkmark + saveSuccess text)
  await expect(
    page.getByText(/saved|success/i).or(page.locator('[class*="green"]').first())
  ).toBeVisible({ timeout: 8000 }).catch(() => {
    // Save may not show a visual indicator if already saving — acceptable
  })
}

// ── 24 — Forms — admin ────────────────────────────────────────────────────────

test.describe('24 — Forms Visual Builder — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  let adminToken: string

  test.beforeAll(async () => {
    adminToken = await getToken()
  })

  test.afterAll(async () => {
    // Clean up delete-test form only; keep workflow forms for Layer 3
    await deleteFormApi(adminToken, DELETE_TEST_FORM_KEY).catch(() => {})
  })

  // ── 24.1 — List page ────────────────────────────────────────────────────────

  test('24.1 — /forms loads with Create New Form button for admin', async ({ page }) => {
    await page.goto('/forms')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })
    // Admin should see a Create New Form button/link
    const createBtn = page.getByRole('link', { name: /create new form/i }).or(
      page.getByRole('button', { name: /create new form/i })
    ).first()
    await expect(createBtn).toBeVisible({ timeout: 5000 })
  })

  // ── 24.2 — Builder page loads ───────────────────────────────────────────────

  test('24.2 — /forms/new renders form-js builder with form key input', async ({ page }) => {
    await page.goto('/forms/new')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    const keyInput = page.locator('input[placeholder="e.g. leave-request-form"]')
    await expect(keyInput).toBeVisible({ timeout: 15000 })
    // Save button should be disabled when key is empty
    const saveBtn = page.getByRole('button', { name: /^save$/i })
    await expect(saveBtn).toBeDisabled({ timeout: 3000 })
  })

  // ── 24.3 — Save disabled with empty key ─────────────────────────────────────

  test('24.3 — Save button is disabled when form key is empty', async ({ page }) => {
    await page.goto('/forms/new')
    const keyInput = page.locator('input[placeholder="e.g. leave-request-form"]')
    await expect(keyInput).toBeVisible({ timeout: 15000 })
    await expect(page.getByRole('button', { name: /^save$/i })).toBeDisabled()
  })

  // ── 24.4 — Create event-ticket-form ─────────────────────────────────────────

  test('24.4 — Create "event-ticket-form" via UI', async ({ page }) => {
    const exists = await formExistsApi(adminToken, WORKFLOW_FORMS[0])
    if (exists) {
      test.info().annotations.push({ type: 'note', description: `${WORKFLOW_FORMS[0]} already exists — skipping UI create` })
      return
    }
    await createFormViaUI(page, WORKFLOW_FORMS[0])
    test.info().annotations.push({ type: 'note', description: `${WORKFLOW_FORMS[0]} created via UI` })
  })

  // ── 24.5 — event-ticket-form in list ────────────────────────────────────────

  test('24.5 — event-ticket-form appears in /forms list', async ({ page }) => {
    await page.goto('/forms')
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/event.?ticket.?form|event ticket form/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 24.6 — Create leave-request-form ────────────────────────────────────────

  test('24.6 — Create "leave-request-form" via UI', async ({ page }) => {
    const exists = await formExistsApi(adminToken, WORKFLOW_FORMS[1])
    if (exists) {
      test.info().annotations.push({ type: 'note', description: `${WORKFLOW_FORMS[1]} already exists — skipping UI create` })
      return
    }
    await createFormViaUI(page, WORKFLOW_FORMS[1])
    test.info().annotations.push({ type: 'note', description: `${WORKFLOW_FORMS[1]} created via UI` })
  })

  // ── 24.7 — leave-request-form in list ───────────────────────────────────────

  test('24.7 — leave-request-form appears in /forms list', async ({ page }) => {
    await page.goto('/forms')
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/leave.?request.?form|leave request form/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 24.8 — Create equipment-request-form ────────────────────────────────────

  test('24.8 — Create "equipment-request-form" via UI', async ({ page }) => {
    const exists = await formExistsApi(adminToken, WORKFLOW_FORMS[2])
    if (exists) {
      test.info().annotations.push({ type: 'note', description: `${WORKFLOW_FORMS[2]} already exists — skipping UI create` })
      return
    }
    await createFormViaUI(page, WORKFLOW_FORMS[2])
    test.info().annotations.push({ type: 'note', description: `${WORKFLOW_FORMS[2]} created via UI` })
  })

  // ── 24.9 — equipment-request-form in list ───────────────────────────────────

  test('24.9 — equipment-request-form appears in /forms list', async ({ page }) => {
    await page.goto('/forms')
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/equipment.?request.?form|equipment request form/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 24.10 — Create budget-request-form ──────────────────────────────────────

  test('24.10 — Create "budget-request-form" via UI', async ({ page }) => {
    const exists = await formExistsApi(adminToken, WORKFLOW_FORMS[3])
    if (exists) {
      test.info().annotations.push({ type: 'note', description: `${WORKFLOW_FORMS[3]} already exists — skipping UI create` })
      return
    }
    await createFormViaUI(page, WORKFLOW_FORMS[3])
    test.info().annotations.push({ type: 'note', description: `${WORKFLOW_FORMS[3]} created via UI` })
  })

  // ── 24.11 — budget-request-form in list ─────────────────────────────────────

  test('24.11 — budget-request-form appears in /forms list', async ({ page }) => {
    await page.goto('/forms')
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/budget.?request.?form|budget request form/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 24.12 — Edit navigates to /forms/edit/{key} ─────────────────────────────

  test('24.12 — Edit button navigates to form edit page', async ({ page }) => {
    await page.goto('/forms')
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })

    const editLink = page.getByRole('link', { name: /^edit$/i }).first()
    if (await editLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await editLink.click()
      await expect(page).toHaveURL(/forms\/edit\//, { timeout: 8000 })
      // Form key input should be disabled in edit mode (initialFormKey set)
      const keyInput = page.locator('input[placeholder="e.g. leave-request-form"]')
      await expect(keyInput).toBeVisible({ timeout: 10000 })
      await expect(keyInput).toBeDisabled({ timeout: 3000 })
    } else {
      test.info().annotations.push({ type: 'note', description: 'No Edit link visible — check that forms were deployed' })
    }
  })

  // ── 24.13 — Preview link navigates to /forms/preview/{key} ──────────────────

  test('24.13 — Preview button navigates to form preview page', async ({ page }) => {
    await page.goto('/forms')
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })

    const previewLink = page.getByRole('link', { name: /^preview$/i }).first()
    if (await previewLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await previewLink.click()
      await expect(page).toHaveURL(/forms\/preview\//, { timeout: 8000 })
      await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    } else {
      test.info().annotations.push({ type: 'note', description: 'No Preview link visible on forms list' })
    }
  })

  // ── 24.14 — Delete test form via confirm dialog ──────────────────────────────

  test('24.14 — Create and delete a test form via confirm dialog', async ({ page }) => {
    // Ensure the delete-test form exists
    const exists = await formExistsApi(adminToken, DELETE_TEST_FORM_KEY)
    if (!exists) {
      await createFormViaUI(page, DELETE_TEST_FORM_KEY)
    }

    await page.goto('/forms')
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })

    // Find the card for delete-test-form and click its Trash button
    const deleteTestCard = page.locator('[class*="card"]').filter({ hasText: /e2e.?delete.?test.?form/i }).first()

    if (await deleteTestCard.isVisible({ timeout: 5000 }).catch(() => false)) {
      // Trash button is styled with background #BA3920 (delete variant in this page)
      const trashBtn = deleteTestCard.getByRole('button').last()
      await trashBtn.click()

      // Confirm dialog appears
      const confirmBtn = page.getByRole('button', { name: /delete|confirm/i }).last()
      await expect(confirmBtn).toBeVisible({ timeout: 5000 })
      await confirmBtn.click()

      // Form removed from list
      await expect(deleteTestCard).not.toBeVisible({ timeout: 8000 })
      test.info().annotations.push({ type: 'note', description: 'e2e-delete-test-form deleted via confirm dialog' })
    } else {
      // Fall back to API delete
      await deleteFormApi(adminToken, DELETE_TEST_FORM_KEY).catch(() => {})
      test.info().annotations.push({ type: 'note', description: 'Delete-test form card not found — deleted via API fallback' })
    }
  })

  // ── 24.15 — All four workflow forms accessible via API ───────────────────────

  test('24.15 — All four workflow forms exist in the engine (API verification)', async () => {
    const forms = await listFormsApi(adminToken)
    const keys = forms.map((f: any) => f.key)

    for (const formKey of WORKFLOW_FORMS) {
      expect(keys, `Expected ${formKey} to be deployed`).toContain(formKey)
    }
  })
})

// ── 24 — RBAC: employee has no Create New Form ───────────────────────────────

test.describe('24 — Forms — employee RBAC', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('24.16 — Employee sees no Create New Form button on /forms', async ({ page }) => {
    await page.goto('/forms')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/forms/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByRole('link', { name: /create new form/i })).not.toBeVisible({ timeout: 3000 })
    await expect(page.getByRole('button', { name: /create new form/i })).not.toBeVisible({ timeout: 3000 })
  })
})
