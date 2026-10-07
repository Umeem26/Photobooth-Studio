import type { BoothEnv } from './env';
import type { BoothConfig, FilterOption, Layout } from './types';

/** Sidecar membalas dengan status error (4xx/5xx). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

/** Sidecar tidak bisa dihubungi (mati, timeout). */
export class ServiceUnavailableError extends Error {}

const TOKEN_HEADER = 'X-Booth-Token';

export class BoothApi {
  constructor(private readonly env: BoothEnv) {}

  private async request(path: string, init: RequestInit = {}, timeoutMs = 10_000): Promise<Response> {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), timeoutMs);
    let res: Response;
    try {
      res = await fetch(this.env.apiBase + path, {
        ...init,
        headers: { ...(init.headers as Record<string, string>), [TOKEN_HEADER]: this.env.token },
        signal: ctrl.signal,
      });
    } catch (e) {
      throw new ServiceUnavailableError(e instanceof Error ? e.message : String(e));
    } finally {
      clearTimeout(timer);
    }
    if (!res.ok) {
      let code = 'error';
      let message = res.statusText;
      try {
        const body = await res.json();
        code = body.error ?? code;
        message = body.message ?? message;
      } catch {
        // body bukan JSON
      }
      throw new ApiError(res.status, code, message);
    }
    return res;
  }

  private async json<T>(path: string, init?: RequestInit, timeoutMs?: number): Promise<T> {
    return (await this.request(path, init, timeoutMs)).json() as Promise<T>;
  }

  private post<T>(path: string, body: unknown, timeoutMs?: number): Promise<T> {
    return this.json<T>(
      path,
      { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) },
      timeoutMs,
    );
  }

  health() {
    return this.json<{ ok: boolean; version: string }>('/health', undefined, 3_000);
  }

  config() {
    return this.json<BoothConfig>('/api/config');
  }

  layouts() {
    return this.json<Layout[]>('/api/layouts');
  }

  filters() {
    return this.json<FilterOption[]>('/api/filters');
  }

  async createSession(layout: string): Promise<string> {
    return (await this.post<{ sessionId: string }>('/api/sessions', { layout })).sessionId;
  }

  async putFrame(sessionId: string, index: number, jpeg: Blob): Promise<void> {
    await this.request(`/api/sessions/${sessionId}/frames/${index}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'image/jpeg' },
      body: jpeg,
    });
  }

  async compose(sessionId: string, filter: string): Promise<string> {
    return (await this.post<{ stripUrl: string }>(`/api/sessions/${sessionId}/compose`, { filter }, 20_000))
      .stripUrl;
  }

  /** Mengambil gambar ber-token dan mengembalikan object URL (header tidak bisa dikirim lewat <img src>). */
  async imageUrl(path: string): Promise<string> {
    const blob = await (await this.request(path, {}, 20_000)).blob();
    return URL.createObjectURL(blob);
  }

  async exportLocal(sessionId: string): Promise<string> {
    return (await this.post<{ path: string }>(`/api/sessions/${sessionId}/export`, { strategy: 'local' })).path;
  }

  async abandon(sessionId: string): Promise<void> {
    await this.post(`/api/sessions/${sessionId}/abandon`, {});
  }
}
