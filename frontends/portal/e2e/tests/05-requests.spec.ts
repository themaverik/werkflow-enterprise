import { test, expect } from '@playwright/test'
import { STORAGE_STATES } from '../fixtures/auth'

test.describe('05 — Request history — employee', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('06.1 — My Requests page loads for employee', async ({ page }) => {
    await page.goto('/requests')
    await expect(page).not.toHaveURL(/403|login/)
    await expect(page.getByText(/my requests|requests/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('06.2 — request list has status filter tabs', async ({ page }) => {
    await page.goto('/requests')
    const tabList = page.getByRole('tablist')
    await expect(tabList).toBeVisible({ timeout: 10000 })
    await expect(page.getByRole('tab').first()).toBeVisible()
  })

  test('06.3 — clicking a request shows process timeline or detail', async ({ page }) => {
    await page.goto('/requests')
    await expect(page.getByText(/my requests|requests/i).first()).toBeVisible({ timeout: 10000 })
    const firstRow = page.locator('table tbody tr, [class*="request-row"], [class*="request-card"]').first()
    if (await firstRow.isVisible({ timeout: 5000 }).catch(() => false)) {
      await firstRow.click()
      await expect(page).toHaveURL(/requests\//)
      await expect(page.getByText(/process|timeline|history|details/i).first()).toBeVisible({ timeout: 5000 })
    } else {
      test.info().annotations.push({ type: 'note', description: 'No requests yet — skipping click test' })
    }
  })

  test('07.3 — search input is present on requests page', async ({ page }) => {
    await page.goto('/requests')
    await expect(page.locator('input[type="text"], input[placeholder]').first()).toBeVisible({ timeout: 10000 })
  })
})

test.describe('05 — Request history — manager', () => {
  test.use({ storageState: STORAGE_STATES.manager })

  test('07.1 — manager can view their requests page', async ({ page }) => {
    await page.goto('/requests')
    await expect(page).not.toHaveURL(/403|login/)
    await expect(page.getByText(/my requests|requests/i).first()).toBeVisible({ timeout: 10000 })
  })
})

test.describe('05 — Request history — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('07.2 — admin can view requests page', async ({ page }) => {
    await page.goto('/requests')
    await expect(page).not.toHaveURL(/403|login/)
    await expect(page.getByText(/my requests|requests/i).first()).toBeVisible({ timeout: 10000 })
  })
})
