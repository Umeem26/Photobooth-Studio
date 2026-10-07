import type { CSSProperties, ReactNode } from 'react';
import { strings } from '../strings';
import { Illustration } from './Illustration';

// ---------------------------------------------------------------- ikon

export function ArrowIcon({ size = 44 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

export function CheckIcon() {
  return (
    <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.4"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={{ color: 'var(--paper)' }}>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  );
}

// ---------------------------------------------------------------- teks merek

export function Wordmark({ size, style }: { size: number; style?: CSSProperties }) {
  return (
    <span className="wm" style={{ fontSize: size, ...style }} aria-label={strings.brand}>
      Van <i>de</i> B<b>oo</b>th
    </span>
  );
}

/** Judul dua frasa: frasa kedua italic weight 400. */
export function Title({ lead, emphasis, className, br }: { lead: string; emphasis: string; className: string; br?: boolean }) {
  return (
    <h1 className={`title ${className}`}>
      {lead}
      {br ? <br /> : ' '}
      <em>{emphasis}</em>
    </h1>
  );
}

// ---------------------------------------------------------------- Button

type Variant = 'primary' | 'outline' | 'ghost';

export function Button({
  variant,
  hero,
  arrow,
  children,
  onClick,
  disabled,
  className = '',
  style,
  testId,
}: {
  variant: Variant;
  hero?: boolean;
  arrow?: boolean;
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  className?: string;
  style?: CSSProperties;
  testId?: string;
}) {
  return (
    <button
      type="button"
      className={`btn btn-${variant}${hero ? ' btn-hero' : ''} ${className}`}
      onClick={onClick}
      disabled={disabled}
      style={style}
      data-testid={testId}
      data-variant={variant}
    >
      {children}
      {arrow && <ArrowIcon />}
    </button>
  );
}

// ---------------------------------------------------------------- Chip, StepPill

export function Chip({ size = 'sm', onCamera, children, style, testId }: {
  size?: 'sm' | 'lg' | 'xl';
  onCamera?: boolean;
  children: ReactNode;
  style?: CSSProperties;
  testId?: string;
}) {
  const cls = `chip${size === 'lg' ? ' chip-lg' : size === 'xl' ? ' chip-lg chip-xl' : ''}${onCamera ? ' chip-on-camera' : ''}`;
  return (
    <span className={cls} style={style} data-testid={testId}>
      {children}
    </span>
  );
}

export function StepPill({ active }: { active: 1 | 2 | 3 }) {
  return (
    <div className="abs steps" style={{ right: 110, top: 62 }} data-testid="step-pill">
      {strings.steps.map((label, i) => (
        <span key={label} className={`step${i + 1 === active ? ' active' : ''}`}>
          <span className="step-num">{i + 1}</span>
          {label}
        </span>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- Card

export function SelectableCard({
  selected,
  onSelect,
  style,
  children,
  testId,
}: {
  selected: boolean;
  onSelect: () => void;
  style: CSSProperties;
  children: ReactNode;
  testId?: string;
}) {
  return (
    <button type="button" className={`card${selected ? ' selected' : ''}`} style={style} onClick={onSelect}
      aria-pressed={selected} data-testid={testId}>
      {selected && (
        <span className="card-check">
          <CheckIcon />
        </span>
      )}
      {children}
    </button>
  );
}

// ---------------------------------------------------------------- StripPreview (dekoratif)

export function StripPreview({
  photos,
  orientation,
  cellW,
  cellH,
  gap,
  padding,
  wordmarkSize,
  caption,
  captionSize,
  showFooter = true,
  style,
}: {
  photos: number;
  orientation: 'vertical' | 'horizontal';
  cellW: number;
  cellH: number;
  gap: number;
  padding: string;
  wordmarkSize: number;
  caption?: string;
  captionSize: number;
  showFooter?: boolean;
  style?: CSSProperties;
}) {
  return (
    <div className="strip" style={{ padding, ...style }}>
      <div className={`strip-cells${orientation === 'horizontal' ? ' row' : ''}`} style={{ gap }}>
        {Array.from({ length: photos }, (_, i) => (
          <div key={i} className="strip-cell" style={{ width: cellW, height: cellH }}>
            <Illustration scene={i} width={cellW} height={cellH} />
          </div>
        ))}
      </div>
      {showFooter && (
        <div className="strip-foot">
          <Wordmark size={wordmarkSize} style={{ display: 'block' }} />
          {caption && (
            <div className="strip-caption" style={{ fontSize: captionSize }}>
              {caption}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- Countdown, corners, toast

export function CountdownDisc({ value }: { value: number }) {
  return (
    <div className="disc" data-testid="countdown">
      <span key={value} className="disc-num">
        {value}
      </span>
    </div>
  );
}

const CORNERS = [
  { left: 110, top: 110, rot: 0, ml: 0 },
  { left: 1760, top: 110, rot: 90, ml: -8 },
  { left: 1760, top: 910, rot: 180, ml: -8 },
  { left: 110, top: 910, rot: 270, ml: 0 },
];

export function ViewfinderCorners() {
  return (
    <>
      {CORNERS.map((c) => (
        <div key={c.rot} className="corner" style={{ left: c.left, top: c.top, transform: `rotate(${c.rot}deg)` }}>
          <div style={{ marginLeft: c.ml }} />
        </div>
      ))}
    </>
  );
}

export function Toast({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="toast" role="status" data-testid="toast">
      {message}
    </div>
  );
}

export function ProgressDots({ total, current, done }: { total: number; current: number; done: (i: number) => boolean }) {
  return (
    <span className="dots" aria-hidden="true">
      {Array.from({ length: total }, (_, i) => {
        const idx = i + 1;
        const cls = idx === current ? 'dot current' : done(idx) ? 'dot done' : 'dot';
        return <span key={idx} className={cls} />;
      })}
    </span>
  );
}
