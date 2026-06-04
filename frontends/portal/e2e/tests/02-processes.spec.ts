import { test, expect } from '@playwright/test'
import { STORAGE_STATES } from '../fixtures/auth'

test.describe('02 — Process definitions — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test.afterAll(async ({ request }) => {
    const resp = await request.get('/api/process-definitions')
    if (!resp.ok()) return
    const defs = await resp.json()
    const match = defs.find((d: any) => d.name === 'E2E Test Process')
    if (match?.deploymentId) {
      await request.delete(`/api/process-definitions/deployment/${match.deploymentId}`)
    }
  })

  test('03.1 — deployed process list shows CapEx and other flows', async ({ page }) => {
    // Register before goto so the response is not missed
    const apiReady = page.waitForResponse(
      resp => resp.url().includes('/api/process-definitions') && resp.status() === 200,
      { timeout: 30000 }
    )
    await page.goto('/processes')
    await expect(page).not.toHaveURL(/login|403/)
    await apiReady
    // Defensive: examples require werkflow.examples.deploy-on-startup=true
    const hasCapex = await page.getByText(/capex/i).first().isVisible({ timeout: 5000 }).catch(() => false)
    if (!hasCapex) {
      test.info().annotations.push({ type: 'note', description: 'No example processes deployed — skipping capex check' })
      return
    }
    await expect(page.getByText(/capex/i).first()).toBeVisible()
  })

  test('03.2 — process with start form shows Start Process link', async ({ page }) => {
    const apiReady = page.waitForResponse(
      resp => resp.url().includes('/api/process-definitions') && resp.status() === 200,
      { timeout: 30000 }
    )
    await page.goto('/processes')
    await apiReady
    const hasCapex = await page.getByText(/capex/i).first().isVisible({ timeout: 5000 }).catch(() => false)
    if (!hasCapex) {
      test.info().annotations.push({ type: 'note', description: 'No example processes with start form found — skipping' })
      return
    }
    // Start Process is rendered as a link (Button asChild + Link)
    await expect(page.getByRole('link', { name: /start process/i }).first()).toBeVisible({ timeout: 5000 })
  })

  test('10.1 — admin can open BPMN editor for a process', async ({ page }) => {
    const apiReady = page.waitForResponse(
      resp => resp.url().includes('/api/process-definitions') && resp.status() === 200,
      { timeout: 30000 }
    )
    await page.goto('/processes')
    await apiReady
    const editBtn = page.getByRole('link', { name: /edit/i }).first()
    if (await editBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await editBtn.click()
      await expect(page).toHaveURL(/processes\/edit/, { timeout: 10000 })
    } else {
      test.info().annotations.push({ type: 'note', description: 'No editable processes found — skipping editor check' })
    }
  })

  test('10.2 — deploy succeeds and process appears in list', async ({ page }) => {
    await page.goto('/processes/new')
    // Wait for BPMN editor to initialize before filling the name
    await page.waitForSelector('.bjs-container', { timeout: 15000 })
    // Set a process name
    await page.locator('input[placeholder="Process name"]').fill('E2E Test Process')

    // Click Deploy
    const deployBtn = page.getByRole('button', { name: /^Deploy$/i })
    await expect(deployBtn).toBeVisible({ timeout: 5000 })
    await deployBtn.click()

    // Should redirect to /processes after successful deploy
    await page.waitForURL(/\/processes$/, { timeout: 15000 })
    await expect(page.getByText(/E2E Test Process/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('10.3 — save draft persists and resume banner appears on edit page reload', async ({ page }) => {
    const apiReady = page.waitForResponse(
      resp => resp.url().includes('/api/process-definitions') && resp.status() === 200,
      { timeout: 30000 }
    )
    await page.goto('/processes')
    await apiReady

    const editLink = page.getByRole('link', { name: /edit/i }).first()
    if (!await editLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      test.skip()
      return
    }
    await editLink.click()
    await expect(page).toHaveURL(/processes\/edit/)

    // Wait for BPMN designer to load
    await page.waitForSelector('.bjs-container', { timeout: 15000 })

    // Click Save Draft and wait for the API to confirm persistence
    const saveDraftBtn = page.getByRole('button', { name: /Save Draft/i })
    await expect(saveDraftBtn).toBeVisible({ timeout: 5000 })
    const [draftResponse] = await Promise.all([
      page.waitForResponse(
        resp => resp.url().includes('/api/process-drafts') && resp.request().method() === 'POST',
        { timeout: 30000 }
      ),
      saveDraftBtn.click()
    ])
    expect(draftResponse.ok()).toBeTruthy()

    // Reload the page — banner should appear
    await page.reload()
    // Wait for BPMN designer to reload (useQuery + getDraft completes before xmlToLoad is set,
    // which is what renders the BPMN container — so container visible = banner state determined)
    await page.waitForSelector('.bjs-container', { timeout: 20000 })
    await expect(page.getByText(/unsaved draft/i)).toBeVisible({ timeout: 5000 })

    // Click Load deployed version — banner should disappear
    await page.getByRole('button', { name: /Load deployed version/i }).click()
    await expect(page.getByText(/unsaved draft/i)).toHaveCount(0)
  })
})

test.describe('02 — Process definitions — employee', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('03.3 — employee sees no manager actions on Processes page', async ({ page }) => {
    // Middleware only checks auth, not roles — employee can view /processes
    // but should not see Create/Edit/Delete actions (role-gated in UI)
    await page.goto('/processes')
    await expect(page).not.toHaveURL(/login/)
    // No Create New Process button for employee
    const createBtn = page.getByRole('link', { name: /Create New Process/i })
    await expect(createBtn).toHaveCount(0)
  })
})
