import { useLayoutEffect, useState, type ReactNode } from 'react';

export const STAGE_W = 1920;
export const STAGE_H = 1080;

/** Skala kanvas 1920x1080 agar muat di jendela apa pun, letterbox warna cream. */
export function stageScale(width: number, height: number): number {
  return Math.min(width / STAGE_W, height / STAGE_H);
}

export function Stage({ children }: { children: ReactNode }) {
  const [scale, setScale] = useState(() => stageScale(window.innerWidth, window.innerHeight));

  useLayoutEffect(() => {
    const onResize = () => setScale(stageScale(window.innerWidth, window.innerHeight));
    window.addEventListener('resize', onResize);
    onResize();
    return () => window.removeEventListener('resize', onResize);
  }, []);

  return (
    <div className="stage-viewport">
      <div className="stage" style={{ transform: `translate(-50%, -50%) scale(${scale})` }} data-testid="stage">
        {children}
      </div>
    </div>
  );
}
