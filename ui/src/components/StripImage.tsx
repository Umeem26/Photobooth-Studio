import type { Layout } from '../api/types';
import { fitBox } from '../layout/fit';

/**
 * Pratinjau strip hasil compose di dalam kotak wadah tetap (boxW x boxH): diskalakan `contain`
 * mengikuti rasio canvas layout, tidak terpotong, bayangan sama untuk semua rasio.
 * Ukuran dihitung dari canvas layout sehingga kotak <img> persis sama dengan gambar yang tergambar.
 */
export function StripImage({ src, layout, boxW, boxH, rotate = 0, testId }: {
  src: string;
  layout: Layout | undefined;
  boxW: number;
  boxH: number;
  rotate?: number;
  testId?: string;
}) {
  const size = layout ? fitBox(layout.canvas.w, layout.canvas.h, boxW, boxH, rotate) : null;
  return (
    <img className="strip-image" src={src} alt="" data-testid={testId}
      style={size
        ? { width: size.w, height: size.h, transform: rotate ? `rotate(${rotate}deg)` : undefined }
        : { maxWidth: boxW, maxHeight: boxH, objectFit: 'contain' }} />
  );
}
