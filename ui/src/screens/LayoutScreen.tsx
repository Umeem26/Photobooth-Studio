import { useState } from 'react';
import { useBooth } from '../app/context';
import type { Layout } from '../api/types';
import { Button, SelectableCard, StepPill, StripPreview, Title, Wordmark } from '../components/ui';
import { useIdle } from '../hooks/useIdle';
import { useKeys } from '../hooks/useKeys';
import { formatEventDateShort, strings } from '../strings';
import { abandonToAttract } from './sessionActions';

function LayoutPreview({ layout, date }: { layout: Layout; date: string }) {
  if (layout.orientation === 'horizontal') {
    return (
      <StripPreview photos={layout.photos} orientation="horizontal" cellW={124} cellH={150} gap={8}
        padding="16px 16px 10px" wordmarkSize={22} captionSize={12} showFooter={false} />
    );
  }
  const big = layout.photos <= 3;
  return (
    <StripPreview photos={layout.photos} orientation="vertical" cellW={big ? 150 : 112} cellH={big ? 110 : 82}
      gap={8} padding="14px 14px 8px" wordmarkSize={22} caption={date} captionSize={12} />
  );
}

export function LayoutScreen() {
  const booth = useBooth();
  const { state, dispatch, api, handleError } = booth;
  const [busy, setBusy] = useState(false);
  const date = state.config ? formatEventDateShort(state.config['event.date']) : '';

  const back = () => abandonToAttract(booth);

  const next = async () => {
    const layoutId = state.layoutId;
    if (!api || !layoutId || !state.sessionId || busy) return;
    setBusy(true);
    try {
      let sessionId = state.sessionId;
      // Sesi dibuat di Attract dengan layout default; ganti sesi bila tamu memilih layout lain
      if (state.sessionLayoutId !== layoutId) {
        const old = sessionId;
        sessionId = await api.createSession(layoutId);
        api.abandon(old).catch(() => undefined);
      }
      dispatch({ type: 'LAYOUT_CONFIRMED', sessionId, layoutId });
    } catch (e) {
      handleError(e, strings.toast.composeFailed);
      setBusy(false);
    }
  };

  useKeys({ onEnter: next, onEscape: back });
  useIdle(back);

  return (
    <>
      <div className="abs" style={{ left: 110, top: 70 }}>
        <Wordmark size={44} />
      </div>
      <StepPill active={1} />
      <div className="abs" style={{ left: 110, top: 150 }}>
        <Title className="title-screen" lead={strings.layout.titleLead} emphasis={strings.layout.titleEmphasis} />
      </div>

      {state.layouts.map((layout, i) => {
        const copy = strings.layout.cards[layout.id] ?? { name: layout.name, description: layout.description };
        return (
          <SelectableCard
            key={layout.id}
            selected={state.layoutId === layout.id}
            onSelect={() => dispatch({ type: 'LAYOUT_SELECTED', layoutId: layout.id })}
            style={{ left: 110 + i * 580, top: 280, width: 540, height: 620 }}
            testId={`layout-${layout.id}`}
          >
            <div className="card-preview">
              <LayoutPreview layout={layout} date={date} />
            </div>
            <div className="card-body">
              <div className="card-title">{copy.name}</div>
              <div className="card-desc">{copy.description}</div>
            </div>
          </SelectableCard>
        );
      })}

      <div className="abs" style={{ left: 110, top: 936 }}>
        <Button variant="ghost" onClick={back} testId="back">
          {strings.layout.back}
        </Button>
      </div>
      <div className="abs" style={{ right: 110, top: 928 }}>
        <Button variant="primary" arrow onClick={next} disabled={busy} testId="next">
          {strings.layout.next}
        </Button>
      </div>
    </>
  );
}
