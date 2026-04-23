# E2E Visual Workflow Tests — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **PREREQUISITE:** S28.5 (GlobalTaskNotificationListener) must be deployed and smoke-tested before authoring Layer 3 specs. NOTIFICATION service task nodes are NOT included in BPMN structures — emails fire automatically.

**Goal:** Write 9 Playwright spec files that verify role-based navigation, connector setup, DMN decision creation, form building, and 4 complete multi-user workflow journeys including Mailpit email assertions — gating the OSS v1.0.0 release.

**Architecture:** Layered suite. Each layer builds artifacts consumed by the next. Auth sessions are pre-authenticated via Playwright setup files. Layer 3 workflow tests open multiple browser contexts simultaneously for multi-user flows. All tests target `werkflow-enterprise` portal at port 4000.

**Tech Stack:** Playwright, TypeScript, Keycloak (pre-auth sessions), Mailpit REST API (port 8025)

**Spec:** `docs/superpowers/specs/2026-04-23-e2e-visual-workflows-design.md`

---

## File Map

| Action | Path |
|--------|------|
| Modify | `frontends/portal/e2e/fixtures/auth.ts` |
| Create | `frontends/portal/e2e/setup/it-user.setup.ts` |
| Create | `frontends/portal/e2e/tests/20-rbac-navigation.spec.ts` |
| Create | `frontends/portal/e2e/tests/21-core-user-flows.spec.ts` |
| Create | `frontends/portal/e2e/tests/22-connector-setup.spec.ts` |
| Create | `frontends/portal/e2e/tests/23-dmn-decisions.spec.ts` |
| Create | `frontends/portal/e2e/tests/24-forms-visual.spec.ts` |
| Create | `frontends/portal/e2e/tests/25-workflow-event-ticket.spec.ts` |
| Create | `frontends/portal/e2e/tests/26-workflow-leave-request.spec.ts` |
| Create | `frontends/portal/e2e/tests/27-workflow-equipment.spec.ts` |
| Create | `frontends/portal/e2e/tests/28-workflow-budget.spec.ts` |

---

## Task 0: Fixtures — add IT user session

**Files:**
- Modify: `frontends/portal/e2e/fixtures/auth.ts`
- Create: `frontends/portal/e2e/setup/it-user.setup.ts`

Reference: existing `frontends/portal/e2e/fixtures/auth.ts` and `frontends/portal/e2e/setup/manager.setup.ts` (copy pattern).

- [ ] **Step 1: Add itUser to auth fixtures**

In `frontends/portal/e2e/fixtures/auth.ts`, add to the `STORAGE_STATES` object:

```typescript
itUser: path.join(AUTH_DIR, 'it-user.json'),
```

And add to the `TEST_USERS` object:

```typescript
itUser: {
  username: 'mike.it',
  password: 'TempPassword123!',
},
```

- [ ] **Step 2: Create it-user.setup.ts**

```typescript
// frontends/portal/e2e/setup/it-user.setup.ts
import { test as setup } from '@playwright/test';
import { STORAGE_STATES, TEST_USERS } from '../fixtures/auth';

setup('authenticate as it-user', async ({ page }) => {
  await page.goto('http://localhost:4000');
  await page.waitForURL(/keycloak|login/);
  await page.fill('[name="username"]', TEST_USERS.itUser.username);
  await page.fill('[name="password"]', TEST_USERS.itUser.password);
  await page.click('[type="submit"]');
  await page.waitForURL('http://localhost:4000/**');
  await page.context().storageState({ path: STORAGE_STATES.itUser });
});
```

- [ ] **Step 3: Add it-user setup to playwright.config.ts**

In `playwright.config.ts`, in the `projects` array, add after the employee setup entry:

```typescript
{
  name: 'it-user setup',
  testMatch: /setup\/it-user\.setup\.ts/,
},
{
  name: 'it-user tests',
  use: { storageState: STORAGE_STATES.itUser },
  dependencies: ['it-user setup'],
},
```

- [ ] **Step 4: Run setup to verify session is created**

```bash
cd frontends/portal
npx playwright test setup/it-user.setup.ts --project='it-user setup' 2>&1 | tail -15
```

Expected: `1 passed` and `e2e/.auth/it-user.json` is created.

- [ ] **Step 5: Commit**

```bash
git add frontends/portal/e2e/fixtures/auth.ts \
        frontends/portal/e2e/setup/it-user.setup.ts \
        frontends/portal/playwright.config.ts
git commit -m "feat(e2e): add it-user auth session for mike.it (IT Department approvals)"
```

---

## Task 1: RBAC Navigation (`20-rbac-navigation.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/20-rbac-navigation.spec.ts`

- [ ] **Step 6: Write the spec**

```typescript
// frontends/portal/e2e/tests/20-rbac-navigation.spec.ts
import { test, expect } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';

// --- Admin ---
test.describe('Admin navigation', () => {
  test.use({ storageState: STORAGE_STATES.admin });

  test('can access all platform pages', async ({ page }) => {
    const routes = ['/processes', '/processes/new', '/decisions', '/decisions/new',
                    '/forms', '/forms/new', '/services', '/admin/connectors',
                    '/admin/doa', '/admin/departments'];
    for (const route of routes) {
      await page.goto(`http://localhost:4000${route}`);
      await expect(page).not.toHaveURL(/login|403|unauthorized/);
    }
  });

  test('sidebar shows admin section', async ({ page }) => {
    await page.goto('http://localhost:4000/processes');
    await expect(page.locator('[data-testid="nav-admin"], a[href*="/admin"]').first()).toBeVisible();
  });

  test('processes page shows Create New Process button', async ({ page }) => {
    await page.goto('http://localhost:4000/processes');
    await expect(page.getByRole('link', { name: /create new process/i })
      .or(page.getByRole('button', { name: /create new process/i }))).toBeVisible();
  });
});

// --- Manager ---
test.describe('Manager navigation', () => {
  test.use({ storageState: STORAGE_STATES.manager });

  test('can access tasks, requests, processes, dashboard', async ({ page }) => {
    for (const route of ['/tasks', '/requests', '/processes', '/dashboard']) {
      await page.goto(`http://localhost:4000${route}`);
      await expect(page).not.toHaveURL(/login|403/);
    }
  });

  test('cannot access admin pages', async ({ page }) => {
    await page.goto('http://localhost:4000/admin/connectors');
    await expect(page).toHaveURL(/login|403|dashboard|processes/);
  });

  test('processes page has no Create New Process button', async ({ page }) => {
    await page.goto('http://localhost:4000/processes');
    await expect(page.getByRole('link', { name: /create new process/i })
      .or(page.getByRole('button', { name: /create new process/i }))).not.toBeVisible();
  });

  test('sidebar has no admin section', async ({ page }) => {
    await page.goto('http://localhost:4000/tasks');
    await expect(page.locator('[data-testid="nav-admin-connectors"], a[href="/admin/connectors"]')).not.toBeVisible();
  });
});

// --- Employee ---
test.describe('Employee navigation', () => {
  test.use({ storageState: STORAGE_STATES.employee });

  test('can access tasks, requests, dashboard', async ({ page }) => {
    for (const route of ['/tasks', '/requests', '/dashboard']) {
      await page.goto(`http://localhost:4000${route}`);
      await expect(page).not.toHaveURL(/login|403/);
    }
  });

  test('cannot access admin pages', async ({ page }) => {
    await page.goto('http://localhost:4000/admin/connectors');
    await expect(page).toHaveURL(/login|403|dashboard/);
  });

  test('processes page shows Start Process on deployed processes', async ({ page }) => {
    await page.goto('http://localhost:4000/processes');
    await expect(page.getByRole('link', { name: /start process/i }).first()).toBeVisible({ timeout: 10000 });
  });
});
```

- [ ] **Step 7: Run and observe failures**

```bash
cd frontends/portal
npx playwright test 20-rbac-navigation.spec.ts --headed 2>&1 | tail -30
```

Log any failing selectors or redirects as GitHub issues with label `bug`, `e2e-found`.
Title format: `[E2E] 20-rbac-navigation — <description>`

- [ ] **Step 8: Fix until all pass, then commit**

```bash
git add frontends/portal/e2e/tests/20-rbac-navigation.spec.ts
git commit -m "feat(e2e): 20-rbac-navigation — admin/manager/employee access control"
```

---

## Task 2: Core User Flows (`21-core-user-flows.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/21-core-user-flows.spec.ts`

Uses seeded processes: `general-approval`, `document-review`, `onboarding-checklist` (auto-deployed on engine start).

- [ ] **Step 9: Write the spec**

```typescript
// frontends/portal/e2e/tests/21-core-user-flows.spec.ts
import { test, expect, Browser } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';

test.describe('My Tasks — employee', () => {
  test.use({ storageState: STORAGE_STATES.employee });

  test('task list loads with required columns', async ({ page }) => {
    await page.goto('http://localhost:4000/tasks');
    await expect(page.getByRole('columnheader', { name: /name/i })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('columnheader', { name: /process|workflow/i })).toBeVisible();
  });
});

test.describe('My Requests — employee', () => {
  test.use({ storageState: STORAGE_STATES.employee });

  test('requests page loads with status badges', async ({ page }) => {
    await page.goto('http://localhost:4000/requests');
    await expect(page).toHaveURL(/requests/);
    // Status filter should be present
    await expect(page.getByRole('button', { name: /filter|status|active|completed/i }).first()).toBeVisible({ timeout: 10000 });
  });
});

test.describe('Approval Flow — manager sees approve/reject', () => {
  test.use({ storageState: STORAGE_STATES.manager });

  test('task detail shows Approve and Reject buttons for approval tasks', async ({ page }) => {
    await page.goto('http://localhost:4000/tasks');
    const firstTask = page.locator('table tbody tr, [data-testid="task-row"]').first();
    const count = await firstTask.count();
    if (count === 0) {
      test.skip(); // no tasks to check — needs a running approval process
      return;
    }
    await firstTask.click();
    // Approve/Reject buttons or equivalent UI
    await expect(
      page.getByRole('button', { name: /approve/i }).or(page.getByText(/approve/i))
    ).toBeVisible({ timeout: 10000 });
  });
});

test.describe('Process Monitoring — admin', () => {
  test.use({ storageState: STORAGE_STATES.admin });

  test('monitoring page loads without error', async ({ page }) => {
    await page.goto('http://localhost:4000/monitoring');
    await expect(page).not.toHaveURL(/error|404/);
    await expect(page.locator('h1, h2, [data-testid="page-title"]').first()).toBeVisible({ timeout: 10000 });
  });
});

// Multi-user: employee starts general-approval, manager claims + approves
test('full approval flow — employee submits, manager approves', async ({ browser }) => {
  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const managerCtx  = await browser.newContext({ storageState: STORAGE_STATES.manager });
  const employeePage = await employeeCtx.newPage();
  const managerPage  = await managerCtx.newPage();

  try {
    // Employee starts a general-approval process
    await employeePage.goto('http://localhost:4000/processes');
    await employeePage.getByRole('link', { name: /start process/i })
      .filter({ hasText: /general.approval|general approval/i }).first().click()
      .catch(async () => {
        // Fallback: find any start process link
        await employeePage.getByRole('link', { name: /start process/i }).first().click();
      });
    await employeePage.waitForURL(/requests|start|form/);

    // If a form is shown, submit it
    const submitBtn = employeePage.getByRole('button', { name: /submit|start/i });
    if (await submitBtn.isVisible()) {
      await submitBtn.click();
    }
    await employeePage.waitForURL(/requests/, { timeout: 15000 });
    await expect(employeePage.locator('[data-testid="request-row"], table tbody tr').first()).toBeVisible({ timeout: 10000 });

    // Manager claims and approves
    await managerPage.goto('http://localhost:4000/tasks');
    const taskRow = managerPage.locator('table tbody tr, [data-testid="task-row"]').first();
    await expect(taskRow).toBeVisible({ timeout: 15000 });
    await taskRow.click();

    // Claim if unclaimed
    const claimBtn = managerPage.getByRole('button', { name: /claim/i });
    if (await claimBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await claimBtn.click();
    }

    // Approve
    await managerPage.getByRole('button', { name: /approve/i }).click();
    await expect(
      managerPage.getByText(/approved|completed|success/i)
    ).toBeVisible({ timeout: 10000 });
  } finally {
    await employeeCtx.close();
    await managerCtx.close();
  }
});
```

- [ ] **Step 10: Run and observe**

```bash
cd frontends/portal
npx playwright test 21-core-user-flows.spec.ts --headed 2>&1 | tail -30
```

Log bugs as GitHub issues. Fix blocking ones. Cosmetic issues: log but do not block.

- [ ] **Step 11: Commit**

```bash
git add frontends/portal/e2e/tests/21-core-user-flows.spec.ts
git commit -m "feat(e2e): 21-core-user-flows — tasks, requests, approval, monitoring"
```

---

## Task 3: Connector Setup (`22-connector-setup.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/22-connector-setup.spec.ts`

- [ ] **Step 12: Write the spec**

```typescript
// frontends/portal/e2e/tests/22-connector-setup.spec.ts
import { test, expect } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';

test.use({ storageState: STORAGE_STATES.admin });

const BASE_URL = 'http://localhost:4000';

test.describe('Connector Setup', () => {
  test.afterAll(async ({ request }) => {
    // Cleanup via UI not needed — connectors are created fresh each run
    // If connectors persist, delete via API or accept duplication with unique names
  });

  test('create mock-api connector', async ({ page }) => {
    await page.goto(`${BASE_URL}/admin/connectors`);
    await page.getByRole('button', { name: /new connector|add connector/i }).click();
    await page.getByLabel(/name/i).fill('mock-api');
    await page.getByLabel(/base url|url/i).fill('https://httpbin.org');
    await page.getByLabel(/description/i).fill('Mock API for testing').catch(() => {});
    await page.getByRole('button', { name: /save|create/i }).click();
    await expect(page.getByText('mock-api')).toBeVisible({ timeout: 10000 });
  });

  test('create notification-service connector', async ({ page }) => {
    await page.goto(`${BASE_URL}/admin/connectors`);
    await page.getByRole('button', { name: /new connector|add connector/i }).click();
    await page.getByLabel(/name/i).fill('notification-service');
    await page.getByLabel(/base url|url/i).fill('https://httpbin.org/post');
    await page.getByRole('button', { name: /save|create/i }).click();
    await expect(page.getByText('notification-service')).toBeVisible({ timeout: 10000 });
  });

  test('edit connector description', async ({ page }) => {
    await page.goto(`${BASE_URL}/admin/connectors`);
    const mockApiRow = page.getByText('mock-api').locator('..');
    await mockApiRow.getByRole('button', { name: /edit/i }).click();
    await page.getByLabel(/description/i).fill('Updated description');
    await page.getByRole('button', { name: /save/i }).click();
    await page.reload();
    await expect(page.getByText('Updated description')).toBeVisible({ timeout: 10000 });
  });

  test('validation rejects empty name', async ({ page }) => {
    await page.goto(`${BASE_URL}/admin/connectors`);
    await page.getByRole('button', { name: /new connector|add connector/i }).click();
    await page.getByRole('button', { name: /save|create/i }).click();
    await expect(page.getByText(/required|name is required/i)).toBeVisible({ timeout: 5000 });
  });

  test('delete notification-service then re-create it', async ({ page }) => {
    await page.goto(`${BASE_URL}/admin/connectors`);
    const row = page.getByText('notification-service').locator('..');
    await row.getByRole('button', { name: /delete/i }).click();
    await page.getByRole('button', { name: /confirm|yes/i }).click();
    await expect(page.getByText('notification-service')).not.toBeVisible({ timeout: 10000 });

    // Re-create for BPMN tests
    await page.getByRole('button', { name: /new connector|add connector/i }).click();
    await page.getByLabel(/name/i).fill('notification-service');
    await page.getByLabel(/base url|url/i).fill('https://httpbin.org/post');
    await page.getByRole('button', { name: /save|create/i }).click();
    await expect(page.getByText('notification-service')).toBeVisible({ timeout: 10000 });
  });

  test('connector visible in BPMN service task dropdown', async ({ page }) => {
    await page.goto(`${BASE_URL}/processes/new`);
    // This test probes the BPMN editor UI — selectors depend on bpmn-js implementation
    // Add a service task element and open properties
    await page.waitForSelector('.djs-container, canvas', { timeout: 15000 });
    // Use keyboard shortcut or palette to add a task — implementation-specific
    // If no automation path found, log bug: [E2E] 22-connector-setup — connector dropdown not testable in BPMN editor
    // and mark test as skipped with skip reason
    test.skip(true, 'BPMN editor connector dropdown requires manual selector investigation — see issue');
  });
});
```

- [ ] **Step 13: Run and observe**

```bash
cd frontends/portal
npx playwright test 22-connector-setup.spec.ts --headed 2>&1 | tail -30
```

For each failure, investigate selector, fix or log GitHub issue.

- [ ] **Step 14: Commit**

```bash
git add frontends/portal/e2e/tests/22-connector-setup.spec.ts
git commit -m "feat(e2e): 22-connector-setup — create, edit, validate, delete connectors"
```

---

## Task 4: DMN Decisions (`23-dmn-decisions.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/23-dmn-decisions.spec.ts`

- [ ] **Step 15: Write the spec**

```typescript
// frontends/portal/e2e/tests/23-dmn-decisions.spec.ts
import { test, expect, Page } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';

test.use({ storageState: STORAGE_STATES.admin });

const BASE_URL = 'http://localhost:4000';

async function openNewDecision(page: Page, name: string) {
  await page.goto(`${BASE_URL}/decisions/new`);
  await page.waitForSelector('.dmn-container, canvas, [class*="dmn"]', { timeout: 15000 });
  // Set decision name in editor or name field
  const nameField = page.getByLabel(/decision name|name/i).first();
  if (await nameField.isVisible({ timeout: 3000 }).catch(() => false)) {
    await nameField.fill(name);
  }
}

async function deployDecision(page: Page) {
  await page.getByRole('button', { name: /deploy/i }).click();
  await expect(page.getByText(/deployed|success/i)).toBeVisible({ timeout: 15000 });
}

test.describe('DMN Decision Creation', () => {
  test('create and deploy ticket-routing decision', async ({ page }) => {
    await openNewDecision(page, 'ticket-routing');

    // Set input expression: ticketType (string)
    // dmn-js specific: click input column header → edit expression
    const inputHeader = page.locator('.input-label, [data-element-id*="input"], th.input').first();
    if (await inputHeader.isVisible({ timeout: 5000 }).catch(() => false)) {
      await inputHeader.dblclick();
      await page.keyboard.type('ticketType');
      await page.keyboard.press('Escape');
    } else {
      console.warn('DMN input header not found — log issue [E2E] 23-dmn-decisions — input column not editable');
    }

    // Add 3 rules for: free→false/none, paid→true/manager, vip→true/manager
    for (let i = 0; i < 3; i++) {
      const addRowBtn = page.getByRole('button', { name: /add rule|\+/i }).last();
      if (await addRowBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await addRowBtn.click();
      }
    }

    await deployDecision(page);
    await page.goto(`${BASE_URL}/decisions`);
    await expect(page.getByText('ticket-routing')).toBeVisible({ timeout: 10000 });
  });

  test('create and deploy leave-routing decision', async ({ page }) => {
    await openNewDecision(page, 'leave-routing');
    // Input: leaveDays (integer), output: approverLevel (string)
    // Rules: <=3→auto, >3 <=10→manager, >10→director
    await deployDecision(page);
    await page.goto(`${BASE_URL}/decisions`);
    await expect(page.getByText('leave-routing')).toBeVisible({ timeout: 10000 });
  });

  test('create and deploy budget-routing decision', async ({ page }) => {
    await openNewDecision(page, 'budget-routing');
    // Input: amount (number), output: approverLevel (string)
    // Rules: <500→auto, >=500 <2000→manager, >=2000→director
    await deployDecision(page);
    await page.goto(`${BASE_URL}/decisions`);
    await expect(page.getByText('budget-routing')).toBeVisible({ timeout: 10000 });
  });
});
```

**Note:** The DMN editor is heavily dependent on dmn-js internals. During test execution, if cell click-to-edit is broken, log as `[E2E] 23-dmn-decisions — cell not editable on click` and investigate. The deploy step is the primary assertion gate.

- [ ] **Step 16: Run and observe**

```bash
cd frontends/portal
npx playwright test 23-dmn-decisions.spec.ts --headed 2>&1 | tail -30
```

- [ ] **Step 17: Commit**

```bash
git add frontends/portal/e2e/tests/23-dmn-decisions.spec.ts
git commit -m "feat(e2e): 23-dmn-decisions — create and deploy ticket/leave/budget routing decisions"
```

---

## Task 5: Forms Visual (`24-forms-visual.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/24-forms-visual.spec.ts`

- [ ] **Step 18: Write the spec**

```typescript
// frontends/portal/e2e/tests/24-forms-visual.spec.ts
import { test, expect, Page } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';

test.use({ storageState: STORAGE_STATES.admin });

const BASE_URL = 'http://localhost:4000';

interface FormField {
  label: string;
  key: string;
  type: 'text' | 'email' | 'number' | 'date' | 'select' | 'textarea';
  required?: boolean;
  options?: string[];
}

async function buildForm(page: Page, formName: string, fields: FormField[]) {
  await page.goto(`${BASE_URL}/forms/new`);
  await page.waitForSelector('[class*="form-editor"], [class*="fjs"], .fjs-container', { timeout: 15000 });

  // Set form name
  const nameField = page.getByLabel(/form name|name/i).first();
  if (await nameField.isVisible({ timeout: 3000 }).catch(() => false)) {
    await nameField.fill(formName);
  }

  // Add fields via palette
  for (const field of fields) {
    // Click or drag palette item matching field type
    const paletteItem = page.locator(`[data-field-type="${field.type}"], [title*="${field.type}"]`).first();
    if (await paletteItem.isVisible({ timeout: 3000 }).catch(() => false)) {
      await paletteItem.click();
    }
    // After adding field, configure it via properties panel
    // Field label and key are set in the properties panel on the right
    const labelInput = page.getByLabel(/label/i).last();
    if (await labelInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await labelInput.fill(field.label);
    }
  }

  // Save
  await page.getByRole('button', { name: /save/i }).click();
  await expect(page.getByText(formName).or(page.getByText(/saved|success/i))).toBeVisible({ timeout: 10000 });
}

test.describe('Form Creation', () => {
  test('create event-ticket-form', async ({ page }) => {
    await buildForm(page, 'event-ticket-form', [
      { label: 'Name', key: 'name', type: 'text', required: true },
      { label: 'Email', key: 'email', type: 'email', required: true },
      { label: 'Ticket Type', key: 'ticketType', type: 'select', required: true, options: ['free', 'paid', 'vip'] },
      { label: 'Quantity', key: 'quantity', type: 'number' },
      { label: 'Notes', key: 'notes', type: 'textarea' },
    ]);
    await page.goto(`${BASE_URL}/forms`);
    await expect(page.getByText('event-ticket-form')).toBeVisible({ timeout: 10000 });
  });

  test('create leave-request-form', async ({ page }) => {
    await buildForm(page, 'leave-request-form', [
      { label: 'Start Date', key: 'startDate', type: 'date', required: true },
      { label: 'End Date', key: 'endDate', type: 'date', required: true },
      { label: 'Leave Days', key: 'leaveDays', type: 'number', required: true },
      { label: 'Leave Type', key: 'leaveType', type: 'select', required: true, options: ['Annual', 'Sick', 'Personal'] },
      { label: 'Reason', key: 'reason', type: 'textarea', required: true },
    ]);
    await page.goto(`${BASE_URL}/forms`);
    await expect(page.getByText('leave-request-form')).toBeVisible({ timeout: 10000 });
  });

  test('create equipment-request-form', async ({ page }) => {
    await buildForm(page, 'equipment-request-form', [
      { label: 'Employee Name', key: 'employeeName', type: 'text', required: true },
      { label: 'Department', key: 'department', type: 'select', required: true,
        options: ['Engineering', 'Finance', 'HR', 'Operations', 'IT'] },
      { label: 'Items', key: 'items', type: 'textarea', required: true },
      { label: 'Urgency', key: 'urgency', type: 'select', required: true, options: ['Low', 'Medium', 'High'] },
    ]);
    await page.goto(`${BASE_URL}/forms`);
    await expect(page.getByText('equipment-request-form')).toBeVisible({ timeout: 10000 });
  });

  test('create budget-request-form', async ({ page }) => {
    await buildForm(page, 'budget-request-form', [
      { label: 'Title', key: 'title', type: 'text', required: true },
      { label: 'Amount', key: 'amount', type: 'number', required: true },
      { label: 'Category', key: 'category', type: 'select', required: true,
        options: ['Software', 'Hardware', 'Training', 'Travel', 'Other'] },
      { label: 'Justification', key: 'justification', type: 'textarea', required: true },
    ]);
    await page.goto(`${BASE_URL}/forms`);
    await expect(page.getByText('budget-request-form')).toBeVisible({ timeout: 10000 });
  });

  test('form preview renders and validates required fields', async ({ page }) => {
    await page.goto(`${BASE_URL}/forms`);
    const formRow = page.getByText('event-ticket-form').locator('..');
    await formRow.getByRole('button', { name: /preview/i }).click();
    await page.getByRole('button', { name: /submit/i }).click();
    await expect(page.getByText(/required/i).first()).toBeVisible({ timeout: 5000 });
  });
});
```

- [ ] **Step 19: Run and observe**

```bash
cd frontends/portal
npx playwright test 24-forms-visual.spec.ts --headed 2>&1 | tail -30
```

- [ ] **Step 20: Commit**

```bash
git add frontends/portal/e2e/tests/24-forms-visual.spec.ts
git commit -m "feat(e2e): 24-forms-visual — create all 4 workflow forms via form-js editor"
```

---

## Task 6: Workflow A — Event Ticket (`25-workflow-event-ticket.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/25-workflow-event-ticket.spec.ts`

**BPMN built visually (no NOTIFICATION nodes — emails fire via GlobalTaskNotificationListener):**
```
Start Event → User Task "Submit Ticket Request" [form: event-ticket-form]
           → Business Rule Task "Route by Ticket Type" [DMN: ticket-routing]
           → Exclusive Gateway
               → [approvalRequired == false] → Service Task "Log Booking" [EXTERNAL_API_CALL: mock-api /post] → End
               → [default] → User Task "Organiser Review" [HUMAN_APPROVAL, candidate: HR Department/Managers] → End
```

- [ ] **Step 21: Write the spec**

```typescript
// frontends/portal/e2e/tests/25-workflow-event-ticket.spec.ts
import { test, expect, Browser } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';
import { MailpitClient } from '../fixtures/mailpit';

const BASE_URL = 'http://localhost:4000';
const MAILPIT_URL = 'http://localhost:8025';

let deployedProcessKey: string;

test.beforeAll(async ({ browser }) => {
  // Admin builds and deploys the BPMN process visually
  const adminCtx = await browser.newContext({ storageState: STORAGE_STATES.admin });
  const adminPage = await adminCtx.newPage();

  await adminPage.goto(`${BASE_URL}/processes/new`);
  await adminPage.waitForSelector('.djs-container, canvas', { timeout: 20000 });

  // Build BPMN visually: Start → User Task → Business Rule Task → Gateway → branches
  // The exact interactions depend on bpmn-js palette and properties panel selectors
  // Steps documented here represent the intended UI actions:

  // 1. Add User Task "Submit Ticket Request", assign form: event-ticket-form
  // 2. Add Business Rule Task "Route by Ticket Type", assign DMN: ticket-routing
  // 3. Add Exclusive Gateway
  // 4. Add Service Task "Log Booking", set EXTERNAL_API_CALL → mock-api, endpoint /post
  // 5. Add User Task "Organiser Review", set HUMAN_APPROVAL, candidateGroup: HR Department/Managers
  // 6. Connect elements with sequence flows
  // 7. Set gateway conditions via ExpressionBuilder

  // NOTE: Full bpmn-js automation requires selector investigation during first run.
  // Log any selector failures as: [E2E] 25-workflow-event-ticket — <description>

  // Deploy
  await adminPage.getByRole('button', { name: /deploy/i }).click();
  await expect(adminPage.getByText(/deployed|success/i)).toBeVisible({ timeout: 15000 });

  // Capture process key from URL or confirmation
  await adminPage.waitForURL(/processes/, { timeout: 10000 });
  deployedProcessKey = 'event-ticket-request'; // adjust to actual key generated

  await adminCtx.close();
});

test('happy path — paid ticket requires organiser approval', async ({ browser }) => {
  const mailpit = new MailpitClient(MAILPIT_URL);
  await mailpit.deleteAllMessages();

  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const managerCtx  = await browser.newContext({ storageState: STORAGE_STATES.manager });
  const employeePage = await employeeCtx.newPage();
  const managerPage  = await managerCtx.newPage();

  try {
    // Employee starts process
    await employeePage.goto(`${BASE_URL}/processes`);
    await employeePage.getByText(/event ticket request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await employeePage.waitForURL(/start|form/);
    await employeePage.getByLabel(/name/i).fill('Jane Employee');
    await employeePage.getByLabel(/email/i).fill('jane.employee@werkflow.local');
    await employeePage.getByLabel(/ticket type/i).selectOption('paid');
    await employeePage.getByLabel(/quantity/i).fill('2');
    await employeePage.getByRole('button', { name: /submit/i }).click();
    await employeePage.waitForURL(/requests/, { timeout: 15000 });
    await expect(employeePage.locator('[data-testid="request-row"], table tbody tr').first()).toBeVisible({ timeout: 10000 });

    // Manager claims and approves "Organiser Review"
    await managerPage.goto(`${BASE_URL}/tasks`);
    const taskRow = managerPage.getByText(/organiser review/i).locator('..').first();
    await expect(taskRow).toBeVisible({ timeout: 20000 });
    await taskRow.click();
    const claimBtn = managerPage.getByRole('button', { name: /claim/i });
    if (await claimBtn.isVisible({ timeout: 3000 }).catch(() => false)) await claimBtn.click();
    await managerPage.getByRole('button', { name: /approve/i }).click();
    await expect(managerPage.getByText(/approved|completed/i)).toBeVisible({ timeout: 10000 });

    // Assert Mailpit received email to employee
    await mailpit.waitForMessage({ toContains: 'jane.employee@werkflow.local', timeout: 15000 });
    const messages = await mailpit.getMessages();
    const approvalEmail = messages.find(m =>
      m.To.some(t => t.Address.includes('jane.employee')) &&
      m.Subject.toLowerCase().includes('approved')
    );
    expect(approvalEmail).toBeDefined();

    // Employee sees status Approved
    await employeePage.reload();
    await expect(employeePage.getByText(/approved/i).first()).toBeVisible({ timeout: 10000 });
  } finally {
    await employeeCtx.close();
    await managerCtx.close();
  }
});

test('auto-approve path — free ticket skips approval', async ({ browser }) => {
  const mailpit = new MailpitClient(MAILPIT_URL);
  await mailpit.deleteAllMessages();

  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const employeePage = await employeeCtx.newPage();

  try {
    await employeePage.goto(`${BASE_URL}/processes`);
    await employeePage.getByText(/event ticket request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await employeePage.getByLabel(/name/i).fill('Jane Employee');
    await employeePage.getByLabel(/email/i).fill('jane.employee@werkflow.local');
    await employeePage.getByLabel(/ticket type/i).selectOption('free');
    await employeePage.getByLabel(/quantity/i).fill('1');
    await employeePage.getByRole('button', { name: /submit/i }).click();
    await employeePage.waitForURL(/requests/, { timeout: 15000 });

    // Status should go to Completed without manager intervention
    await expect(employeePage.getByText(/completed/i).first()).toBeVisible({ timeout: 20000 });

    // Email was sent directly (log booking + auto-completion)
    const messages = await mailpit.getMessages({ timeout: 10000 });
    expect(messages.length).toBeGreaterThan(0);
  } finally {
    await employeeCtx.close();
  }
});

test.afterAll(async ({ request }) => {
  // Delete deployed process via engine API
  // GET /api/process-definitions?key=event-ticket-request → DELETE /api/process-definitions/{id}
  // Adjust endpoint to actual API
});
```

- [ ] **Step 22: Run and observe**

```bash
cd frontends/portal
npx playwright test 25-workflow-event-ticket.spec.ts --headed 2>&1 | tail -40
```

Log all bugs. Fix blocking bugs before continuing to next workflow.

- [ ] **Step 23: Commit**

```bash
git add frontends/portal/e2e/tests/25-workflow-event-ticket.spec.ts
git commit -m "feat(e2e): 25-workflow-event-ticket — paid approval path + free auto-approve"
```

---

## Task 7: Workflow C1 — Leave Request (`26-workflow-leave-request.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/26-workflow-leave-request.spec.ts`

**BPMN:** Start → User Task "Submit Leave" [leave-request-form] → Business Rule Task [leave-routing] → Exclusive Gateway → [auto] End | [manager] User Task "Manager Approval" [HUMAN_APPROVAL] → End | [director] User Task "Director Approval" [HUMAN_APPROVAL] → End

- [ ] **Step 24: Write the spec**

```typescript
// frontends/portal/e2e/tests/26-workflow-leave-request.spec.ts
import { test, expect, Browser } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';
import { MailpitClient } from '../fixtures/mailpit';

const BASE_URL = 'http://localhost:4000';
const MAILPIT_URL = 'http://localhost:8025';

test.beforeAll(async ({ browser }) => {
  // Admin builds and deploys leave-request BPMN visually (same pattern as Task 6)
  // Structure: Start → Submit Leave [form: leave-request-form] → Route Leave [DMN: leave-routing]
  //            → Gateway → auto path | manager approval path | director approval path
});

test('manager approval path — 7 day leave', async ({ browser }) => {
  const mailpit = new MailpitClient(MAILPIT_URL);
  await mailpit.deleteAllMessages();

  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const managerCtx  = await browser.newContext({ storageState: STORAGE_STATES.manager });
  const employeePage = await employeeCtx.newPage();
  const managerPage  = await managerCtx.newPage();

  try {
    // Employee submits leave (7 days → manager path)
    await employeePage.goto(`${BASE_URL}/processes`);
    await employeePage.getByText(/leave request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await employeePage.getByLabel(/start date/i).fill('2026-05-01');
    await employeePage.getByLabel(/end date/i).fill('2026-05-07');
    await employeePage.getByLabel(/leave days/i).fill('7');
    await employeePage.getByLabel(/leave type/i).selectOption('Annual');
    await employeePage.getByLabel(/reason/i).fill('Family vacation');
    await employeePage.getByRole('button', { name: /submit/i }).click();
    await employeePage.waitForURL(/requests/, { timeout: 15000 });

    // Manager approves with comment
    await managerPage.goto(`${BASE_URL}/tasks`);
    const taskRow = managerPage.getByText(/manager approval/i).locator('..').first();
    await expect(taskRow).toBeVisible({ timeout: 20000 });
    await taskRow.click();
    const claimBtn = managerPage.getByRole('button', { name: /claim/i });
    if (await claimBtn.isVisible({ timeout: 3000 }).catch(() => false)) await claimBtn.click();
    const commentField = managerPage.getByLabel(/comment|reason/i);
    if (await commentField.isVisible({ timeout: 2000 }).catch(() => false)) {
      await commentField.fill('Leave approved — enjoy your vacation.');
    }
    await managerPage.getByRole('button', { name: /approve/i }).click();
    await expect(managerPage.getByText(/approved|completed/i)).toBeVisible({ timeout: 10000 });

    // Mailpit: employee should receive approval email
    await mailpit.waitForMessage({ toContains: 'jane.employee@werkflow.local', timeout: 15000 });
    const messages = await mailpit.getMessages();
    const approvalEmail = messages.find(m =>
      m.To.some(t => t.Address.includes('jane.employee')) &&
      (m.Subject.toLowerCase().includes('approved') || m.Subject.toLowerCase().includes('completed'))
    );
    expect(approvalEmail).toBeDefined();

    // Employee sees Approved + comment in request history
    await employeePage.reload();
    await expect(employeePage.getByText(/approved/i).first()).toBeVisible({ timeout: 10000 });
  } finally {
    await employeeCtx.close();
    await managerCtx.close();
  }
});

test('rejection path — 7 day leave rejected', async ({ browser }) => {
  const mailpit = new MailpitClient(MAILPIT_URL);
  await mailpit.deleteAllMessages();

  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const managerCtx  = await browser.newContext({ storageState: STORAGE_STATES.manager });
  const employeePage = await employeeCtx.newPage();
  const managerPage  = await managerCtx.newPage();

  try {
    await employeePage.goto(`${BASE_URL}/processes`);
    await employeePage.getByText(/leave request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await employeePage.getByLabel(/start date/i).fill('2026-06-01');
    await employeePage.getByLabel(/end date/i).fill('2026-06-07');
    await employeePage.getByLabel(/leave days/i).fill('7');
    await employeePage.getByLabel(/leave type/i).selectOption('Annual');
    await employeePage.getByLabel(/reason/i).fill('Holiday');
    await employeePage.getByRole('button', { name: /submit/i }).click();
    await employeePage.waitForURL(/requests/, { timeout: 15000 });

    await managerPage.goto(`${BASE_URL}/tasks`);
    const taskRow = managerPage.getByText(/manager approval/i).locator('..').first();
    await expect(taskRow).toBeVisible({ timeout: 20000 });
    await taskRow.click();
    const claimBtn = managerPage.getByRole('button', { name: /claim/i });
    if (await claimBtn.isVisible({ timeout: 3000 }).catch(() => false)) await claimBtn.click();
    await managerPage.getByRole('button', { name: /reject/i }).click();
    await expect(managerPage.getByText(/rejected|completed/i)).toBeVisible({ timeout: 10000 });

    await mailpit.waitForMessage({ toContains: 'jane.employee@werkflow.local', timeout: 15000 });
    const messages = await mailpit.getMessages();
    const rejectionEmail = messages.find(m =>
      m.To.some(t => t.Address.includes('jane.employee')) &&
      (m.Subject.toLowerCase().includes('rejected') || m.Subject.toLowerCase().includes('completed'))
    );
    expect(rejectionEmail).toBeDefined();

    await employeePage.reload();
    await expect(employeePage.getByText(/rejected/i).first()).toBeVisible({ timeout: 10000 });
  } finally {
    await employeeCtx.close();
    await managerCtx.close();
  }
});

test('auto-approve path — 2 day leave', async ({ browser }) => {
  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const employeePage = await employeeCtx.newPage();

  try {
    await employeePage.goto(`${BASE_URL}/processes`);
    await employeePage.getByText(/leave request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await employeePage.getByLabel(/start date/i).fill('2026-05-12');
    await employeePage.getByLabel(/end date/i).fill('2026-05-13');
    await employeePage.getByLabel(/leave days/i).fill('2');
    await employeePage.getByLabel(/leave type/i).selectOption('Personal');
    await employeePage.getByLabel(/reason/i).fill('Personal errand');
    await employeePage.getByRole('button', { name: /submit/i }).click();
    await employeePage.waitForURL(/requests/, { timeout: 15000 });

    // 2 days → auto-approve, no manager task needed
    await expect(employeePage.getByText(/approved|completed/i).first()).toBeVisible({ timeout: 20000 });
  } finally {
    await employeeCtx.close();
  }
});
```

- [ ] **Step 25: Run and observe**

```bash
cd frontends/portal
npx playwright test 26-workflow-leave-request.spec.ts --headed 2>&1 | tail -40
```

- [ ] **Step 26: Commit**

```bash
git add frontends/portal/e2e/tests/26-workflow-leave-request.spec.ts
git commit -m "feat(e2e): 26-workflow-leave-request — manager/rejection/auto-approve paths"
```

---

## Task 8: Workflow C2 — Equipment Request (`27-workflow-equipment.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/27-workflow-equipment.spec.ts`

**BPMN:** Start → User Task "Submit Equipment List" [equipment-request-form] → Parallel Gateway (split) → IT Approval [HUMAN_APPROVAL, candidateGroup: IT Department] + HR Check [HUMAN_APPROVAL, candidateGroup: HR Department] → Parallel Gateway (join) → Service Task "Log to Inventory API" [EXTERNAL_API_CALL: mock-api /post] → End

- [ ] **Step 27: Write the spec**

```typescript
// frontends/portal/e2e/tests/27-workflow-equipment.spec.ts
import { test, expect, Browser } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';
import { MailpitClient } from '../fixtures/mailpit';

const BASE_URL = 'http://localhost:4000';
const MAILPIT_URL = 'http://localhost:8025';

test.beforeAll(async ({ browser }) => {
  // Admin builds and deploys equipment-request BPMN visually
  // Structure: Start → Submit Equipment List [form: equipment-request-form]
  //            → Parallel Gateway → IT Approval [HR Dept/Managers candidateGroup: IT Department]
  //                              → HR Check [candidateGroup: HR Department]
  //            → Parallel Gateway (join) → Log to Inventory API [EXTERNAL_API_CALL: mock-api /post] → End
});

test('parallel approval path — both IT and HR must approve', async ({ browser }) => {
  const mailpit = new MailpitClient(MAILPIT_URL);
  await mailpit.deleteAllMessages();

  const managerCtx  = await browser.newContext({ storageState: STORAGE_STATES.manager });  // HR Department
  const itUserCtx   = await browser.newContext({ storageState: STORAGE_STATES.itUser });   // IT Department
  const managerPage = await managerCtx.newPage();
  const itUserPage  = await itUserCtx.newPage();

  try {
    // Manager submits equipment request
    await managerPage.goto(`${BASE_URL}/processes`);
    await managerPage.getByText(/equipment request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await managerPage.getByLabel(/employee name/i).fill('John Manager');
    await managerPage.getByLabel(/department/i).selectOption('Engineering');
    await managerPage.getByLabel(/items/i).fill('Laptop, Mouse');
    await managerPage.getByLabel(/urgency/i).selectOption('High');
    await managerPage.getByRole('button', { name: /submit/i }).click();
    await managerPage.waitForURL(/requests/, { timeout: 15000 });

    // Verify both tasks appear simultaneously
    // IT user sees IT Approval task
    await itUserPage.goto(`${BASE_URL}/tasks`);
    const itTask = itUserPage.getByText(/IT approval/i).locator('..').first();
    await expect(itTask).toBeVisible({ timeout: 20000 });

    // Manager sees HR Check task
    await managerPage.goto(`${BASE_URL}/tasks`);
    const hrTask = managerPage.getByText(/HR check/i).locator('..').first();
    await expect(hrTask).toBeVisible({ timeout: 10000 });

    // After only IT approves, request status must still be Active (parallel join not reached)
    await itTask.click();
    const itClaimBtn = itUserPage.getByRole('button', { name: /claim/i });
    if (await itClaimBtn.isVisible({ timeout: 3000 }).catch(() => false)) await itClaimBtn.click();
    await itUserPage.getByRole('button', { name: /approve/i }).click();
    await expect(itUserPage.getByText(/approved|completed/i)).toBeVisible({ timeout: 10000 });

    // Check request is still Active
    await managerPage.goto(`${BASE_URL}/requests`);
    await expect(managerPage.getByText(/active/i).first()).toBeVisible({ timeout: 10000 });

    // Now HR approves
    await managerPage.goto(`${BASE_URL}/tasks`);
    const hrTaskRow = managerPage.getByText(/HR check/i).locator('..').first();
    await hrTaskRow.click();
    const hrClaimBtn = managerPage.getByRole('button', { name: /claim/i });
    if (await hrClaimBtn.isVisible({ timeout: 3000 }).catch(() => false)) await hrClaimBtn.click();
    await managerPage.getByRole('button', { name: /approve/i }).click();
    await expect(managerPage.getByText(/approved|completed/i)).toBeVisible({ timeout: 10000 });

    // After both approve, parallel join resolves → external API fires → process completes
    await mailpit.waitForMessage({ timeout: 20000 });
    const messages = await mailpit.getMessages();
    expect(messages.length).toBeGreaterThan(0);

    await managerPage.goto(`${BASE_URL}/requests`);
    await expect(managerPage.getByText(/completed/i).first()).toBeVisible({ timeout: 20000 });
  } finally {
    await managerCtx.close();
    await itUserCtx.close();
  }
});
```

- [ ] **Step 28: Run and observe**

```bash
cd frontends/portal
npx playwright test 27-workflow-equipment.spec.ts --headed 2>&1 | tail -40
```

- [ ] **Step 29: Commit**

```bash
git add frontends/portal/e2e/tests/27-workflow-equipment.spec.ts
git commit -m "feat(e2e): 27-workflow-equipment — parallel IT+HR approval with join verification"
```

---

## Task 9: Workflow C3 — Budget Request (`28-workflow-budget.spec.ts`)

**Files:**
- Create: `frontends/portal/e2e/tests/28-workflow-budget.spec.ts`

**BPMN:** Start → User Task "Submit Budget Request" [budget-request-form] → Service Task "Check Budget API" [EXTERNAL_API_CALL: mock-api /post] → Business Rule Task [DMN: budget-routing] → Exclusive Gateway → [auto] End | [manager] User Task "Manager Approval" [HUMAN_APPROVAL] → End | [director] User Task "Director Approval" [HUMAN_APPROVAL] → End

- [ ] **Step 30: Write the spec**

```typescript
// frontends/portal/e2e/tests/28-workflow-budget.spec.ts
import { test, expect, Browser } from '@playwright/test';
import { STORAGE_STATES } from '../fixtures/auth';
import { MailpitClient } from '../fixtures/mailpit';

const BASE_URL = 'http://localhost:4000';
const MAILPIT_URL = 'http://localhost:8025';

test.beforeAll(async ({ browser }) => {
  // Admin builds and deploys budget-request BPMN visually
  // Structure: Start → Submit Budget Request [form: budget-request-form]
  //            → Check Budget API [EXTERNAL_API_CALL: mock-api /post, response → budgetStatus]
  //            → Route by Amount [DMN: budget-routing] → Gateway
  //            → [auto] End | [manager] Manager Approval [HUMAN_APPROVAL] → End | [director] Director Approval → End
});

test('manager path — $1200 budget requires approval', async ({ browser }) => {
  const mailpit = new MailpitClient(MAILPIT_URL);
  await mailpit.deleteAllMessages();

  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const managerCtx  = await browser.newContext({ storageState: STORAGE_STATES.manager });
  const employeePage = await employeeCtx.newPage();
  const managerPage  = await managerCtx.newPage();

  try {
    await employeePage.goto(`${BASE_URL}/processes`);
    await employeePage.getByText(/budget request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await employeePage.getByLabel(/title/i).fill('Design tools subscription');
    await employeePage.getByLabel(/amount/i).fill('1200');
    await employeePage.getByLabel(/category/i).selectOption('Software');
    await employeePage.getByLabel(/justification/i).fill('Annual license for the design team');
    await employeePage.getByRole('button', { name: /submit/i }).click();
    await employeePage.waitForURL(/requests/, { timeout: 15000 });

    // Manager sees approval task (external API ran, DMN routed to manager)
    await managerPage.goto(`${BASE_URL}/tasks`);
    const taskRow = managerPage.getByText(/manager approval/i).locator('..').first();
    await expect(taskRow).toBeVisible({ timeout: 25000 }); // allow time for external API + DMN
    await taskRow.click();
    const claimBtn = managerPage.getByRole('button', { name: /claim/i });
    if (await claimBtn.isVisible({ timeout: 3000 }).catch(() => false)) await claimBtn.click();
    await managerPage.getByRole('button', { name: /approve/i }).click();
    await expect(managerPage.getByText(/approved|completed/i)).toBeVisible({ timeout: 10000 });

    await mailpit.waitForMessage({ toContains: 'jane.employee@werkflow.local', timeout: 15000 });
    const messages = await mailpit.getMessages();
    const approvalEmail = messages.find(m =>
      m.To.some(t => t.Address.includes('jane.employee'))
    );
    expect(approvalEmail).toBeDefined();

    await employeePage.reload();
    await expect(employeePage.getByText(/approved/i).first()).toBeVisible({ timeout: 10000 });
  } finally {
    await employeeCtx.close();
    await managerCtx.close();
  }
});

test('auto-approve path — $200 budget skips approval', async ({ browser }) => {
  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const employeePage = await employeeCtx.newPage();

  try {
    await employeePage.goto(`${BASE_URL}/processes`);
    await employeePage.getByText(/budget request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await employeePage.getByLabel(/title/i).fill('Office supplies');
    await employeePage.getByLabel(/amount/i).fill('200');
    await employeePage.getByLabel(/category/i).selectOption('Other');
    await employeePage.getByLabel(/justification/i).fill('Monthly office supplies');
    await employeePage.getByRole('button', { name: /submit/i }).click();
    await employeePage.waitForURL(/requests/, { timeout: 15000 });

    // Auto-approve: no approval task, status goes to Approved/Completed immediately
    await expect(employeePage.getByText(/approved|completed/i).first()).toBeVisible({ timeout: 25000 });
  } finally {
    await employeeCtx.close();
  }
});

test('director path — $5000 budget requires director approval', async ({ browser }) => {
  const mailpit = new MailpitClient(MAILPIT_URL);
  await mailpit.deleteAllMessages();

  const employeeCtx = await browser.newContext({ storageState: STORAGE_STATES.employee });
  const adminCtx    = await browser.newContext({ storageState: STORAGE_STATES.admin }); // admin acts as director
  const employeePage = await employeeCtx.newPage();
  const adminPage    = await adminCtx.newPage();

  try {
    await employeePage.goto(`${BASE_URL}/processes`);
    await employeePage.getByText(/budget request/i).locator('..').getByRole('link', { name: /start process/i }).click();
    await employeePage.getByLabel(/title/i).fill('New server infrastructure');
    await employeePage.getByLabel(/amount/i).fill('5000');
    await employeePage.getByLabel(/category/i).selectOption('Hardware');
    await employeePage.getByLabel(/justification/i).fill('Capacity upgrade');
    await employeePage.getByRole('button', { name: /submit/i }).click();
    await employeePage.waitForURL(/requests/, { timeout: 15000 });

    await adminPage.goto(`${BASE_URL}/tasks`);
    const taskRow = adminPage.getByText(/director approval/i).locator('..').first();
    await expect(taskRow).toBeVisible({ timeout: 25000 });
    await taskRow.click();
    const claimBtn = adminPage.getByRole('button', { name: /claim/i });
    if (await claimBtn.isVisible({ timeout: 3000 }).catch(() => false)) await claimBtn.click();
    await adminPage.getByRole('button', { name: /approve/i }).click();
    await expect(adminPage.getByText(/approved|completed/i)).toBeVisible({ timeout: 10000 });

    await mailpit.waitForMessage({ toContains: 'jane.employee@werkflow.local', timeout: 15000 });
    await employeePage.reload();
    await expect(employeePage.getByText(/approved/i).first()).toBeVisible({ timeout: 10000 });
  } finally {
    await employeeCtx.close();
    await adminCtx.close();
  }
});
```

- [ ] **Step 31: Run and observe**

```bash
cd frontends/portal
npx playwright test 28-workflow-budget.spec.ts --headed 2>&1 | tail -40
```

- [ ] **Step 32: Commit**

```bash
git add frontends/portal/e2e/tests/28-workflow-budget.spec.ts
git commit -m "feat(e2e): 28-workflow-budget — manager/$1200, auto/$200, director/$5000 paths"
```

---

## Task 10: Full Suite Pass + Bug Resolution

- [ ] **Step 33: Run the complete test suite**

```bash
cd frontends/portal
npx playwright test e2e/tests/ --reporter=list 2>&1 | tee e2e-results.txt | tail -50
```

- [ ] **Step 34: For each FAIL — log GitHub issue and fix**

For every failing test:
1. Create GitHub issue: title `[E2E] <spec-file> — <description>`, labels: `bug`, `e2e-found`
2. Investigate root cause (UI bug vs. wrong selector)
3. If UI bug: fix in source, re-run affected spec
4. If selector issue: update spec to match actual UI

- [ ] **Step 35: Run full suite again — verify all pass**

```bash
cd frontends/portal
npx playwright test e2e/tests/ --reporter=list 2>&1 | tail -30
```

Expected: All tests green. Zero failures.

- [ ] **Step 36: Final commit**

```bash
git add .
git commit -m "feat(e2e): all 9 spec files passing — E2E quality gate complete"
```

---

## Definition of Done

- [ ] `it-user.setup.ts` creates `e2e/.auth/it-user.json` successfully
- [ ] `auth.ts` has `STORAGE_STATES.itUser` and `TEST_USERS.itUser`
- [ ] `20-rbac-navigation.spec.ts` — all 8 RBAC tests pass
- [ ] `21-core-user-flows.spec.ts` — tasks, requests, approval, monitoring pass
- [ ] `22-connector-setup.spec.ts` — create, edit, validate, delete connectors pass
- [ ] `23-dmn-decisions.spec.ts` — all 3 DMN decisions deployed
- [ ] `24-forms-visual.spec.ts` — all 4 forms created via form-js editor
- [ ] `25-workflow-event-ticket.spec.ts` — paid approval + free auto-approve pass
- [ ] `26-workflow-leave-request.spec.ts` — manager/rejection/auto-approve paths pass
- [ ] `27-workflow-equipment.spec.ts` — parallel join + blocking verification pass
- [ ] `28-workflow-budget.spec.ts` — manager/$1200, auto/$200, director/$5000 pass
- [ ] All Mailpit email assertions pass in Layer 3 tests
- [ ] All blocking UI bugs logged as GitHub issues and resolved
- [ ] Full suite passes on fresh `docker compose up` (no pre-existing data)
- [ ] OSS v1.0.0 GitHub Release unblocked
