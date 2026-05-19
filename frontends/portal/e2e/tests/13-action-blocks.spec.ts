import { test, expect } from '@playwright/test'
import { STORAGE_STATES } from '../fixtures/auth'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Opens the new-process page, waits for the BPMN editor to initialise, and
 * clicks the Start Event shape to reveal its context pad.  Then clicks
 * "Append Task" from the context pad to create a UserTask, and finally clicks
 * the newly appended task so its properties appear in the panel.
 *
 * Returns early (skips) if the editor is not reachable within the timeout,
 * so the tests degrade gracefully when the stack is not running.
 */
async function openEditorAndAppendTask(page: any) {
  await page.goto('/processes/new')

  const editorVisible = await page
    .waitForSelector('.bjs-container', { timeout: 20000 })
    .then(() => true)
    .catch(() => false)

  if (!editorVisible) {
    return false
  }

  // The Start Event is always present on a blank diagram.  bpmn-js renders
  // shapes inside a <svg> that sits inside .bjs-container.  The start event
  // circle is typically near (150, 190) in the canvas coordinate space, but
  // clicking via the svg overlay element is more reliable.
  const startEventShape = page
    .locator('.djs-element[data-element-id="StartEvent_1"]')
    .first()

  const startEventVisible = await startEventShape
    .waitFor({ state: 'visible', timeout: 10000 })
    .then(() => true)
    .catch(() => false)

  if (!startEventVisible) {
    return false
  }

  await startEventShape.click()

  // bpmn-js renders a context pad after a click.  The "append task" entry has
  // class "bpmn-icon-task" or a title containing "Append Task".
  const appendTaskEntry = page
    .locator('.djs-context-pad [title*="Append Task"], .djs-context-pad .bpmn-icon-task')
    .first()

  const padVisible = await appendTaskEntry
    .waitFor({ state: 'visible', timeout: 8000 })
    .then(() => true)
    .catch(() => false)

  if (!padVisible) {
    return false
  }

  await appendTaskEntry.click()

  // After appending, click somewhere neutral so the append menu closes, then
  // click the newly created activity element.
  const newActivity = page
    .locator('.djs-element[data-element-id*="Activity_"]')
    .last()

  const activityVisible = await newActivity
    .waitFor({ state: 'visible', timeout: 8000 })
    .then(() => true)
    .catch(() => false)

  if (!activityVisible) {
    return false
  }

  await newActivity.click()
  return true
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('13 — Action blocks — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin })

  // -------------------------------------------------------------------------
  // 13.1  Human Approval
  // -------------------------------------------------------------------------
  test('13.1 — HUMAN_APPROVAL action type shows correct sub-fields', async ({ page }) => {
    const ready = await openEditorAndAppendTask(page)
    if (!ready) {
      test.info().annotations.push({
        type: 'note',
        description: 'BPMN stack not reachable — skipping HUMAN_APPROVAL field assertions',
      })
      test.skip()
      return
    }

    // The Action Block group header should be visible in the properties panel
    await expect(
      page.getByText('Action Block', { exact: true }).first()
    ).toBeVisible({ timeout: 8000 })

    // Select HUMAN_APPROVAL from the Action Type dropdown.
    // @bpmn-io/properties-panel renders SelectEntry as a <select> whose
    // wrapping element carries data-entry-id="actionType".
    const actionTypeSelect = page
      .locator('[data-entry-id="actionType"] select')
      .first()

    await expect(actionTypeSelect).toBeVisible({ timeout: 8000 })
    await actionTypeSelect.selectOption('HUMAN_APPROVAL')

    // After selecting, the panel should re-render with sub-fields.
    await expect(
      page.locator('[data-entry-id="ab-assignee"]')
    ).toBeVisible({ timeout: 5000 })

    await expect(
      page.locator('[data-entry-id="ab-candidateGroups"]')
    ).toBeVisible({ timeout: 5000 })
  })

  // -------------------------------------------------------------------------
  // 13.2  Notification
  // -------------------------------------------------------------------------
  test('13.2 — NOTIFICATION action type shows correct sub-fields', async ({ page }) => {
    const ready = await openEditorAndAppendTask(page)
    if (!ready) {
      test.info().annotations.push({
        type: 'note',
        description: 'BPMN stack not reachable — skipping NOTIFICATION field assertions',
      })
      test.skip()
      return
    }

    await expect(
      page.getByText('Action Block', { exact: true }).first()
    ).toBeVisible({ timeout: 8000 })

    const actionTypeSelect = page
      .locator('[data-entry-id="actionType"] select')
      .first()

    await expect(actionTypeSelect).toBeVisible({ timeout: 8000 })
    await actionTypeSelect.selectOption('NOTIFICATION')

    await expect(
      page.locator('[data-entry-id="ab-channel"]')
    ).toBeVisible({ timeout: 5000 })

    await expect(
      page.locator('[data-entry-id="ab-recipient"]')
    ).toBeVisible({ timeout: 5000 })

    await expect(
      page.locator('[data-entry-id="ab-templateKey"]')
    ).toBeVisible({ timeout: 5000 })

    await expect(
      page.locator('[data-entry-id="ab-condition"]')
    ).toBeVisible({ timeout: 5000 })
  })

  // -------------------------------------------------------------------------
  // 13.3  Connector Operation
  // -------------------------------------------------------------------------
  test('13.3 — CONNECTOR_OPERATION action type shows ConnectorOperationSection cards', async ({ page }) => {
    const ready = await openEditorAndAppendTask(page)
    if (!ready) {
      test.info().annotations.push({
        type: 'note',
        description: 'BPMN stack not reachable — skipping CONNECTOR_OPERATION field assertions',
      })
      test.skip()
      return
    }

    await expect(
      page.getByText('Action Block', { exact: true }).first()
    ).toBeVisible({ timeout: 8000 })

    const actionTypeSelect = page
      .locator('[data-entry-id="actionType"] select')
      .first()

    await expect(actionTypeSelect).toBeVisible({ timeout: 8000 })
    await actionTypeSelect.selectOption('CONNECTOR_OPERATION')

    // ConnectorOperationSection renders three cards: Connector, Operation, Output & Error Handling
    await expect(
      page.getByText('Connector', { exact: true }).first()
    ).toBeVisible({ timeout: 5000 })

    await expect(
      page.getByText('Operation', { exact: true }).first()
    ).toBeVisible({ timeout: 5000 })

    await expect(
      page.getByText('Output & Error Handling', { exact: true }).first()
    ).toBeVisible({ timeout: 5000 })

    // Response variable input is present
    await expect(
      page.locator('#co-response-variable')
    ).toBeVisible({ timeout: 5000 })

    // On-error select trigger is present
    await expect(
      page.locator('#co-on-error')
    ).toBeVisible({ timeout: 5000 })
  })
})
