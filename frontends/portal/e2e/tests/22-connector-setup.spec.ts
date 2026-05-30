/**
 * 22 — Connector Setup
 *
 * Tests connector management via /admin/connectors.
 * Creates the two connectors required by Layer 3 BPMN tests:
 *   - mock-api        (https://httpbin.org)
 *   - notification-service (https://httpbin.org/post)
 *
 * Key facts:
 *   - Admin API: http://localhost:8083/api/connectors
 *   - ConnectorForm requires: displayName, connectorKey, baseUrl (General tab)
 *     + Auth Scheme selection (Authentication tab; defaults to BEARER which requires a credential).
 *     These tests select "None" to avoid pre-creating credentials.
 *   - Save button is disabled until displayName, connectorKey, baseUrl are filled
 *     and authScheme is NONE (or a credentialRef is selected for other schemes).
 *   - No DELETE endpoint exists on the backend — delete tests log as missing feature.
 *   - Connectors are tenant-scoped; NEXT_PUBLIC_TENANT_CODE defaults to "default".
 *
 * M4.12 B.6: secretRef field removed. Auth tab now has:
 *   - authScheme Select (BEARER / API_KEY / BASIC / NONE)
 *   - credentialRef Select (shown when authScheme != NONE)
 */

import { test, expect } from '@playwright/test'
import { STORAGE_STATES, TEST_USERS } from '../fixtures/auth'

const ADMIN_URL = process.env.E2E_ADMIN_SERVICE_URL ?? 'http://localhost:8083'
const KEYCLOAK_URL = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8090'
const PORTAL_CLIENT_SECRET = process.env.E2E_PORTAL_CLIENT_SECRET ?? 'REDACTED_KC_PORTAL_SECRET'
const TENANT_CODE = process.env.E2E_TENANT_CODE ?? 'default'

const CONNECTOR_MOCK_API = {
  key: 'mock-api',
  displayName: 'Mock API',
  baseUrl: 'https://httpbin.org',
}

const CONNECTOR_NOTIFICATION = {
  key: 'notification-service',
  displayName: 'Notification Service',
  baseUrl: 'https://httpbin.org/post',
}

// ── API helpers ───────────────────────────────────────────────────────────────

async function getAdminToken(): Promise<string> {
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

async function listConnectorsApi(token: string): Promise<any[]> {
  const res = await fetch(`${ADMIN_URL}/api/connectors?tenantCode=${TENANT_CODE}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`List connectors failed: ${res.status}`)
  const data = await res.json()
  return Array.isArray(data) ? data : (data.content ?? [])
}

async function connectorExistsApi(token: string, key: string): Promise<boolean> {
  const connectors = await listConnectorsApi(token)
  return connectors.some((c: any) => c.connectorKey === key)
}

// ── Helper: fill and submit the connector creation dialog ────────────────────
//
// Auth tab defaults to BEARER (requires a credentialRef). We select "None" so
// E2E tests don't need to pre-create credentials. The Save button becomes
// enabled once displayName + connectorKey + baseUrl are filled and authScheme
// is NONE (credentialOk = authScheme === 'NONE' || credentialRef !== '').

async function createConnectorViaUI(
  page: any,
  connector: { key: string; displayName: string; baseUrl: string }
): Promise<void> {
  const newBtn = page.getByRole('button', { name: /new connector/i })
  await expect(newBtn).toBeVisible({ timeout: 5000 })
  await newBtn.click()

  await expect(page.getByRole('dialog')).toBeVisible({ timeout: 5000 })

  // General tab — fill required fields
  await page.getByLabel('Display Name').fill(connector.displayName)
  await page.getByLabel('Connector Key').fill(connector.key)
  await page.getByLabel('Base URL').fill(connector.baseUrl)

  // Authentication tab — switch scheme to None (default BEARER blocks save without a credential)
  await page.getByRole('tab', { name: /authentication/i }).click()
  await page.locator('#authScheme').click()
  await page.getByRole('option', { name: 'None' }).click()

  // Save button should now be enabled
  const saveBtn = page.getByRole('button', { name: /save connector/i })
  await expect(saveBtn).toBeEnabled({ timeout: 3000 })
  await saveBtn.click()

  // Dialog closes on success (or stays open for duplicate key — handled by callers)
  await page.waitForTimeout(1500)
}

// ── 22 — Connector Setup ──────────────────────────────────────────────────────

test.describe('22 — Connector Setup — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  // ── 22.1 — Page loads ──────────────────────────────────────────────────────

  test('22.1 — /admin/connectors loads without error', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    await expect(page.getByText(/connectors/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByRole('button', { name: /new connector/i })).toBeVisible({ timeout: 5000 })
  })

  // ── 22.2 — Create mock-api ─────────────────────────────────────────────────

  test('22.2 — Create "mock-api" connector via UI', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page.getByRole('button', { name: /new connector/i })).toBeVisible({ timeout: 10000 })

    await createConnectorViaUI(page, CONNECTOR_MOCK_API)

    const dialogStillOpen = await page.getByRole('dialog').isVisible({ timeout: 2000 }).catch(() => false)
    if (dialogStillOpen) {
      await page.keyboard.press('Escape')
      test.info().annotations.push({ type: 'note', description: 'mock-api dialog still open — connector may already exist (idempotent)' })
    } else {
      test.info().annotations.push({ type: 'note', description: 'mock-api connector created via UI' })
    }
  })

  // ── 22.3 — mock-api in list ────────────────────────────────────────────────

  test('22.3 — mock-api appears in connector list', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page.getByText(/connectors/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/loading/i)).not.toBeVisible({ timeout: 10000 }).catch(() => {})
    await expect(page.getByText(/mock.?api|mock api/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 22.4 — Create notification-service ────────────────────────────────────

  test('22.4 — Create "notification-service" connector via UI', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page.getByRole('button', { name: /new connector/i })).toBeVisible({ timeout: 10000 })

    await createConnectorViaUI(page, CONNECTOR_NOTIFICATION)

    const dialogStillOpen = await page.getByRole('dialog').isVisible({ timeout: 2000 }).catch(() => false)
    if (dialogStillOpen) {
      await page.keyboard.press('Escape')
      test.info().annotations.push({ type: 'note', description: 'notification-service dialog still open — connector may already exist (idempotent)' })
    } else {
      test.info().annotations.push({ type: 'note', description: 'notification-service connector created via UI' })
    }
  })

  // ── 22.5 — notification-service in list ───────────────────────────────────

  test('22.5 — notification-service appears in connector list', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page.getByText(/connectors/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/loading/i)).not.toBeVisible({ timeout: 10000 }).catch(() => {})
    await expect(page.getByText(/notification.?service|notification service/i).first()).toBeVisible({ timeout: 10000 })
  })

  // ── 22.6 — Validation: Save disabled with incomplete required fields ────────
  //
  // With authScheme = NONE (selected in this test), Save requires only
  // displayName + connectorKey + baseUrl. With only displayName filled the
  // button must remain disabled.

  test('22.6 — Save Connector button disabled when required fields are empty', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page.getByRole('button', { name: /new connector/i })).toBeVisible({ timeout: 10000 })

    await page.getByRole('button', { name: /new connector/i }).click()
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 5000 })

    const saveBtn = page.getByRole('button', { name: /save connector/i })

    // No fields filled — disabled
    await expect(saveBtn).toBeDisabled({ timeout: 3000 })

    // Switch auth to None so credential gate is cleared (only General fields remain)
    await page.getByRole('tab', { name: /authentication/i }).click()
    await page.locator('#authScheme').click()
    await page.getByRole('option', { name: 'None' }).click()

    // Back to General — only displayName filled; still disabled (connectorKey + baseUrl empty)
    await page.getByRole('tab', { name: /general/i }).click()
    await page.getByLabel('Display Name').fill('Partial Test')
    await expect(saveBtn).toBeDisabled({ timeout: 1000 })

    await page.keyboard.press('Escape')
  })

  // ── 22.7 — Delete connector (feature pending) ─────────────────────────────

  test('22.7 — Delete "notification-service" connector (or log as missing feature)', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page.getByText(/connectors/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/loading/i)).not.toBeVisible({ timeout: 10000 }).catch(() => {})

    const deleteBtn = page.getByRole('button', { name: /delete/i }).first()
    const hasDeleteBtn = await deleteBtn.isVisible({ timeout: 3000 }).catch(() => false)

    if (hasDeleteBtn) {
      await deleteBtn.click()
      const confirmBtn = page.getByRole('button', { name: /confirm|yes|delete/i }).last()
      if (await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await confirmBtn.click()
        await page.waitForTimeout(1000)
      }
      await expect(page.getByText(/notification.?service/i)).not.toBeVisible({ timeout: 5000 })
      test.info().annotations.push({ type: 'note', description: 'notification-service deleted via UI' })
    } else {
      test.info().annotations.push({
        type: 'bug',
        description: 'Delete button not found on connector cards — delete connector feature not yet implemented in UI. Backend ConnectorController has no DELETE endpoint. Log as GitHub issue.',
      })
    }
  })

  // ── 22.8 — Ensure notification-service exists (re-create if deleted) ───────

  test('22.8 — notification-service exists after delete/re-create cycle', async ({ page }) => {
    const token = await getAdminToken()
    const exists = await connectorExistsApi(token, CONNECTOR_NOTIFICATION.key)

    if (!exists) {
      await page.goto('/admin/connectors')
      await expect(page.getByRole('button', { name: /new connector/i })).toBeVisible({ timeout: 10000 })
      await createConnectorViaUI(page, CONNECTOR_NOTIFICATION)
      await page.waitForTimeout(1500)
      test.info().annotations.push({ type: 'note', description: 'notification-service re-created via UI' })
    } else {
      test.info().annotations.push({ type: 'note', description: 'notification-service already exists — no re-create needed' })
    }

    const existsNow = await connectorExistsApi(token, CONNECTOR_NOTIFICATION.key)
    expect(existsNow).toBe(true)
  })

  // ── 22.9 — Both connectors accessible via API ─────────────────────────────

  test('22.9 — Both connectors returned by admin API (required for BPMN connector dropdown)', async () => {
    const token = await getAdminToken()
    const connectors = await listConnectorsApi(token)
    const keys = connectors.map((c: any) => c.connectorKey)

    expect(keys).toContain(CONNECTOR_MOCK_API.key)
    expect(keys).toContain(CONNECTOR_NOTIFICATION.key)
  })

  // ── 22.10 — BPMN process designer loads ───────────────────────────────────

  test('22.10 — /processes/new renders BPMN designer', async ({ page }) => {
    await page.goto('/processes/new')
    await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    const canvas = page.locator('canvas, .bjs-container, svg.djs-overlay-container, [class*="bpmn"]').first()
    await expect(canvas).toBeVisible({ timeout: 15000 })
  })
})
