import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

// Playwright mentranspilasi tes ke CommonJS, jadi require tersedia
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { startSidecar } = require('../desktop/sidecar.cjs');

export interface TestSidecar {
  apiBase: string;
  token: string;
  outDir: string;
  stop: () => Promise<void>;
}

/**
 * Sidecar sungguhan untuk tes: folder output dan folder config sementara (PIN tidak pernah
 * menyentuh folder pengguna), server berbagi hanya di 127.0.0.1 port acak, cetak ke file.
 */
export async function startTestSidecar(extra: Record<string, string> = {}): Promise<TestSidecar> {
  const outDir = mkdtempSync(join(tmpdir(), 'vandebooth-e2e-'));
  const props: Record<string, string> = {
    'output.dir': outDir,
    'config.dir': join(outDir, 'config'),
    'event.name': 'Sample event',
    'event.date': '2026-10-12',
    maxRetakes: '2',
    'share.bindAddress': '127.0.0.1',
    'share.host': '127.0.0.1',
    'share.port': '0',
    'print.mode': 'file',
    ...extra,
  };
  const sidecar = await startSidecar({
    javaArgs: Object.entries(props).map(([k, v]) => `-Dvandebooth.${k}=${v}`),
  });
  return {
    apiBase: `http://127.0.0.1:${sidecar.port}`,
    token: sidecar.token,
    outDir,
    stop: async () => {
      await sidecar.stop();
      rmSync(outDir, { recursive: true, force: true });
    },
  };
}

/** PIN operator acak per run tes (tidak pernah ditulis ke repo). */
export function randomPin(): string {
  return String(100000 + Math.floor(Math.random() * 900000));
}
