import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { _electron as electron, expect, test } from '@playwright/test';

// Smoke test shell Electron: sidecar dijalankan oleh aplikasi, UI hasil build termuat,
// kamera palsu diizinkan, dan sidecar ikut mati saat aplikasi ditutup.
test('Electron menjalankan sidecar, memuat UI, dan mematikan sidecar saat keluar', async () => {
  const outDir = mkdtempSync(join(tmpdir(), 'vandebooth-electron-'));
  // VS Code/terminal tertentu menyetel ELECTRON_RUN_AS_NODE=1; Electron harus berjalan sebagai aplikasi
  const { ELECTRON_RUN_AS_NODE: _ignored, ...baseEnv } = process.env;
  const app = await electron.launch({
    args: ['desktop'],
    env: {
      ...(baseEnv as Record<string, string>),
      VANDEBOOTH_FAKE_CAMERA: '1',
      // Folder output/config sementara, server berbagi hanya lokal (tanpa prompt firewall)
      JAVA_TOOL_OPTIONS: [
        `-Dvandebooth.output.dir=${outDir}`,
        `-Dvandebooth.config.dir=${join(outDir, 'config')}`,
        '-Dvandebooth.share.bindAddress=127.0.0.1',
        '-Dvandebooth.share.port=0',
      ].join(' '),
    },
  });
  let apiBase = '';
  try {
    const win = await app.firstWindow();
    await expect(win.locator('[data-screen="attract"]')).toBeVisible({ timeout: 30_000 });
    await win.waitForFunction(() => {
      const v = document.querySelector('video');
      return !!v && v.readyState >= 2 && v.videoWidth > 0;
    });
    const env = await win.evaluate(() => (window as unknown as { booth: { apiBase: string; token: string } }).booth);
    apiBase = env.apiBase;
    expect(apiBase).toMatch(/^http:\/\/127\.0\.0\.1:\d+$/);
    expect(env.token.length).toBeGreaterThanOrEqual(32);

    const health = await fetch(apiBase + '/health', { headers: { 'X-Booth-Token': env.token } });
    expect(health.status).toBe(200);
    const denied = await fetch(apiBase + '/health');
    expect(denied.status).toBe(401);
  } finally {
    await app.close();
  }

  // Sidecar harus sudah berhenti (beri waktu hingga 5 detik)
  await expect
    .poll(async () => {
      try {
        await fetch(apiBase + '/health', { signal: AbortSignal.timeout(500) });
        return 'hidup';
      } catch {
        return 'mati';
      }
    }, { timeout: 5_000 })
    .toBe('mati');
  rmSync(outDir, { recursive: true, force: true });
});
