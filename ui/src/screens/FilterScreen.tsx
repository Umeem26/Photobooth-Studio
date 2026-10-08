import { useEffect, useState } from 'react';
import { useBooth } from '../app/context';
import { Button, SelectableCard, Title, Wordmark } from '../components/ui';
import { useIdle } from '../hooks/useIdle';
import { useKeys } from '../hooks/useKeys';
import { strings } from '../strings';
import { FlowSteps } from './FlowSteps';
import { abandonToAttract } from './sessionActions';

const DEBOUNCE_MS = 150;
const SKELETON_AFTER_MS = 300;

/** Swatch: foto pertama dengan perkiraan filter (hasil sebenarnya dari compose di sidecar). */
const SWATCH_CSS: Record<string, string> = {
  original: 'none',
  mono: 'grayscale(1)',
  vintage: 'sepia(0.85)',
  warm: 'sepia(0.25) saturate(1.2)',
};

const CARD_W = 430;
const CARD_H = 290;
const CARDS_LEFT = 1810 - (2 * 430 + 40); // rata kanan dengan margin 110
const CARDS_TOP = 280;
const GAP = 40;

export function FilterScreen() {
  const booth = useBooth();
  const { state, dispatch, api, handleError } = booth;
  const [composing, setComposing] = useState(true);
  const [skeleton, setSkeleton] = useState(false);
  const filterId = state.filterId;

  // Compose ulang (debounce 150 ms) setiap pilihan berubah; skeleton bila > 300 ms
  useEffect(() => {
    if (!api || !state.sessionId) return;
    const sessionId = state.sessionId;
    let cancelled = false;
    setComposing(true);
    const skeletonTimer = window.setTimeout(() => !cancelled && setSkeleton(true), DEBOUNCE_MS + SKELETON_AFTER_MS);
    const debounce = window.setTimeout(async () => {
      try {
        const path = await api.compose(sessionId, filterId);
        const url = await api.imageUrl(path);
        if (cancelled) {
          URL.revokeObjectURL(url);
          return;
        }
        window.clearTimeout(skeletonTimer);
        dispatch({ type: 'STRIP_COMPOSED', filterId, url });
        setComposing(false);
        setSkeleton(false);
      } catch (e) {
        if (!cancelled) {
          window.clearTimeout(skeletonTimer);
          handleError(e, strings.toast.composeFailed);
          setComposing(false);
          setSkeleton(false);
        }
      }
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      window.clearTimeout(debounce);
      window.clearTimeout(skeletonTimer);
    };
  }, [api, state.sessionId, filterId, dispatch, handleError]);

  const ready = !composing && !!state.stripUrl;
  const next = () => ready && dispatch({ type: 'FILTER_CONFIRMED' });

  useKeys({ onEnter: next });
  useIdle(() => abandonToAttract(booth));

  const firstFrame = state.frames[0];

  return (
    <>
      <div className="abs" style={{ left: 110, top: 70 }}>
        <Wordmark size={44} />
      </div>
      <FlowSteps screen="filter" />
      <div className="abs" style={{ left: 110, top: 150 }}>
        <Title className="title-screen" lead={strings.filter.titleLead} emphasis={strings.filter.titleEmphasis} />
      </div>

      <div className="abs card-preview" style={{ left: 110, top: 280, width: 760, height: 620, margin: 0, overflow: 'hidden' }}
        data-testid="filter-strip">
        {skeleton || !state.stripUrl ? (
          <div className="skeleton" style={{ width: 300, height: 540 }} data-testid="skeleton" />
        ) : (
          <img className="strip-image" src={state.stripUrl} alt=""
            style={{ maxWidth: 700, maxHeight: 560, objectFit: 'contain' }} />
        )}
      </div>

      {state.filters.map((f, i) => (
        <SelectableCard
          key={f.id}
          selected={filterId === f.id}
          onSelect={() => dispatch({ type: 'FILTER_SELECTED', filterId: f.id })}
          style={{
            left: CARDS_LEFT + (i % 2) * (CARD_W + GAP),
            top: CARDS_TOP + Math.floor(i / 2) * (CARD_H + GAP),
            width: CARD_W,
            height: CARD_H,
            padding: 19,
          }}
          testId={`filter-${f.id}`}
        >
          {firstFrame ? (
            <img className="photo" src={firstFrame} alt=""
              style={{ height: 170, aspectRatio: 'auto', filter: SWATCH_CSS[f.id] ?? 'none' }} />
          ) : (
            <div className="photo" style={{ height: 170 }} />
          )}
          <div className="card-title" style={{ fontSize: 40, marginTop: 18 }}>
            {strings.filter.names[f.id] ?? f.name}
          </div>
        </SelectableCard>
      ))}

      <div className="abs" style={{ right: 110, top: 928 }}>
        <Button variant="primary" arrow onClick={next} disabled={!ready} testId="next">
          {strings.filter.next}
        </Button>
      </div>
    </>
  );
}
