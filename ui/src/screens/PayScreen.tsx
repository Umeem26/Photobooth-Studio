import { useEffect, useState } from 'react';
import { useBooth } from '../app/context';
import { Button, QrImage, Wordmark } from '../components/ui';
import { useIdle } from '../hooks/useIdle';
import { useKeys } from '../hooks/useKeys';
import { photosFor } from '../state/machine';
import { strings } from '../strings';
import { FlowSteps } from './FlowSteps';
import { abandonToAttract } from './sessionActions';

const POLL_MS = 2_000;

/**
 * Pembayaran demo (docs/phase3.md bagian 1): QR palsu, chip "Demo mode" selalu tampil,
 * status diperiksa berkala, tombol "Simulate payment" melanjutkan ke Capture.
 */
export function PayScreen() {
  const booth = useBooth();
  const { state, dispatch, api, handleError } = booth;
  const [qr, setQr] = useState<string | null>(null);
  const [amount, setAmount] = useState(state.config?.payment.price ?? 0);
  const [busy, setBusy] = useState(false);
  const sessionId = state.sessionId;

  // Mulai pembayaran; sesi yang sudah lunas ("Start over") langsung lanjut
  useEffect(() => {
    if (!api || !sessionId) return;
    let cancelled = false;
    api
      .startPayment(sessionId)
      .then((p) => {
        if (cancelled) return;
        if (p.status === 'paid') dispatch({ type: 'PAYMENT_CONFIRMED' });
        setQr(p.qrPng);
        setAmount(p.amount);
      })
      .catch((e) => !cancelled && handleError(e, strings.toast.composeFailed));
    return () => {
      cancelled = true;
    };
  }, [api, sessionId, dispatch, handleError]);

  // "This screen updates by itself": cek status berkala
  useEffect(() => {
    if (!api || !sessionId) return;
    const timer = window.setInterval(() => {
      api
        .paymentStatus(sessionId)
        .then((s) => s.status === 'paid' && dispatch({ type: 'PAYMENT_CONFIRMED' }))
        .catch(() => undefined);
    }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [api, sessionId, dispatch]);

  const simulate = async () => {
    if (!api || !sessionId || busy) return;
    setBusy(true);
    try {
      await api.simulatePayment(sessionId);
      dispatch({ type: 'PAYMENT_CONFIRMED' });
    } catch (e) {
      handleError(e, strings.toast.composeFailed);
      setBusy(false);
    }
  };

  const back = () => dispatch({ type: 'BACK_TO_LAYOUT' });

  useKeys({ onEscape: back });
  useIdle(() => abandonToAttract(booth));

  return (
    <>
      <div className="abs" style={{ left: 110, top: 70 }}>
        <Wordmark size={44} />
      </div>
      <FlowSteps screen="pay" />
      <h1 className="abs title title-result" style={{ left: 110, top: 230 }}>
        {strings.pay.titleLead}
        <br />
        {strings.pay.titleMiddle} <em>{strings.pay.titleEmphasis}</em>
      </h1>
      <div className="abs label-caps" style={{ left: 114, top: 520 }} data-testid="pay-session">
        {strings.pay.session(photosFor(state, state.layoutId))}
      </div>
      <div className="abs title" style={{ left: 106, top: 570, fontSize: 150, fontWeight: 600 }} data-testid="pay-price">
        {strings.pay.price(amount)}
      </div>
      <div className="abs" style={{ left: 114, top: 790 }}>
        <span className="chip" style={{ height: 72, fontSize: 28, background: 'var(--butter)', borderColor: 'var(--butter)' }}
          data-testid="demo-chip">
          {strings.pay.demo}
        </span>
      </div>
      <div className="abs" style={{ left: 110, top: 928 }}>
        <Button variant="ghost" onClick={back} testId="back">
          {strings.pay.back}
        </Button>
      </div>

      <div className="abs card" style={{ left: 1060, top: 170, width: 700, height: 760, padding: 40, textAlign: 'center',
        display: 'block' }}>
        <div style={{ width: 560, height: 560, margin: '0 auto' }}>
          {qr ? <QrImage png={qr} size={560} testId="pay-qr" /> : <div className="skeleton" style={{ width: 560, height: 560 }} />}
        </div>
        <div className="card-title" style={{ fontSize: 40, marginTop: 28, textAlign: 'center' }}>{strings.pay.waiting}</div>
        <div className="note" style={{ marginTop: 6, fontWeight: 500 }}>{strings.pay.updates}</div>
      </div>
      <div className="abs" style={{ left: 1060, top: 950 }}>
        <Button variant="outline" onClick={simulate} disabled={busy} testId="simulate"
          style={{ height: 96, fontSize: 32, padding: '0 44px', borderWidth: 3 }}>
          {strings.pay.simulate}
        </Button>
      </div>
    </>
  );
}
