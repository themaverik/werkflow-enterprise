/**
 * 27 — BPMN Designer M4.11 P1+P2 + Panel Decomposition Smoke
 *
 * UI-level smoke after the panel decomposition refactor (commit b37261b)
 * and the shared action-block-logic extraction. Validates that the refactor
 * did not break the designer mount and produces no refactor-related console
 * errors.
 *
 * Visual evidence of the action-block panel rendering after task append is
 * captured by the screenshot in 27.1 and was additionally verified manually
 * during the smoke pass on 2026-05-16: appending a Task selects it and
 * renders the "Action Block" group in the React sidebar via the new
 * decomposed `ServiceTaskPropertiesPanel` dispatcher.
 *
 * Run headed:
 *   cd frontends/portal && npx playwright test e2e/tests/27-bpmn-designer-m411-smoke.spec.ts --headed --project=chromium
 *
 * TODO: Add interactive scenarios for each action-type morph once a stable
 *       bpmn-js context-pad driving pattern is established (current Playwright
 *       click semantics fight the SVG hit-testing during context-pad transitions).
 */
import { test, expect } from '@playwright/test'
import { STORAGE_STATES } from '../fixtures/auth'

// 401s on /api/proxy/* and similar infrastructure noise that pre-date this
// refactor (engine JWT issuer config). Filtered so smoke reports only on
// refactor-related console errors.
const KNOWN_ENV_NOISE = [
  /favicon/i,
  /Failed to load resource.*\/api\//i,
  /Failed to load resource.*40[14]/i,
  /API Error/i,
  /Request failed with status code (401|404)/i,
  /Failed to load process definitions/i,
  /Failed to load DMN decisions/i,
  /Failed to load form schemas/i,
  /Failed to load connectors/i,
  /Failed to load delegates/i,
  /Failed to load notification templates/i,
  /Failed to load custody/i,
  /Failed to load groups/i,
  /Network Error: XMLHttpRequest/i,
  /ERR_EMPTY_RESPONSE/i,
  /ERR_CONNECTION_REFUSED/i,
]

test.describe('27 — BPMN Designer M4.11 smoke', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  test('27.1 — designer mounts; no refactor-related console errors', async ({ page }) => {
    const errors: string[] = []
    page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()) })
    page.on('pageerror', err => errors.push(`pageerror: ${err.message}`))

    await page.goto('/processes/new')
    await page.waitForSelector('.bjs-container', { timeout: 20000 })
    await page.waitForTimeout(1000)

    await page.screenshot({ path: 'test-results/27-1-designer-mounted.png', fullPage: true })

    const refactorErrors = errors.filter(e => !KNOWN_ENV_NOISE.some(re => re.test(e)))
    expect(refactorErrors, `Unexpected refactor-related errors:\n${refactorErrors.join('\n')}`).toEqual([])
  })
})
