import { useEffect, useRef } from 'react';

export const IDLE_MS = 90_000;

/** Memanggil onIdle setelah `ms` tanpa sentuhan/klik/tombol. */
export function useIdle(onIdle: () => void, ms = IDLE_MS, enabled = true) {
  const ref = useRef(onIdle);
  ref.current = onIdle;

  useEffect(() => {
    if (!enabled) return;
    let timer = window.setTimeout(() => ref.current(), ms);
    const reset = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => ref.current(), ms);
    };
    const events = ['pointerdown', 'keydown', 'touchstart'] as const;
    events.forEach((ev) => window.addEventListener(ev, reset, { passive: true }));
    return () => {
      window.clearTimeout(timer);
      events.forEach((ev) => window.removeEventListener(ev, reset));
    };
  }, [ms, enabled]);
}
