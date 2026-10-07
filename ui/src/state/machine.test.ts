import { describe, expect, it } from 'vitest';
import { initialState, reducer, retakesLeft, stepFor, type Action, type State } from './machine';
import type { BoothConfig, Layout } from '../api/types';

const config: BoothConfig = {
  'event.name': 'Sample event',
  'event.date': '2026-10-12',
  maxRetakes: 2,
  photosPerLayout: { 'vertical-4': 4, 'vertical-3': 3, 'horizontal-3': 3 },
};
const layouts: Layout[] = [
  { id: 'vertical-4', name: 'Vertical, 4 photos', description: '', photos: 4, orientation: 'vertical' },
  { id: 'vertical-3', name: 'Vertical, 3 photos', description: '', photos: 3, orientation: 'vertical' },
  { id: 'horizontal-3', name: 'Horizontal, 3 photos', description: '', photos: 3, orientation: 'horizontal' },
];
const filters = [
  { id: 'original', name: 'Original' },
  { id: 'mono', name: 'Black & white' },
  { id: 'vintage', name: 'Vintage' },
  { id: 'warm', name: 'Warm' },
];

function run(state: State, ...actions: Action[]): State {
  return actions.reduce(reducer, state);
}

const booted = run(initialState, { type: 'BOOTED', config, layouts, filters });

function captureAll(state: State): State {
  let s = state;
  while (s.queue.length > 0) s = reducer(s, { type: 'FRAME_CAPTURED', index: s.queue[0], url: `blob:${s.queue[0]}` });
  return reducer(s, { type: 'CAPTURE_FINISHED' });
}

function toReview(layoutId = 'vertical-4'): State {
  const s = run(
    booted,
    { type: 'SESSION_STARTED', sessionId: 's1', layoutId: 'vertical-4' },
    { type: 'LAYOUT_SELECTED', layoutId },
    { type: 'LAYOUT_CONFIRMED', sessionId: 's1', layoutId },
  );
  return captureAll(s);
}

describe('reducer', () => {
  it('boots to attract with first layout selected', () => {
    expect(booted.screen).toBe('attract');
    expect(booted.layoutId).toBe('vertical-4');
  });

  it('walks the full happy path attract -> result -> attract', () => {
    let s = toReview();
    expect(s.screen).toBe('review');
    expect(s.frames).toEqual(['blob:1', 'blob:2', 'blob:3', 'blob:4']);
    s = run(s, { type: 'REVIEW_ACCEPTED' });
    expect(s.screen).toBe('filter');
    expect(s.filterId).toBe('original');
    s = run(s, { type: 'FILTER_CONFIRMED' });
    expect(s.screen).toBe('filter'); // belum ada strip
    s = run(s, { type: 'STRIP_COMPOSED', filterId: 'original', url: 'blob:strip' }, { type: 'FILTER_CONFIRMED' });
    expect(s.screen).toBe('result');
    s = run(s, { type: 'BACK_TO_ATTRACT' });
    expect(s.screen).toBe('attract');
    expect(s.sessionId).toBeNull();
    expect(s.frames).toEqual([]);
  });

  it('sizes capture queue from the chosen layout', () => {
    const s = run(
      booted,
      { type: 'SESSION_STARTED', sessionId: 's1', layoutId: 'vertical-4' },
      { type: 'LAYOUT_SELECTED', layoutId: 'horizontal-3' },
      { type: 'LAYOUT_CONFIRMED', sessionId: 's2', layoutId: 'horizontal-3' },
    );
    expect(s.screen).toBe('capture');
    expect(s.queue).toEqual([1, 2, 3]);
    expect(s.sessionId).toBe('s2');
    expect(s.sessionLayoutId).toBe('horizontal-3');
  });

  it('ignores frames out of order and unknown layouts', () => {
    let s = run(booted, { type: 'SESSION_STARTED', sessionId: 's1', layoutId: 'vertical-4' });
    expect(run(s, { type: 'LAYOUT_SELECTED', layoutId: 'square-9' }).layoutId).toBe('vertical-4');
    s = run(s, { type: 'LAYOUT_CONFIRMED', sessionId: 's1', layoutId: 'vertical-4' });
    expect(run(s, { type: 'FRAME_CAPTURED', index: 3, url: 'x' }).queue).toEqual([1, 2, 3, 4]);
    expect(run(s, { type: 'CAPTURE_FINISHED' }).screen).toBe('capture');
  });

  it('limits retakes across all photos to maxRetakes', () => {
    let s = toReview();
    expect(retakesLeft(s)).toBe(2);
    s = run(s, { type: 'RETAKE_REQUESTED', index: 2 });
    expect(s.screen).toBe('capture');
    expect(s.queue).toEqual([2]);
    s = run(s, { type: 'FRAME_CAPTURED', index: 2, url: 'blob:2b' }, { type: 'CAPTURE_FINISHED' });
    expect(s.screen).toBe('review');
    expect(s.frames[1]).toBe('blob:2b');
    s = run(s, { type: 'RETAKE_REQUESTED', index: 4 });
    s = run(s, { type: 'FRAME_CAPTURED', index: 4, url: 'blob:4b' }, { type: 'CAPTURE_FINISHED' });
    expect(retakesLeft(s)).toBe(0);
    const blocked = run(s, { type: 'RETAKE_REQUESTED', index: 1 });
    expect(blocked.screen).toBe('review');
    expect(blocked.retakesUsed).toBe(2);
  });

  it('start over from review goes to layout with a new session and keeps the layout', () => {
    const s = run(toReview('vertical-3'), { type: 'SESSION_STARTED', sessionId: 's9', layoutId: 'vertical-3' });
    expect(s.screen).toBe('layout');
    expect(s.sessionId).toBe('s9');
    expect(s.layoutId).toBe('vertical-3');
    expect(s.frames).toEqual([]);
  });

  it('drops stale compose results when the filter changed', () => {
    let s = run(toReview(), { type: 'REVIEW_ACCEPTED' }, { type: 'FILTER_SELECTED', filterId: 'mono' });
    s = run(s, { type: 'STRIP_COMPOSED', filterId: 'original', url: 'blob:old' });
    expect(s.stripUrl).toBeNull();
    s = run(s, { type: 'STRIP_COMPOSED', filterId: 'mono', url: 'blob:mono' });
    expect(s.stripUrl).toBe('blob:mono');
    expect(run(s, { type: 'FILTER_SELECTED', filterId: 'sepia' }).filterId).toBe('mono');
  });

  it('retake from result restarts capture with the same layout', () => {
    let s = run(toReview('horizontal-3'), { type: 'REVIEW_ACCEPTED' });
    s = run(s, { type: 'STRIP_COMPOSED', filterId: 'original', url: 'blob:s' }, { type: 'FILTER_CONFIRMED' });
    s = run(s, { type: 'RESTARTED_SAME_LAYOUT', sessionId: 's3' });
    expect(s.screen).toBe('capture');
    expect(s.layoutId).toBe('horizontal-3');
    expect(s.queue).toEqual([1, 2, 3]);
    expect(s.retakesUsed).toBe(0);
    expect(s.stripUrl).toBeNull();
  });

  it('errors and retry go back through boot', () => {
    const failed = run(booted, { type: 'FAILED', kind: 'camera' });
    expect(failed.screen).toBe('error');
    expect(failed.errorKind).toBe('camera');
    const retry = run(failed, { type: 'RETRY' });
    expect(retry.screen).toBe('boot');
    expect(run(booted, { type: 'RETRY' })).toBe(booted);
  });

  it('maps screens to step pills', () => {
    expect(stepFor('layout')).toBe(1);
    expect(stepFor('review')).toBe(2);
    expect(stepFor('filter')).toBe(3);
    expect(stepFor('attract')).toBeNull();
  });
});
