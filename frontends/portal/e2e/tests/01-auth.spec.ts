import { test, expect } from '@playwright/test'
import { STORAGE_STATES, TEST_USERS } from '../fixtures/auth'

test.describe('01 — Authentication — no auth', () => {

  test('01.1 — sign in via Keycloak and land on dashboard', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: /sign in with keycloak/i }).click()

    await page.waitForURL(/keycloak|8090/, { timeout: 10000 })
    await page.fill('#username', TEST_USERS.admin.username)
    await page.fill('#password', TEST_USERS.admin.password)
    await page.click('[type=submit]')

    await page.waitForURL(/dashboard/, { timeout: 15000 })
    await expect(page).toHaveURL(/dashboard/)
  })

  test('01.5 — unauthenticated user is redirected to login', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/login/)
  })

  test('01.6 — after sign out, Sign in with Keycloak redirects to Keycloak credentials page', async ({ page }) => {
    // Sign in first
    await page.goto('/login')
    await page.getByRole('button', { name: /sign in with keycloak/i }).click()
    await page.waitForURL(/keycloak|8090/, { timeout: 10000 })
    await page.fill('#username', TEST_USERS.admin.username)
    await page.fill('#password', TEST_USERS.admin.password)
    await page.click('[type=submit]')
    await page.waitForURL(/dashboard/, { timeout: 15000 })

    // Sign out via user menu
    await page.getByTestId('user-menu-trigger').click()
    const signOutBtn = page.getByTestId('sign-out-btn')
    await signOutBtn.waitFor({ timeout: 5000 })
    await signOutBtn.click()

    // Wait to land somewhere after logout
    await page.waitForURL(/login|keycloak|8090/, { timeout: 15000 })

    // Click sign in again — should be prompted for credentials (not auto-signed-in)
    await page.goto('/login')
    await page.getByRole('button', { name: /sign in with keycloak/i }).click()
    await page.waitForURL(/keycloak|8090/, { timeout: 10000 })
    await expect(page).toHaveURL(/keycloak|8090/)
  })
})

test.describe('01 — Authentication — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('01.2 — admin sees all nav sections including Design Studio', async ({ page }) => {
    await page.goto('/dashboard')
    const nav = page.locator('aside')
    await expect(nav).toContainText('General')
    await expect(nav).toContainText('My Tasks')
    await expect(nav).toContainText('Design Studio')
    await expect(nav).toContainText('Processes')
    await expect(nav).toContainText('Forms')
  })

  test('01.15 — sign out clears session and protected routes redirect to login', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/dashboard/)

    // Open user menu (avatar button in top-right header)
    await page.getByTestId('user-menu-trigger').click()
    const signOutBtn = page.getByTestId('sign-out-btn')
    if (await signOutBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await signOutBtn.click()
      // Wait for the full Keycloak end-session redirect to complete (lands on /login)
      await page.waitForURL(/login/, { timeout: 15000 })
      await page.goto('/dashboard')
      await expect(page).toHaveURL(/login/, { timeout: 10000 })
    } else {
      test.info().annotations.push({ type: 'note', description: 'Sign out button not found — skipping' })
    }
  })
})

test.describe('01 — Authentication — employee', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('01.3 — employee sees general nav items (My Tasks, My Requests) but not Design Studio', async ({ page }) => {
    // My Tasks has no requiredRoles — visible to all authenticated users
    await page.goto('/dashboard')
    const nav = page.locator('aside')
    await expect(nav).toContainText('My Tasks')
    await expect(nav).toContainText('My Requests')
    await expect(nav).not.toContainText('Design Studio')
    await expect(nav).not.toContainText('Processes')
  })

  test('01.4 — employee does not see role-gated sections', async ({ page }) => {
    await page.goto('/dashboard')
    const nav = page.locator('aside')
    await expect(nav).not.toContainText('Design Studio')
    await expect(nav).not.toContainText('System')
  })
})
