import { useEffect, useRef, useState } from 'react';
import { useBooth } from '../app/context';
import { LiveVideo } from '../components/LiveVideo';
import { PinDialog } from '../components/PinDialog';
import { Button, Chip, StripPreview, Title, Wordmark } from '../components/ui';
import { useKeys } from '../hooks/useKeys';
import { offeredLayouts } from '../state/machine';
import { formatEventDateShort, strings } from '../strings';

export const OPERATOR_HOLD_MS = 3_000;

export function AttractScreen() {
  const { state, dispatch, api, camera, handleError } = useBooth();
  const [starting, setStarting] = useState(false);
  const [pinMode, setPinMode] = useState<'create' | 'enter' | null>(null);
  const holdTimer = useRef<number>();
  const config = state.config;

  // Mode Operator: tekan dan tahan wordmark 3 detik
  const startHold = () => {
    window.clearTimeout(holdTimer.current);
    holdTimer.current = window.setTimeout(async () => {
      if (!api) return;
      try {
        setPinMode((await api.pinStatus()).set ? 'enter' : 'create');
      } catch (e) {
        handleError(e, strings.toast.composeFailed);
      }
    }, OPERATOR_HOLD_MS);
  };
  const cancelHold = () => window.clearTimeout(holdTimer.current);
  useEffect(() => () => window.clearTimeout(holdTimer.current), []);

  // Kamera diminta sekali di layar ini; gagal -> layar Error
  useEffect(() => {
    let cancelled = false;
    camera.request().then((ok) => {
      if (!ok && !cancelled) dispatch({ type: 'FAILED', kind: 'camera' });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const start = async () => {
    const layoutId = offeredLayouts(state)[0]?.id;
    if (!api || !layoutId || starting) return;
    setStarting(true);
    try {
      const sessionId = await api.createSession(layoutId);
      dispatch({ type: 'SESSION_STARTED', sessionId, layoutId });
    } catch (e) {
      handleError(e, strings.toast.composeFailed);
      setStarting(false);
    }
  };

  useKeys({ onEnter: pinMode ? undefined : start });

  return (
    <>
      <div className="abs" style={{ left: 110, top: 84, touchAction: 'none' }} data-testid="wordmark-hold"
        onPointerDown={startHold} onPointerUp={cancelHold} onPointerLeave={cancelHold} onPointerCancel={cancelHold}
        onContextMenu={(e) => e.preventDefault()}>
        <Wordmark size={52} />
      </div>
      <div className="abs" style={{ left: 110, top: 230 }}>
        <Title className="title-hero" lead={strings.attract.titleLead} emphasis={strings.attract.titleEmphasis} br />
      </div>
      <p className="abs body-text" style={{ left: 114, top: 648, width: 760 }}>
        {strings.attract.bodyLine1}
        <br />
        {strings.attract.bodyLine2}
      </p>
      <div className="abs" style={{ left: 110, top: 800 }}>
        <Button variant="primary" hero arrow onClick={start} disabled={starting} testId="start">
          {strings.attract.start}
        </Button>
      </div>
      {config && (
        <div className="abs" style={{ left: 114, top: 978 }}>
          <Chip testId="event-chip">{strings.attract.eventChip(config['event.name'], config['event.date'])}</Chip>
        </div>
      )}

      <div className="abs" style={{ left: 1060, top: 60, width: 800, height: 800, borderRadius: '50%', background: 'var(--butter)' }} />
      <div className="abs camera-card" style={{ left: 1090, top: 130, width: 700, height: 820 }}>
        <LiveVideo stream={camera.stream} />
      </div>
      <div className="abs" style={{ left: 1126, top: 166 }}>
        <Chip onCamera style={{ background: 'var(--paper-94)' }}>
          <span className="chip-live-dot" />
          {strings.attract.cameraChip}
        </Chip>
      </div>
      <div className="abs" style={{ left: 1640, top: 320, transform: 'rotate(7deg)' }}>
        <StripPreview
          photos={4}
          orientation="vertical"
          cellW={176}
          cellH={132}
          gap={10}
          padding="16px 16px 10px"
          wordmarkSize={22}
          caption={config ? formatEventDateShort(config['event.date']) : undefined}
          captionSize={12}
        />
      </div>
      {pinMode && api && (
        <PinDialog
          api={api}
          mode={pinMode}
          onCancel={() => setPinMode(null)}
          onSuccess={(token) => dispatch({ type: 'OPERATOR_OPENED', token })}
        />
      )}
    </>
  );
}
