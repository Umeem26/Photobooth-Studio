import { describe, expect, it } from 'vitest';
import { centerCrop43 } from './capture';

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
