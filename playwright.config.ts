import { defineConfig } from '@playwright/test';

// E2E: UI hasil build (vite preview) + sidecar Java sungguhan + kamera palsu Chromium.
// Jalankan lewat `npm run e2e` (membangun jar dan ui/dist dulu).
export default defineConfig({
  testDir: 'e2e',
  timeout: 180_000,
  expect: { timeout: 15_000 },
  workers: 1,
  fullyParallel: false,
  reporter: [['list']],
  globalSetup: './e2e/global-setup.ts',
  use: {
    baseURL: 'http://localhost:4173',
    viewport: { width: 1920, height: 1080 },
    deviceScaleFactor: 1,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      testIgnore: /electron\.spec\.ts/,
      use: {
        browserName: 'chromium',
        launchOptions: {
          args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'],
        },
      },
    },
    { name: 'electron', testMatch: /electron\.spec\.ts/ },
  ],
  webServer: {
    command: 'npm run preview --workspace @vandebooth/ui',
    url: 'http://localhost:4173',
    reuseExistingServer: false,
    timeout: 60_000,
  },
});
