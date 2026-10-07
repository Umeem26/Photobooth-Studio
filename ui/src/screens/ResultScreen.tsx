import { useEffect, useState } from 'react';
import { useBooth } from '../app/context';
import { Button, Title, Wordmark } from '../components/ui';
import { strings } from '../strings';

export const RESULT_TIMEOUT_S = 45;

export function ResultScreen() {
  const { state, dispatch, api, handleError, showToast } = useBooth();
  const [seconds, setSeconds] = useState(RESULT_TIMEOUT_S);
  const [saving, setSaving] = useState(false);

  // Hitung mundur kembali ke Attract
  useEffect(() => {
    const timer = window.setInterval(() => setSeconds((s) => Math.max(0, s - 1)), 1_000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    if (seconds === 0) dispatch({ type: 'BACK_TO_ATTRACT' });
  }, [seconds, dispatch]);

  const save = async () => {
    if (!api || !state.sessionId || saving) return;
    setSaving(true);
    try {
      showToast(strings.result.saved(await api.exportLocal(state.sessionId)));
    } catch (e) {
      handleError(e, strings.toast.exportFailed);
    } finally {
      setSaving(false);
    }
  };

  const retake = async () => {
    if (!api || !state.layoutId) return;
    try {
      const sessionId = await api.createSession(state.layoutId);
      dispatch({ type: 'RESTARTED_SAME_LAYOUT', sessionId });
    } catch (e) {
      handleError(e, strings.toast.composeFailed);
    }
  };

  return (
    <>
      <div className="abs" style={{ left: -120, top: 120, width: 960, height: 960, borderRadius: '50%', background: 'var(--butter)' }} />
      <div className="abs" style={{ left: 10, top: 57, width: 640, height: 930, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        {state.stripUrl && (
          <img className="strip-image" src={state.stripUrl} alt="" data-testid="result-strip"
            style={{ maxWidth: 640, maxHeight: 930, objectFit: 'contain', transform: 'rotate(-3.5deg)' }} />
        )}
      </div>

      <div className="abs" style={{ left: 900, top: 84 }}>
        <Wordmark size={44} />
      </div>
      <div className="abs" style={{ left: 900, top: 176 }}>
        <Title className="title-result" lead={strings.result.titleLead} emphasis={strings.result.titleEmphasis} br />
      </div>
      <div className="abs" style={{ left: 900, top: 470, width: 480, display: 'flex', flexDirection: 'column', gap: 26 }}>
        <Button variant="outline" onClick={save} disabled={saving} style={{ width: 480 }} testId="save">
          {strings.result.save}
        </Button>
        <Button variant="ghost" onClick={retake} style={{ width: 480, justifyContent: 'flex-start' }} testId="retake">
          {strings.result.retake}
        </Button>
      </div>
      <div className="abs note" style={{ left: 900, top: 1010 }} data-testid="returning">
        {strings.result.returning(seconds)}
      </div>
    </>
  );
}
