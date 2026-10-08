import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { delimiter, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { _electron as electron, expect, test } from '@playwright/test';
import { TEST_FEED } from './feed';

// Smoke test aplikasi terpaket: sidecar naik dengan runtime Java bundel (Java sistem
// disembunyikan dari PATH/JAVA_HOME), /health ok, dan alur Attract -> Result lulus dalam waktu nyata.
const EXE = process.env.VANDEBOOTH_PACKAGED_EXE || resolve(__dirname, '..', 'desktop', 'release', 'win-unpacked', 'Van de Booth.exe');

test.skip(!existsSync(EXE), `Aplikasi terpaket tidak ditemukan (${EXE}); jalankan "npm run package -- --dir"`);

test('aplikasi terpaket: runtime bundel, /health, alur Attract -> Result', async () => {
  const outDir = mkdtempSync(join(tmpdir(), 'vandebooth-packaged-'));
  const { ELECTRON_RUN_AS_NODE: _a, JAVA_HOME: _b, VANDEBOOTH_JAVA: _c, VANDEBOOTH_JAR: _d, ...baseEnv } = process.env;
  const path = (baseEnv.PATH ?? baseEnv.Path ?? '')
    .split(delimiter)
    .filter((p) => !/java|jdk|jre|adoptium|temurin/i.test(p))
    .join(delimiter);
  delete baseEnv.Path;
  const app = await electron.launch({
    executablePath: EXE,
    env: {
      ...(baseEnv as Record<string, string>),
      PATH: path,
      VANDEBOOTH_FAKE_CAMERA: '1',
      VANDEBOOTH_FAKE_VIDEO: TEST_FEED,
      JAVA_TOOL_OPTIONS: [
        `-Dvandebooth.output.dir=${outDir}`,
        `-Dvandebooth.config.dir=${join(outDir, 'config')}`,
        '-Dvandebooth.share.bindAddress=127.0.0.1',
        '-Dvandebooth.share.port=0',
        '-Dvandebooth.print.mode=file',
      ].join(' '),
    },
  });
  try {
    const win = await app.firstWindow();
    await win.setViewportSize({ width: 1920, height: 1080 });
    await expect(win.locator('[data-screen="attract"]')).toBeVisible({ timeout: 30_000 });
    const env = await win.evaluate(() => (window as unknown as { booth: { apiBase: string; token: string } }).booth);
    const health = await fetch(env.apiBase + '/health', { headers: { 'X-Booth-Token': env.token } });
    expect(health.status).toBe(200);

    await win.waitForFunction(() => {
      const v = document.querySelector('video');
      return !!v && v.readyState >= 2 && v.videoWidth > 0;
    });
    await win.getByTestId('start').click();
    await expect(win.locator('[data-screen="layout"]')).toBeVisible();
    await win.getByTestId('layout-vertical-3').click();
    await win.getByTestId('next').click();
    await expect(win.locator('[data-screen="capture"]')).toBeVisible();
    await expect(win.locator('[data-screen="review"]')).toBeVisible({ timeout: 60_000 });
    await expect(win.locator('[data-testid^="review-photo-"] img')).toHaveCount(3);
    await win.getByTestId('looks-good').click();
    await expect(win.locator('[data-screen="filter"]')).toBeVisible();
    await expect(win.locator('[data-testid="filter-strip"] img')).toBeVisible();
    await win.getByTestId('next').click();
    await expect(win.locator('[data-screen="result"]')).toBeVisible();
    await expect(win.getByTestId('result-strip')).toBeVisible();
  } finally {
    await app.close();
    rmSync(outDir, { recursive: true, force: true });
  }
});
