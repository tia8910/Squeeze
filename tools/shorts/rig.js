// Squeeze shorts rig: poses in the sagittal plane, solved to joint positions.
// World space is metres, x forward (the figure faces +x), y up, floor at y = 0.
// Legs are two-bone IK to ankle targets; arms are forward kinematics with absolute
// angles (0 = hanging straight down, 90 = pointing forward, 180 = overhead).
export const L = { thigh: .44, shin: .43, foot: .2, torso: .52, upper: .30, fore: .27 };

// ── maths ──────────────────────────────────────────────────────────────────────
export const rad = d => d * Math.PI / 180;
export const lerp = (a, b, t) => a + (b - a) * t;
export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
export const ease = t => t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;   // cubic in-out
export const easeOut = t => 1 - Math.pow(1 - t, 3);
export const add = (a, b) => [a[0] + b[0], a[1] + b[1]];
export const sub = (a, b) => [a[0] - b[0], a[1] - b[1]];
export const mul = (a, k) => [a[0] * k, a[1] * k];
export const len = a => Math.hypot(a[0], a[1]);
export const norm = a => { const l = len(a) || 1; return [a[0] / l, a[1] / l]; };
export const ccw = a => [-a[1], a[0]];                // rotate +90°: for a downward vector this is "forward"
export const cw = a => [a[1], -a[0]];
const dirDown = deg => [Math.sin(rad(deg)), -Math.cos(rad(deg))];   // 0 = down, 90 = forward
const dirFoot = deg => [Math.cos(rad(deg)), -Math.sin(rad(deg))];   // 0 = forward, + = toes down

function lerpDeep(a, b, t) {
  if (typeof a === 'number') return lerp(a, b, t);
  if (Array.isArray(a)) return a.map((v, i) => lerpDeep(v, b[i], t));
  const o = {};
  for (const k in a) o[k] = (k in b) ? lerpDeep(a[k], b[k], t) : a[k];
  return o;
}

function ik(hip, target, l1, l2, bendFwd) {
  let d = sub(target, hip);
  let dist = len(d);
  const dn = norm(d);
  dist = clamp(dist, Math.abs(l1 - l2) + 1e-3, l1 + l2 - 1e-4);
  const a = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist);
  const h = Math.sqrt(Math.max(0, l1 * l1 - a * a));
  const base = add(hip, mul(dn, a));
  const knee = add(base, mul(bendFwd ? ccw(dn) : cw(dn), h));
  return { knee, end: add(hip, mul(dn, dist)) };
}

// ── poses ──────────────────────────────────────────────────────────────────────
// A pose: pelvis [x,y], torso lean (deg, + forward), head (deg), near/far legs
// { ankle:[x,y], foot:deg }, near/far arms { up:deg, fo:deg }.
const stand = (x = 0) => ({
  pelvis: [x, .92], torso: 2, head: 0,
  nl: { ankle: [x + .03, .08], foot: 0 }, fl: { ankle: [x - .03, .08], foot: 0 },
  na: { up: 2, fo: 8 }, fa: { up: -2, fo: 4 },
});
export const P = (base, over) => {                    // shallow-deep merge helper for keyframes
  const o = JSON.parse(JSON.stringify(base));
  for (const k in over) o[k] = (typeof over[k] === 'object' && !Array.isArray(over[k])) ? { ...o[k], ...over[k] } : over[k];
  return o;
};

export const MOVES = {
  // Running in place: four stride keys, near and far legs mirror half a cycle apart.
  run: (() => {
    const a = { pelvis: [0, .9], torso: 11, head: -6,
      nl: { ankle: [.30, .13], foot: -8 }, fl: { ankle: [-.36, .38], foot: 55 },
      na: { up: -38, fo: 45 }, fa: { up: 42, fo: 110 } };
    const b = { pelvis: [0, .86], torso: 11, head: -6,
      nl: { ankle: [.02, .08], foot: 0 }, fl: { ankle: [-.10, .46], foot: 40 },
      na: { up: 0, fo: 80 }, fa: { up: 0, fo: 80 } };
    const swap = p => ({ ...p, nl: p.fl, fl: p.nl, na: p.fa, fa: p.na });
    return { period: .72, scroll: 3.2, track: null, curve: 'linear',
      keys: [[0, a], [.25, b], [.5, swap(a)], [.75, swap(b)], [1, a]] };
  })(),

  idle: { period: 3.2, track: null,
    keys: [[0, stand()], [.5, P(stand(), { pelvis: [0, .915], na: { up: 6, fo: 14 }, fa: { up: 2, fo: 10 } })], [1, stand()]] },

  // Bulgarian split squat: front (near) foot planted, rear (far) foot laces-down on a bench.
  split_squat: (() => {
    const top = { pelvis: [-.04, .86], torso: 6, head: 0,
      nl: { ankle: [.36, .08], foot: 0 }, fl: { ankle: [-.60, .53], foot: 160 },
      na: { up: -1, fo: 0 }, fa: { up: -3, fo: -2 } };
    const bot = P(top, { pelvis: [-.11, .50], torso: 15 });
    return { period: 2.8, track: 'pelvis', props: [{ type: 'bench', x: -.62, h: .45, w: .56 }, { type: 'dumbbell', hand: 'both' }],
      keys: [[0, top], [.12, top], [.62, bot], [.68, bot], [1, top]] };
  })(),

  rdl: (() => {
    const top = { pelvis: [0, .92], torso: 0, head: 0,
      nl: { ankle: [.03, .08], foot: 0 }, fl: { ankle: [-.02, .08], foot: 0 },
      na: { up: -4, fo: -4 }, fa: { up: -6, fo: -6 } };
    const bot = P(top, { pelvis: [-.23, .85], torso: 72, head: -18, na: { up: -15, fo: -15 }, fa: { up: -17, fo: -17 } });
    return { period: 3.0, track: 'nearHand', props: [{ type: 'dumbbell', hand: 'both' }],
      keys: [[0, top], [.1, top], [.6, bot], [.66, bot], [1, top]] };
  })(),

  kb_swing: (() => {
    const bot = { pelvis: [-.21, .79], torso: 60, head: -22,
      nl: { ankle: [.05, .08], foot: 0 }, fl: { ankle: [-.03, .08], foot: 0 },
      na: { up: -28, fo: -30 }, fa: { up: -30, fo: -32 } };
    const top = { pelvis: [0, .925], torso: -4, head: 0,
      nl: { ankle: [.05, .08], foot: 0 }, fl: { ankle: [-.03, .08], foot: 0 },
      na: { up: 88, fo: 90 }, fa: { up: 86, fo: 88 } };
    return { period: 1.5, track: 'kb', handZ: .05, props: [{ type: 'kettlebell' }],
      keys: [[0, bot], [.45, top], [.55, top], [1, bot]] };
  })(),

  box_jump: (() => {
    const s0 = P(stand(-.28), { na: { up: 0, fo: 5 }, fa: { up: -2, fo: 3 } });
    const load = P(s0, { pelvis: [-.36, .64], torso: 42, head: -15, na: { up: -45, fo: -40 }, fa: { up: -48, fo: -42 } });
    const take = { pelvis: [-.12, 1.04], torso: 12, head: 0,
      nl: { ankle: [-.10, .16], foot: 45 }, fl: { ankle: [-.14, .16], foot: 45 },
      na: { up: 150, fo: 160 }, fa: { up: 146, fo: 156 } };
    const peak = { pelvis: [.28, 1.32], torso: 24, head: -5,
      nl: { ankle: [.42, .78], foot: 15 }, fl: { ankle: [.38, .78], foot: 15 },
      na: { up: 95, fo: 110 }, fa: { up: 92, fo: 105 } };
    const land = { pelvis: [.42, 1.0], torso: 38, head: -12,
      nl: { ankle: [.57, .58], foot: 0 }, fl: { ankle: [.53, .58], foot: 0 },
      na: { up: 60, fo: 75 }, fa: { up: 56, fo: 70 } };
    const up = { pelvis: [.53, 1.42], torso: 2, head: 0,
      nl: { ankle: [.57, .58], foot: 0 }, fl: { ankle: [.53, .58], foot: 0 },
      na: { up: 2, fo: 8 }, fa: { up: -2, fo: 4 } };
    const step = P(up, { pelvis: [.22, 1.18], torso: 6, nl: { ankle: [-.06, .2], foot: 10 } });
    return { period: 3.4, track: 'pelvis', props: [{ type: 'box', x: .55, w: .5, h: .5 }],
      keys: [[0, s0], [.18, load], [.3, take], [.4, peak], [.5, land], [.62, up], [.74, up], [.88, step], [1, s0]] };
  })(),

  calf_raise: (() => {
    const down = { pelvis: [0, .92], torso: 3, head: 0,
      nl: { ankle: [.02, .08], foot: 0 }, fl: { ankle: [-.30, .40], foot: 60 },
      na: { up: -2, fo: -2 }, fa: { up: 4, fo: 10 } };
    const up = P(down, { pelvis: [.07, 1.05], nl: { ankle: [.09, .21], foot: 48 }, fl: { ankle: [-.24, .52], foot: 60 } });
    return { period: 2.6, track: 'nearAnkle', props: [{ type: 'dumbbell', hand: 'near' }],
      keys: [[0, down], [.22, up], [.36, up], [.92, down], [1, down]] };
  })(),

  thruster: (() => {
    const bot = { pelvis: [-.16, .52], torso: 28, head: -10,
      nl: { ankle: [.05, .08], foot: 0 }, fl: { ankle: [-.02, .08], foot: 0 },
      na: { up: 35, fo: 170 }, fa: { up: 32, fo: 168 } };
    const top = { pelvis: [0, .93], torso: 0, head: -4,
      nl: { ankle: [.05, .08], foot: 0 }, fl: { ankle: [-.02, .08], foot: 0 },
      na: { up: 176, fo: 178 }, fa: { up: 174, fo: 176 } };
    return { period: 2.0, track: 'nearHand', props: [{ type: 'dumbbell', hand: 'both' }],
      keys: [[0, top], [.4, bot], [.5, bot], [.8, top], [1, top]] };
  })(),
};

export function sampleMove(move, t) {
  const ph = ((t / move.period) % 1 + 1) % 1;
  const k = move.keys;
  let i = 0;
  while (i < k.length - 2 && ph > k[i + 1][0]) i++;
  const [t0, p0] = k[i], [t1, p1] = k[i + 1];
  const u = t1 > t0 ? clamp((ph - t0) / (t1 - t0), 0, 1) : 0;
  return lerpDeep(p0, p1, move.curve === 'linear' ? u : ease(u));
}

// ── rig ────────────────────────────────────────────────────────────────────────
export function solve(p) {
  const up = [Math.sin(rad(p.torso)), Math.cos(rad(p.torso))];
  const fwd = [up[1], -up[0]];
  const hip = p.pelvis;
  const shoulder = add(add(hip, mul(up, .465)), mul(fwd, -.015));
  const neck = add(hip, mul(up, .52));
  const leg = l => {
    const { knee, end } = ik(hip, l.ankle, L.thigh, L.shin, true);
    return { hip, knee, ankle: end, toe: add(end, mul(dirFoot(l.foot), L.foot)), heel: add(end, mul(dirFoot(l.foot), -.055)), footDeg: l.foot };
  };
  const arm = a => {
    const elbow = add(shoulder, mul(dirDown(a.up), L.upper));
    const wrist = add(elbow, mul(dirDown(a.fo), L.fore));
    return { shoulder, elbow, wrist, hand: add(wrist, mul(dirDown(a.fo), .055)), foreDir: dirDown(a.fo) };
  };
  return { up, fwd, hip, shoulder, neck, torso: p.torso, head: p.head || 0,
    nl: leg(p.nl), fl: leg(p.fl), na: arm(p.na), fa: arm(p.fa) };
}

