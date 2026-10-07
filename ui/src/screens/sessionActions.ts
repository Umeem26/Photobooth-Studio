import type { Booth } from '../app/context';

/** Membatalkan sesi aktif (meta "abandoned") lalu kembali ke Attract. Error sidecar diabaikan. */
export async function abandonToAttract(booth: Pick<Booth, 'api' | 'state' | 'dispatch'>) {
  const { api, state, dispatch } = booth;
  const id = state.sessionId;
  dispatch({ type: 'BACK_TO_ATTRACT' });
  if (api && id) {
    try {
      await api.abandon(id);
    } catch {
      // sesi tetap tersimpan di disk; status abandoned tidak kritis
    }
  }
}
