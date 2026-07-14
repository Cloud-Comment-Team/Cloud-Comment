import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4173'

export default defineConfig({
  testDir: './e2e/ux',
  testMatch: 'widget-isolation.spec.ts',
  grep: /(?:dedicated iframe изолирует стили|одинаковый widget и API origin завершается fail closed|навигация A→B сохраняет site bearer|гость публикует комментарий без регистрации)/u,
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI
    ? [['html', { open: 'never', outputFolder: 'playwright-report/widget-isolation' }], ['list']]
    : [['list']],
  outputDir: 'test-results/widget-isolation',
  use: { baseURL, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'widget-chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'widget-firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'widget-webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: process.env.E2E_BASE_URL ? undefined : {
    command: 'npm run dev -- --host 127.0.0.1 --port 4173',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
  },
})
