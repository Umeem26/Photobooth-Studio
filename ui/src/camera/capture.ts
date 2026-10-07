/** Area crop tengah dengan rasio 4:3 dari frame berukuran w x h. */
export function centerCrop43(w: number, h: number): { sx: number; sy: number; sw: number; sh: number } {
  const target = 4 / 3;
  let sw = w;
  let sh = Math.round(w / target);
  if (sh > h) {
    sh = h;
    sw = Math.round(h * target);
  }
  return { sx: Math.floor((w - sw) / 2), sy: Math.floor((h - sh) / 2), sw, sh };
}

export const JPEG_QUALITY = 0.92;

/**
 * Mengambil satu frame dari video: crop tengah 4:3, TIDAK dicermin
 * (preview dicermin lewat CSS, foto tersimpan sesuai aslinya), JPEG kualitas 0,92.
 */
export async function captureFrame(video: HTMLVideoElement): Promise<Blob> {
  const { sx, sy, sw, sh } = centerCrop43(video.videoWidth, video.videoHeight);
  if (sw === 0 || sh === 0) throw new Error('Video belum siap');
  const canvas = document.createElement('canvas');
  canvas.width = sw;
  canvas.height = sh;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas 2D tidak tersedia');
  ctx.drawImage(video, sx, sy, sw, sh, 0, 0, sw, sh);
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Gagal membuat JPEG'))), 'image/jpeg', JPEG_QUALITY),
  );
}

/** Constraint kamera: ideal 1920x1080, browser memilih fallback otomatis. */
export const CAMERA_CONSTRAINTS: MediaStreamConstraints = {
  audio: false,
  video: { width: { ideal: 1920 }, height: { ideal: 1080 }, frameRate: { ideal: 30 } },
};
