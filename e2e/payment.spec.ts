import { expect, test } from '@playwright/test';
import { advance, expectScreen, shootAll, shot, startFrozen, trackSession, waitForVideo } from './helpers';
import { startTestSidecar, type TestSidecar } from './sidecar';

// Sidecar khusus dengan pembayaran demo ON (Rp 25.000)
let sidecar: TestSidecar;
test.beforeAll(async () => {
  sidecar = await startTestSidecar({ 'payment.enabled': 'true', 'payment.price': '25000' });
});
test.afterAll(async () => {
  await sidecar.stop();
});

test('payment ON: Layout -> Pay (demo) -> Capture -> ... -> Result', async ({ page }) => {
  const session = trackSession(page);
  await startFrozen(page, sidecar);
  await expectScreen(page, 'attract');
  await waitForVideo(page);
  await page.getByTestId('start').click();
  await expectScreen(page, 'layout');
  await expect(page.getByTestId('step-pill')).toHaveText(/1Layout2Pay3Photos4Result/);
  await page.getByTestId('next').click();

  // Layar Pay (mockup 6)
  await expectScreen(page, 'pay');
  await expect(page.getByTestId('pay-price')).toHaveText('Rp 25.000');
  await expect(page.getByTestId('pay-session')).toHaveText('One session, 4 photos');
  await expect(page.getByTestId('pay-session')).toHaveCSS('text-transform', 'uppercase');
  await expect(page.getByTestId('demo-chip')).toHaveText('Demo mode · no real payment');
  await expect(page.getByTestId('pay-qr')).toBeVisible();
  await expect(page.getByText('Waiting for payment…')).toBeVisible();
  await expect(page.getByText('This screen updates by itself.')).toBeVisible();
  await shot(page, '7-pay.png');

  // Sidecar menolak foto sebelum lunas (402)
  const blocked = await page.request.put(`${sidecar.apiBase}/api/sessions/${session()}/frames/1`, {
    headers: { 'X-Booth-Token': sidecar.token, 'Content-Type': 'image/jpeg' },
    data: Buffer.from([0xff, 0xd8, 0xff, 0xd9]),
  });
  expect(blocked.status()).toBe(402);

  await page.getByTestId('simulate').click();
  await expectScreen(page, 'capture');
  await expect(page.getByTestId('step-pill')).toHaveCount(0);
  await shootAll(page, 4);
  await expect(page.getByTestId('step-pill')).toContainText('3Photos');

  await page.getByTestId('looks-good').click();
  await expectScreen(page, 'filter');
  await advance(page, 200);
  await expect(page.getByTestId('next')).toBeEnabled();
  await page.getByTestId('next').click();
  await expectScreen(page, 'result');
  await expect(page.getByTestId('print')).toBeVisible();
  await expect(page.getByTestId('qr-card')).toBeVisible();
});

test('payment ON: layar Pay memperbarui diri sendiri dan Back kembali ke Layout', async ({ page }) => {
  const session = trackSession(page);
  await startFrozen(page, sidecar);
  await expectScreen(page, 'attract');
  await page.getByTestId('start').click();
  await page.getByTestId('next').click();
  await expectScreen(page, 'pay');
  await page.keyboard.press('Escape');
  await expectScreen(page, 'layout');
  await page.getByTestId('next').click();
  await expectScreen(page, 'pay');
  await expect(page.getByTestId('pay-qr')).toBeVisible();

  // Pembayaran dikonfirmasi di luar layar (mis. gateway kelak): polling membawa tamu ke Capture
  const res = await page.request.post(`${sidecar.apiBase}/api/sessions/${session()}/payment/simulate`, {
    headers: { 'X-Booth-Token': sidecar.token },
    data: {},
  });
  expect((await res.json()).status).toBe('paid');
  await advance(page, 2_500, 500);
  await expectScreen(page, 'capture');
});
