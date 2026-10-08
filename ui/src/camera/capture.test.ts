import { afterEach, describe, expect, it, vi } from 'vitest';
import { captureFrame, centerCrop43 } from './capture';

describe('centerCrop43', () => {
  it('crops 16:9 1080p to centered 1440x1080', () => {
    expect(centerCrop43(1920, 1080)).toEqual({ sx: 240, sy: 0, sw: 1440, sh: 1080 });
  });
  it('keeps 4:3 frames as is', () => {
    expect(centerCrop43(640, 480)).toEqual({ sx: 0, sy: 0, sw: 640, sh: 480 });
  });
  it('crops tall frames vertically', () => {
    expect(centerCrop43(1080, 1920)).toEqual({ sx: 0, sy: 555, sw: 1080, sh: 810 });
  });
});

describe('captureFrame', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('mirrors the photo horizontally so it matches the mirrored preview', async () => {
    const calls: string[] = [];
    const ctx = {
      translate: (x: number, y: number) => calls.push(`translate(${x},${y})`),
      scale: (x: number, y: number) => calls.push(`scale(${x},${y})`),
      drawImage: () => calls.push('drawImage'),
    };
    const canvas = {
      width: 0,
      height: 0,
      getContext: () => ctx,
      toBlob: (cb: (b: Blob | null) => void) => cb(new Blob(['x'])),
    };
    vi.stubGlobal('document', { createElement: () => canvas });
    await captureFrame({ videoWidth: 1920, videoHeight: 1080 } as HTMLVideoElement);
    expect(canvas.width).toBe(1440);
    expect(calls).toEqual(['translate(1440,0)', 'scale(-1,1)', 'drawImage']);
  });

  it('keeps the camera orientation when mirroring is off', async () => {
    const calls: string[] = [];
    const ctx = {
      translate: () => calls.push('translate'),
      scale: () => calls.push('scale'),
      drawImage: () => calls.push('drawImage'),
    };
    const canvas = { width: 0, height: 0, getContext: () => ctx, toBlob: (cb: (b: Blob | null) => void) => cb(new Blob(['x'])) };
    vi.stubGlobal('document', { createElement: () => canvas });
    await captureFrame({ videoWidth: 640, videoHeight: 480 } as HTMLVideoElement, false);
    expect(calls).toEqual(['drawImage']);
  });
});
