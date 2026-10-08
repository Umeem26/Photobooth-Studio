import type { Layout } from '../api/types';
import { fitBox } from '../layout/fit';

const CELL_FILLS = ['var(--illu-sand)', 'var(--illu-sage)', 'var(--illu-blush)', 'var(--illu-lilac)'];

/**
 * Miniatur layout digambar dari canvas/cells (SVG, bukan gambar tetap): kertas putih,
 * sel foto berwarna ilustrasi, dan bilah footer sesuai gaya (tengah, kiri-kanan, atau kolom).
 */
export function LayoutMiniature({ layout, maxW, maxH }: { layout: Layout; maxW: number; maxH: number }) {
  const { w, h } = layout.canvas;
  const box = fitBox(w, h, maxW, maxH);
  const f = layout.footer;
  const bar = (x: number, y: number, bw: number, bh: number, fill: string, key: string) => (
    <rect key={key} x={x} y={y} width={bw} height={bh} rx={bh / 2} fill={fill} />
  );
  const mid = f.y + f.h / 2;
  const unit = Math.min(f.w, f.h);
  const footer: JSX.Element[] = [];
  if (f.style === 'center') {
    footer.push(bar(f.x + f.w * 0.2, mid - unit * 0.12, f.w * 0.6, unit * 0.13, 'var(--ink)', 'm'));
    footer.push(bar(f.x + f.w * 0.3, mid + unit * 0.12, f.w * 0.4, unit * 0.06, 'var(--ink-2)', 'c'));
  } else if (f.style === 'split') {
    const left = layout.cells[0].x;
    const right = Math.max(...layout.cells.map((c) => c.x + c.w));
    footer.push(bar(left, mid - unit * 0.08, (right - left) * 0.24, unit * 0.16, 'var(--ink)', 'm'));
    footer.push(bar(right - (right - left) * 0.2, mid - unit * 0.025, (right - left) * 0.2, unit * 0.05, 'var(--ink-2)', 'c'));
  } else {
    const cx = f.x + f.w / 2;
    footer.push(bar(cx - f.w * 0.2, mid - unit * 0.34, f.w * 0.4, unit * 0.12, 'var(--ink)', 'm1'));
    footer.push(bar(cx - f.w * 0.1, mid - unit * 0.18, f.w * 0.2, unit * 0.12, 'var(--ink)', 'm2'));
    footer.push(bar(cx - f.w * 0.23, mid - unit * 0.02, f.w * 0.46, unit * 0.12, 'var(--ink)', 'm3'));
    footer.push(bar(cx - f.w * 0.2, mid + unit * 0.2, f.w * 0.4, unit * 0.05, 'var(--ink-2)', 'c'));
  }
  const radius = Math.min(w, h) * 0.012;
  return (
    <svg width={box.w} height={box.h} viewBox={`0 0 ${w} ${h}`} role="img" aria-hidden="true"
      data-testid={`mini-${layout.id}`} style={{ display: 'block', boxShadow: 'var(--shadow-strip)', background: 'var(--strip-white)' }}>
      <rect width={w} height={h} fill="var(--strip-white)" />
      {layout.cells.map((c, i) => (
        <rect key={i} x={c.x} y={c.y} width={c.w} height={c.h} rx={radius} fill={CELL_FILLS[i % CELL_FILLS.length]} />
      ))}
      {footer}
    </svg>
  );
}
