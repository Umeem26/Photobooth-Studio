'use strict';
// Shell Electron minimal: jalankan sidecar (port + token acak), tunggu /health, muat UI,
// izinkan kamera hanya untuk origin aplikasi, matikan sidecar saat aplikasi keluar.
// Satu instance saja; --kiosk = layar penuh tanpa menu; F11 = toggle layar penuh.

const { app, BrowserWindow, Menu, ipcMain, session } = require('electron');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { startSidecar, waitForHealth } = require('./sidecar.cjs');

const DEV_URL = process.env.VITE_DEV_SERVER_URL || '';
const KIOSK = process.argv.includes('--kiosk') || process.env.VANDEBOOTH_KIOSK === '1';
// Terpaket: UI hasil build disalin ke resources/ui (extraResources electron-builder)
const UI_INDEX = app.isPackaged
  ? path.join(process.resourcesPath, 'ui', 'index.html')
  : path.join(__dirname, '..', 'ui', 'dist', 'index.html');

// Untuk tes otomatis tanpa webcam (Playwright): kamera palsu Chromium
if (process.env.VANDEBOOTH_FAKE_CAMERA === '1') {
  app.commandLine.appendSwitch('use-fake-device-for-media-stream');
  app.commandLine.appendSwitch('use-fake-ui-for-media-stream');
  // Video uji (MJPEG) menggantikan pola bawaan Chromium
  if (process.env.VANDEBOOTH_FAKE_VIDEO) {
    app.commandLine.appendSwitch('use-file-for-fake-video-capture', process.env.VANDEBOOTH_FAKE_VIDEO);
  }
  // Jendela tes sering tertutup jendela lain; tanpa ini Chromium di Windows menganggapnya
  // tersembunyi dan kamera/video bisa tidak pernah mulai
  app.commandLine.appendSwitch('disable-backgrounding-occluded-windows');
  app.commandLine.appendSwitch('disable-renderer-backgrounding');
  app.commandLine.appendSwitch('disable-features', 'CalculateNativeWinOcclusion');
}

let sidecar = null;
let mainWindow = null;
let boothEnv = { apiBase: 'http://127.0.0.1:1', token: '' };

function isAppUrl(url) {
  if (!url) return false;
  if (DEV_URL) return url.startsWith(new URL(DEV_URL).origin);
  return url.startsWith(pathToFileURL(path.dirname(UI_INDEX)).href);
}

async function bootSidecar() {
  try {
    sidecar = await startSidecar({ log: (l) => process.env.VANDEBOOTH_DEBUG && console.log('[sidecar]', l) });
    await waitForHealth(sidecar.port, sidecar.token, 10000);
    boothEnv = { apiBase: `http://127.0.0.1:${sidecar.port}`, token: sidecar.token };
  } catch (e) {
    // UI tetap dimuat dan menampilkan "The booth service is not responding."
    console.error('Sidecar gagal start:', e.message);
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 720,
    fullscreen: KIOSK,
    kiosk: KIOSK,
    backgroundColor: '#F7EFE2',
    autoHideMenuBar: true,
    // Langsung tampil: getUserMedia dari jendela tersembunyi bisa menggantung di Windows
    show: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  });
  if (KIOSK || app.isPackaged) win.removeMenu();
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown' || input.key !== 'F11') return;
    e.preventDefault();
    if (win.isKiosk()) win.setKiosk(false);
    else win.setFullScreen(!win.isFullScreen());
  });
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e, url) => {
    if (!isAppUrl(url)) e.preventDefault();
  });
  if (DEV_URL) win.loadURL(DEV_URL);
  else win.loadFile(UI_INDEX);
  return win;
}

ipcMain.on('vandebooth:env', (e) => {
  e.returnValue = isAppUrl(e.senderFrame && e.senderFrame.url) ? boothEnv : null;
});

// Instance kedua (mis. ikon diklik lagi) hanya memfokuskan jendela yang sudah ada
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });
  app.whenReady().then(start);
}

async function start() {
  if (KIOSK) Menu.setApplicationMenu(null);
  // Origin file:// bersifat opaque, jadi yang dicek adalah URL halaman pemohon
  const allowMedia = (permission, wc) => permission === 'media' && !!wc && isAppUrl(wc.getURL());
  session.defaultSession.setPermissionRequestHandler((wc, permission, cb) => cb(allowMedia(permission, wc)));
  session.defaultSession.setPermissionCheckHandler((wc, permission) => allowMedia(permission, wc));

  await bootSidecar();
  mainWindow = createWindow();
}

app.on('window-all-closed', () => app.quit());

let stopping = false;
app.on('will-quit', (e) => {
  if (!sidecar || stopping) return;
  stopping = true;
  e.preventDefault();
  sidecar.stop().finally(() => app.quit());
});

// Cadangan bila proses Electron berakhir tanpa will-quit
process.on('exit', () => {
  if (sidecar && sidecar.child.exitCode === null) sidecar.child.kill();
});
