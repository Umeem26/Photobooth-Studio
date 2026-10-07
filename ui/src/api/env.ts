/** Alamat sidecar dan token. Sumber: preload Electron, init script tes, query string, atau env Vite. */
export interface BoothEnv {
  apiBase: string;
  token: string;
}

declare global {
  interface Window {
    booth?: BoothEnv;
    __BOOTH__?: BoothEnv;
  }
}

export function resolveEnv(
  win: Pick<Window, 'booth' | '__BOOTH__' | 'location'> | undefined = typeof window === 'undefined' ? undefined : window,
  env: Record<string, string | undefined> = (import.meta as { env?: Record<string, string | undefined> }).env ?? {},
): BoothEnv | null {
  const fromWindow = win?.booth ?? win?.__BOOTH__;
  if (fromWindow?.apiBase && fromWindow.token) return fromWindow;

  if (win?.location) {
    const q = new URLSearchParams(win.location.search);
    const api = q.get('api');
    const token = q.get('token');
    if (api && token) return { apiBase: api, token };
  }

  if (env.VITE_BOOTH_API && env.VITE_BOOTH_TOKEN) {
    return { apiBase: env.VITE_BOOTH_API, token: env.VITE_BOOTH_TOKEN };
  }
  return null;
}
