import { expect, type Page, test } from '@playwright/test';

const coreScenarios = [
  'Home',
  'HomePlayedRows',
  'FavoritePlaylists',
  'FavoriteArtists',
  'FavoriteAlbums',
  'Library',
  'Playlist',
  'Artist',
  'ArtistRadio',
  'Album',
  'CollectionValues',
  'CollectionItems',
  'Search',
  'Radio',
  'Player',
  'Settings',
  'SignIn',
] as const;

const lightScenarios = ['Home', 'Library', 'Search', 'Player'] as const;
const phoneLightScenarios = [
  ['PlayerBlurredArtworkOn', 'player-blurred-artwork-on'],
  ['PlayerBlurredArtworkOff', 'player-blurred-artwork-off'],
  ['PlayerVisualizerAlchemy', 'player-visualizer-alchemy'],
  ['PlayerVisualizerBattery', 'player-visualizer-battery'],
  ['PlayerVisualizerBarsAndWaves', 'player-visualizer-bars-and-waves'],
  ['PlayerVisualizerBlazingColors', 'player-visualizer-blazing-colors'],
  ['PlayerVisualizerPlenoptic', 'player-visualizer-plenoptic'],
] as const;
const phoneDarkScenarios = [
  ['LibraryFiveColumnGrid', 'library-five-column-grid'],
  ['Radio', 'radio'],
] as const;
const scrollbarScenarios = [
  ['LibraryScrollbar', 'library-scrollbar'],
] as const;

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

for (const [scenario, slug] of phoneLightScenarios) {
  test(`web phone ${scenario} light`, async ({ page }) => {
    await openScenario(page, scenario, 'light', { width: 430, height: 932 });
    await expect(page).toHaveScreenshot(`web-phone-${slug}-light.png`, {
      animations: 'disabled',
      fullPage: true,
    });
  });
}

for (const [scenario, slug] of phoneDarkScenarios) {
  test(`web phone ${scenario} dark`, async ({ page }) => {
    await openScenario(page, scenario, 'dark', { width: 430, height: 932 });
    await expect(page).toHaveScreenshot(`web-phone-${slug}-dark.png`, {
      animations: 'disabled',
      fullPage: true,
    });
  });
}

for (const [scenario, slug] of scrollbarScenarios) {
  test(`web ${scenario} dark`, async ({ page }) => {
    await openScenario(page, scenario, 'dark', undefined, 1_500);
    await expect(page).toHaveScreenshot(`web-${slug}-dark.png`, {
      animations: 'disabled',
      fullPage: true,
    });
  });
}

async function openScenario(
  page: Page,
  scenario: string,
  theme: 'dark' | 'light',
  viewport = { width: 1365, height: 900 },
  settleMs = 750,
) {
  await page.setViewportSize(viewport);
  await page.goto(`/?screenshot=${scenario}&theme=${theme}`, { waitUntil: 'domcontentloaded' });
  await page.locator('canvas').waitFor({ state: 'visible', timeout: 60_000 });
  await page.waitForTimeout(settleMs);
}
