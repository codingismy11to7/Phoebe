import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './web-screenshot-tests',
  snapshotPathTemplate: '{testDir}/{testFilePath}-snapshots/{arg}{ext}',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : [['list']],
  timeout: 90_000,
  expect: {
    timeout: 15_000,
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.01,
    },
  },
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:8099',
    browserName: 'chromium',
    viewport: { width: 1365, height: 900 },
    deviceScaleFactor: 1,
    colorScheme: 'dark',
  },
  webServer: {
    command: process.env.CI
      ? 'PHOEBE_WEB_PORT=8099 ./gradlew :composeApp:wasmJsBrowserDevelopmentRun'
      : 'PHOEBE_WEB_PORT=8099 ./gradlew :composeApp:wasmJsBrowserDevelopmentRun --no-daemon',
    url: 'http://127.0.0.1:8099',
    reuseExistingServer: false,
    timeout: process.env.CI ? 600_000 : 180_000,
  },
  projects: [
    {
      name: 'chromium-linux',
      use: { browserName: 'chromium' },
    },
  ],
});
