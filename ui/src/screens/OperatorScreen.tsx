import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useBooth } from '../app/context';
import { ApiError, type BoothApi } from '../api/client';
import type { AdminConfig, AdminStatus, Printer, SessionSummary, ShareLink } from '../api/types';
import { Button, QrImage, Segmented, Toggle, Wordmark } from '../components/ui';
import { useIdle } from '../hooks/useIdle';
import { strings } from '../strings';

const OPERATOR_IDLE_MS = 5 * 60_000;
const PANELS = ['event', 'photos', 'payment', 'sharing', 'printing', 'gallery', 'status'] as const;
type Panel = (typeof PANELS)[number];
type Admin = ReturnType<BoothApi['admin']>;
type Save = (changes: AdminConfig) => Promise<boolean>;

const t = strings.operator;

// ---------------------------------------------------------------- bagian umum

function Row({ label, hint, children, testId }: { label: string; hint?: string; children?: ReactNode; testId?: string }) {
  return (
    <div className="op-row" data-testid={testId}>
      <div>
        <div className="op-label">{label}</div>
        {hint && <div className="op-hint">{hint}</div>}
      </div>
      {children}
    </div>
  );
}

/** Input teks yang disimpan saat Enter atau kehilangan fokus. */
function TextField({ value, onSave, type = 'text', label, testId, placeholder }: {
  value: string;
  onSave: (v: string) => void;
  type?: string;
  label: string;
  testId?: string;
  placeholder?: string;
}) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  const commit = () => draft !== value && onSave(draft);
  return (
    <input className="op-input" type={type} value={draft} aria-label={label} placeholder={placeholder}
      onChange={(e) => setDraft(e.target.value)} onBlur={commit}
      onKeyDown={(e) => e.key === 'Enter' && (e.currentTarget as HTMLInputElement).blur()} data-testid={testId} />
  );
}

function Stepper({ value, min, max, onChange, label, testId }: {
  value: number;
  min: number;
  max: number;
  onChange: (v: number) => void;
  label: string;
  testId?: string;
}) {
  return (
    <span className="stepper" aria-label={label} data-testid={testId}>
      <Button variant="outline" onClick={() => onChange(value - 1)} disabled={value <= min}>-</Button>
      <span className="stepper-value">{value}</span>
      <Button variant="outline" onClick={() => onChange(value + 1)} disabled={value >= max}>+</Button>
    </span>
  );
}

function Confirm({ title, body, confirm, onConfirm, onCancel }: {
  title: string;
  body?: string;
  confirm: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="scrim" data-testid="confirm">
      <div className="dialog" role="dialog" aria-modal="true">
        <h2 className="title" style={{ fontSize: 56 }}>{title}</h2>
        {body && <p className="body-text" style={{ fontSize: 28, marginTop: 14 }}>{body}</p>}
        <div className="dialog-actions">
          <Button variant="primary" onClick={onConfirm} className="op-btn" testId="confirm-yes">{confirm}</Button>
          <Button variant="outline" onClick={onCancel} className="op-btn" testId="confirm-no">{t.gallery.cancel}</Button>
        </div>
      </div>
    </div>
  );
}

const num = (v: string | undefined, d: number) => (v === undefined || v === '' ? d : Number(v));

// ---------------------------------------------------------------- panel

function EventPanel({ config, save }: { config: AdminConfig; save: Save }) {
  return (
    <>
      <Row label={t.event.name} hint={t.event.nameHint}>
        <TextField value={config['event.name'] ?? ''} label={t.event.name} testId="event-name"
          onSave={(v) => save({ 'event.name': v })} />
      </Row>
      <Row label={t.event.date} hint={t.event.dateHint}>
        <TextField type="date" value={config['event.date'] ?? ''} label={t.event.date} testId="event-date"
          onSave={(v) => save({ 'event.date': v })} />
      </Row>
    </>
  );
}

function PhotosPanel({ config, save }: { config: AdminConfig; save: Save }) {
  return (
    <>
      <Row label={t.photos.retakes} hint={t.photos.retakesHint}>
        <Stepper value={num(config.maxRetakes, 2)} min={0} max={10} label={t.photos.retakes} testId="retakes"
          onChange={(v) => save({ maxRetakes: String(v) })} />
      </Row>
      <Row label={t.photos.countdown} hint={t.photos.countdownHint}>
        <Segmented label={t.photos.countdown} value={num(config['countdown.seconds'], 3)} testId="countdown"
          options={[2, 3, 4, 5].map((n) => ({ value: n, label: t.photos.seconds(n) }))}
          onChange={(v) => save({ 'countdown.seconds': String(v) })} />
      </Row>
      <Row label={t.photos.pause} hint={t.photos.pauseHint}>
        <Segmented label={t.photos.pause} value={num(config['capture.pauseSeconds'], 1)} testId="pause"
          options={[0, 1, 2, 3].map((n) => ({ value: n, label: t.photos.seconds(n) }))}
          onChange={(v) => save({ 'capture.pauseSeconds': String(v) })} />
      </Row>
    </>
  );
}

function PaymentPanel({ config, save }: { config: AdminConfig; save: Save }) {
  return (
    <>
      <Row label={t.payment.toggle} hint={t.payment.toggleHint}>
        <Toggle on={config['payment.enabled'] === 'true'} label={t.payment.toggle} testId="payment-toggle"
          onChange={(on) => save({ 'payment.enabled': String(on) })} />
      </Row>
      <Row label={t.payment.price} hint={t.payment.priceHint}>
        <TextField type="number" value={config['payment.price'] ?? ''} label={t.payment.price} testId="payment-price"
          onSave={(v) => save({ 'payment.price': v })} />
      </Row>
    </>
  );
}

function SharingPanel({ config, save, admin }: { config: AdminConfig; save: Save; admin: Admin }) {
  const [status, setStatus] = useState<AdminStatus | null>(null);
  const [test, setTest] = useState<ShareLink | null>(null);
  const [editing, setEditing] = useState(false);
  const enabled = config['share.enabled'] === 'true';

  useEffect(() => {
    let cancelled = false;
    admin.status().then((s) => !cancelled && setStatus(s)).catch(() => undefined);
    if (enabled) admin.shareTest().then((l) => !cancelled && setTest(l)).catch(() => !cancelled && setTest(null));
    else setTest(null);
    return () => {
      cancelled = true;
    };
  }, [admin, enabled, config['share.host'], config['share.port']]);

  const host = config['share.host'] || status?.shareDetectedHost || '';
  const address = host ? `${host} : ${status?.sharePort ?? config['share.port']}` : t.sharing.addressNone;

  return (
    <>
      <Row label={t.sharing.toggle} hint={t.sharing.toggleHint}>
        <Toggle on={enabled} label={t.sharing.toggle} testId="share-toggle"
          onChange={(on) => save({ 'share.enabled': String(on) })} />
      </Row>
      <Row label={t.sharing.address} hint={t.sharing.addressHint}>
        {editing ? (
          <TextField value={config['share.host'] ?? ''} label={t.sharing.address} testId="share-host"
            placeholder={status?.shareDetectedHost ?? ''}
            onSave={async (v) => {
              await save({ 'share.host': v.trim() });
              setEditing(false);
            }} />
        ) : (
          <button type="button" className="chip" style={{ height: 72, fontSize: 28 }} onClick={() => setEditing(true)}
            data-testid="share-address">
            {address}
          </button>
        )}
      </Row>
      <Row label={t.sharing.expiry} hint={t.sharing.expiryHint}>
        <Segmented label={t.sharing.expiry} value={num(config['share.expiryHours'], 6)} testId="share-expiry"
          options={[1, 6, 24].map((n) => ({ value: n, label: t.sharing.hours(n) }))}
          onChange={(v) => save({ 'share.expiryHours': String(v) })} />
      </Row>
      <div style={{ display: 'flex', gap: 34, alignItems: 'center', padding: '20px 0 8px' }}>
        <div style={{ width: 210, height: 210, borderRadius: 24, border: '2px solid var(--line)', padding: 14, flex: 'none' }}>
          {test ? <QrImage png={test.qrPng} size={178} testId="share-test-qr" /> : <div className="skeleton" style={{ width: 178, height: 178 }} />}
        </div>
        <div style={{ width: 820 }}>
          <div className="card-title" style={{ fontSize: 38 }}>{t.sharing.testTitle}</div>
          <div className="op-hint" style={{ fontSize: 24, lineHeight: 1.45, marginTop: 8 }}>
            {enabled ? t.sharing.testBody : t.sharing.off}
          </div>
        </div>
      </div>
    </>
  );
}

function PrintingPanel({ config, save, admin, api, toast }: {
  config: AdminConfig;
  save: Save;
  admin: Admin;
  api: BoothApi;
  toast: (m: string) => void;
}) {
  const [printers, setPrinters] = useState<Printer[]>([]);
  const [testing, setTesting] = useState(false);
  useEffect(() => {
    api.printers().then(setPrinters).catch(() => setPrinters([]));
  }, [api]);

  const printTest = async () => {
    setTesting(true);
    try {
      await admin.printTest();
      for (let i = 0; i < 60; i++) {
        await new Promise((r) => window.setTimeout(r, 1_000));
        const s = await admin.printTestStatus();
        if (s.status === 'done' || s.status === 'failed') {
          toast(s.status === 'done' ? t.printing.testSent : strings.result.printFailed);
          break;
        }
      }
    } catch {
      toast(strings.result.printFailed);
    } finally {
      setTesting(false);
    }
  };

  return (
    <>
      <Row label={t.printing.printer} hint={t.printing.printerHint}>
        <select className="op-input" value={config['print.printer'] ?? ''} aria-label={t.printing.printer}
          onChange={(e) => save({ 'print.printer': e.target.value })} data-testid="printer-select">
          <option value="">{t.printing.systemDefault}</option>
          {printers.map((p) => (
            <option key={p.name} value={p.name}>{p.name}</option>
          ))}
        </select>
      </Row>
      <Row label={t.printing.copies} hint={t.printing.copiesHint}>
        <Stepper value={num(config['print.maxCopies'], 2)} min={1} max={10} label={t.printing.copies} testId="copies"
          onChange={(v) => save({ 'print.maxCopies': String(v) })} />
      </Row>
      <Row label={t.printing.layout} hint={t.printing.layoutHint}>
        <Segmented label={t.printing.layout} value={config['print.layout'] ?? 'single'} testId="print-layout"
          options={[{ value: 'single', label: t.printing.single }, { value: 'two-up', label: t.printing.twoUp }]}
          onChange={(v) => save({ 'print.layout': v })} />
      </Row>
      <div style={{ padding: '24px 0 8px' }}>
        <Button variant="outline" className="op-btn" onClick={printTest} disabled={testing} testId="print-test">
          {t.printing.test}
        </Button>
      </div>
    </>
  );
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${d.getDate()} ${months[d.getMonth()]}, ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function GalleryPanel({ admin, toast }: { admin: Admin; toast: (m: string) => void }) {
  const [sessions, setSessions] = useState<SessionSummary[] | null>(null);
  const [thumbs, setThumbs] = useState<Record<string, string>>({});
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [purgeDays, setPurgeDays] = useState(30);
  const [confirmPurge, setConfirmPurge] = useState(false);

  const load = useCallback(async () => {
    const list = await admin.sessions();
    setSessions(list);
    const urls: Record<string, string> = {};
    await Promise.all(
      list.filter((s) => s.hasStrip || s.frames > 0).map(async (s) => {
        try {
          urls[s.id] = await admin.thumbUrl(s.id);
        } catch {
          // tanpa thumbnail
        }
      }),
    );
    setThumbs((old) => {
      Object.values(old).forEach((u) => URL.revokeObjectURL(u));
      return urls;
    });
  }, [admin]);

  useEffect(() => {
    load().catch(() => setSessions([]));
  }, [load]);
  useEffect(() => () => Object.values(thumbs).forEach((u) => URL.revokeObjectURL(u)), [thumbs]);

  const remove = async (id: string) => {
    setConfirmId(null);
    await admin.deleteSession(id).catch(() => undefined);
    await load();
  };
  const purge = async () => {
    setConfirmPurge(false);
    const r = await admin.purge(purgeDays);
    toast(t.gallery.purged(r.deleted));
    await load();
  };
  const exportAll = async () => toast(t.gallery.exported((await admin.exportAll()).path));

  return (
    <>
      <div className="op-row" style={{ minHeight: 120 }}>
        <Button variant="outline" className="op-btn" onClick={exportAll} testId="export-all">{t.gallery.exportAll}</Button>
        <span style={{ display: 'flex', gap: 18, alignItems: 'center' }}>
          <Segmented label={t.gallery.purge} value={purgeDays} testId="purge-days"
            options={[7, 30, 90].map((n) => ({ value: n, label: t.gallery.days(n) }))} onChange={setPurgeDays} />
          <Button variant="outline" className="op-btn" onClick={() => setConfirmPurge(true)} testId="purge">
            {t.gallery.purge}
          </Button>
        </span>
      </div>
      <div className="op-scroll" style={{ maxHeight: 600 }}>
        {sessions && sessions.length === 0 && <p className="op-hint" style={{ padding: '32px 0' }}>{t.gallery.empty}</p>}
        <div className="gallery-grid" data-testid="gallery-grid">
          {sessions?.map((s) => (
            <div key={s.id} className="gallery-item" data-testid="gallery-item">
              {thumbs[s.id] ? <img className="gallery-thumb" src={thumbs[s.id]} alt="" /> : <div className="gallery-thumb" />}
              <div className="gallery-meta">
                {(t.gallery.status[s.status] ?? s.status)} · {formatTime(s.createdAt)}
              </div>
              <Button variant="outline" className="op-btn" style={{ width: '100%', height: 72, fontSize: 24 }}
                onClick={() => setConfirmId(s.id)} testId="gallery-delete">
                {t.gallery.delete}
              </Button>
            </div>
          ))}
        </div>
      </div>
      {confirmId && (
        <Confirm title={t.gallery.confirmDelete} body={t.gallery.confirmBody} confirm={t.gallery.delete}
          onConfirm={() => remove(confirmId)} onCancel={() => setConfirmId(null)} />
      )}
      {confirmPurge && (
        <Confirm title={t.gallery.purgeConfirm(purgeDays)} body={t.gallery.purgeHint(purgeDays)} confirm={t.gallery.delete}
          onConfirm={purge} onCancel={() => setConfirmPurge(false)} />
      )}
    </>
  );
}

function StatusPanel({ admin, toast }: { admin: Admin; toast: (m: string) => void }) {
  const { camera } = useBooth();
  const [status, setStatus] = useState<AdminStatus | null>(null);
  useEffect(() => {
    admin.status().then(setStatus).catch(() => undefined);
  }, [admin]);

  const cameraName = camera.stream?.getVideoTracks()[0]?.label || t.status.cameraNone;
  const printer = status?.printers.find((p) => p.default) ?? status?.printers[0];
  const printerText = printer ? `${printer.name} (${printer.status})` : t.status.printerNone;
  const disk = status && status.diskFreeBytes >= 0 ? t.status.gigabytes((status.diskFreeBytes / 1e9).toFixed(1)) : '-';

  const copy = async () => {
    const diag = { camera: cameraName, ...status, userAgent: navigator.userAgent };
    try {
      await navigator.clipboard.writeText(JSON.stringify(diag, null, 2));
      toast(t.status.copied);
    } catch {
      toast(t.saveFailed);
    }
  };

  return (
    <>
      <Row label={t.status.camera} testId="status-camera"><span className="op-hint" style={{ fontSize: 28 }}>{cameraName}</span></Row>
      <Row label={t.status.printer}><span className="op-hint" style={{ fontSize: 28 }}>{printerText}</span></Row>
      <Row label={t.status.version}><span className="op-hint" style={{ fontSize: 28 }}>{status?.version ?? '-'}</span></Row>
      <Row label={t.status.disk}><span className="op-hint" style={{ fontSize: 28 }}>{disk}</span></Row>
      <Row label={t.status.sharing}>
        <span className="op-hint" style={{ fontSize: 28 }}>{status?.shareUrl || t.sharing.off}</span>
      </Row>
      <div style={{ padding: '24px 0 8px' }}>
        <Button variant="outline" className="op-btn" onClick={copy} testId="copy-diagnostics">{t.status.copy}</Button>
      </div>
    </>
  );
}

// ---------------------------------------------------------------- layar

export function OperatorScreen() {
  const { state, dispatch, api, showToast } = useBooth();
  const token = state.adminToken;
  const admin = useMemo(() => (api && token ? api.admin(token) : null), [api, token]);
  const [panel, setPanel] = useState<Panel>('event');
  const [config, setConfig] = useState<AdminConfig | null>(null);

  const exit = useCallback(async () => {
    if (admin) admin.logout().catch(() => undefined);
    if (api) {
      try {
        dispatch({ type: 'CONFIG_UPDATED', config: await api.config() });
      } catch {
        // config lama tetap dipakai
      }
    }
    dispatch({ type: 'OPERATOR_CLOSED' });
  }, [admin, api, dispatch]);

  const expired = (e: unknown) => e instanceof ApiError && e.code === 'admin_unauthorized';

  useEffect(() => {
    if (!admin) return;
    admin.config().then(setConfig).catch((e) => {
      if (expired(e)) exit();
    });
  }, [admin, exit]);

  const save: Save = useCallback(
    async (changes) => {
      if (!admin || !api) return false;
      try {
        setConfig(await admin.saveConfig(changes));
        dispatch({ type: 'CONFIG_UPDATED', config: await api.config() });
        showToast(t.saved);
        return true;
      } catch (e) {
        if (expired(e)) exit();
        else showToast(t.saveFailed);
        return false;
      }
    },
    [admin, api, dispatch, showToast, exit],
  );

  useIdle(exit, OPERATOR_IDLE_MS);

  if (!admin || !api) return null;

  return (
    <>
      <nav className="op-sidebar" aria-label={t.mode}>
        <Wordmark size={44} />
        <div className="op-caption">{t.mode}</div>
        {PANELS.map((p) => (
          <button key={p} type="button" className={`op-menu${p === panel ? ' active' : ''}`} onClick={() => setPanel(p)}
            aria-current={p === panel} data-testid={`menu-${p}`}>
            {t.menu[p]}
          </button>
        ))}
      </nav>
      <div className="abs" style={{ right: 80, top: 62 }}>
        <Button variant="outline" className="op-btn" onClick={exit} testId="exit-operator">{t.exit}</Button>
      </div>
      <h1 className="abs op-title" style={{ left: 510, top: 86 }} data-testid="operator-title">{t.menu[panel]}</h1>
      <section className="op-card" data-testid={`panel-${panel}`}>
        {config ? (
          <>
            {panel === 'event' && <EventPanel config={config} save={save} />}
            {panel === 'photos' && <PhotosPanel config={config} save={save} />}
            {panel === 'payment' && <PaymentPanel config={config} save={save} />}
            {panel === 'sharing' && <SharingPanel config={config} save={save} admin={admin} />}
            {panel === 'printing' && <PrintingPanel config={config} save={save} admin={admin} api={api} toast={showToast} />}
            {panel === 'gallery' && <GalleryPanel admin={admin} toast={showToast} />}
            {panel === 'status' && <StatusPanel admin={admin} toast={showToast} />}
          </>
        ) : (
          <div className="skeleton" style={{ height: 400 }} />
        )}
      </section>
    </>
  );
}
