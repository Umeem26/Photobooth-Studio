import { useState } from 'react';
import { useBooth } from '../app/context';
import { Button, Wordmark } from '../components/ui';
import { useKeys } from '../hooks/useKeys';
import { strings } from '../strings';

export function ErrorScreen() {
  const { state, dispatch, camera } = useBooth();
  const [busy, setBusy] = useState(false);
  const isCamera = state.errorKind === 'camera';

  const retry = async () => {
    if (busy) return;
    setBusy(true);
    // Kamera: minta ulang dulu; sidecar: boot ulang memeriksa /health
    if (isCamera && !(await camera.request())) {
      setBusy(false);
      return;
    }
    dispatch({ type: 'RETRY' });
  };

  useKeys({ onEnter: retry });

  return (
    <>
      <div className="abs" style={{ left: 110, top: 84 }}>
        <Wordmark size={52} />
      </div>
      <h1 className="abs title title-screen" style={{ left: 110, top: 300, width: 1400, lineHeight: 1.05 }}
        data-testid="error-message">
        {isCamera ? strings.error.camera : strings.error.service}
      </h1>
      <div className="abs" style={{ left: 110, top: 760 }}>
        <Button variant="primary" hero arrow onClick={retry} disabled={busy} testId="try-again">
          {strings.error.tryAgain}
        </Button>
      </div>
    </>
  );
}
