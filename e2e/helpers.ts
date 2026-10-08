import { expect, type Page } from '@playwright/test';

export const SHOTS = 'docs/screenshots';

export function boothEnv() {
  const apiBase = process.env.E2E_API;
  const token = process.env.E2E_TOKEN;
  if (!apiBase || !token) throw new Error('Sidecar e2e belum berjalan (global-setup)');
  return { apiBase, token };
}

/** Menyuntikkan alamat sidecar + token seperti preload Electron. */
export async function injectEnv(page: Page, env: { apiBase: string; token: string } = boothEnv()) {
  await page.addInitScript((e) => {
    (window as unknown as { __BOOTH__: unknown }).__BOOTH__ = e;
  }, env);
}

export const screen = (page: Page, name: string) => page.locator(`[data-screen="${name}"]`);

/** Memajukan jam palsu sedikit demi sedikit agar rantai async antar-timer ikut berjalan. */
export async function advance(page: Page, ms: number, step = 100) {
  for (let t = 0; t < ms; t += step) {
    await page.clock.runFor(Math.min(step, ms - t));
    await page.waitForTimeout(15); // beri waktu fetch/microtask nyata di antara langkah jam palsu
  }
}

/** Menunggu video kamera palsu benar-benar menampilkan frame. */
export async function waitForVideo(page: Page) {
  await page.waitForFunction(() => {
    const v = document.querySelector('video');
    return !!v && v.readyState >= 2 && v.videoWidth > 0;
  });
}

/** Tunggu animasi masuk layar (250 ms) dan fon selesai dimuat sebelum screenshot. */
export async function settle(page: Page) {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(450);
}

export async function shot(page: Page, file: string) {
  await settle(page);
  await page.screenshot({ path: `${SHOTS}/${file}`, fullPage: false });
}

export async function expectScreen(page: Page, name: string) {
  await expect(screen(page, name)).toBeVisible();
}

// Durasi dari CaptureScreen (jam dipalsukan agar countdown deterministik)
export const FIRST_DELAY = 800;
export const BETWEEN = 1_000;
export const TICK = 1_000;

/** Jam palsu dibekukan: timer hanya maju lewat advance(). */
export async function startFrozen(page: Page, env: { apiBase: string; token: string } = boothEnv()) {
  await page.clock.install({ time: new Date('2026-10-12T10:00:00') });
  await injectEnv(page, env);
  await page.goto('/');
  await page.clock.pauseAt(new Date('2026-10-12T10:00:01'));
}

/** Menjalankan satu foto: jeda, countdown 3-2-1, jepret, tunggu thumbnail. */
export async function shootOne(page: Page, delay: number, expectedLabel: string) {
  await expect(page.getByTestId('photo-chip')).toContainText(expectedLabel);
  await advance(page, delay);
  await expect(page.getByTestId('countdown')).toHaveText('3');
  await advance(page, 3 * TICK);
  await expect(page.getByTestId('thumb')).toBeVisible();
}

/** Mengambil semua foto layout lalu menunggu layar Review. */
export async function shootAll(page: Page, photos: number) {
  await waitForVideo(page);
  for (let i = 1; i <= photos; i++) await shootOne(page, i === 1 ? FIRST_DELAY : BETWEEN, `Photo ${i} of ${photos}`);
  await advance(page, BETWEEN);
  await expectScreen(page, 'review');
}

/** Mencatat sessionId terakhir yang dipakai UI (dari URL request ke sidecar). */
export function trackSession(page: Page): () => string {
  let last = '';
  page.on('request', (r) => {
    const m = /\/api\/sessions\/(\d{8}_\d{6}_\d{3}(?:_\d+)?)\//.exec(r.url());
    if (m) last = m[1];
  });
  return () => last;
}

/**
 * Memajukan jam palsu per detik sampai teks locator sesuai (untuk proses yang di sidecar berjalan
 * dalam waktu nyata, mis. antrean cetak, sementara polling UI memakai jam palsu).
 */
export async function advanceUntilText(page: Page, testId: string, text: string, timeoutMs = 20_000) {
  const loc = page.getByTestId(testId);
  await expect
    .poll(
      async () => {
        await advance(page, 1_000, 250);
        return (await loc.count()) ? ((await loc.first().textContent()) ?? '') : '';
      },
      { timeout: timeoutMs, intervals: [100] },
    )
    .toBe(text);
}
