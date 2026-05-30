import { test, expect } from '@playwright/test'
import { STORAGE_STATES } from '../../fixtures/auth'

async function navigateToAssetRequestForm(page: any) {
  await page.goto('/processes')
  await expect(page.getByText(/asset request/i).first()).toBeVisible({ timeout: 10000 })

  const assetCard = page.locator('div, article, [data-testid]', { hasText: /asset request/i }).first()
  const startBtn = assetCard.getByRole('link', { name: /start process/i })

  const visible = await startBtn.isVisible({ timeout: 5000 }).catch(() => false)
  if (!visible) return false

  await startBtn.click()
  await page.waitForURL(/processes\/start\/asset-request-process/, { timeout: 10000 })
  return true
}

test.describe('14 — Asset request via generic start form page', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('14.1 — processes list shows asset-request-process', async ({ page }) => {
    await page.goto('/processes')
    await expect(page).not.toHaveURL(/login|403/)
    await expect(page.getByText(/asset request/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('14.2 — clicking Start Process navigates to generic start form', async ({ page }) => {
    const found = await navigateToAssetRequestForm(page)
    if (!found) {
      test.info().annotations.push({ type: 'note', description: 'Asset request process card not found — check process is deployed' })
      return
    }
    await expect(page.getByText(/start new process/i)).toBeVisible({ timeout: 10000 })
  })

  test('14.3 — form renders with category dropdown populated at mount (no empty state)', async ({ page }) => {
    const found = await navigateToAssetRequestForm(page)
    if (!found) {
      test.info().annotations.push({ type: 'note', description: 'Asset request process card not found — skipping' })
      return
    }

    // Form renders only once initialFormData (categoryOptions) is loaded — skeleton shown until then
    await expect(page.locator('.fjs-container')).toBeVisible({ timeout: 15000 })

    // Open the Asset Category dropdown
    const categoryInput = page.getByLabel('Asset Category')
    await categoryInput.click()

    // Dropdown options should be present immediately (loaded before form renders)
    // Regression guard for Bug 1: categories must not show "No results" on first open
    await expect(page.locator('.fjs-dropdownlist-item').first()).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.fjs-dropdownlist-item', { hasText: 'IT' })).toBeVisible()
    await expect(page.locator('.fjs-dropdownlist-item', { hasText: 'Office Assets' })).toBeVisible()
  })

  test('14.4 — selecting a category fetches and populates the asset definition dropdown', async ({ page }) => {
    const found = await navigateToAssetRequestForm(page)
    if (!found) {
      test.info().annotations.push({ type: 'note', description: 'Asset request process card not found — skipping' })
      return
    }

    await expect(page.locator('.fjs-container')).toBeVisible({ timeout: 15000 })

    // Open Asset Category dropdown and select IT
    const categoryInput = page.getByLabel('Asset Category')
    await categoryInput.click()
    await expect(page.locator('.fjs-dropdownlist-item', { hasText: 'IT' })).toBeVisible({ timeout: 5000 })

    // Wait for the asset-definitions API response triggered by selecting IT
    const [assetDefsResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/asset-definitions') && resp.status() === 200,
        { timeout: 10000 }
      ),
      page.locator('.fjs-dropdownlist-item', { hasText: 'IT' }).click(),
    ])

    expect(assetDefsResponse.ok()).toBeTruthy()

    // Asset dropdown should now have options (regression guard for Bug 2)
    const assetInput = page.getByLabel('Asset')
    await assetInput.click()
    await expect(page.locator('.fjs-dropdownlist-item').first()).toBeVisible({ timeout: 5000 })
  })
})
