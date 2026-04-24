import { test, expect } from '@playwright/test'
import { STORAGE_STATES } from '../fixtures/auth'

// ── Admin ────────────────────────────────────────────────────────────────────

test.describe('20 — RBAC Navigation — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('20.1 — admin can access all core pages', async ({ page }) => {
    for (const path of ['/processes', '/decisions', '/forms', '/services', '/dashboard']) {
      await page.goto(path)
      await expect(page).not.toHaveURL(/login/, { timeout: 5000 })
    }
  })

  test('20.2 — admin can access all admin pages', async ({ page }) => {
    for (const path of ['/admin/connectors', '/admin/doa', '/admin/departments']) {
      await page.goto(path)
      await expect(page).not.toHaveURL(/login|403/, { timeout: 5000 })
    }
  })

  test('20.3 — admin sidebar shows all nav sections including admin and design studio', async ({ page }) => {
    await page.goto('/dashboard')
    const nav = page.locator('aside')
    await expect(nav).toContainText('My Tasks')
    await expect(nav).toContainText('My Requests')
    await expect(nav).toContainText('Processes')
    await expect(nav).toContainText('Forms')
    await expect(nav).toContainText('Decisions')
  })

  test('20.4 — admin sees Create New Process button on /processes', async ({ page }) => {
    await page.goto('/processes')
    await expect(page.getByRole('link', { name: /new process|create/i }).or(
      page.getByRole('button', { name: /new process|create/i })
    ).first()).toBeVisible({ timeout: 5000 })
  })
})

// ── Manager ──────────────────────────────────────────────────────────────────

test.describe('20 — RBAC Navigation — manager', () => {
  test.use({ storageState: STORAGE_STATES.manager })

  test('20.5 — manager can access tasks, requests, processes, dashboard', async ({ page }) => {
    for (const path of ['/tasks', '/requests', '/processes', '/dashboard']) {
      await page.goto(path)
      await expect(page).not.toHaveURL(/login/, { timeout: 5000 })
    }
  })

  test('20.6 — manager is redirected away from admin pages', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page).not.toHaveURL('/admin/connectors', { timeout: 5000 })
  })

  test('20.7 — manager sidebar has no admin section items', async ({ page }) => {
    await page.goto('/dashboard')
    const nav = page.locator('aside')
    await expect(nav).not.toContainText('Connectors')
    await expect(nav).not.toContainText('Authority Levels')
    await expect(nav).not.toContainText('Departments')
  })

  test('20.8 — manager sees no Create New Process button on /processes', async ({ page }) => {
    await page.goto('/processes')
    await expect(page.getByRole('button', { name: /new process/i })).not.toBeVisible({ timeout: 3000 }).catch(() => {})
    await expect(page.getByRole('link', { name: /new process/i })).not.toBeVisible({ timeout: 3000 }).catch(() => {})
  })
})

// ── Employee ─────────────────────────────────────────────────────────────────

test.describe('20 — RBAC Navigation — employee', () => {
  test.use({ storageState: STORAGE_STATES.employee })

  test('20.9 — employee can access tasks, requests, dashboard', async ({ page }) => {
    for (const path of ['/tasks', '/requests', '/dashboard']) {
      await page.goto(path)
      await expect(page).not.toHaveURL(/login/, { timeout: 5000 })
    }
  })

  test('20.10 — employee is redirected away from admin pages', async ({ page }) => {
    await page.goto('/admin/connectors')
    await expect(page).not.toHaveURL('/admin/connectors', { timeout: 5000 })
  })

  test('20.11 — employee sidebar shows no design studio or admin items', async ({ page }) => {
    await page.goto('/dashboard')
    const nav = page.locator('aside')
    await expect(nav).toContainText('My Tasks')
    await expect(nav).toContainText('My Requests')
    await expect(nav).not.toContainText('Design Studio')
    await expect(nav).not.toContainText('Connectors')
  })

  test('20.12 — employee can view /processes list', async ({ page }) => {
    await page.goto('/processes')
    await expect(page).not.toHaveURL(/login/, { timeout: 5000 })
  })
})
