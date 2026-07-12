import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4173'

export default defineConfig({
  testDir: './e2e/ux',
  snapshotPathTemplate: '{testDir}/__screenshots__/{testFilePath}/{platform}/{projectName}/{arg}{ext}',
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : [['list']],
  use: { baseURL, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'phone-light', use: { ...devices['Pixel 5'], viewport: { width: 390, height: 844 }, colorScheme: 'light' } },
    { name: 'tablet-dark', use: { ...devices['Desktop Chrome'], viewport: { width: 768, height: 1024 }, colorScheme: 'dark', hasTouch: true } },
    { name: 'desktop-light', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, colorScheme: 'light' } },
    { name: 'desktop-reduced-motion', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, colorScheme: 'dark', reducedMotion: 'reduce' } },
  ],
  webServer: process.env.E2E_BASE_URL ? undefined : {
    command: 'npm run dev -- --host 127.0.0.1 --port 4173',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
  },
})
