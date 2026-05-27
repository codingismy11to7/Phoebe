import { defineConfig, devices } from '@playwright/test';

const webPort = process.env.PHOEBE_WEB_PORT ?? '8099';
const webBaseURL = process.env.PHOEBE_WEB_BASE_URL ?? `http://127.0.0.1:${webPort}`;
const webServerCommand = process.env.PHOEBE_WEB_SERVER_COMMAND ?? (
  process.env.CI
    ? `PHOEBE_WEB_PORT=${webPort} ./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
    : `PHOEBE_WEB_PORT=${webPort} ./gradlew :composeApp:wasmJsBrowserDevelopmentRun --no-daemon`
);

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
    baseURL: webBaseURL,
    browserName: 'chromium',
    viewport: { width: 1365, height: 900 },
    deviceScaleFactor: 1,
    colorScheme: 'dark',
  },
  webServer: {
    command: webServerCommand,
    url: webBaseURL,
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
