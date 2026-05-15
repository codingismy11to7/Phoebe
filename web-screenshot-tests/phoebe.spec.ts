import { expect, type Page, test } from '@playwright/test';

const coreScenarios = [
  'Home',
  'Library',
  'Playlist',
  'Artist',
  'Album',
  'CollectionValues',
  'CollectionItems',
  'Search',
  'Player',
  'Settings',
  'SignIn',
] as const;

const lightScenarios = ['Home', 'Library', 'Search', 'Player'] as const;

for (const scenario of coreScenarios) {
  test(`web ${scenario} dark`, async ({ page }) => {
    await openScenario(page, scenario, 'dark');
    await expect(page).toHaveScreenshot(`web-${scenario.toLowerCase()}-dark.png`, {
      animations: 'disabled',
      fullPage: true,
    });
  });
}

for (const scenario of lightScenarios) {
  test(`web ${scenario} light`, async ({ page }) => {
    await openScenario(page, scenario, 'light');
    await expect(page).toHaveScreenshot(`web-${scenario.toLowerCase()}-light.png`, {
      animations: 'disabled',
      fullPage: true,
    });
  });
}

async function openScenario(page: Page, scenario: string, theme: 'dark' | 'light') {
  await page.setViewportSize({ width: 1365, height: 900 });
  await page.goto(`/?screenshot=${scenario}&theme=${theme}`, { waitUntil: 'domcontentloaded' });
  await page.locator('canvas').waitFor({ state: 'visible', timeout: 60_000 });
  await page.waitForTimeout(750);
}
