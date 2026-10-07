import { describe, expect, it } from 'vitest';
import { resolveEnv } from './env';

const loc = (search: string) => ({ search }) as Location;

describe('resolveEnv', () => {
  it('prefers the Electron preload bridge', () => {
    const env = resolveEnv({ booth: { apiBase: 'http://127.0.0.1:1', token: 't' }, location: loc('') }, {});
    expect(env).toEqual({ apiBase: 'http://127.0.0.1:1', token: 't' });
  });

  it('falls back to query string, then Vite env', () => {
    expect(resolveEnv({ location: loc('?api=http://127.0.0.1:2&token=q') }, {})?.token).toBe('q');
    expect(resolveEnv({ location: loc('') }, { VITE_BOOTH_API: 'http://x', VITE_BOOTH_TOKEN: 'e' })?.token).toBe('e');
    expect(resolveEnv({ location: loc('') }, {})).toBeNull();
  });
});
