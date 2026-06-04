import { test as setup, expect } from '@playwright/test'
import { STORAGE_STATES, TEST_USERS } from '../fixtures/auth'

setup('authenticate as employee', async ({ page }) => {
  const user = TEST_USERS.employee

  await page.goto('/login')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()

  await page.waitForURL(/keycloak|8090/, { timeout: 10000 })
  await page.fill('#username', user.username)
  await page.fill('#password', user.password)
  await page.click('[type=submit]')

  await page.waitForURL(/dashboard/, { timeout: 15000 })
  await expect(page.getByRole('link', { name: 'Dashboard' }).first()).toBeVisible({ timeout: 5000 })

  await page.context().storageState({ path: STORAGE_STATES.employee })
})
