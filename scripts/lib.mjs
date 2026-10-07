// Utilitas bersama skrip root (lintas platform).
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
export const JAR = join(ROOT, 'target', 'vandebooth.jar');
export const UI_DIST = join(ROOT, 'ui', 'dist', 'index.html');
const isWin = process.platform === 'win32';

/** Menjalankan perintah, mewarisi stdio; reject bila kode keluar bukan 0. */
export function run(cmd, args, opts = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { cwd: ROOT, stdio: 'inherit', shell: isWin, ...opts });
    child.on('error', reject);
    child.on('exit', (code) => (code === 0 ? resolve() : reject(new Error(`${cmd} ${args.join(' ')} -> ${code}`))));
  });
}

export function mvnw(args) {
  // Path absolut berkutip: folder repo boleh mengandung spasi
  return isWin ? run(`"${join(ROOT, 'mvnw.cmd')}"`, args) : run(join(ROOT, 'mvnw'), args);
}

export function npm(args, opts) {
  return run(isWin ? 'npm.cmd' : 'npm', args, opts);
}

export async function buildJar() {
  await mvnw(['-q', 'package', '-DskipTests']);
}

export async function ensureJar() {
  if (!existsSync(JAR)) await buildJar();
}

export async function buildUi() {
  await npm(['run', 'build', '--workspace', '@vandebooth/ui']);
}
