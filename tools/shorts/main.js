// Frame loop: pose the mannequin for time t, render the 3D studio, then draw overlays.
import { MOVES, sampleMove, solve, clamp, easeOut } from './rig.js';
import * as S3 from './scene3d.js';
import * as O from './overlay.js';

const out = document.getElementById('out');
const ctx = out.getContext('2d');
const icon = new Image();
const badge = new Image();
const emblem = new Image();
let TL = null;

const NONE = { quads: 0, hamstrings: 0, glutes: 0, calves: 0, core: 0, chest: 0, back: 0, shoulders: 0, arms: 0 };

window.setup = async tl => {
  TL = tl;
  icon.src = '/icon.png'; await icon.decode();
  badge.src = '/badge.png'; await badge.decode().catch(() => {});
  emblem.src = '/emblem.png'; await emblem.decode().catch(() => {});
  const screens = {};
  for (const [k, f] of [['scan', 1], ['physique', 2], ['privacy', 3], ['training', 4], ['nutrition', 5]]) {
    const im = new Image(); im.src = `/screen${f}.png`; await im.decode().catch(() => {}); screens[k] = im;
  }
  O.bindScreens(screens);
  for (const w of [600, 700, 800]) await document.fonts.load(`${w} 40px J`);
  await document.fonts.load('40px Anton');
  for (const w of [500, 600, 700]) await document.fonts.load(`${w} 40px Oswald`);
  O.bind(ctx, icon, badge, emblem);
  S3.initTextures();
  await S3.initHuman();
  return true;
};

function frame(t, opts = {}) {
  const T = TL;
  const scene = T.scenes.find(s => t >= s.start && t < s.end) || T.scenes[T.scenes.length - 1];
  const local = t - scene.start, dur = scene.end - scene.start;
  const move = MOVES[scene.move || 'idle'];
  const c = scene.camera || {};
  // slow orbit and push-in through the scene, with a small punch on the cut
  const u = local / dur;
  const punch = local < .3 ? .5 * (1 - easeOut(local / .3)) : 0;
  S3.setCamera({ ...c, az: (c.az ?? 58) + (c.orbit ?? 10) * (u - .5), dist: (c.dist ?? 5.6) * (1 - .06 * u) + punch }, t);

  const R = solve(sampleMove(move, local));
  const hl = { ...NONE };
  for (const m of scene.muscleKeys || []) hl[m] = 1;
  const ph = (local / move.period) % 1;
  const pulse = .5 + .5 * Math.sin(ph * Math.PI * 2 - Math.PI / 2);
  const { kbPos } = S3.applyPose(R, move, hl, pulse);
  if (move.scroll) S3.scrollFloor(-local * move.scroll * .25);
  if (opts.skip3d) return;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  if (opts.bg) ctx.drawImage(opts.bg, 0, 0, 1080, 1920);
  else { S3.render(); ctx.drawImage(S3.renderer.domElement, 0, 0); }

  if (move.track) {
    const pick = (R, kbp) => move.track === 'pelvis' ? [R.hip[0], R.hip[1], 0]
      : move.track === 'nearHand' ? [R.na.hand[0], R.na.hand[1], .2]
      : move.track === 'nearAnkle' ? [R.nl.ankle[0], R.nl.ankle[1], .1]
      : kbp ? [kbp.x, kbp.y, kbp.z] : [R.na.hand[0], R.na.hand[1], 0];
    const dt = 1 / 30;
    const R1 = solve(sampleMove(move, local + dt));
    const kb1 = move.track === 'kb' ? (() => { const h = [(R1.na.hand[0] + R1.fa.hand[0]) / 2, (R1.na.hand[1] + R1.fa.hand[1]) / 2]; return { x: h[0] + R1.na.foreDir[0] * .17, y: h[1] + R1.na.foreDir[1] * .17, z: 0 }; })() : null;
    const a = S3.project(pick(R, kbPos)), b = S3.project(pick(R1, kb1));
    O.drawMotionArrow(a, [(b[0] - a[0]) / dt, (b[1] - a[1]) / dt], clamp(local / .6, 0, 1), t);
  }

  O.drawProgress(T, t);
  if (scene.kind === 'exercise') O.drawTitle(scene, local);
  if (scene.kind === 'hook') O.drawHook(scene, local);
  if (scene.kind === 'cta') O.drawCTA(scene, local, T);
  if (scene.kind !== 'cta' || local < .4) O.drawCaption(T.words, t);
  if (local < .18 && scene.start > 0) O.drawFlash(.35 * (1 - local / .18));
}

window.draw = (t, mime = 'image/jpeg') => { frame(t); return out.toDataURL(mime, .94); };

// Pose the scene for time t (no 3D render) and return it for the offline renderer.
window.exportFrame = t => { posed = true; frame(t, { skip3d: true }); return S3.exportState(); };
let posed = false;

// Overlays for time t drawn over an offline-rendered background image.
window.drawOver = async (t, bgUrl) => {
  const img = new Image(); img.src = bgUrl; await img.decode();
  frame(t, { bg: img });
  return out.toDataURL('image/png');
};
