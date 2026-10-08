import type { BoothEnv } from './env';
import type {
  AdminConfig,
  AdminStatus,
  BoothConfig,
  FilterOption,
  Layout,
  PaymentStart,
  Printer,
  PrintStatus,
  SessionSummary,
  ShareLink,
} from './types';

/** Sidecar membalas dengan status error (4xx/5xx). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    /** Isi body error lain (mis. attemptsLeft, retryAfterSeconds). */
    readonly details: Record<string, unknown> = {},
  ) {
    super(message);
  }
}

/** Sidecar tidak bisa dihubungi (mati, timeout). */
export class ServiceUnavailableError extends Error {}

const TOKEN_HEADER = 'X-Booth-Token';
const ADMIN_HEADER = 'X-Admin-Token';

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
      let details: Record<string, unknown> = {};
      try {
        const body = await res.json();
        code = body.error ?? code;
        message = body.message ?? message;
        details = body;
      } catch {
        // body bukan JSON
      }
      throw new ApiError(res.status, code, message, details);
    }
    return res;
  }

  private async json<T>(path: string, init?: RequestInit, timeoutMs?: number): Promise<T> {
    return (await this.request(path, init, timeoutMs)).json() as Promise<T>;
  }

  private post<T>(path: string, body: unknown, timeoutMs?: number, headers: Record<string, string> = {}): Promise<T> {
    return this.json<T>(
      path,
      { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) },
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

  /** @param continueFrom sesi sebelumnya ("Start over") agar status lunas ikut terbawa */
  async createSession(layout: string, continueFrom?: string | null): Promise<string> {
    const body = continueFrom ? { layout, continueFrom } : { layout };
    return (await this.post<{ sessionId: string }>('/api/sessions', body)).sessionId;
  }

  // ---------------------------------------------------------------- pembayaran demo

  startPayment(sessionId: string) {
    return this.post<PaymentStart>(`/api/sessions/${sessionId}/payment`, {});
  }

  simulatePayment(sessionId: string) {
    return this.post<{ status: string }>(`/api/sessions/${sessionId}/payment/simulate`, {});
  }

  paymentStatus(sessionId: string) {
    return this.json<{ status: string; required: boolean }>(`/api/sessions/${sessionId}/payment`);
  }

  // ---------------------------------------------------------------- berbagi dan cetak

  share(sessionId: string) {
    return this.post<ShareLink>(`/api/sessions/${sessionId}/share`, {});
  }

  print(sessionId: string, copies = 1) {
    return this.post<PrintStatus>(`/api/sessions/${sessionId}/print`, { copies });
  }

  printStatus(sessionId: string) {
    return this.json<PrintStatus>(`/api/sessions/${sessionId}/print`);
  }

  printers() {
    return this.json<Printer[]>('/api/printers');
  }

  // ---------------------------------------------------------------- Mode Operator

  pinStatus() {
    return this.json<{ set: boolean }>('/api/admin/pin');
  }

  async createPin(pin: string): Promise<string> {
    return (await this.post<{ token: string }>('/api/admin/pin', { pin })).token;
  }

  /** Salah PIN -> ApiError 403 (details.attemptsLeft); terkunci -> 423 (details.retryAfterSeconds). */
  async login(pin: string): Promise<string> {
    return (await this.post<{ token: string }>('/api/admin/login', { pin })).token;
  }

  admin(token: string) {
    const h = { [ADMIN_HEADER]: token };
    return {
      logout: () => this.post('/api/admin/logout', {}, undefined, h),
      config: () => this.json<AdminConfig>('/api/admin/config', { headers: h }),
      saveConfig: (changes: AdminConfig) =>
        this.json<AdminConfig>('/api/admin/config', {
          method: 'PUT',
          headers: { ...h, 'Content-Type': 'application/json' },
          body: JSON.stringify(changes),
        }),
      sessions: () => this.json<SessionSummary[]>('/api/admin/sessions', { headers: h }),
      thumbUrl: async (id: string) =>
        URL.createObjectURL(await (await this.request(`/api/admin/sessions/${id}/thumb.jpg`, { headers: h })).blob()),
      deleteSession: (id: string) => this.json(`/api/admin/sessions/${id}`, { method: 'DELETE', headers: h }),
      exportAll: () => this.post<{ path: string }>('/api/admin/export-all', {}, 60_000, h),
      purge: (olderThanDays: number) =>
        this.post<{ deleted: number }>('/api/admin/purge', { olderThanDays }, undefined, h),
      status: () => this.json<AdminStatus>('/api/admin/status', { headers: h }),
      shareTest: () => this.post<ShareLink>('/api/admin/share-test', {}, undefined, h),
      printTest: () => this.post<PrintStatus>('/api/admin/print-test', {}, undefined, h),
      printTestStatus: () => this.json<PrintStatus>('/api/admin/print-test', { headers: h }),
    };
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
