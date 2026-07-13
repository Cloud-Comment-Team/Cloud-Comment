import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'

export async function expectNoSeriousAccessibilityViolations(page: Page) {
  await page.waitForTimeout(250)
  await page.evaluate(() => {
    for (const animation of document.getAnimations()) {
      if (animation.effect?.getTiming().iterations !== Infinity) animation.finish()
    }
  })
  const results = await new AxeBuilder({ page }).analyze()
  const violations = results.violations.filter((violation) => ['critical', 'serious'].includes(violation.impact ?? ''))
  expect(violations).toEqual([])
}
