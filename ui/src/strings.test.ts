import { describe, expect, it } from 'vitest';
import { formatEventDateLong, formatEventDateShort, strings } from './strings';

function collect(value: unknown, out: string[] = []): string[] {
  if (typeof value === 'string') out.push(value);
  else if (typeof value === 'function') out.push(String((value as (...a: unknown[]) => string)(3, 4)));
  else if (value && typeof value === 'object') Object.values(value).forEach((v) => collect(v, out));
  return out;
}

const all = collect(strings);

describe('strings', () => {
  it('has no empty strings', () => {
    expect(all.length).toBeGreaterThan(40);
    for (const s of all) expect(s.trim().length, JSON.stringify(s)).toBeGreaterThan(0);
  });

  it('has no emoji and no repeated exclamation marks', () => {
    for (const s of all) {
      expect(/\p{Extended_Pictographic}/u.test(s), s).toBe(false);
      expect(/!{2,}/.test(s), s).toBe(false);
    }
  });

  it('starts every string with an uppercase letter (sentence case)', () => {
    // Frasa kedua judul (italic) melanjutkan kalimat, jadi boleh huruf kecil
    const emphasis = new Set(Object.values(strings).flatMap((v) =>
      v && typeof v === 'object' && 'titleEmphasis' in v ? [String(v.titleEmphasis)] : []));
    for (const s of all) if (!emphasis.has(s)) expect(s[0], s).toBe(s[0].toUpperCase());
    expect(strings.attract.start).toBe('Start photos');
    expect(strings.review.looksGood).toBe('Looks good');
  });

  it('is English only (no common Indonesian UI words)', () => {
    const indonesian = /\b(foto|ambil|simpan|ulang|kamera|selesai|batal|mulai|lagi)\b/i;
    for (const s of all) expect(indonesian.test(s), s).toBe(false);
  });

  it('matches the design-system copy', () => {
    expect(strings.attract.titleLead + ' ' + strings.attract.titleEmphasis).toBe('One click, one memory.');
    expect(strings.capture.captions).toEqual([
      'Smile! Look at the camera.',
      'Strike a pose!',
      'Get closer!',
      'One more!',
    ]);
    expect(strings.capture.photoOf(2, 4)).toBe('Photo 2 of 4');
    expect(strings.result.returning(45)).toBe('Returning to start in 45 seconds');
    expect(strings.result.returning(1)).toBe('Returning to start in 1 second');
    expect(strings.result.saved('C:/x.png')).toBe('Saved to C:/x.png');
    expect(Object.keys(strings.layout.cards)).toEqual(['vertical-4', 'vertical-3', 'horizontal-3']);
    expect(Object.keys(strings.filter.names)).toEqual(['original', 'mono', 'vintage', 'warm']);
  });

  it('formats event dates', () => {
    expect(formatEventDateLong('2026-10-12')).toBe('12 Oct 2026');
    expect(formatEventDateShort('2026-10-12')).toBe('12.10.2026');
    expect(strings.attract.eventChip('Sample event', '2026-10-12')).toBe('Sample event \u00b7 12 Oct 2026');
    expect(strings.attract.eventChip('', '2026-01-05')).toBe('5 Jan 2026');
  });
});
