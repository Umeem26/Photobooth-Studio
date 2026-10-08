/**
 * Geometri tampilan hasil: semua layout (portrait, landscape, strip tinggi) dimuat `contain`
 * ke kotak wadah tetap. Dihitung eksplisit agar kotak gambar sama dengan gambar yang tergambar.
 */

/** Persegi terbesar berrasio aw:ah yang, setelah diputar `deg` derajat, muat di cw x ch. */
export function fitBox(aw: number, ah: number, cw: number, ch: number, deg = 0): { w: number; h: number } {
  const r = (Math.abs(deg) * Math.PI) / 180;
  const cos = Math.cos(r);
  const sin = Math.sin(r);
  const s = Math.min(cw / (aw * cos + ah * sin), ch / (aw * sin + ah * cos));
  return { w: Math.floor(aw * s), h: Math.floor(ah * s) };
}

export interface ReviewGrid {
  cols: number;
  rows: number;
  /** Ukuran foto (4:3) dan kartu, dalam px kanvas 1920x1080. */
  photoW: number;
  photoH: number;
  cardW: number;
  cardH: number;
  gap: number;
}

export const REVIEW_AREA = { left: 110, top: 280, width: 1700, height: 620 };
export const REVIEW_PAD = 22;
export const REVIEW_BUTTON_H = 96;
export const REVIEW_GAP = 40;

/**
 * Grid Review untuk 1..6 foto: memilih jumlah kolom yang membuat foto terbesar dan
 * tetap muat di area (tombol Retake >= 96 px di setiap kartu).
 */
export function reviewGrid(n: number, area = REVIEW_AREA): ReviewGrid {
  const count = Math.max(1, n);
  let best: ReviewGrid | null = null;
  for (let cols = 1; cols <= count; cols++) {
    const rows = Math.ceil(count / cols);
    const maxCardW = (area.width - REVIEW_GAP * (cols - 1)) / cols;
    const maxCardH = (area.height - REVIEW_GAP * (rows - 1)) / rows;
    const fromW = maxCardW - 2 * REVIEW_PAD;
    const fromH = ((maxCardH - 3 * REVIEW_PAD - REVIEW_BUTTON_H) * 4) / 3;
    const photoW = Math.floor(Math.min(fromW, fromH));
    if (photoW <= 0) continue;
    const photoH = Math.floor((photoW * 3) / 4);
    const cand: ReviewGrid = {
      cols,
      rows,
      photoW,
      photoH,
      cardW: photoW + 2 * REVIEW_PAD,
      cardH: photoH + 3 * REVIEW_PAD + REVIEW_BUTTON_H,
      gap: REVIEW_GAP,
    };
    if (!best || cand.photoW > best.photoW) best = cand;
  }
  return best as ReviewGrid;
}

/** Posisi kartu ke-i (0-based) pada grid; baris terakhir yang tidak penuh dipusatkan. */
export function reviewCardPos(g: ReviewGrid, n: number, i: number, area = REVIEW_AREA): { left: number; top: number } {
  const row = Math.floor(i / g.cols);
  const inRow = Math.min(g.cols, n - row * g.cols);
  const rowW = inRow * g.cardW + (inRow - 1) * g.gap;
  const totalH = g.rows * g.cardH + (g.rows - 1) * g.gap;
  return {
    left: area.left + (area.width - rowW) / 2 + (i % g.cols) * (g.cardW + g.gap),
    top: area.top + (area.height - totalH) / 2 + row * (g.cardH + g.gap),
  };
}

export interface LayoutGridPos {
  left: number;
  top: number;
}

export const LAYOUT_CARD = { w: 540, h: 300, gapX: 40, gapY: 30 };
export const LAYOUT_AREA = { left: 110, top: 270, width: 1700, height: 640 };

/** Posisi kartu layout (1..6 kartu): maksimal 3 kolom, setiap baris dipusatkan. */
export function layoutCardPositions(n: number): LayoutGridPos[] {
  const { w, h, gapX, gapY } = LAYOUT_CARD;
  const cols = Math.min(3, Math.max(1, n));
  const rows = Math.ceil(n / cols);
  const totalH = rows * h + (rows - 1) * gapY;
  const out: LayoutGridPos[] = [];
  for (let i = 0; i < n; i++) {
    const row = Math.floor(i / cols);
    const inRow = Math.min(cols, n - row * cols);
    const rowW = inRow * w + (inRow - 1) * gapX;
    out.push({
      left: LAYOUT_AREA.left + (LAYOUT_AREA.width - rowW) / 2 + (i % cols) * (w + gapX),
      top: LAYOUT_AREA.top + (LAYOUT_AREA.height - totalH) / 2 + row * (h + gapY),
    });
  }
  return out;
}
