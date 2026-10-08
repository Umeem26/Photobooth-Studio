export interface BoothConfig {
  'event.name': string;
  'event.date': string;
  maxRetakes: number;
  photosPerLayout: Record<string, number>;
  /** Id layout yang ditawarkan ke tamu (urutan tetap, minimal satu). */
  layoutsOffered?: string[];
  countdownSeconds: number;
  pauseSeconds: number;
  payment: { enabled: boolean; price: number };
  share: { enabled: boolean };
  print: { maxCopies: number };
}

export interface Layout {
  id: string;
  name: string;
  description: string;
  photos: number;
  orientation: 'vertical' | 'horizontal';
  /** Kertas asal layout: "2x6", "6x4", atau "4x6". */
  paper: string;
  /** Ukuran canvas hasil compose (px @300 DPI). */
  canvas: { w: number; h: number };
  /** Area footer (wordmark + caption) dalam px canvas. */
  footer: { x: number; y: number; w: number; h: number; style: 'center' | 'split' | 'side' };
  /** Sel foto dalam px canvas. */
  cells: { x: number; y: number; w: number; h: number }[];
}

export interface FilterOption {
  id: string;
  name: string;
}

export interface PaymentStart {
  status: 'pending' | 'paid';
  amount: number;
  qrPng: string;
}

export interface ShareLink {
  url: string;
  qrPng: string;
}

export type PrintState = 'idle' | 'queued' | 'printing' | 'done' | 'failed';

export interface PrintStatus {
  status: PrintState;
  queued: boolean;
  copiesUsed: number;
  maxCopies: number;
  message?: string;
}

export interface Printer {
  name: string;
  default: boolean;
  status: string;
}

/** Nilai config yang bisa diubah operator (semua string, sesuai config.properties). */
export type AdminConfig = Record<string, string>;

export interface SessionSummary {
  id: string;
  status: string;
  layout: string;
  filter: string;
  createdAt: string;
  frames: number;
  hasStrip: boolean;
}

export interface AdminStatus {
  version: string;
  printers: Printer[];
  printMode: string;
  diskFreeBytes: number;
  outputDir: string;
  sessions: number;
  shareEnabled: boolean;
  shareRunning: boolean;
  shareUrl: string;
  shareDetectedHost: string;
  sharePort: number;
  shareError: string;
}
