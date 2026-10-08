import { useState } from 'react';
import { useBooth } from '../app/context';
import { LayoutMiniature } from '../components/LayoutMiniature';
import { Button, SelectableCard, Title, Wordmark } from '../components/ui';
import { useIdle } from '../hooks/useIdle';
import { useKeys } from '../hooks/useKeys';
import { LAYOUT_CARD, layoutCardPositions } from '../layout/fit';
import { offeredLayouts } from '../state/machine';
import { strings } from '../strings';
import { FlowSteps } from './FlowSteps';
import { abandonToAttract } from './sessionActions';

export function LayoutScreen() {
  const booth = useBooth();
  const { state, dispatch, api, handleError } = booth;
  const [busy, setBusy] = useState(false);
  const offered = offeredLayouts(state);
  const positions = layoutCardPositions(offered.length);

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
        sessionId = await api.createSession(layoutId, old);
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
      <FlowSteps screen="layout" />
      <div className="abs" style={{ left: 110, top: 150 }}>
        <Title className="title-screen" lead={strings.layout.titleLead} emphasis={strings.layout.titleEmphasis} />
      </div>

      {offered.map((layout, i) => {
        const copy = strings.layout.cards[layout.id] ?? { name: layout.name, description: layout.description };
        return (
          <SelectableCard
            key={layout.id}
            selected={state.layoutId === layout.id}
            onSelect={() => dispatch({ type: 'LAYOUT_SELECTED', layoutId: layout.id })}
            style={{ ...positions[i], width: LAYOUT_CARD.w, height: LAYOUT_CARD.h, flexDirection: 'row', alignItems: 'center' }}
            testId={`layout-${layout.id}`}
          >
            <div className="layout-mini">
              <LayoutMiniature layout={layout} maxW={200} maxH={252} />
            </div>
            <div className="layout-text">
              <div className="card-title" style={{ fontSize: 38 }}>{copy.name}</div>
              <div className="card-desc" data-testid={`layout-meta-${layout.id}`}>
                {strings.layout.meta(layout.photos, layout.paper)}
              </div>
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
