import { resolve } from 'node:path';

// Video uji sintetis (MJPEG 1280x720, 12 fps, berulang; ilustrasi, bukan orang sungguhan)
// sebagai kamera palsu Chromium/Electron
export const TEST_FEED = resolve(__dirname, '..', 'ui', 'e2e', 'fixtures', 'test-feed.mjpeg');
