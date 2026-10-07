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
