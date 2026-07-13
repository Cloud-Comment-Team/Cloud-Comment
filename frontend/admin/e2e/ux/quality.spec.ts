import { expect, test } from '@playwright/test'
import { expectNoSeriousAccessibilityViolations } from './accessibility'

test('экран входа доступен с клавиатуры и не содержит серьёзных нарушений', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: /вход/i })).toBeVisible()
  await page.keyboard.press('Tab')
  const focused = page.locator(':focus-visible')
  await expect(focused).toBeVisible()

  await expectNoSeriousAccessibilityViolations(page)
  await expect(page).toHaveScreenshot('login.png', { animations: 'disabled', fullPage: true })
})

test('уменьшение движения отключает декоративные переходы', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop-reduced-motion')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/login')
  const duration = await page.locator('body').evaluate(() => {
    const probe = document.createElement('button')
    probe.className = 'cc-button-primary'
    document.body.append(probe)
    const value = getComputedStyle(probe).transitionDuration
    probe.remove()
    return value
  })
  expect(Number.parseFloat(duration) || 0).toBeLessThanOrEqual(0.001)
})
