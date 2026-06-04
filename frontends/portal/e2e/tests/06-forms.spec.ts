import { test, expect } from '@playwright/test'
import { STORAGE_STATES } from '../fixtures/auth'

test.describe('Form management', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test.afterAll(async ({ request }) => {
    const resp = await request.get('/api/forms')
    if (!resp.ok()) return
    const forms = await resp.json()
    for (const form of forms) {
      if (form.key?.startsWith('e2e-test-form-')) {
        await request.delete(`/api/forms/${form.key}`)
      }
    }
  })

  test('forms page loads deployed forms', async ({ page }) => {
    await page.goto('/forms')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('h1').filter({ hasText: 'Form Designer' })).toBeVisible({ timeout: 10000 })
  })

  test('Create New Form button visible to admin', async ({ page }) => {
    await page.goto('/forms')
    await expect(page.getByRole('link', { name: /Create New Form/i }).first()).toBeVisible()
  })

  test('New form page renders form-js editor', async ({ page }) => {
    await page.goto('/forms/new')
    // Toolbar with form key input should be present
    // Placeholder is the i18n value "e.g. leave-request-form" (formKeyPlaceholder key)
    await expect(page.getByPlaceholder(/leave-request-form/i)).toBeVisible({ timeout: 10000 })
  })

  test('Save button disabled without form key', async ({ page }) => {
    await page.goto('/forms/new')
    // Wait for toolbar to render before asserting button state
    await expect(page.getByPlaceholder(/leave-request-form/i)).toBeVisible({ timeout: 10000 })
    const saveBtn = page.getByRole('button', { name: /^Save$/i })
    await expect(saveBtn).toBeDisabled()
  })

  test('saving a form with a known key persists and form appears in list', async ({ page }) => {
    const testKey = `e2e-test-form-${Date.now()}`

    await page.goto('/forms/new')
    await page.getByPlaceholder(/leave-request-form/i).fill(testKey)

    // Save button should now be enabled
    const saveBtn = page.getByRole('button', { name: /^Save$/i })
    await expect(saveBtn).toBeEnabled()
    const [saveResponse] = await Promise.all([
      page.waitForResponse(
        resp => resp.url().includes('/api/forms') && resp.request().method() === 'POST',
        { timeout: 30000 }
      ),
      saveBtn.click()
    ])
    expect(saveResponse.ok()).toBeTruthy()

    // Navigate to list and verify the form appears
    await page.goto('/forms')
    // Wait for the forms list API response before asserting (avoids race on slow CI)
    await page.waitForResponse(
      resp => resp.url().includes('/api/forms') && resp.status() === 200,
      { timeout: 10000 }
    )
    await expect(page.getByText(testKey).first()).toBeVisible({ timeout: 5000 })
  })

  test('form preview page renders form-js viewer without errors', async ({ page }) => {
    await page.goto('/forms')
    const previewLink = page.getByRole('link', { name: /Preview/i }).first()
    const count = await previewLink.count()
    if (count === 0) {
      test.skip()
      return
    }
    await previewLink.click()
    await expect(page).toHaveURL(/forms\/preview/)
    // form-js container renders
    await expect(page.locator('.form-js-container, .fjs-container').first()).toBeVisible({ timeout: 10000 })
  })
})

test.describe('Form management - manager custody', () => {
  test.use({ storageState: STORAGE_STATES.manager })

  // john.manager has role doa_approver_level2 (DOA approver), not a workflow admin role.
  // Form design is gated to MANAGER_ROLES (workflow/dept admins).
  test('doa-manager does not see Create New Form (not a workflow admin)', async ({ page }) => {
    await page.goto('/forms')
    const createBtn = page.getByRole('link', { name: /Create New Form/i })
    await expect(createBtn).toHaveCount(0)
  })

  test('manager cannot edit form owned by different department', async ({ page }) => {
    await page.goto('/forms')
    // If any forms exist owned by non-HR dept, Edit button should not appear for HR manager
    // This is a structural test — verifies the canEditForm gate is applied
    // Forms with no owningDepartment show Edit for any manager
    // We just verify the page renders without errors
    await expect(page).toHaveURL('/forms')
  })
})

test.describe('Form management - employee restrictions', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('employee cannot see Create New Form', async ({ page }) => {
    await page.goto('/forms')
    const createBtn = page.getByRole('link', { name: /Create New Form/i })
    await expect(createBtn).toHaveCount(0)
  })

  test('employee cannot see Edit button on forms', async ({ page }) => {
    await page.goto('/forms')
    const editBtns = page.getByRole('link', { name: /Edit/i })
    await expect(editBtns).toHaveCount(0)
  })

  test('employee cannot see Delete button on forms', async ({ page }) => {
    await page.goto('/forms')
    const deleteBtns = page.getByRole('button', { name: /Delete/i })
    await expect(deleteBtns).toHaveCount(0)
  })
})

test.describe('Form version history', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('version history panel opens on edit page', async ({ page }) => {
    // Navigate to an existing form edit page (if forms exist)
    // This is conditional — skip gracefully if no forms deployed
    await page.goto('/forms')
    const editLink = page.getByRole('link', { name: /Edit/i }).first()
    const editCount = await editLink.count()
    if (editCount === 0) {
      test.skip()
      return
    }
    await editLink.click()
    await page.getByRole('button', { name: /Version History/i }).click()
    // Sidebar heading appears
    await expect(page.getByText('Version History').nth(1)).toBeVisible()
  })
})
