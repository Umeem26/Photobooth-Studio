// npm run dev: server Vite + Electron (Electron yang menjalankan sidecar dari target/vandebooth.jar).
import { spawn } from 'node:child_process';
import http from 'node:http';
import { createRequire } from 'node:module';
import { ROOT, ensureJar } from './lib.mjs';

const DEV_URL = 'http://localhost:5173';
const isWin = process.platform === 'win32';
const children = [];

function start(cmd, args, env = {}) {
  const child = spawn(cmd, args, { cwd: ROOT, stdio: 'inherit', shell: isWin, env: { ...process.env, ...env } });
  children.push(child);
  return child;
}

function waitForUrl(url, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const attempt = () => {
      http
        .get(url, (res) => {
          res.resume();
          resolve();
        })
        .on('error', () => (Date.now() > deadline ? reject(new Error(`${url} tidak siap`)) : setTimeout(attempt, 250)));
    };
    attempt();
  });
}

function shutdown(code = 0) {
  for (const c of children) {
    if (c.exitCode !== null) continue;
    // Di Windows anak dijalankan lewat shell; matikan seluruh pohon proses (vite di bawah npm.cmd)
    if (isWin) spawn('taskkill', ['/pid', String(c.pid), '/T', '/F'], { stdio: 'ignore' });
    else c.kill();
  }
  process.exit(code);
}
process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));

await ensureJar();
start(isWin ? 'npm.cmd' : 'npm', ['run', 'dev', '--workspace', '@vandebooth/ui']);
await waitForUrl(DEV_URL);

const electron = createRequire(import.meta.url)('electron');
const app = spawn(electron, ['desktop', ...process.argv.slice(2)], {
  cwd: ROOT,
  stdio: 'inherit',
  env: { ...process.env, VITE_DEV_SERVER_URL: DEV_URL },
});
children.push(app);
app.on('exit', (code) => shutdown(code ?? 0));
