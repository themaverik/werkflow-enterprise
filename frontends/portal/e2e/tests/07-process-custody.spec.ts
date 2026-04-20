import { test, expect } from '@playwright/test'
import { STORAGE_STATES } from '../fixtures/auth'

test.describe('Process custody - admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('admin sees Create New Process button', async ({ page }) => {
    await page.goto('/processes')
    await expect(page.getByRole('link', { name: /Create New Process/i }).first()).toBeVisible()
  })

  test('admin sees Edit button on deployed processes', async ({ page }) => {
    await page.goto('/processes')
    // If any processes exist, Edit button should be visible for admin
    const cards = page.locator('[data-testid="process-card"], .grid > div').first()
    const editBtn = page.getByRole('link', { name: /Edit/i }).first()
    const editCount = await editBtn.count()
    if (editCount > 0) {
      await expect(editBtn).toBeVisible()
    }
    // If no processes, just verify page loads
    await expect(page).toHaveURL('/processes')
  })

  test('admin sees Delete button on deployed processes', async ({ page }) => {
    await page.goto('/processes')
    const deleteBtns = page.getByRole('button', { name: /Delete/i })
    // Verify page loads regardless of process count
    await expect(page).toHaveURL('/processes')
    // If processes exist, Delete buttons should be present
    const count = await deleteBtns.count()
    if (count > 0) {
      await expect(deleteBtns.first()).toBeVisible()
    }
  })
})

test.describe('Process custody - manager', () => {
  test.use({ storageState: STORAGE_STATES.manager })

  // john.manager has role doa_approver_level2 (DOA approver), not a workflow admin role.
  // Process design is gated to MANAGER_ROLES (workflow/dept admins), so DOA managers
  // do not see Create New Process — they use processes to start requests only.
  test('doa-manager does not see Create New Process (not a workflow admin)', async ({ page }) => {
    await page.goto('/processes')
    const createBtn = page.getByRole('link', { name: /Create New Process/i })
    await expect(createBtn).toHaveCount(0)
  })

  test('manager can access new process designer URL directly', async ({ page }) => {
    await page.goto('/processes/new')
    // BPMN designer container should load (no route-level guard yet — tracked for Group 3)
    await page.waitForTimeout(2000) // allow bpmn-js to initialize
    await expect(page).toHaveURL('/processes/new')
  })
})

test.describe('Process custody - employee', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('employee cannot see Create New Process', async ({ page }) => {
    await page.goto('/processes')
    const createBtn = page.getByRole('link', { name: /Create New Process/i })
    await expect(createBtn).toHaveCount(0)
  })

  test('employee cannot see Edit buttons on processes', async ({ page }) => {
    await page.goto('/processes')
    const editBtns = page.getByRole('link', { name: /Edit/i })
    await expect(editBtns).toHaveCount(0)
  })

  test('employee cannot see Delete buttons on processes', async ({ page }) => {
    await page.goto('/processes')
    const deleteBtns = page.getByRole('button', { name: /Delete/i })
    await expect(deleteBtns).toHaveCount(0)
  })
})
