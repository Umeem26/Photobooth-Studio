import { describe, expect, it } from 'vitest';
import { REVIEW_AREA, REVIEW_BUTTON_H, fitBox, layoutCardPositions, reviewCardPos, reviewGrid } from './fit';

describe('fitBox', () => {
  const ratios: [number, number][] = [
    [600, 1800],
    [1200, 1800],
    [1800, 1200],
  ];

  it.each(ratios)('fits %i x %i contain inside the box with the limiting side >= 90%', (w, h) => {
    for (const [bw, bh, deg] of [
      [732, 592, 0],
      [820, 930, -3.5],
    ] as const) {
      const b = fitBox(w, h, bw, bh, deg);
      const r = (Math.abs(deg) * Math.PI) / 180;
      expect(b.w * Math.cos(r) + b.h * Math.sin(r)).toBeLessThanOrEqual(bw);
      expect(b.w * Math.sin(r) + b.h * Math.cos(r)).toBeLessThanOrEqual(bh);
      expect(b.w / b.h).toBeCloseTo(w / h, 1);
      const fill = Math.max(
        (b.w * Math.cos(r) + b.h * Math.sin(r)) / bw,
        (b.w * Math.sin(r) + b.h * Math.cos(r)) / bh,
      );
      expect(fill).toBeGreaterThanOrEqual(0.9);
    }
  });
});

describe('reviewGrid', () => {
  it.each([1, 2, 3, 4, 5, 6])('fits %i photos in the area with 96 px retake buttons', (n) => {
    const g = reviewGrid(n);
    expect(g.cols * g.rows).toBeGreaterThanOrEqual(n);
    expect(g.photoH).toBeGreaterThanOrEqual(120);
    expect(g.cardH).toBeGreaterThanOrEqual(g.photoH + REVIEW_BUTTON_H);
    for (let i = 0; i < n; i++) {
      const p = reviewCardPos(g, n, i);
      expect(p.left).toBeGreaterThanOrEqual(REVIEW_AREA.left - 0.5);
      expect(p.left + g.cardW).toBeLessThanOrEqual(REVIEW_AREA.left + REVIEW_AREA.width + 0.5);
      expect(p.top).toBeGreaterThanOrEqual(REVIEW_AREA.top - 0.5);
      expect(p.top + g.cardH).toBeLessThanOrEqual(REVIEW_AREA.top + REVIEW_AREA.height + 0.5);
    }
  });

  it('shows one photo big and keeps 4:3', () => {
    const one = reviewGrid(1);
    expect(one.cols).toBe(1);
    expect(one.photoW).toBeGreaterThan(reviewGrid(4).photoW);
    expect(one.photoW / one.photoH).toBeCloseTo(4 / 3, 1);
  });
});

describe('layoutCardPositions', () => {
  it.each([1, 2, 3, 4, 5, 6])('lays out %i cards without overlap and inside the stage', (n) => {
    const pos = layoutCardPositions(n);
    expect(pos).toHaveLength(n);
    for (const p of pos) {
      expect(p.left).toBeGreaterThanOrEqual(110);
      expect(p.left + 540).toBeLessThanOrEqual(1810);
      expect(p.top + 300).toBeLessThanOrEqual(910);
    }
    for (let i = 0; i < n; i++)
      for (let j = i + 1; j < n; j++) {
        const a = pos[i];
        const b = pos[j];
        const overlap = a.left < b.left + 540 && b.left < a.left + 540 && a.top < b.top + 300 && b.top < a.top + 300;
        expect(overlap).toBe(false);
      }
  });
});
