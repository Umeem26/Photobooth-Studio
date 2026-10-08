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

/** 25000 -> "25.000" (pemisah ribuan titik). */
export function formatThousands(n: number): string {
  return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
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
      'vertical-4': { name: 'Classic Strip', description: 'The classic booth look.' },
      'vertical-3': { name: 'Tall Strip', description: 'Roomier, with a bigger signature.' },
      'horizontal-3': { name: 'Wide', description: 'One hero shot and two close-ups.' },
      'postcard-1': { name: 'Big Shot', description: 'One big photo, for groups and outfits.' },
      'grid-4': { name: 'Four Square', description: 'Four poses in a tidy grid.' },
      'grid-6': { name: 'Contact Sheet', description: 'Six quick shots on one sheet.' },
    } as Record<string, { name: string; description: string }>,
    /** "4 photos · 2×6" */
    meta: (photos: number, paper: string) => `${photos} ${photos === 1 ? 'photo' : 'photos'} · ${String(paper).replace('x', '×')}`,
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
    print: 'Print strip',
    printing: 'Printing…',
    printLimit: 'Print limit reached',
    printSent: 'Sent to the printer',
    printFailed: 'Printer not found. Ask the host for help.',
    scanTitle: 'Scan to download',
    scanBody: 'Join the booth Wi-Fi, then scan this code.',
    sharingOff: 'Sharing is off.',
  },

  pay: {
    titleLead: 'Scan',
    titleMiddle: 'to',
    titleEmphasis: 'pay.',
    /** Ditampilkan huruf kapital lewat CSS (label kecil). */
    session: (photos: number) => `One session, ${photos} photos`,
    price: (amount: number) => `Rp ${formatThousands(amount)}`,
    demo: 'Demo mode · no real payment',
    waiting: 'Waiting for payment…',
    updates: 'This screen updates by itself.',
    simulate: 'Simulate payment',
    back: 'Back',
  },

  pin: {
    create: 'Create a PIN',
    createHint: 'Choose 4 to 8 digits. Keep it private.',
    confirm: 'Enter it again',
    mismatch: 'The PINs do not match. Try again.',
    enter: 'Enter PIN',
    wrong: 'Wrong PIN. Try again.',
    locked: 'Too many attempts. Wait 30 seconds.',
    deleteKey: 'Delete',
    enterKey: 'Enter',
    cancel: 'Cancel',
  },

  operator: {
    mode: 'Operator mode',
    exit: 'Exit operator mode',
    saved: 'Saved.',
    saveFailed: 'Could not save. Check the value and try again.',
    menu: {
      event: 'Event',
      photos: 'Photos',
      payment: 'Payment',
      sharing: 'Sharing',
      printing: 'Printing',
      gallery: 'Gallery',
      status: 'Status',
    } as Record<string, string>,
    event: {
      name: 'Event name',
      nameHint: 'Shown on the welcome screen and under every strip.',
      date: 'Event date',
      dateHint: 'Printed in the strip footer. Leave empty to use today.',
      layouts: 'Layouts offered',
      layoutsHint: 'Guests choose from these.',
    },
    photos: {
      retakes: 'Retakes per session',
      retakesHint: 'How many photos a guest can take again.',
      countdown: 'Countdown',
      countdownHint: 'Seconds before each photo.',
      pause: 'Pause between photos',
      pauseHint: 'Seconds to get ready for the next pose.',
      mirror: 'Mirror photos',
      mirrorHint: 'Saved photos match the preview. Turn off to keep them as the camera sees them. Applies to new photos.',
      seconds: (n: number) => `${n} s`,
    },
    payment: {
      toggle: 'Demo payment',
      toggleHint: 'Guests see a demo QR code before the photos. No real money moves.',
      price: 'Price',
      priceHint: 'Shown on the payment screen, in rupiah.',
    },
    sharing: {
      toggle: 'Share over the local network',
      toggleHint: 'Guests scan a QR code and download from this booth.',
      address: 'Network address',
      addressHint: 'Detected automatically. Override it if the QR code does not open.',
      addressNone: 'Not connected',
      expiry: 'Links expire after',
      expiryHint: 'Older links stop working. Photos stay on this computer.',
      hours: (n: number) => `${n} h`,
      testTitle: 'Test it before the event',
      testBody:
        'Join the booth Wi-Fi on your phone, then scan this code. If it does not open, allow Van de Booth through the Windows firewall on private networks.',
      off: 'Sharing is off.',
    },
    printing: {
      printer: 'Printer',
      printerHint: 'Strips print on 4x6 paper.',
      systemDefault: 'System default',
      copies: 'Copies per guest',
      copiesHint: 'The print button stops after this many copies.',
      layout: 'Paper layout',
      layoutHint: 'Two-up puts two strips on one sheet to cut. Strips only: other layouts print single.',
      single: 'Single',
      twoUp: 'Two-up',
      test: 'Print test page',
      testSent: 'Test page sent.',
    },
    gallery: {
      exportAll: 'Export all',
      exported: (path: string) => `Exported to ${path}`,
      purge: 'Delete older sessions',
      purgeHint: (days: number) => `Removes sessions older than ${days} days from this computer.`,
      days: (n: number) => `${n} days`,
      purgeConfirm: (days: number) => `Delete sessions older than ${days} days?`,
      purged: (n: number) => `${n} ${n === 1 ? 'session' : 'sessions'} deleted.`,
      empty: 'No sessions yet.',
      confirmDelete: 'Delete this session?',
      confirmBody: 'The photos and strip are removed from this computer.',
      delete: 'Delete',
      cancel: 'Cancel',
      status: {
        active: 'In progress',
        composed: 'Finished',
        exported: 'Saved',
        abandoned: 'Cancelled',
      } as Record<string, string>,
    },
    status: {
      camera: 'Camera',
      cameraNone: 'No camera found',
      printer: 'Printer',
      printerNone: 'No printer found',
      version: 'Version',
      disk: 'Free disk space',
      sharing: 'Sharing address',
      copy: 'Copy diagnostics',
      copied: 'Copied.',
      gigabytes: (gb: string) => `${gb} GB`,
    },
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
