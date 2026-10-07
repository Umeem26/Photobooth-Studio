
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import type { Plugin } from 'vite';

// CSP hanya untuk build produksi (dev butuh skrip inline untuk React Refresh)
const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; " +
  "img-src 'self' blob: data:; media-src 'self' blob: mediastream:; connect-src 'self' http://127.0.0.1:*";

function cspPlugin(): Plugin {
  return {
    name: 'vandebooth-csp',
    apply: 'build',
    transformIndexHtml: (html) =>
      html.replace('<head>', `<head>
    <meta http-equiv="Content-Security-Policy" content="${CSP}" />`),
  };
}

// base './' agar build bisa dimuat Electron lewat file://
export default defineConfig({
  base: './',
  plugins: [react(), cspPlugin()],
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
  build: { outDir: 'dist', emptyOutDir: true, assetsInlineLimit: 0 },
  test: { environment: 'node', include: ['src/**/*.test.ts'] },
});
