import { existsSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { advance, expectScreen, injectEnv, shot, waitForVideo } from './helpers';

// Durasi dari CaptureScreen (jam dipalsukan agar countdown deterministik)
const FIRST_DELAY = 800;
const BETWEEN = 1_000;
const TICK = 1_000;

/** Jam palsu dibekukan: timer hanya maju lewat advance(). */
async function startFrozen(page: Page) {
  await page.clock.install({ time: new Date('2026-10-12T10:00:00') });
  await injectEnv(page);
  await page.goto('/');
  await page.clock.pauseAt(new Date('2026-10-12T10:00:01'));
}

/** Menjalankan satu foto: jeda, countdown 3-2-1, jepret, tunggu thumbnail. */
async function shootOne(page: Page, delay: number, expectedLabel: string) {
  await expect(page.getByTestId('photo-chip')).toContainText(expectedLabel);
  await advance(page, delay);
  await expect(page.getByTestId('countdown')).toHaveText('3');
  await advance(page, 3 * TICK);
  await expect(page.getByTestId('thumb')).toBeVisible();
}

test('alur penuh Attract -> Result dengan kamera palsu dan sidecar sungguhan', async ({ page }) => {
  await startFrozen(page);

  // 1. Attract
  await expectScreen(page, 'attract');
  await waitForVideo(page);
  await expect(page.getByTestId('event-chip')).toHaveText('Sample event · 12 Oct 2026');
  await shot(page, '1-attract.png');

  // 2. Layout
  await page.getByTestId('start').click();
  await expectScreen(page, 'layout');
  await expect(page.getByTestId('layout-vertical-4')).toHaveAttribute('aria-pressed', 'true');
  await shot(page, '2-layout.png');

  // 3. Capture: 4 foto; screenshot saat foto 2 menghitung "3" dengan thumbnail foto 1
  await page.getByTestId('next').click();
  await expectScreen(page, 'capture');
  await waitForVideo(page);
  await shootOne(page, FIRST_DELAY, 'Photo 1 of 4');
  await expect(page.getByTestId('photo-chip')).toContainText('Photo 2 of 4');
  await advance(page, BETWEEN);
  await expect(page.getByTestId('countdown')).toHaveText('3');
  await expect(page.getByTestId('thumb')).toBeVisible();
  await expect(page.getByTestId('caption')).toHaveText('Strike a pose!');
  await shot(page, '3-capture.png');
  await advance(page, 3 * TICK);
  await expect(page.getByTestId('thumb')).toBeVisible();
  await shootOne(page, BETWEEN, 'Photo 3 of 4');
  await shootOne(page, BETWEEN, 'Photo 4 of 4');
  await advance(page, BETWEEN);

  // 4. Review
  await expectScreen(page, 'review');
  await expect(page.locator('[data-testid^="review-photo-"] img')).toHaveCount(4);
  await shot(page, '4-review.png');

  // Retake foto 2 (kembali ke Capture hanya untuk foto itu)
  await page.getByTestId('retake-2').click();
  await expectScreen(page, 'capture');
  await shootOne(page, FIRST_DELAY, 'Photo 2 of 4');
  await advance(page, BETWEEN);
  await expectScreen(page, 'review');

  // 5. Filter: ganti ke Warm -> compose ulang
  await page.getByTestId('looks-good').click();
  await expectScreen(page, 'filter');
  await advance(page, 200);
  await expect(page.locator('[data-testid="filter-strip"] img')).toBeVisible();
  await page.getByTestId('filter-warm').click();
  await advance(page, 200);
  await expect(page.getByTestId('filter-warm')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('next')).toBeEnabled();
  await shot(page, '5-filter.png');

  // 6. Result
  await page.getByTestId('next').click();
  await expectScreen(page, 'result');
  await expect(page.getByTestId('result-strip')).toBeVisible();
  await expect(page.getByTestId('returning')).toHaveText('Returning to start in 45 seconds');
  await shot(page, '6-result.png');

  // Save photos -> toast berisi path, file benar-benar ada
  await page.getByTestId('save').click();
  const toast = page.getByTestId('toast');
  await expect(toast).toContainText('Saved to ');
  const saved = (await toast.textContent())!.replace('Saved to ', '').trim();
  expect(saved).toContain(process.env.E2E_OUT!);
  expect(existsSync(saved)).toBe(true);

  // 45 detik tanpa aksi -> kembali ke Attract
  await advance(page, 45_000, 1_000);
  await expectScreen(page, 'attract');
});

test('batas retake: setelah 2 retake tombol nonaktif dan muncul "No retakes left"', async ({ page }) => {
  await startFrozen(page);
  await expectScreen(page, 'attract');
  await waitForVideo(page);
  await page.getByTestId('start').click();
  await page.getByTestId('layout-vertical-3').click();
  await page.getByTestId('next').click();
  await expectScreen(page, 'capture');
  await waitForVideo(page);
  await shootOne(page, FIRST_DELAY, 'Photo 1 of 3');
  await shootOne(page, BETWEEN, 'Photo 2 of 3');
  await shootOne(page, BETWEEN, 'Photo 3 of 3');
  await advance(page, BETWEEN);
  await expectScreen(page, 'review');

  for (const idx of [1, 3]) {
    await page.getByTestId(`retake-${idx}`).click();
    await expectScreen(page, 'capture');
    await shootOne(page, FIRST_DELAY, `Photo ${idx} of 3`);
    await advance(page, BETWEEN);
    await expectScreen(page, 'review');
  }
  await expect(page.getByTestId('no-retakes')).toHaveText('No retakes left');
  await expect(page.getByTestId('retake-2')).toBeDisabled();
});

test('Esc membatalkan sesi dari Layout dan Capture (dengan konfirmasi)', async ({ page }) => {
  await injectEnv(page);
  await page.goto('/');
  await expectScreen(page, 'attract');
  await page.getByTestId('start').click();
  await expectScreen(page, 'layout');
  await page.keyboard.press('Escape');
  await expectScreen(page, 'attract');

  await page.keyboard.press('Enter');
  await expectScreen(page, 'layout');
  await page.keyboard.press('Enter');
  await expectScreen(page, 'capture');
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('confirm')).toBeVisible();
  await page.getByTestId('discard').click();
  await expectScreen(page, 'attract');
});

test('layar Error: sidecar tidak merespons', async ({ page }) => {
  await injectEnv(page, { apiBase: 'http://127.0.0.1:9', token: 'x'.repeat(32) });
  await page.goto('/');
  await expectScreen(page, 'error');
  await expect(page.getByTestId('error-message')).toHaveText('The booth service is not responding.');
  await expect(page.getByTestId('try-again')).toBeVisible();
});

test('layar Error: kamera tidak ditemukan', async ({ page }) => {
  await page.addInitScript(() => {
    navigator.mediaDevices.getUserMedia = () => Promise.reject(new DOMException('none', 'NotFoundError'));
  });
  await injectEnv(page);
  await page.goto('/');
  await expectScreen(page, 'error');
  await expect(page.getByTestId('error-message')).toHaveText('Camera not found. Check the cable, then try again.');
});

test('tombol interaktif minimal 96 px pada kanvas 1920x1080', async ({ page }) => {
  await injectEnv(page);
  await page.goto('/');
  await expectScreen(page, 'attract');
  await page.getByTestId('start').click();
  await expectScreen(page, 'layout');
  const heights = await page.locator('button.btn').evaluateAll((els) => els.map((e) => e.getBoundingClientRect().height));
  expect(heights.length).toBeGreaterThan(1);
  for (const h of heights) expect(h).toBeGreaterThanOrEqual(96);
});
