import { useCallback, useEffect, useMemo, useReducer, useRef } from 'react';
import { BoothApi } from './api/client';
import { resolveEnv } from './api/env';
import { BoothProvider, useCameraStream } from './app/context';
import { Stage } from './components/Stage';
import { Toast } from './components/ui';
import { initialState, reducer, type Screen } from './state/machine';
import { AttractScreen } from './screens/AttractScreen';
import { LayoutScreen } from './screens/LayoutScreen';
import { CaptureScreen } from './screens/CaptureScreen';
import { ReviewScreen } from './screens/ReviewScreen';
import { FilterScreen } from './screens/FilterScreen';
import { ResultScreen } from './screens/ResultScreen';
import { ErrorScreen } from './screens/ErrorScreen';
import { PayScreen } from './screens/PayScreen';
import { OperatorScreen } from './screens/OperatorScreen';

const TOAST_MS = 5_000;

const SCREENS: Record<Exclude<Screen, 'boot'>, () => JSX.Element | null> = {
  attract: AttractScreen,
  layout: LayoutScreen,
  pay: PayScreen,
  capture: CaptureScreen,
  review: ReviewScreen,
  filter: FilterScreen,
  result: ResultScreen,
  operator: OperatorScreen,
  error: ErrorScreen,
};

export function App() {
  const api = useMemo(() => {
    const env = resolveEnv();
    return env ? new BoothApi(env) : null;
  }, []);
  const [state, dispatch] = useReducer(reducer, initialState);
  const camera = useCameraStream();

  const toastTimer = useRef<number>();
  const showToast = useCallback((message: string) => {
    dispatch({ type: 'TOAST', message });
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => dispatch({ type: 'TOAST', message: null }), TOAST_MS);
  }, []);

  // Boot: cek /health lalu muat config, layout, filter
  useEffect(() => {
    if (state.screen !== 'boot') return;
    let cancelled = false;
    (async () => {
      if (!api) {
        dispatch({ type: 'FAILED', kind: 'service' });
        return;
      }
      try {
        await api.health();
        const [config, layouts, filters] = await Promise.all([api.config(), api.layouts(), api.filters()]);
        if (!cancelled) dispatch({ type: 'BOOTED', config, layouts, filters });
      } catch {
        if (!cancelled) dispatch({ type: 'FAILED', kind: 'service' });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [state.screen, api]);

  // Lepas object URL foto/strip yang tidak dipakai lagi
  const liveUrls = useRef(new Set<string>());
  useEffect(() => {
    const now = new Set([...state.frames, state.stripUrl].filter((u): u is string => !!u));
    liveUrls.current.forEach((u) => {
      if (!now.has(u)) URL.revokeObjectURL(u);
    });
    liveUrls.current = now;
  }, [state.frames, state.stripUrl]);

  const ScreenView = state.screen === 'boot' ? null : SCREENS[state.screen];

  return (
    <BoothProvider value={{ state, dispatch, api, camera, showToast }}>
      <Stage>
        {ScreenView && (
          <div key={state.screen} className="screen" data-screen={state.screen}>
            <ScreenView />
          </div>
        )}
        <Toast message={state.toast} />
      </Stage>
    </BoothProvider>
  );
}
