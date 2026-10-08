'use strict';
// Menjalankan sidecar Java (target/vandebooth.jar --server) di port acak dengan token acak.

const { spawn } = require('node:child_process');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

const READY_PREFIX = 'VANDEBOOTH_LISTENING port=';
const TOKEN_HEADER = 'X-Booth-Token';

/** Mengambil port dari baris "VANDEBOOTH_LISTENING port=<n>", atau null. */
function parseListening(line) {
  const trimmed = String(line).trim();
  if (!trimmed.startsWith(READY_PREFIX)) return null;
  const port = Number(trimmed.slice(READY_PREFIX.length));
  return Number.isInteger(port) && port > 0 && port < 65536 ? port : null;
}

function newToken() {
  return crypto.randomBytes(24).toString('hex');
}

function findJava(env = process.env, resourcesPath = process.resourcesPath) {
  if (env.VANDEBOOTH_JAVA) return env.VANDEBOOTH_JAVA;
  // Aplikasi terpaket: runtime ringkas hasil jlink di resources/runtime
  if (resourcesPath) {
    const bundled = path.join(resourcesPath, 'runtime', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
    if (fs.existsSync(bundled)) return bundled;
  }
  if (env.JAVA_HOME) {
    const bin = path.join(env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
    if (fs.existsSync(bin)) return bin;
  }
  return 'java';
}

function findJar(env = process.env) {
  const candidates = [
    env.VANDEBOOTH_JAR,
    process.resourcesPath && path.join(process.resourcesPath, 'vandebooth.jar'),
    path.join(__dirname, '..', 'target', 'vandebooth.jar'),
  ].filter(Boolean);
  const found = candidates.find((p) => fs.existsSync(p));
  if (!found) throw new Error('vandebooth.jar tidak ditemukan. Jalankan "npm run build" dulu.');
  return found;
}

/**
 * @returns {Promise<{port:number, token:string, child:import('node:child_process').ChildProcess, stop:()=>Promise<void>}>}
 */
function startSidecar({ token = newToken(), java = findJava(), jar = findJar(), javaArgs = [], timeoutMs = 15000, log = () => {} } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(java, [...javaArgs, '-jar', jar, '--server', '--port', '0', '--exit-on-stdin-eof'], {
      env: { ...process.env, VANDEBOOTH_TOKEN: token },
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
    });

    let settled = false;
    let buffer = '';
    const timer = setTimeout(() => fail(new Error('Sidecar tidak siap dalam ' + timeoutMs + ' ms')), timeoutMs);

    function fail(err) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      child.kill();
      reject(err);
    }

    const stop = () =>
      new Promise((done) => {
        if (child.exitCode !== null || child.signalCode !== null) return done();
        child.once('exit', () => done());
        // stdin ditutup -> sidecar keluar sendiri (--exit-on-stdin-eof); kill sebagai cadangan
        child.stdin.end();
        setTimeout(() => child.kill(), 2000).unref();
      });

    child.stdout.setEncoding('utf8');
    child.stdout.on('data', (chunk) => {
      buffer += chunk;
      let idx;
      while ((idx = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 1);
        log(line);
        const port = parseListening(line);
        if (port && !settled) {
          settled = true;
          clearTimeout(timer);
          resolve({ port, token, child, stop });
        }
      }
    });
    child.stderr.setEncoding('utf8');
    child.stderr.on('data', (chunk) => log(chunk));
    child.on('error', fail);
    child.on('exit', (code) => fail(new Error('Sidecar berhenti dengan kode ' + code)));
  });
}

function getHealth(port, token, timeoutMs = 1000) {
  return new Promise((resolve, reject) => {
    const req = http.get(
      { host: '127.0.0.1', port, path: '/health', headers: { [TOKEN_HEADER]: token }, timeout: timeoutMs },
      (res) => {
        let body = '';
        res.on('data', (c) => (body += c));
        res.on('end', () => (res.statusCode === 200 ? resolve(JSON.parse(body)) : reject(new Error('HTTP ' + res.statusCode))));
      },
    );
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', reject);
  });
}

/** Menunggu /health sampai OK atau batas waktu. */
async function waitForHealth(port, token, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      return await getHealth(port, token);
    } catch (e) {
      lastError = e;
      await new Promise((r) => setTimeout(r, 150));
    }
  }
  throw new Error('/health tidak merespons: ' + (lastError && lastError.message));
}

module.exports = { parseListening, newToken, findJava, findJar, startSidecar, getHealth, waitForHealth, READY_PREFIX };
