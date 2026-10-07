import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

// Playwright mentranspilasi tes ke CommonJS, jadi require tersedia
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { startSidecar } = require('../desktop/sidecar.cjs');

/** Menjalankan sidecar sungguhan (folder output sementara, event contoh) untuk seluruh e2e. */
export default async function globalSetup() {
  const outDir = mkdtempSync(join(tmpdir(), 'vandebooth-e2e-'));
  const sidecar = await startSidecar({
    javaArgs: [
      `-Dvandebooth.output.dir=${outDir}`,
      '-Dvandebooth.event.name=Sample event',
      '-Dvandebooth.event.date=2026-10-12',
      '-Dvandebooth.maxRetakes=2',
    ],
  });
  process.env.E2E_API = `http://127.0.0.1:${sidecar.port}`;
  process.env.E2E_TOKEN = sidecar.token;
  process.env.E2E_OUT = outDir;

  return async () => {
    await sidecar.stop();
    rmSync(outDir, { recursive: true, force: true });
  };
}
