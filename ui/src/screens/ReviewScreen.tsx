import { useState } from 'react';
import { useBooth } from '../app/context';
import { Button, Chip, Title, Wordmark } from '../components/ui';
import { useIdle } from '../hooks/useIdle';
import { useKeys } from '../hooks/useKeys';
import { retakesLeft } from '../state/machine';
import { strings } from '../strings';
import { FlowSteps } from './FlowSteps';
import { abandonToAttract } from './sessionActions';

const AREA_LEFT = 110;
const AREA_WIDTH = 1700;
const AREA_TOP = 280;
const AREA_HEIGHT = 620;
const GAP = 40;
const PAD = 22;
const BUTTON_H = 96;

export function ReviewScreen() {
  const booth = useBooth();
  const { state, dispatch, api, handleError } = booth;
  const [busy, setBusy] = useState(false);
  const left = retakesLeft(state);
  const n = state.frames.length;

  const cardW = (AREA_WIDTH - GAP * (n - 1)) / n;
  const photoW = cardW - 2 * PAD;
  const photoH = (photoW * 3) / 4;
  const cardH = PAD + photoH + PAD + BUTTON_H + PAD;
  const top = AREA_TOP + (AREA_HEIGHT - cardH) / 2;

  const accept = () => dispatch({ type: 'REVIEW_ACCEPTED' });

  const startOver = async () => {
    if (!api || !state.layoutId || busy) return;
    setBusy(true);
    const old = state.sessionId;
    try {
      // Status lunas ikut terbawa: tamu tidak membayar dua kali
      const sessionId = await api.createSession(state.layoutId, old);
      if (old) api.abandon(old).catch(() => undefined);
      dispatch({ type: 'SESSION_STARTED', sessionId, layoutId: state.layoutId });
    } catch (e) {
      handleError(e, strings.toast.composeFailed);
      setBusy(false);
    }
  };

  useKeys({ onEnter: accept });
  useIdle(() => abandonToAttract(booth));

  return (
    <>
      <div className="abs" style={{ left: 110, top: 70 }}>
        <Wordmark size={44} />
      </div>
      <FlowSteps screen="review" />
      <div className="abs" style={{ left: 110, top: 150 }}>
        <Title className="title-screen" lead={strings.review.titleLead} emphasis={strings.review.titleEmphasis} />
      </div>

      {state.frames.map((url, i) => (
        <div
          key={i}
          className="card"
          style={{ left: AREA_LEFT + i * (cardW + GAP), top, width: cardW, height: cardH, padding: PAD - 3 }}
          data-testid={`review-photo-${i + 1}`}
        >
          {url && <img className="photo" src={url} alt="" style={{ height: photoH }} />}
          <Button
            variant="outline"
            onClick={() => dispatch({ type: 'RETAKE_REQUESTED', index: i + 1 })}
            disabled={left === 0}
            style={{ width: '100%', height: BUTTON_H, marginTop: PAD, fontSize: 32 }}
            testId={`retake-${i + 1}`}
          >
            {strings.review.retake}
          </Button>
        </div>
      ))}

      <div className="abs" style={{ left: 110, top: 936 }}>
        <Button variant="ghost" onClick={startOver} disabled={busy} testId="start-over">
          {strings.review.startOver}
        </Button>
      </div>
      {left === 0 && (
        <div className="abs" style={{ left: 0, right: 0, top: 956, display: 'flex', justifyContent: 'center' }}>
          <Chip testId="no-retakes">{strings.review.noRetakes}</Chip>
        </div>
      )}
      <div className="abs" style={{ right: 110, top: 928 }}>
        <Button variant="primary" arrow onClick={accept} testId="looks-good">
          {strings.review.looksGood}
        </Button>
      </div>
    </>
  );
}
