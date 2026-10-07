/**
 * Ilustrasi dekoratif dua orang (dari mockup). Warna dari token --illu-*.
 * viewBox 176x132 dengan "slice" agar bisa mengisi sel berukuran apa pun.
 */
interface Person {
  cx: number;
  hairCy: number;
  r: number;
  body: string;
  skin: string;
  hair: string;
}

interface Scene {
  bg: string;
  sun: string;
  people: [Person, Person];
}

const v = (name: string) => `var(--${name})`;

const P1 = { cx: 63.36, hairCy: 91.657, r: 11.786 };
const P2 = { cx: 119.68, hairCy: 96.485, r: 10.843 };

const SCENES: Scene[] = [
  {
    bg: 'illu-sand',
    sun: 'butter',
    people: [
      { ...P1, body: 'verm', skin: 'illu-skin-1', hair: 'illu-hair-1' },
      { ...P2, body: 'butter', skin: 'illu-skin-2', hair: 'illu-hair-2' },
    ],
  },
  {
    bg: 'illu-sage',
    sun: 'cream',
    people: [
      { ...P1, body: 'illu-teal', skin: 'illu-skin-3', hair: 'illu-hair-3' },
      { ...P2, body: 'illu-plum', skin: 'illu-skin-4', hair: 'ink' },
    ],
  },
  {
    bg: 'illu-blush',
    sun: 'paper',
    people: [
      { ...P1, body: 'butter', skin: 'illu-skin-2', hair: 'illu-hair-2' },
      { ...P2, body: 'illu-green', skin: 'illu-skin-1', hair: 'illu-hair-1' },
    ],
  },
  {
    bg: 'illu-lilac',
    sun: 'butter',
    people: [
      { ...P1, body: 'illu-plum', skin: 'illu-skin-4', hair: 'ink' },
      { ...P2, body: 'verm', skin: 'illu-skin-3', hair: 'illu-hair-3' },
    ],
  },
];

function PersonShape({ p }: { p: Person }) {
  const { cx, hairCy, r } = p;
  return (
    <g>
      <rect x={cx - 1.4 * r} y={hairCy + 1.32 * r} width={2.8 * r} height={8 * r} rx={1.2 * r} style={{ fill: v(p.body) }} />
      <rect x={cx - 0.34 * r} y={hairCy + 0.72 * r} width={0.68 * r} height={1.2 * r} style={{ fill: v(p.skin) }} />
      <circle cx={cx} cy={hairCy} r={r} style={{ fill: v(p.hair) }} />
      <circle cx={cx} cy={hairCy + 0.2 * r} r={0.88 * r} style={{ fill: v(p.skin) }} />
    </g>
  );
}

export function Illustration({ scene, width, height }: { scene: number; width: number; height: number }) {
  const s = SCENES[((scene % SCENES.length) + SCENES.length) % SCENES.length];
  return (
    <svg width={width} height={height} viewBox="0 0 176 132" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <rect width="176" height="132" style={{ fill: v(s.bg) }} />
      <circle cx="140.8" cy="36.96" r="21.12" style={{ fill: v(s.sun) }} />
      <PersonShape p={s.people[0]} />
      <PersonShape p={s.people[1]} />
    </svg>
  );
}
