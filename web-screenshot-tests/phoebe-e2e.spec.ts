import { expect, test } from '@playwright/test';

type PhoebeE2eResult = { passed: boolean; message: string };

async function waitForPhoebeE2eResult(page, timeout = 60_000): Promise<PhoebeE2eResult> {
  await page.waitForFunction(
    () => (window as unknown as { phoebeE2eResults?: PhoebeE2eResult }).phoebeE2eResults !== undefined,
    undefined,
    { timeout },
  );
  return page.evaluate(() => (window as unknown as { phoebeE2eResults: PhoebeE2eResult }).phoebeE2eResults);
}

test('web local library indexes mp3 and starts playback', async ({ page }) => {
  await page.goto('/?e2e=localLibrary', { waitUntil: 'domcontentloaded' });
  const results = await waitForPhoebeE2eResult(page);
  expect(results.passed, results.message).toBe(true);
  expect(results.message).toContain('playback started');
});

test('web local playlist export formats m3u8 text and csv', async ({ page }) => {
  await page.goto('/?e2e=localPlaylist', { waitUntil: 'domcontentloaded' });
  const results = await waitForPhoebeE2eResult(page);
  expect(results.passed, results.message).toBe(true);
  expect(results.message).toContain('m3u8');
  expect(results.message).toContain('csv');
});

test('web chromecast mock connects and loads a remote stream', async ({ page }) => {
  await page.goto('/?e2e=castMock', { waitUntil: 'domcontentloaded' });
  const results = await waitForPhoebeE2eResult(page);
  expect(results.passed, results.message).toBe(true);
  expect(results.message).toContain('mock Chromecast connected');
});

test('web local playback regression starts real browser audio after tap', async ({ page }) => {
  await page.goto('/?e2e=localPlaybackRegression', { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(
    () => (window as unknown as { phoebeE2eReady?: boolean }).phoebeE2eReady === true,
    undefined,
    { timeout: 60_000 },
  );
  await page.locator('#phoebe-web-playback-regression-play').click();
  const results = await waitForPhoebeE2eResult(page, 10_000);
  expect(results.passed, results.message).toBe(true);
  expect(results.message).toContain('web local playback started');
});
