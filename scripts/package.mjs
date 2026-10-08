// npm run package: JAR + UI, runtime Java ringkas (jlink) lalu electron-builder (NSIS x64).
// Argumen tambahan diteruskan ke electron-builder, mis. `npm run package -- --dir`.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { JAR, ROOT, buildJar, buildUi, run } from './lib.mjs';

const RUNTIME = join(ROOT, 'desktop', 'build', 'runtime');
const DIST = join(ROOT, 'desktop', 'release');
// Modul tambahan di luar hasil jdeps (logging, JNDI, preferensi untuk pustaka webcam/gson)
const EXTRA_MODULES = ['java.desktop', 'java.logging', 'java.naming', 'jdk.httpserver', 'java.prefs', 'java.sql'];

function jdkTool(name) {
  const exe = process.platform === 'win32' ? `${name}.exe` : name;
  return process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', exe) : exe;
}

function buildRuntime() {
  const deps = execFileSync(jdkTool('jdeps'), ['--multi-release', '17', '--ignore-missing-deps', '--print-module-deps', JAR], {
    encoding: 'utf8',
  })
    .trim()
    .split(/\r?\n/)
    .pop()
    .split(',');
  const modules = [...new Set([...deps, ...EXTRA_MODULES])].sort();
  console.log('Modul runtime:', modules.join(','));
  rmSync(RUNTIME, { recursive: true, force: true });
  execFileSync(
    jdkTool('jlink'),
    ['--add-modules', modules.join(','), '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=zip-6', '--output', RUNTIME],
    { stdio: 'inherit' },
  );
}

function writeChecksums() {
  const installers = existsSync(DIST) ? readdirSync(DIST).filter((f) => f.endsWith('.exe')) : [];
  if (!installers.length) return;
  const lines = installers.map((f) => `${createHash('sha256').update(readFileSync(join(DIST, f))).digest('hex')}  ${f}`);
  writeFileSync(join(DIST, 'SHA256SUMS.txt'), lines.join('\n') + '\n');
  console.log(lines.join('\n'));
}

await buildJar();
await buildUi();
buildRuntime();
await run(process.platform === 'win32' ? 'npx.cmd' : 'npx', ['electron-builder', '--win', '--x64', ...process.argv.slice(2)], {
  cwd: join(ROOT, 'desktop'),
});
writeChecksums();
