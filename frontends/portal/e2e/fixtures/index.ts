import { test as base, Page } from '@playwright/test'

type Fixtures = {
  authenticatedPage: Page
}

export const test = base.extend<Fixtures>({
  authenticatedPage: async ({ page }, use) => {
    await use(page)
  },
})

export { expect } from '@playwright/test'
