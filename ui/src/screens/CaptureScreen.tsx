import { useEffect, useRef, useState } from 'react';
import { useBooth } from '../app/context';
import { captureFrame } from '../camera/capture';
import { LiveVideo } from '../components/LiveVideo';
import { Button, Chip, CountdownDisc, ProgressDots, ViewfinderCorners } from '../components/ui';
import { useKeys } from '../hooks/useKeys';
import { strings } from '../strings';
import { abandonToAttract } from './sessionActions';

const TICK_MS = 1_000;
const FIRST_DELAY_MS = 800;
const THUMB_MS = 1_200;

export function CaptureScreen() {
  const booth = useBooth();
  const { state, dispatch, api, camera, handleError } = booth;
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [count, setCount] = useState<number | null>(null);
  const [flashKey, setFlashKey] = useState(0);
  const [thumb, setThumb] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const shots = useRef(0);
  const thumbTimer = useRef<number>();

  const current = state.queue[0];
  const total = state.frames.length;
  // Dari Mode Operator: detik hitung mundur (2-5) dan jeda antar foto
  const countdownFrom = state.config?.countdownSeconds ?? 3;
  const betweenMs = (state.config?.pauseSeconds ?? 1) * 1_000;
  // Foto baru dicermin atau tidak mengikuti Mode Operator; dibaca lewat ref agar hitung mundur tidak mulai ulang
  const mirrorRef = useRef(true);
  mirrorRef.current = state.config?.mirrorPhotos !== false;

  // Kamera bisa hilang di tengah sesi: minta ulang, gagal -> layar Error
  useEffect(() => {
    if (camera.stream) return;
    camera.request().then((ok) => {
      if (!ok) dispatch({ type: 'FAILED', kind: 'camera' });
    });
  }, [camera, dispatch]);

  useEffect(() => () => window.clearTimeout(thumbTimer.current), []);

  // Satu foto per putaran: countdown 3-2-1, flash, ambil frame, unggah, thumbnail
  useEffect(() => {
    const timers: number[] = [];
    let cancelled = false;
    const wait = (ms: number) => new Promise<void>((resolve) => timers.push(window.setTimeout(resolve, ms)));

    if (current === undefined) {
      wait(Math.max(betweenMs, 1_000)).then(() => !cancelled && dispatch({ type: 'CAPTURE_FINISHED' }));
      return () => {
        cancelled = true;
        timers.forEach(window.clearTimeout);
      };
    }
    if (confirming || !camera.stream || !api || !state.sessionId) return;
    const sessionId = state.sessionId;

    (async () => {
      await wait(shots.current === 0 ? FIRST_DELAY_MS : betweenMs);
      for (let n = countdownFrom; n >= 1; n--) {
        if (cancelled) return;
        setCount(n);
        await wait(TICK_MS);
      }
      if (cancelled || !videoRef.current) return;
      setCount(null);
      setFlashKey((k) => k + 1);

      let blob: Blob;
      try {
        blob = await captureFrame(videoRef.current, mirrorRef.current);
        await api.putFrame(sessionId, current, blob);
      } catch (e) {
        if (!cancelled) {
          handleError(e, strings.toast.uploadFailed);
          setAttempt((a) => a + 1); // ulangi foto yang sama
        }
        return;
      }
      if (cancelled) return;
      shots.current += 1;
      const url = URL.createObjectURL(blob);
      setThumb(url);
      window.clearTimeout(thumbTimer.current);
      thumbTimer.current = window.setTimeout(() => setThumb(null), THUMB_MS);
      dispatch({ type: 'FRAME_CAPTURED', index: current, url });
    })();

    return () => {
      cancelled = true;
      timers.forEach(window.clearTimeout);
      setCount(null);
    };
  }, [current, confirming, camera.stream, api, state.sessionId, attempt, dispatch, handleError, countdownFrom, betweenMs]);

  const keepGoing = () => setConfirming(false);
  const discard = () => abandonToAttract(booth);

  useKeys({
    onEnter: confirming ? keepGoing : undefined,
    onEscape: () => setConfirming((c) => !c),
  });

  const shown = current ?? total;
  const caption = strings.capture.captions[(shown - 1 + strings.capture.captions.length) % strings.capture.captions.length];

  return (
    <>
      <div className="abs camera-full">
        <LiveVideo ref={videoRef} stream={camera.stream} />
      </div>
      <ViewfinderCorners />

      <div className="abs" style={{ left: 0, right: 0, top: 92, display: 'flex', justifyContent: 'center' }}>
        <Chip size="lg" onCamera testId="photo-chip">
          {strings.capture.photoOf(shown, total)}
          <ProgressDots total={total} current={current ?? 0} done={(i) => state.frames[i - 1] !== null} />
        </Chip>
      </div>

      <div className="abs" style={{ left: 230, top: 84 }}>
        <Button variant="ghost" className="btn-on-camera" onClick={() => setConfirming(true)} testId="cancel">
          {strings.capture.cancel}
        </Button>
      </div>

      {count !== null && <CountdownDisc value={count} />}

      <div className="abs" style={{ left: 0, right: 0, top: 880, textAlign: 'center' }}>
        <Chip size="xl" onCamera testId="caption">
          {caption}
        </Chip>
      </div>

      {thumb && (
        <div className="thumb" data-testid="thumb">
          <img src={thumb} alt="" />
        </div>
      )}

      {flashKey > 0 && <div key={flashKey} className="flash" />}

      {confirming && (
        <div className="scrim" data-testid="confirm">
          <div className="dialog" role="dialog" aria-modal="true">
            <h2 className="title" style={{ fontSize: 64 }}>{strings.capture.confirmTitle}</h2>
            <p className="body-text" style={{ marginTop: 18 }}>{strings.capture.confirmBody}</p>
            <div className="dialog-actions">
              <Button variant="primary" onClick={keepGoing} testId="keep-going">
                {strings.capture.confirmKeep}
              </Button>
              <Button variant="outline" onClick={discard} testId="discard">
                {strings.capture.confirmDiscard}
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
