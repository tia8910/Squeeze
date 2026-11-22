// 2D overlays drawn over the 3D frame: titles, dose pill, captions, progress, hook, CTA.
import { clamp, lerp, easeOut, ccw, cw, norm, len } from './rig.js';

const W = 1080, H = 1920;
const C = { navy: '#081C45', blue: '#1768FF', sky: '#8DB3FF', ice: '#DDE8FF', hot: '#FF4D5E', white: '#FFFFFF' };
let ctx, icon;
export function bind(context, iconImage) { ctx = context; icon = iconImage; }

// ── text overlays ──────────────────────────────────────────────────────────────
function spaced(text, x, y, spacing) {
  // canvas letterSpacing is supported in Chromium
  ctx.letterSpacing = spacing + 'px';
  ctx.fillText(text, x, y);
  ctx.letterSpacing = '0px';
}
function fitFont(text, weight, size, maxW, spacing = 0) {
  let s = size;
  do { ctx.font = `${weight} ${s}px J`; ctx.letterSpacing = spacing + 'px'; s -= 2; }
  while (ctx.measureText(text).width > maxW && s > 20);
  ctx.letterSpacing = '0px';
  return s + 2;
}

export function drawTitle(scene, local) {
  const a = easeOut(clamp(local / .45, 0, 1));
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  ctx.globalAlpha = a;
  ctx.translate(0, (1 - a) * -40);
  ctx.textAlign = 'center'; ctx.textBaseline = 'alphabetic';
  if (scene.kicker) {
    ctx.font = '700 34px J'; ctx.fillStyle = C.sky;
    spaced(scene.kicker.toUpperCase(), 540, 205, 8);
  }
  const title = scene.title.toUpperCase();
  fitFont(title, 800, 84, 960, 1);
  ctx.shadowColor = C.blue; ctx.shadowBlur = 40; ctx.fillStyle = C.white;
  spaced(title, 540, 300, 1);
  ctx.shadowBlur = 0;
  if (scene.dose) {
    const dose = scene.dose.toUpperCase();
    ctx.font = '800 44px J';
    const w = ctx.measureText(dose).width + 64;
    ctx.fillStyle = 'rgba(23,104,255,.9)';
    ctx.beginPath(); ctx.roundRect(540 - w / 2, 334, w, 70, 35); ctx.fill();
    ctx.fillStyle = C.white; ctx.fillText(dose, 540, 385);
  }
  if (scene.muscles && scene.muscles.length) {
    ctx.font = '700 38px J'; ctx.fillStyle = '#ff8592';
    spaced(scene.muscles.map(m => m.toUpperCase()).join('  ·  '), 540, 460, 3);
  }
  ctx.restore();
}

export function drawCaption(words, t) {
  let cur = null;
  for (const w of words) if (w.start <= t && t < w.end + .25) cur = w;
  if (!cur) return;
  const age = t - cur.start;
  const pop = age < .12 ? lerp(1.28, 1, easeOut(age / .12)) : 1;
  const text = cur.text.toUpperCase();
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  ctx.translate(540, 1270);
  ctx.scale(pop, pop);
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  const size = fitFont(text, 800, 104, 900);
  ctx.font = `800 ${size}px J`;
  ctx.lineJoin = 'round';
  ctx.lineWidth = 18; ctx.strokeStyle = '#020713';
  ctx.shadowColor = 'rgba(0,0,0,.6)'; ctx.shadowBlur = 30; ctx.shadowOffsetY = 8;
  ctx.strokeText(text, 0, 0);
  ctx.shadowBlur = 0; ctx.shadowOffsetY = 0;
  ctx.fillStyle = cur.emph ? C.hot : C.white;
  ctx.fillText(text, 0, 0);
  ctx.restore();
}

export function drawProgress(T, t) {
  const ex = T.scenes.filter(s => s.kind === 'exercise');
  if (!ex.length) return;
  const n = ex.length, gap = 14, w = 600, segW = (w - gap * (n - 1)) / n, x0 = 540 - w / 2, y = 108;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ex.forEach((s, i) => {
    const f = clamp((t - s.start) / (s.end - s.start), 0, 1);
    ctx.fillStyle = 'rgba(141,179,255,.25)';
    ctx.beginPath(); ctx.roundRect(x0 + i * (segW + gap), y, segW, 10, 5); ctx.fill();
    if (f > 0) {
      ctx.fillStyle = C.sky; ctx.shadowColor = C.blue; ctx.shadowBlur = 12;
      ctx.beginPath(); ctx.roundRect(x0 + i * (segW + gap), y, segW * f, 10, 5); ctx.fill();
      ctx.shadowBlur = 0;
    }
  });
}

export function drawHook(scene, local) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  ctx.textAlign = 'center';
  const lines = scene.headline;
  lines.forEach((ln, i) => {
    const a = easeOut(clamp((local - i * .18) / .4, 0, 1));
    ctx.globalAlpha = a;
    const size = fitFont(ln.text.toUpperCase(), 800, ln.size || 110, 980);
    ctx.font = `800 ${size}px J`;
    ctx.fillStyle = ln.color === 'hot' ? C.hot : ln.color === 'sky' ? C.sky : C.white;
    ctx.shadowColor = ln.color === 'hot' ? 'rgba(255,77,94,.7)' : C.blue; ctx.shadowBlur = 40;
    ctx.fillText(ln.text.toUpperCase(), 540 + (1 - a) * 60 * (i % 2 ? 1 : -1), 250 + i * (size + 14));
  });
  ctx.restore();
}

export function drawCTA(scene, local, T) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  // veil over the stage
  ctx.fillStyle = `rgba(4,11,31,${.78 * easeOut(clamp(local / .5, 0, 1))})`;
  ctx.fillRect(0, 0, W, H);
  const a = easeOut(clamp((local - .2) / .5, 0, 1));
  ctx.globalAlpha = a;
  ctx.translate(0, (1 - a) * 50);
  const s = 300;
  ctx.shadowColor = C.blue; ctx.shadowBlur = 90;
  ctx.save();
  ctx.beginPath(); ctx.roundRect(540 - s / 2, 430, s, s, 72); ctx.clip();
  if (icon.complete) ctx.drawImage(icon, 540 - s / 2, 430, s, s);
  ctx.restore();
  ctx.shadowBlur = 0;
  ctx.textAlign = 'center';
  ctx.fillStyle = C.white; ctx.font = '800 96px J';
  ctx.fillText(scene.title, 540, 870);
  ctx.fillStyle = C.ice;
  (scene.lines || []).forEach((ln, i) => {
    fitFont(ln, 600, 46, 920); ctx.fillText(ln, 540, 960 + i * 64);
  });
  const pillY = 1120 + ((scene.lines || []).length - 2) * 64;
  const pulse = 1 + .03 * Math.sin(local * 6);
  ctx.translate(540, pillY + 50); ctx.scale(pulse, pulse);
  ctx.font = '800 48px J';
  const label = scene.button || 'Free on Google Play';
  const w = ctx.measureText(label).width + 110;
  ctx.fillStyle = C.blue; ctx.shadowColor = C.blue; ctx.shadowBlur = 50;
  ctx.beginPath(); ctx.roundRect(-w / 2, -55, w, 110, 55); ctx.fill();
  ctx.shadowBlur = 0; ctx.fillStyle = C.white; ctx.textBaseline = 'middle';
  ctx.fillText(label, 0, 2);
  ctx.restore();
}


// Dashed arrow in screen space beside a moving point; v is its screen velocity (px/s).
export function drawMotionArrow(p, v, alpha, t) {
  const sp = len(v);
  if (sp < 40 || alpha <= 0) return;
  const d = norm(v);
  const side = d[0] >= 0 ? ccw(d) : cw(d);
  const o = [p[0] + side[0] * 150, p[1] + side[1] * 150];
  const ax = o[0] - d[0] * 95, ay = o[1] - d[1] * 95, bx = o[0] + d[0] * 95, by = o[1] + d[1] * 95;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  ctx.globalAlpha = alpha * clamp(sp / 350, .3, 1);
  ctx.strokeStyle = C.ice; ctx.fillStyle = C.ice; ctx.lineWidth = 10; ctx.lineCap = 'round';
  ctx.shadowColor = C.blue; ctx.shadowBlur = 28;
  ctx.setLineDash([24, 18]); ctx.lineDashOffset = -t * 120;
  ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(bx, by); ctx.stroke();
  ctx.setLineDash([]);
  ctx.translate(bx, by); ctx.rotate(Math.atan2(by - ay, bx - ax));
  ctx.beginPath(); ctx.moveTo(22, 0); ctx.lineTo(-18, -24); ctx.lineTo(-18, 24); ctx.closePath(); ctx.fill();
  ctx.restore();
}

export function drawFlash(a) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.fillStyle = `rgba(200,220,255,${a})`;
  ctx.fillRect(0, 0, W, H);
}
