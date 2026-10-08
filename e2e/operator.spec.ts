import { expect, test, type Page } from '@playwright/test';
import { expectScreen, injectEnv, settle, shot } from './helpers';
import { randomPin, startTestSidecar, type TestSidecar } from './sidecar';

// Sidecar khusus dengan folder config baru: belum ada PIN (tidak ada PIN bawaan)
let sidecar: TestSidecar;
const PIN = randomPin();

test.beforeAll(async () => {
  sidecar = await startTestSidecar();
});
test.afterAll(async () => {
  await sidecar.stop();
});

async function api(page: Page, method: string, path: string, data?: unknown, body?: Buffer) {
  const res = await page.request.fetch(sidecar.apiBase + path, {
    method,
    headers: { 'X-Booth-Token': sidecar.token, ...(body ? { 'Content-Type': 'image/jpeg' } : {}) },
    data: body ?? data,
  });
  expect(res.status(), `${method} ${path}`).toBeLessThan(300);
  return res;
}

/** JPEG 1440x1080 dibuat di browser (canvas), dipakai untuk semua frame. */
async function makeJpeg(page: Page): Promise<Buffer> {
  const dataUrl = await page.evaluate(() => {
    const c = document.createElement('canvas');
    c.width = 1440;
    c.height = 1080;
    const g = c.getContext('2d')!;
    g.fillStyle = 'rgb(120, 160, 200)';
    g.fillRect(0, 0, 1440, 1080);
    return c.toDataURL('image/jpeg', 0.92);
  });
  return Buffer.from(dataUrl.split(',')[1], 'base64');
}

/** Sesi vertical-3 lengkap lewat API, mengembalikan strip PNG (base64). */
async function composeStrip(page: Page, jpeg: Buffer): Promise<string> {
  const { sessionId } = await (await api(page, 'POST', '/api/sessions', { layout: 'vertical-3' })).json();
  for (let i = 1; i <= 3; i++) await api(page, 'PUT', `/api/sessions/${sessionId}/frames/${i}`, undefined, jpeg);
  const { stripUrl } = await (await api(page, 'POST', `/api/sessions/${sessionId}/compose`, { filter: 'original' })).json();
  return (await (await api(page, 'GET', stripUrl)).body()).toString('base64');
}

/** Selisih piksel rata-rata dua PNG pada rentang baris tertentu (dihitung di browser). */
async function rowDiff(page: Page, a: string, b: string, y0: number, y1: number): Promise<number> {
  return page.evaluate(
    async ({ a, b, y0, y1 }) => {
      const load = async (s: string) => {
        // <img> dengan data URL (CSP UI mengizinkan img-src data:, bukan fetch)
        const img = new Image();
        img.src = `data:image/png;base64,${s}`;
        await img.decode();
        const c = document.createElement('canvas');
        c.width = img.naturalWidth;
        c.height = img.naturalHeight;
        const g = c.getContext('2d')!;
        g.drawImage(img, 0, 0);
        return g.getImageData(0, y0, c.width, y1 - y0).data;
      };
      const [da, db] = await Promise.all([load(a), load(b)]);
      let sum = 0;
      for (let i = 0; i < da.length; i++) sum += Math.abs(da[i] - db[i]);
      return sum / da.length;
    },
    { a, b, y0, y1 },
  );
}

async function holdWordmark(page: Page) {
  const box = (await page.getByTestId('wordmark-hold').boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.waitForTimeout(3_300);
  await page.mouse.up();
}

async function typePin(page: Page, pin: string) {
  for (const d of pin) await page.getByTestId(`key-${d}`).click();
  await page.getByTestId('key-enter').click();
}

test('operator: buat PIN, ubah nama event (footer strip berubah), galeri hapus', async ({ page }) => {
  await injectEnv(page, sidecar);
  await page.goto('/');
  await expectScreen(page, 'attract');
  const jpeg = await makeJpeg(page);
  const before = await composeStrip(page, jpeg);

  // Tekan dan tahan wordmark 3 detik -> belum ada PIN -> "Create a PIN"
  await holdWordmark(page);
  await expect(page.getByTestId('pin-title')).toHaveText('Create a PIN');
  for (const d of PIN.slice(0, 4)) await page.getByTestId(`key-${d}`).click();
  await shot(page, '8-operator-pin.png');
  for (const d of PIN.slice(4)) await page.getByTestId(`key-${d}`).click();
  await page.getByTestId('key-enter').click();
  await expect(page.getByTestId('pin-title')).toHaveText('Enter it again');
  await typePin(page, PIN);
  await expectScreen(page, 'operator');
  await expect(page.getByTestId('operator-title')).toHaveText('Event');

  // Tombol/kontrol operator minimal 72 px
  const heights = await page.locator('[data-screen="operator"] button, [data-screen="operator"] input').evaluateAll(
    (els) => els.map((e) => e.getBoundingClientRect().height));
  for (const h of heights) expect(h).toBeGreaterThanOrEqual(72);

  // Ubah nama event
  const name = page.getByTestId('event-name');
  await expect(name).toHaveValue('Sample event');
  await name.fill('Rina & Bayu');
  await name.press('Enter');
  await expect(page.getByTestId('toast')).toHaveText('Saved.');

  // Footer strip berubah: area foto identik, area footer berbeda
  const after = await composeStrip(page, jpeg);
  expect(await rowDiff(page, before, after, 100, 500)).toBe(0);
  expect(await rowDiff(page, before, after, 1580, 1750)).toBeGreaterThan(1);

  // Panel Sharing (mockup 7)
  await page.getByTestId('menu-sharing').click();
  await expect(page.getByTestId('operator-title')).toHaveText('Sharing');
  await expect(page.getByTestId('share-toggle')).toHaveAttribute('aria-checked', 'true');
  await expect(page.getByTestId('share-address')).toContainText('127.0.0.1');
  await expect(page.getByTestId('share-test-qr')).toBeVisible();
  await expect(page.getByTestId('share-expiry').getByRole('radio', { name: '6 h' })).toHaveAttribute('aria-checked', 'true');
  await expect(page.getByTestId('toast')).toBeHidden({ timeout: 8_000 }); // toast "Saved." sementara
  await shot(page, '9-operator-sharing.png');

  // Galeri: dua sesi -> hapus satu dengan konfirmasi
  await page.getByTestId('menu-gallery').click();
  const items = page.getByTestId('gallery-item');
  await expect(items).toHaveCount(2);
  await expect(page.locator('.gallery-thumb[src]')).toHaveCount(2);
  await settle(page);
  await shot(page, '10-operator-gallery.png');
  await items.first().getByTestId('gallery-delete').click();
  await expect(page.getByTestId('confirm')).toContainText('Delete this session?');
  await page.getByTestId('confirm-yes').click();
  await expect(items).toHaveCount(1);

  // Keluar -> chip acara di Attract memakai nama baru
  await page.getByTestId('exit-operator').click();
  await expectScreen(page, 'attract');
  await expect(page.getByTestId('event-chip')).toHaveText('Rina & Bayu · 12 Oct 2026');

  // Masuk lagi dengan PIN salah -> pesan salah, PIN benar -> operator
  await holdWordmark(page);
  await expect(page.getByTestId('pin-title')).toHaveText('Enter PIN');
  await typePin(page, PIN === '999999' ? '999998' : '999999');
  await expect(page.getByTestId('pin-message')).toHaveText('Wrong PIN. Try again.');
  await typePin(page, PIN);
  await expectScreen(page, 'operator');
  await page.getByTestId('exit-operator').click();
  await expectScreen(page, 'attract');
});
