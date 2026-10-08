import { createContext, useCallback, useContext, useRef, useState, type Dispatch, type ReactNode } from 'react';
import { ApiError, ServiceUnavailableError, type BoothApi } from '../api/client';
import { CAMERA_CONSTRAINTS } from '../camera/capture';
import type { Action, State } from '../state/machine';

export interface Camera {
  stream: MediaStream | null;
  /** Meminta kamera sekali; mengembalikan false bila ditolak/tidak ada. */
  request: () => Promise<boolean>;
}

export interface Booth {
  state: State;
  dispatch: Dispatch<Action>;
  api: BoothApi | null;
  camera: Camera;
  showToast: (message: string) => void;
  /** Error sidecar -> layar Error; error lain -> toast. */
  handleError: (e: unknown, toastMessage: string) => void;
}

const BoothContext = createContext<Booth | null>(null);

export function useBooth(): Booth {
  const ctx = useContext(BoothContext);
  if (!ctx) throw new Error('useBooth di luar BoothProvider');
  return ctx;
}

const CAMERA_STALL_MS = 5_000;

/**
 * getUserMedia yang tidak selesai dalam 5 detik diminta ulang sekali (permintaan pertama kadang
 * menggantung di Electron). Stream dari permintaan lama yang datang terlambat langsung dihentikan.
 */
async function getUserMediaWithRetry(): Promise<MediaStream> {
  const first = navigator.mediaDevices.getUserMedia(CAMERA_CONSTRAINTS);
  const stalled = Symbol('stalled');
  let timer: number | undefined;
  const winner = await Promise.race([
    first,
    new Promise<typeof stalled>((resolve) => {
      timer = window.setTimeout(() => resolve(stalled), CAMERA_STALL_MS);
    }),
  ]);
  window.clearTimeout(timer);
  if (winner !== stalled) return winner;
  first.then((late) => late.getTracks().forEach((t) => t.stop())).catch(() => undefined);
  return navigator.mediaDevices.getUserMedia(CAMERA_CONSTRAINTS);
}

export function useCameraStream(): Camera {
  const [stream, setStream] = useState<MediaStream | null>(null);
  const pending = useRef<Promise<boolean> | null>(null);

  const request = useCallback(async () => {
    if (stream && stream.getVideoTracks().some((t) => t.readyState === 'live')) return true;
    if (pending.current) return pending.current;
    pending.current = (async () => {
      try {
        const s = await getUserMediaWithRetry();
        s.getVideoTracks().forEach((t) => t.addEventListener('ended', () => setStream(null)));
        setStream(s);
        return true;
      } catch {
        setStream(null);
        return false;
      } finally {
        pending.current = null;
      }
    })();
    return pending.current;
  }, [stream]);

  return { stream, request };
}

export function BoothProvider({
  value,
  children,
}: {
  value: Omit<Booth, 'handleError'>;
  children: ReactNode;
}) {
  const { dispatch, showToast } = value;
  const handleError = useCallback(
    (e: unknown, toastMessage: string) => {
      if (e instanceof ServiceUnavailableError || (e instanceof ApiError && e.status === 401)) {
        dispatch({ type: 'FAILED', kind: 'service' });
      } else {
        showToast(toastMessage);
      }
    },
    [dispatch, showToast],
  );
  return <BoothContext.Provider value={{ ...value, handleError }}>{children}</BoothContext.Provider>;
}
