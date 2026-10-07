import { useEffect, useRef } from 'react';

/** Keyboard cadangan: Enter = tombol primary, Esc = Cancel/Back. */
export function useKeys(handlers: { onEnter?: () => void; onEscape?: () => void }) {
  const ref = useRef(handlers);
  ref.current = handlers;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.repeat) return;
      if (e.key === 'Enter' && ref.current.onEnter) {
        e.preventDefault();
        ref.current.onEnter();
      } else if (e.key === 'Escape' && ref.current.onEscape) {
        e.preventDefault();
        ref.current.onEscape();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);
}
