import { startTestSidecar } from './sidecar';

/** Sidecar bersama untuk alur utama (pembayaran OFF, bawaan). */
export default async function globalSetup() {
  const sidecar = await startTestSidecar();
  process.env.E2E_API = sidecar.apiBase;
  process.env.E2E_TOKEN = sidecar.token;
  process.env.E2E_OUT = sidecar.outDir;
  return sidecar.stop;
}
