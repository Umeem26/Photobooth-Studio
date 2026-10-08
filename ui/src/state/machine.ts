import type { BoothConfig, FilterOption, Layout } from '../api/types';

/**
 * State machine tunggal (docs/flow.md + docs/phase3.md):
 * attract -> layout -> [pay] -> capture -> review -> filter -> result -> attract.
 * 'pay' hanya bila payment.enabled. 'operator' = Mode Operator dari Attract.
 * 'boot' memeriksa sidecar; 'error' menampilkan kamera/sidecar bermasalah.
 */
export type Screen =
  | 'boot'
  | 'attract'
  | 'layout'
  | 'pay'
  | 'capture'
  | 'review'
  | 'filter'
  | 'result'
  | 'operator'
  | 'error';
export type ErrorKind = 'camera' | 'service';

export const RESULT_TIMEOUT_S = 45;

export interface State {
  screen: Screen;
  errorKind: ErrorKind | null;
  config: BoothConfig | null;
  layouts: Layout[];
  filters: FilterOption[];
  sessionId: string | null;
  /** Layout tempat sessionId dibuat di sidecar. */
  sessionLayoutId: string | null;
  layoutId: string | null;
  /** Object URL foto per slot (index 0 = foto 1). */
  frames: (string | null)[];
  /** Index foto (1-based) yang masih harus diambil di layar Capture. */
  queue: number[];
  retakesUsed: number;
  filterId: string;
  stripUrl: string | null;
  /** Hitung mundur layar Result; dijeda selama mencetak. */
  resultSecondsLeft: number;
  printing: boolean;
  printsUsed: number;
  /** Token admin Mode Operator (hanya di memori). */
  adminToken: string | null;
  toast: string | null;
}

export type Action =
  | { type: 'BOOTED'; config: BoothConfig; layouts: Layout[]; filters: FilterOption[] }
  | { type: 'CONFIG_UPDATED'; config: BoothConfig }
  | { type: 'FAILED'; kind: ErrorKind }
  | { type: 'RETRY' }
  | { type: 'SESSION_STARTED'; sessionId: string; layoutId: string }
  | { type: 'LAYOUT_SELECTED'; layoutId: string }
  | { type: 'LAYOUT_CONFIRMED'; sessionId: string; layoutId: string }
  | { type: 'PAYMENT_CONFIRMED' }
  | { type: 'BACK_TO_LAYOUT' }
  | { type: 'FRAME_CAPTURED'; index: number; url: string }
  | { type: 'CAPTURE_FINISHED' }
  | { type: 'RETAKE_REQUESTED'; index: number }
  | { type: 'REVIEW_ACCEPTED' }
  | { type: 'FILTER_SELECTED'; filterId: string }
  | { type: 'STRIP_COMPOSED'; filterId: string; url: string }
  | { type: 'FILTER_CONFIRMED' }
  | { type: 'RESULT_TICK' }
  | { type: 'PRINT_STARTED' }
  | { type: 'PRINT_FINISHED'; ok: boolean; copiesUsed?: number }
  | { type: 'RESTARTED_SAME_LAYOUT'; sessionId: string }
  | { type: 'OPERATOR_OPENED'; token: string }
  | { type: 'OPERATOR_CLOSED' }
  | { type: 'BACK_TO_ATTRACT' }
  | { type: 'TOAST'; message: string | null };

export const DEFAULT_FILTER = 'original';

export const initialState: State = {
  screen: 'boot',
  errorKind: null,
  config: null,
  layouts: [],
  filters: [],
  sessionId: null,
  sessionLayoutId: null,
  layoutId: null,
  frames: [],
  queue: [],
  retakesUsed: 0,
  filterId: DEFAULT_FILTER,
  stripUrl: null,
  resultSecondsLeft: RESULT_TIMEOUT_S,
  printing: false,
  printsUsed: 0,
  adminToken: null,
  toast: null,
};

export function photosFor(state: Pick<State, 'layouts'>, layoutId: string | null): number {
  return state.layouts.find((l) => l.id === layoutId)?.photos ?? 0;
}

export function retakesLeft(state: State): number {
  return Math.max(0, (state.config?.maxRetakes ?? 0) - state.retakesUsed);
}

export function paymentEnabled(state: Pick<State, 'config'>): boolean {
  return !!state.config?.payment?.enabled;
}

export function printLimitReached(state: State): boolean {
  return state.printsUsed >= (state.config?.print?.maxCopies ?? 0);
}

function freshSession(state: State, sessionId: string, layoutId: string): State {
  const n = photosFor(state, layoutId);
  return {
    ...state,
    // Dengan pembayaran aktif, sesi baru selalu lewat layar Pay dulu
    screen: paymentEnabled(state) ? 'pay' : 'capture',
    sessionId,
    sessionLayoutId: layoutId,
    layoutId,
    frames: Array.from({ length: n }, () => null),
    queue: Array.from({ length: n }, (_, i) => i + 1),
    retakesUsed: 0,
    filterId: DEFAULT_FILTER,
    stripUrl: null,
    printing: false,
    printsUsed: 0,
  };
}

function clearSession(state: State): State {
  return {
    ...state,
    sessionId: null,
    sessionLayoutId: null,
    frames: [],
    queue: [],
    retakesUsed: 0,
    filterId: DEFAULT_FILTER,
    stripUrl: null,
    resultSecondsLeft: RESULT_TIMEOUT_S,
    printing: false,
    printsUsed: 0,
  };
}

export function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'BOOTED':
      return {
        ...clearSession(state),
        screen: 'attract',
        errorKind: null,
        config: action.config,
        layouts: action.layouts,
        filters: action.filters,
        layoutId: action.layouts[0]?.id ?? null,
      };

    case 'CONFIG_UPDATED':
      return { ...state, config: action.config };

    case 'FAILED':
      return { ...clearSession(state), screen: 'error', errorKind: action.kind, adminToken: null };

    case 'RETRY':
      return state.screen === 'error' ? { ...state, screen: 'boot', errorKind: null } : state;

    case 'SESSION_STARTED':
      // Dari Attract (Start photos) atau Review (Start over): sesi baru, pilih layout lagi
      if (state.screen !== 'attract' && state.screen !== 'review') return state;
      return {
        ...clearSession(state),
        screen: 'layout',
        sessionId: action.sessionId,
        sessionLayoutId: action.layoutId,
        layoutId: state.screen === 'attract' ? (state.layouts[0]?.id ?? null) : state.layoutId,
      };

    case 'LAYOUT_SELECTED':
      if (state.screen !== 'layout' || !state.layouts.some((l) => l.id === action.layoutId)) return state;
      return { ...state, layoutId: action.layoutId };

    case 'LAYOUT_CONFIRMED':
      if (state.screen !== 'layout') return state;
      return freshSession(state, action.sessionId, action.layoutId);

    case 'PAYMENT_CONFIRMED':
      return state.screen === 'pay' ? { ...state, screen: 'capture' } : state;

    case 'BACK_TO_LAYOUT':
      return state.screen === 'pay' ? { ...state, screen: 'layout' } : state;

    case 'FRAME_CAPTURED': {
      if (state.screen !== 'capture' || state.queue[0] !== action.index) return state;
      const frames = [...state.frames];
      frames[action.index - 1] = action.url;
      return { ...state, frames, queue: state.queue.slice(1) };
    }

    case 'CAPTURE_FINISHED':
      if (state.screen !== 'capture' || state.queue.length > 0) return state;
      return { ...state, screen: 'review' };

    case 'RETAKE_REQUESTED':
      if (state.screen !== 'review' || retakesLeft(state) === 0) return state;
      if (action.index < 1 || action.index > state.frames.length) return state;
      return { ...state, screen: 'capture', queue: [action.index], retakesUsed: state.retakesUsed + 1 };

    case 'REVIEW_ACCEPTED':
      if (state.screen !== 'review' || state.frames.some((f) => f === null)) return state;
      return { ...state, screen: 'filter', filterId: DEFAULT_FILTER, stripUrl: null };

    case 'FILTER_SELECTED':
      if (state.screen !== 'filter' || !state.filters.some((f) => f.id === action.filterId)) return state;
      return { ...state, filterId: action.filterId };

    case 'STRIP_COMPOSED':
      // Abaikan hasil compose lama bila tamu sudah memilih filter lain
      if (state.screen !== 'filter' || action.filterId !== state.filterId) return state;
      return { ...state, stripUrl: action.url };

    case 'FILTER_CONFIRMED':
      if (state.screen !== 'filter' || !state.stripUrl) return state;
      return { ...state, screen: 'result', resultSecondsLeft: RESULT_TIMEOUT_S, printing: false };

    case 'RESULT_TICK': {
      if (state.screen !== 'result' || state.printing) return state;
      const left = state.resultSecondsLeft - 1;
      return left <= 0 ? { ...clearSession(state), screen: 'attract' } : { ...state, resultSecondsLeft: left };
    }

    case 'PRINT_STARTED':
      if (state.screen !== 'result' || state.printing || printLimitReached(state)) return state;
      return { ...state, printing: true };

    case 'PRINT_FINISHED':
      if (state.screen !== 'result' || !state.printing) return state;
      return {
        ...state,
        printing: false,
        printsUsed: action.copiesUsed ?? (action.ok ? state.printsUsed + 1 : state.printsUsed),
      };

    case 'RESTARTED_SAME_LAYOUT':
      if (state.screen !== 'result' || !state.layoutId) return state;
      return freshSession(state, action.sessionId, state.layoutId);

    case 'OPERATOR_OPENED':
      if (state.screen !== 'attract') return state;
      return { ...clearSession(state), screen: 'operator', adminToken: action.token };

    case 'OPERATOR_CLOSED':
      return state.screen === 'operator'
        ? { ...state, screen: 'attract', adminToken: null, layoutId: state.layouts[0]?.id ?? null }
        : state;

    case 'BACK_TO_ATTRACT':
      return { ...clearSession(state), screen: 'attract', layoutId: state.layouts[0]?.id ?? null };

    case 'TOAST':
      return { ...state, toast: action.message };
  }
}

export type StepLabel = 'Layout' | 'Pay' | 'Photos' | 'Result';

/** Langkah StepPill: 3 langkah, atau 4 (dengan Pay) bila pembayaran aktif. */
export function stepsFor(withPayment: boolean): StepLabel[] {
  return withPayment ? ['Layout', 'Pay', 'Photos', 'Result'] : ['Layout', 'Photos', 'Result'];
}

/** Nomor langkah aktif (1-based) untuk layar, atau null bila tanpa StepPill. */
export function stepFor(screen: Screen, withPayment = false): number | null {
  const label: StepLabel | null =
    screen === 'layout'
      ? 'Layout'
      : screen === 'pay'
        ? 'Pay'
        : screen === 'capture' || screen === 'review'
          ? 'Photos'
          : screen === 'filter' || screen === 'result'
            ? 'Result'
            : null;
  if (!label) return null;
  const idx = stepsFor(withPayment).indexOf(label);
  return idx < 0 ? null : idx + 1;
}
