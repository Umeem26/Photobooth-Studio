import { useCallback, useEffect, useState } from 'react';
import { ApiError, type BoothApi } from '../api/client';
import { strings } from '../strings';
import { Button } from './ui';

const MIN = 4;
const MAX = 8;

/**
 * Dialog PIN Mode Operator. mode 'create' = PIN pertama (dua kali ketik), 'enter' = login.
 * Keypad layar sentuh + keyboard fisik (angka, Backspace, Enter, Esc).
 */
export function PinDialog({
  api,
  mode,
  onSuccess,
  onCancel,
}: {
  api: BoothApi;
  mode: 'create' | 'enter';
  onSuccess: (token: string) => void;
  onCancel: () => void;
}) {
  const [digits, setDigits] = useState('');
  const [first, setFirst] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const title = mode === 'enter' ? strings.pin.enter : first === null ? strings.pin.create : strings.pin.confirm;
  const hint = message ?? (mode === 'create' && first === null ? strings.pin.createHint : ' ');

  const press = useCallback((d: string) => {
    setMessage(null);
    setDigits((cur) => (cur.length < MAX ? cur + d : cur));
  }, []);
  const erase = useCallback(() => setDigits((cur) => cur.slice(0, -1)), []);

  const submit = useCallback(async () => {
    if (busy || digits.length < MIN) return;
    if (mode === 'create' && first === null) {
      setFirst(digits);
      setDigits('');
      return;
    }
    if (mode === 'create' && first !== digits) {
      setFirst(null);
      setDigits('');
      setMessage(strings.pin.mismatch);
      return;
    }
    setBusy(true);
    try {
      onSuccess(mode === 'create' ? await api.createPin(digits) : await api.login(digits));
    } catch (e) {
      setDigits('');
      setMessage(e instanceof ApiError && e.status === 423 ? strings.pin.locked : strings.pin.wrong);
      if (mode === 'create') setFirst(null);
    } finally {
      setBusy(false);
    }
  }, [api, busy, digits, first, mode, onSuccess]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (/^[0-9]$/.test(e.key)) press(e.key);
      else if (e.key === 'Backspace') erase();
      else if (e.key === 'Enter') submit();
      else if (e.key === 'Escape') onCancel();
      else return;
      e.preventDefault();
      e.stopPropagation();
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [press, erase, submit, onCancel]);

  return (
    <div className="scrim" data-testid="pin-dialog">
      <div className="dialog" role="dialog" aria-modal="true" style={{ width: 720, textAlign: 'center', padding: '48px 56px' }}>
        <h2 className="title" style={{ fontSize: 64 }} data-testid="pin-title">{title}</h2>
        <p className="body-text" style={{ fontSize: 26, marginTop: 12 }} data-testid="pin-message">{hint}</p>
        <div className="pin-dots" aria-label={`${digits.length} digits`}>
          {Array.from({ length: Math.max(MIN, digits.length) }, (_, i) => (
            <span key={i} className={`pin-dot${i < digits.length ? ' filled' : ''}`} />
          ))}
        </div>
        <div className="keypad">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
            <Button key={d} variant="outline" onClick={() => press(d)} testId={`key-${d}`}>{d}</Button>
          ))}
          <Button variant="outline" className="wide" onClick={erase} testId="key-delete">{strings.pin.deleteKey}</Button>
          <Button variant="outline" onClick={() => press('0')} testId="key-0">0</Button>
          <Button variant="primary" className="wide" onClick={submit} disabled={busy || digits.length < MIN} testId="key-enter">
            {strings.pin.enterKey}
          </Button>
        </div>
        <div style={{ marginTop: 24 }}>
          <Button variant="ghost" onClick={onCancel} testId="pin-cancel">{strings.pin.cancel}</Button>
        </div>
      </div>
    </div>
  );
}
