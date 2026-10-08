import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import {
  SHOTS,
  advance,
  boothEnv,
  expectScreen,
  settle,
  shootAll,
  startFrozen,
  trackSession,
  waitForVideo,
} from './helpers';

/** Enam layout (docs/phase5.md): id, jumlah foto, canvas px, nama dan keterangan kartu. */
const LAYOUTS = [
  { id: 'vertical-4', n: 4, w: 600, h: 1800, name: 'Classic Strip', paper: '2×6' },
  { id: 'vertical-3', n: 3, w: 600, h: 1800, name: 'Tall Strip', paper: '2×6' },
  { id: 'horizontal-3', n: 3, w: 1800, h: 1200, name: 'Wide', paper: '6×4' },
  { id: 'postcard-1', n: 1, w: 1800, h: 1200, name: 'Big Shot', paper: '6×4' },
  { id: 'grid-4', n: 4, w: 1800, h: 1200, name: 'Four Square', paper: '6×4' },
  { id: 'grid-6', n: 6, w: 1200, h: 1800, name: 'Contact Sheet', paper: '4×6' },
] as const;

const LAYOUT_SHOTS = `${SHOTS}/layouts`;
const STAGE = { w: 1920, h: 1080 };

interface Box { left: number; top: number; right: number; bottom: number; width: number; height: number }

async function measure(page: Page, frame: string, img: string) {
  return page.evaluate(
    ([frameSel, imgSel]) => {
      const box = (r: DOMRect) => ({ left: r.left, top: r.top, right: r.right, bottom: r.bottom, width: r.width, height: r.height });
      const fr = document.querySelector(frameSel)!.getBoundingClientRect();
      const im = document.querySelector(imgSel) as HTMLElement;
      const stage = document.querySelector('[data-testid="stage"]') as HTMLElement;
      const buttons = [...document.querySelectorAll('[data-screen] button')].map((b) => b.getBoundingClientRect().height);
      return {
        frame: box(fr),
        img: box(im.getBoundingClientRect()),
        offsetW: im.offsetWidth,
        offsetH: im.offsetHeight,
        docScrollH: document.documentElement.scrollHeight,
        stageScrollH: stage.scrollHeight,
        minButton: buttons.length ? Math.min(...buttons) : 999,
      };
    },
    [frame, img],
  );
}

/** Aturan terukur docs/phase5.md bagian 2 untuk pratinjau strip (Filter dan Result). */
function expectFits(m: Awaited<ReturnType<typeof measure>>, label: string, rotated: boolean) {
  const f: Box = m.frame;
  const i: Box = m.img;
  const eps = 0.5;
  // kotak gambar sepenuhnya di dalam wadah
  expect(i.left, `${label}: kiri`).toBeGreaterThanOrEqual(f.left - eps);
  expect(i.top, `${label}: atas`).toBeGreaterThanOrEqual(f.top - eps);
  expect(i.right, `${label}: kanan`).toBeLessThanOrEqual(f.right + eps);
  expect(i.bottom, `${label}: bawah`).toBeLessThanOrEqual(f.bottom + eps);
  // sisi pembatas terisi >= 90% wadah
  const fillW = (rotated ? i.width : m.offsetW) / f.width;
  const fillH = (rotated ? i.height : m.offsetH) / f.height;
  expect(Math.max(fillW, fillH), `${label}: isi wadah`).toBeGreaterThanOrEqual(0.9);
  // terpusat
  expect(Math.abs(i.left + i.width / 2 - (f.left + f.width / 2)), `${label}: pusat x`).toBeLessThanOrEqual(2);
  expect(Math.abs(i.top + i.height / 2 - (f.top + f.height / 2)), `${label}: pusat y`).toBeLessThanOrEqual(2);
  // tanpa scroll, tombol >= 96 px
  expect(m.docScrollH, `${label}: scroll dokumen`).toBeLessThanOrEqual(STAGE.h);
  expect(m.stageScrollH, `${label}: scroll stage`).toBeLessThanOrEqual(STAGE.h);
  expect(m.minButton, `${label}: tombol`).toBeGreaterThanOrEqual(96);
}

const rounded = (b: { left: number; top: number; width: number; height: number }) =>
  [b.left, b.top, b.width, b.height].map((v) => Math.round(v));

const controls: Record<string, Record<string, number[]>> = { filter: {}, result: {} };

test.describe.configure({ mode: 'serial' });

for (const L of LAYOUTS) {
  test(`layout ${L.id}: alur penuh, tampilan pas di Layout, Review, Filter dan Result`, async ({ page }) => {
    const session = trackSession(page);
    await startFrozen(page);
    await expectScreen(page, 'attract');
    await waitForVideo(page);
    await page.getByTestId('start').click();

    // Layout: enam kartu terlihat tanpa scroll, nama dan "n photos · paper" dari data
    await expectScreen(page, 'layout');
    await settle(page);
    for (const o of LAYOUTS) {
      const card = page.getByTestId(`layout-${o.id}`);
      await expect(card).toBeVisible();
      const b = (await card.boundingBox())!;
      expect(b.x).toBeGreaterThanOrEqual(0);
      expect(b.y).toBeGreaterThanOrEqual(0);
      expect(b.x + b.width).toBeLessThanOrEqual(STAGE.w);
      expect(b.y + b.height).toBeLessThanOrEqual(STAGE.h - 96 - 30); // di atas tombol Next
      await expect(card).toContainText(o.name);
      await expect(page.getByTestId(`layout-meta-${o.id}`)).toHaveText(`${o.n} ${o.n === 1 ? 'photo' : 'photos'} · ${o.paper}`);
      await expect(page.getByTestId(`mini-${o.id}`)).toBeVisible();
    }
    expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBeLessThanOrEqual(STAGE.h);
    const nextBox = (await page.getByTestId('next').boundingBox())!;
    expect(nextBox.height).toBeGreaterThanOrEqual(96);
    if (L.id === 'vertical-4') await expect(page.getByTestId('layout-vertical-4')).toHaveAttribute('aria-pressed', 'true');
    await page.getByTestId(`layout-${L.id}`).click();
    await expect(page.getByTestId(`layout-${L.id}`)).toHaveAttribute('aria-pressed', 'true');
    await page.getByTestId('next').click();

    // Capture: 1..6 foto
    await expectScreen(page, 'capture');
    await shootAll(page, L.n);

    // Review: satu foto besar atau grid yang muat tinggi layar, Retake >= 96 px
    await expect(page.locator('[data-testid^="review-photo-"] img')).toHaveCount(L.n);
    await settle(page); // animasi masuk layar selesai sebelum mengukur
    for (let k = 1; k <= L.n; k++) {
      const b = (await page.getByTestId(`review-photo-${k}`).boundingBox())!;
      expect(b.x).toBeGreaterThanOrEqual(110 - 1);
      expect(b.x + b.width).toBeLessThanOrEqual(1810 + 1);
      expect(b.y).toBeGreaterThanOrEqual(280 - 1);
      expect(b.y + b.height).toBeLessThanOrEqual(900 + 1);
      expect((await page.getByTestId(`retake-${k}`).boundingBox())!.height).toBeGreaterThanOrEqual(96);
    }
    expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBeLessThanOrEqual(STAGE.h);
    await page.screenshot({ path: `test-results/screens/${L.id}-review.png` });
    await page.getByTestId('looks-good').click();

    // Filter
    await expectScreen(page, 'filter');
    await advance(page, 200);
    await expect(page.getByTestId('filter-strip-image')).toBeVisible();
    await expect(page.getByTestId('next')).toBeEnabled();
    await settle(page);
    const filterFit = await measure(page, '[data-testid="filter-strip"]', '[data-testid="filter-strip-image"]');
    expectFits(filterFit, `${L.id} filter`, false);
    await page.screenshot({ path: `test-results/screens/${L.id}-filter.png` });
    controls.filter[L.id] = [
      ...rounded((await page.getByTestId('next').boundingBox())! as never),
      ...rounded((await page.getByTestId('filter-warm').boundingBox())! as never),
    ];
    await page.getByTestId('next').click();

    // Result
    await expectScreen(page, 'result');
    await expect(page.getByTestId('result-strip')).toBeVisible();
    await expect(page.getByTestId('qr-card')).toBeVisible();
    await settle(page);
    const resultFit = await measure(page, '[data-testid="result-frame"]', '[data-testid="result-strip"]');
    expectFits(resultFit, `${L.id} result`, true);
    await page.screenshot({ path: `test-results/screens/${L.id}-result.png` });
    const qr = (await page.getByTestId('qr-card').boundingBox())!;
    expect(qr.x + qr.width).toBeLessThanOrEqual(STAGE.w);
    expect(qr.y + qr.height).toBeLessThanOrEqual(STAGE.h);
    controls.result[L.id] = [
      ...rounded((await page.getByTestId('print').boundingBox())! as never),
      ...rounded((await page.getByTestId('save').boundingBox())! as never),
      ...rounded((await page.getByTestId('retake').boundingBox())! as never),
      ...rounded(qr as never),
    ];

    // Hasil compose nyata layout ini (PNG dari sidecar) berukuran canvas
    const { apiBase, token } = boothEnv();
    const png = await (await page.request.get(`${apiBase}/api/sessions/${session()}/strip.png`, {
      headers: { 'X-Booth-Token': token },
    })).body();
    expect(png.readUInt32BE(16), `${L.id} lebar canvas`).toBe(L.w);
    expect(png.readUInt32BE(20), `${L.id} tinggi canvas`).toBe(L.h);
    mkdirSync(LAYOUT_SHOTS, { recursive: true });
    writeFileSync(`${LAYOUT_SHOTS}/${L.id}.png`, png);
  });
}

test('panel kontrol Filter dan Result tidak bergeser antar layout', () => {
  test.skip(Object.keys(controls.filter).length < LAYOUTS.length, 'butuh enam alur layout dijalankan dulu');
  for (const screen of ['filter', 'result'] as const) {
    const ref = controls[screen][LAYOUTS[0].id];
    for (const L of LAYOUTS) expect(controls[screen][L.id], `${screen} ${L.id}`).toEqual(ref);
  }
});

test('lembar ringkasan layouts-overview.png dari hasil compose nyata', async ({ page }) => {
  const imgs = LAYOUTS.map((L) => ({ L, data: readFileSync(`${LAYOUT_SHOTS}/${L.id}.png`).toString('base64') }));
  const font = (file: string) => readFileSync(`src/main/resources/fonts/${file}`).toString('base64');
  const html = `<!doctype html><meta charset="utf-8"><style>
    @font-face{font-family:F;font-weight:600;src:url(data:font/ttf;base64,${font('Fraunces-SemiBold.ttf')})}
    @font-face{font-family:J;font-weight:600;src:url(data:font/ttf;base64,${font('PlusJakartaSans-SemiBold.ttf')})}
    body{margin:0;width:2200px;background:#F7EFE2;color:#241B16;font-family:J,sans-serif}
    h1{font:600 64px F,serif;letter-spacing:-0.02em;margin:0;padding:56px 72px 0}
    p{margin:8px 72px 0;font-size:26px;color:#6B5D52}
    .row{display:flex;flex-wrap:wrap;justify-content:center;gap:56px 48px;padding:48px 72px 64px}
    figure{margin:0;width:640px;display:flex;flex-direction:column;align-items:center;justify-content:flex-end}
    img{display:block;max-width:640px;max-height:560px;box-shadow:0 18px 40px rgba(36,27,22,.18);background:#fff}
    figcaption{margin-top:26px;font-size:30px}figcaption span{display:block;font-size:24px;color:#6B5D52;margin-top:4px}
  </style><h1>Six layouts</h1><p>Real compose output from the test camera feed.</p>
  <div class="row">${imgs
    .map(({ L, data }) => `<figure><img src="data:image/png;base64,${data}"><figcaption>${L.name}<span>${L.n} ${L.n === 1 ? 'photo' : 'photos'} · ${L.paper}</span></figcaption></figure>`)
    .join('')}</div>`;
  await page.setViewportSize({ width: 2200, height: 1000 });
  await page.setContent(html);
  await page.evaluate(() => document.fonts.ready);
  const body = await page.locator('body').boundingBox();
  await page.screenshot({ path: `${LAYOUT_SHOTS}/layouts-overview.png`, clip: { x: 0, y: 0, width: 2200, height: Math.ceil(body!.height) }, fullPage: true });
});
