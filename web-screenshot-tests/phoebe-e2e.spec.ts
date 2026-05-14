import { expect, test } from '@playwright/test';

test('web local library indexes mp3 and starts playback', async ({ page }) => {
  await page.goto('/?e2e=localLibrary', { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(
    () => {
      const results = (window as unknown as { phoebeE2eResults?: { passed?: boolean } }).phoebeE2eResults;
      return results?.passed === true;
    },
    undefined,
    { timeout: 60_000 },
  );
  const results = await page.evaluate(() => (window as unknown as { phoebeE2eResults: { passed: boolean; message: string } }).phoebeE2eResults);
  expect(results.passed).toBe(true);
  expect(results.message).toContain('playback started');
});

test('web local playlist export formats m3u8 text and csv', async ({ page }) => {
  await page.goto('/?e2e=localPlaylist', { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(
    () => {
      const results = (window as unknown as { phoebeE2eResults?: { passed?: boolean } }).phoebeE2eResults;
      return results?.passed === true;
    },
    undefined,
    { timeout: 60_000 },
  );
  const results = await page.evaluate(() => (window as unknown as { phoebeE2eResults: { passed: boolean; message: string } }).phoebeE2eResults);
  expect(results.passed).toBe(true);
  expect(results.message).toContain('m3u8');
  expect(results.message).toContain('csv');
});
