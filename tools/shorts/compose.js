// Overlay-only frames for footage-based shorts: titles, dose and muscle tags, captions,
// progress, hook and CTA cards, drawn on a transparent canvas to be laid over the clips.
import * as O from './overlay.js';

const out = document.getElementById('out');
const ctx = out.getContext('2d');
const icon = new Image();
let TL = null;

window.setup = async tl => {
  TL = tl;
  icon.src = '/icon.png'; await icon.decode();
  for (const w of [600, 700, 800]) await document.fonts.load(`${w} 40px J`);
  await document.fonts.load('40px Anton');
  for (const w of [500, 600, 700]) await document.fonts.load(`${w} 40px Oswald`);
  O.bind(ctx, icon);
  return true;
};

window.draw = t => {
  const T = TL;
  const scene = T.scenes.find(s => t >= s.start && t < s.end) || T.scenes[T.scenes.length - 1];
  const local = t - scene.start;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, 1080, 1920);
  if (!T.panel) {
    // full-bleed footage: a soft top and bottom shade so white type reads over it
    const g = ctx.createLinearGradient(0, 0, 0, 1920);
    g.addColorStop(0, 'rgba(3,9,26,.62)'); g.addColorStop(.3, 'rgba(3,9,26,0)');
    g.addColorStop(.62, 'rgba(3,9,26,0)'); g.addColorStop(1, 'rgba(3,9,26,.5)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, 1080, 1920);
  }
  O.drawProgress(T, t);
  if (scene.kind === 'exercise') { O.drawTitle(scene, local); O.drawReps(scene, local, ...(T.repsAt || [905, 560])); }
  if (T.panel && scene.kind !== 'cta') O.drawBrand(...(T.brandAt || [70, 1430]));
  if (scene.kind === 'hook') O.drawHook(scene, local);
  if (scene.kind === 'cta') O.drawCTA(scene, local);
  if (scene.kind !== 'cta' || local < .4) O.drawCaption(T.words, t, T.captionY || 1330);
  if (local < .18 && scene.start > 0) O.drawFlash(.3 * (1 - local / .18));
  return out.toDataURL('image/png');
};
