// Semua teks UI (Inggris, v1). Lihat docs/design-system.md bagian 7.

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function parseIsoDate(iso: string): [number, number, number] | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null;
}

/** "2026-10-12" -> "12 Oct 2026" (chip acara di layar sambut). */
export function formatEventDateLong(iso: string): string {
  const d = parseIsoDate(iso);
  return d ? `${d[2]} ${MONTHS[d[1] - 1]} ${d[0]}` : iso;
}

/** "2026-10-12" -> "12.10.2026" (footer strip). */
export function formatEventDateShort(iso: string): string {
  const d = parseIsoDate(iso);
  return d ? `${String(d[2]).padStart(2, '0')}.${String(d[1]).padStart(2, '0')}.${d[0]}` : iso;
}

export const strings = {
  brand: 'Van de Booth',
  steps: ['Layout', 'Photos', 'Result'] as const,

  attract: {
    titleLead: 'One click,',
    titleEmphasis: 'one memory.',
    bodyLine1: 'Four poses, one strip.',
    bodyLine2: 'Tap the button to start.',
    start: 'Start photos',
    cameraChip: 'Camera preview',
    eventChip: (name: string, isoDate: string) =>
      name ? `${name} \u00b7 ${formatEventDateLong(isoDate)}` : formatEventDateLong(isoDate),
  },

  layout: {
    titleLead: 'Pick your',
    titleEmphasis: 'strip.',
    back: 'Back',
    next: 'Next',
    cards: {
      'vertical-4': { name: 'Vertical, 4 photos', description: 'The classic booth look.' },
      'vertical-3': { name: 'Vertical, 3 photos', description: 'Roomier, great for one hero pose.' },
      'horizontal-3': { name: 'Horizontal, 3 photos', description: 'Wide, made for framing or display.' },
    } as Record<string, { name: string; description: string }>,
  },

  capture: {
    photoOf: (i: number, n: number) => `Photo ${i} of ${n}`,
    captions: ['Smile! Look at the camera.', 'Strike a pose!', 'Get closer!', 'One more!'],
    cancel: 'Cancel',
    confirmTitle: 'Cancel this session?',
    confirmBody: 'Your photos will be discarded.',
    confirmKeep: 'Keep going',
    confirmDiscard: 'Cancel session',
  },

  review: {
    titleLead: 'Happy with',
    titleEmphasis: 'these?',
    retake: 'Retake',
    looksGood: 'Looks good',
    startOver: 'Start over',
    noRetakes: 'No retakes left',
  },

  filter: {
    titleLead: 'Pick a',
    titleEmphasis: 'look.',
    next: 'Next',
    names: {
      original: 'Original',
      mono: 'Black & white',
      vintage: 'Vintage',
      warm: 'Warm',
    } as Record<string, string>,
  },

  result: {
    titleLead: 'Your strip',
    titleEmphasis: 'is ready.',
    save: 'Save photos',
    retake: 'Retake',
    returning: (s: number) => `Returning to start in ${s} ${s === 1 ? 'second' : 'seconds'}`,
    saved: (path: string) => `Saved to ${path}`,
  },

  error: {
    camera: 'Camera not found. Check the cable, then try again.',
    service: 'The booth service is not responding.',
    tryAgain: 'Try again',
  },

  toast: {
    composeFailed: 'Could not build your strip. Please try again.',
    exportFailed: 'Could not save your photos. Please try again.',
    uploadFailed: 'That photo did not save. Let us take it again.',
  },
};

export type Strings = typeof strings;
