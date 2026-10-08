import { useEffect, useState } from 'react';
import { useBooth } from '../app/context';
import type { ShareLink } from '../api/types';
import { StripImage } from '../components/StripImage';
import { Button, QrImage, Title, Wordmark } from '../components/ui';
import { useKeys } from '../hooks/useKeys';
import { printLimitReached } from '../state/machine';
import { strings } from '../strings';

const PRINT_POLL_MS = 1_000;
/** Wadah strip tetap (kiri); strip miring -3,5 derajat dan dimuat contain untuk semua rasio canvas. */
export const STRIP_BOX = { left: 40, top: 57, width: 820, height: 930, tilt: -3.5 };

/** Layar hasil (mockup 4): Print strip, Save photos, Retake, kartu QR, hitung mundur 45 detik. */
export function ResultScreen() {
  const { state, dispatch, api, handleError, showToast } = useBooth();
  const [saving, setSaving] = useState(false);
  const [share, setShare] = useState<ShareLink | null>(null);
  const [shareOff, setShareOff] = useState(!state.config?.share.enabled);
  const sessionId = state.sessionId;
  const limit = printLimitReached(state);
  const layout = state.layouts.find((l) => l.id === state.layoutId);

  // Hitung mundur kembali ke Attract (reducer menjedanya selama mencetak)
  useEffect(() => {
    const timer = window.setInterval(() => dispatch({ type: 'RESULT_TICK' }), 1_000);
    return () => window.clearInterval(timer);
  }, [dispatch]);

  // Link unduh + QR; gagal atau nonaktif -> "Sharing is off."
  useEffect(() => {
    if (!api || !sessionId || !state.config?.share.enabled) return;
    let cancelled = false;
    api
      .share(sessionId)
      .then((link) => !cancelled && setShare(link))
      .catch(() => !cancelled && setShareOff(true));
    return () => {
      cancelled = true;
    };
  }, [api, sessionId, state.config?.share.enabled]);

  // Selama mencetak: polling status sampai done/failed
  useEffect(() => {
    if (!state.printing || !api || !sessionId) return;
    let cancelled = false;
    const poll = async () => {
      try {
        const s = await api.printStatus(sessionId);
        if (cancelled) return;
        if (s.status === 'done' || s.status === 'failed') {
          dispatch({ type: 'PRINT_FINISHED', ok: s.status === 'done', copiesUsed: s.copiesUsed });
          showToast(s.status === 'done' ? strings.result.printSent : strings.result.printFailed);
          return;
        }
      } catch (e) {
        if (cancelled) return;
        dispatch({ type: 'PRINT_FINISHED', ok: false });
        handleError(e, strings.result.printFailed);
        return;
      }
      timer = window.setTimeout(poll, PRINT_POLL_MS);
    };
    let timer = window.setTimeout(poll, PRINT_POLL_MS);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [state.printing, api, sessionId, dispatch, handleError, showToast]);

  const print = async () => {
    if (!api || !sessionId || state.printing || limit) return;
    dispatch({ type: 'PRINT_STARTED' });
    try {
      await api.print(sessionId, 1);
    } catch (e) {
      dispatch({ type: 'PRINT_FINISHED', ok: false });
      handleError(e, strings.result.printFailed);
    }
  };

  const save = async () => {
    if (!api || !sessionId || saving) return;
    setSaving(true);
    try {
      showToast(strings.result.saved(await api.exportLocal(sessionId)));
    } catch (e) {
      handleError(e, strings.toast.exportFailed);
    } finally {
      setSaving(false);
    }
  };

  const retake = async () => {
    if (!api || !state.layoutId) return;
    try {
      const next = await api.createSession(state.layoutId);
      dispatch({ type: 'RESTARTED_SAME_LAYOUT', sessionId: next });
    } catch (e) {
      handleError(e, strings.toast.composeFailed);
    }
  };

  useKeys({ onEnter: print });

  const printLabel = state.printing ? strings.result.printing : limit ? strings.result.printLimit : strings.result.print;

  return (
    <>
      <div className="abs" style={{ left: -120, top: 120, width: 960, height: 960, borderRadius: '50%', background: 'var(--butter)' }} />
      <div className="abs" data-testid="result-frame"
        style={{ left: STRIP_BOX.left, top: STRIP_BOX.top, width: STRIP_BOX.width, height: STRIP_BOX.height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        {state.stripUrl && (
          <StripImage src={state.stripUrl} layout={layout} testId="result-strip" rotate={STRIP_BOX.tilt}
            boxW={STRIP_BOX.width} boxH={STRIP_BOX.height} />
        )}
      </div>

      <div className="abs" style={{ left: 900, top: 84 }}>
        <Wordmark size={44} />
      </div>
      <div className="abs" style={{ left: 900, top: 176 }}>
        <Title className="title-result" lead={strings.result.titleLead} emphasis={strings.result.titleEmphasis} br />
      </div>
      <div className="abs" style={{ left: 900, top: 470, width: 480, display: 'flex', flexDirection: 'column', gap: 26 }}>
        <Button variant="primary" onClick={print} disabled={state.printing || limit} style={{ width: 480 }} testId="print">
          {printLabel}
        </Button>
        <Button variant="outline" onClick={save} disabled={saving} style={{ width: 480 }} testId="save">
          {strings.result.save}
        </Button>
        <Button variant="ghost" onClick={retake} style={{ width: 480, justifyContent: 'flex-start' }} testId="retake">
          {strings.result.retake}
        </Button>
      </div>

      {share && !shareOff ? (
        <div className="abs card" style={{ left: 1440, top: 430, width: 400, height: 560, padding: 30, display: 'block', textAlign: 'center' }}
          data-testid="qr-card">
          <QrImage png={share.qrPng} size={340} testId="share-qr" />
          <div className="card-title" style={{ fontSize: 38, marginTop: 24, textAlign: 'center' }}>{strings.result.scanTitle}</div>
          <div style={{ fontSize: 21, lineHeight: 1.35, color: 'var(--ink-2)', marginTop: 8, fontWeight: 500 }}>
            {strings.result.scanBody}
          </div>
        </div>
      ) : shareOff ? (
        <div className="abs note" style={{ left: 1440, top: 690, width: 400, textAlign: 'center' }} data-testid="sharing-off">
          {strings.result.sharingOff}
        </div>
      ) : null}

      <div className="abs note" style={{ left: 900, top: 1010 }} data-testid="returning">
        {strings.result.returning(state.resultSecondsLeft)}
      </div>
    </>
  );
}
