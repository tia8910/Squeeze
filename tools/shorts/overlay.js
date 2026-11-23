// 2D overlays drawn over the 3D frame: titles, dose, muscle tags, captions, progress,
// hook and CTA. Display type is Anton, labels Oswald, brand copy Plus Jakarta Sans (J).
import { clamp, lerp, easeOut, ccw, cw, norm, len } from './rig.js';

const W = 1080, H = 1920;
const C = { blue: '#1768FF', sky: '#8DB3FF', ice: '#DDE8FF', hot: '#FF4D5E', white: '#FFFFFF', ink: '#030a1c' };
let ctx, icon, badge, emblem;
export function bind(context, iconImage, badgeImage, emblemImage) { ctx = context; icon = iconImage; badge = badgeImage; emblem = emblemImage; }

function font(f, text, maxW, spacing = 0) {
  // f like "120px Anton": shrinks the size until text fits maxW
  let [size, ...rest] = f.split(' ');
  let s = parseInt(size);
  const pre = f.match(/^\d+\s/) ? '' : '';
  const weight = isNaN(parseInt(f)) ? f.split(' ')[0] + ' ' : '';
  const fam = f.split(' ').slice(-1)[0];
  const base = weight ? parseInt(f.split(' ')[1]) : s;
  let px = base;
  for (; px > 20; px -= 2) {
    ctx.font = `${weight}${px}px ${fam}`; ctx.letterSpacing = spacing + 'px';
    if (ctx.measureText(text).width <= maxW) break;
  }
  return px;
}

function display(text, x, y, px, opts = {}) {
  // Anton headline: soft blue glow, ink outline for legibility, white-to-ice fill
  ctx.save();
  ctx.font = `${px}px Anton`; ctx.letterSpacing = (opts.spacing ?? 1) + 'px';
  ctx.textAlign = 'center'; ctx.textBaseline = 'alphabetic';
  ctx.lineJoin = 'round';
  ctx.shadowColor = opts.glow ?? 'rgba(23,104,255,.75)'; ctx.shadowBlur = 50;
  ctx.lineWidth = px * .09; ctx.strokeStyle = C.ink; ctx.strokeText(text, x, y);
  ctx.shadowBlur = 0;
  const g = ctx.createLinearGradient(0, y - px * .8, 0, y);
  if (opts.color) { g.addColorStop(0, opts.color); g.addColorStop(1, opts.color2 ?? opts.color); }
  else { g.addColorStop(0, '#ffffff'); g.addColorStop(1, '#c8dbff'); }
  ctx.fillStyle = g; ctx.fillText(text, x, y);
  ctx.restore();
}

function chip(x, y, h, label, opts = {}) {
  // returns width; draws centred on x when opts.measure is false
  ctx.font = opts.font ?? `600 ${Math.round(h * .5)}px Oswald`;
  ctx.letterSpacing = (opts.spacing ?? 3) + 'px';
  const pad = h * .55, dot = opts.dot ? h * .42 : 0;
  const w = ctx.measureText(label).width + pad * 2 + dot;
  if (opts.measure) { ctx.letterSpacing = '0px'; return w; }
  const x0 = x - w / 2;
  ctx.save();
  ctx.fillStyle = opts.fill ?? 'rgba(10,24,60,.55)';
  ctx.strokeStyle = opts.stroke ?? 'rgba(141,179,255,.55)'; ctx.lineWidth = 2;
  ctx.shadowColor = 'rgba(0,0,0,.35)'; ctx.shadowBlur = 20;
  ctx.beginPath(); ctx.roundRect(x0, y, w, h, h / 2); ctx.fill(); ctx.shadowBlur = 0; ctx.stroke();
  if (opts.dot) {
    ctx.fillStyle = C.hot; ctx.shadowColor = C.hot; ctx.shadowBlur = 16;
    ctx.beginPath(); ctx.arc(x0 + pad + h * .12, y + h / 2, h * .12, 0, Math.PI * 2); ctx.fill(); ctx.shadowBlur = 0;
  }
  ctx.fillStyle = opts.color ?? C.white; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
  ctx.fillText(label, x0 + pad + dot, y + h / 2 + 1);
  ctx.restore();
  ctx.letterSpacing = '0px';
  return w;
}

export function drawTitle(scene, local) {
  const a = easeOut(clamp(local / .45, 0, 1));
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  ctx.globalAlpha = a;
  ctx.translate(0, (1 - a) * -36);
  if (scene.kicker) {
    ctx.font = '600 32px Oswald'; ctx.letterSpacing = '10px'; ctx.fillStyle = C.sky;
    ctx.textAlign = 'center'; ctx.fillText(scene.kicker.toUpperCase(), 545, 196);
    const w = ctx.measureText(scene.kicker.toUpperCase()).width;
    ctx.letterSpacing = '0px';
    ctx.strokeStyle = 'rgba(141,179,255,.5)'; ctx.lineWidth = 2;
    for (const s of [-1, 1]) { ctx.beginPath(); ctx.moveTo(540 + s * (w / 2 + 80), 185); ctx.lineTo(540 + s * (w / 2 + 30), 185); ctx.stroke(); }
  }
  const title = scene.title.toUpperCase();
  const px = font('128px Anton', title, 980, 1);
  display(title, 540, 210 + px * .92, px);
  const y = 230 + px;
  const items = [];
  if (scene.dose) items.push({ label: scene.dose.toUpperCase(), opts: { fill: 'rgba(23,104,255,.92)', stroke: 'rgba(141,179,255,.9)', font: '700 34px Oswald' } });
  for (const m of scene.muscles || []) items.push({ label: m.toUpperCase(), opts: { dot: true, fill: 'rgba(60,10,25,.45)', stroke: 'rgba(255,77,94,.75)' } });
  const h = 64, gap = 14;
  const ws = items.map(it => chip(0, 0, h, it.label, { ...it.opts, measure: true }));
  let x = 540 - (ws.reduce((s, w) => s + w, 0) + gap * (items.length - 1)) / 2;
  items.forEach((it, i) => { chip(x + ws[i] / 2, y, h, it.label, it.opts); x += ws[i] + gap; });
  ctx.restore();
}

export function drawCaption(words, t, y = 1330) {
  let cur = null;
  for (const w of words) if (w.start <= t && t < w.end + .25) cur = w;
  if (!cur) return;
  const age = t - cur.start;
  const pop = age < .14 ? lerp(1.3, 1, easeOut(age / .14)) : 1;
  const text = cur.text.toUpperCase();
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  ctx.translate(540, y);
  ctx.scale(pop, pop);
  ctx.rotate(-.02);
  const px = font('150px Anton', text, 920, 2);
  ctx.font = `${px}px Anton`; ctx.letterSpacing = '2px';
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.lineJoin = 'round';
  ctx.shadowColor = 'rgba(0,0,0,.7)'; ctx.shadowBlur = 36; ctx.shadowOffsetY = 10;
  ctx.lineWidth = px * .16; ctx.strokeStyle = C.ink; ctx.strokeText(text, 0, 0);
  ctx.shadowBlur = 0; ctx.shadowOffsetY = 0;
  ctx.fillStyle = cur.emph ? C.hot : C.white;
  ctx.fillText(text, 0, 0);
  ctx.restore();
}

export function drawProgress(T, t) {
  const ex = T.scenes.filter(s => s.kind === 'exercise');
  if (ex.length < 2) return;
  const n = ex.length, gap = 12, w = 560, segW = (w - gap * (n - 1)) / n, x0 = 540 - w / 2, y = 104;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ex.forEach((s, i) => {
    const f = clamp((t - s.start) / (s.end - s.start), 0, 1);
    ctx.fillStyle = 'rgba(141,179,255,.22)';
    ctx.beginPath(); ctx.roundRect(x0 + i * (segW + gap), y, segW, 8, 4); ctx.fill();
    if (f > 0) {
      ctx.fillStyle = C.white; ctx.shadowColor = C.blue; ctx.shadowBlur = 14;
      ctx.beginPath(); ctx.roundRect(x0 + i * (segW + gap), y, Math.max(8, segW * f), 8, 4); ctx.fill();
      ctx.shadowBlur = 0;
    }
  });
}

export function drawHook(scene, local) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  if (scene.tag) {
    const a = easeOut(clamp(local / .35, 0, 1));
    ctx.globalAlpha = a;
    chip(540, 150, 60, scene.tag.toUpperCase(), { fill: 'rgba(23,104,255,.9)', stroke: 'rgba(141,179,255,.9)', font: '600 28px Oswald', spacing: 5 });
  }
  let y = 250;
  scene.headline.forEach((ln, i) => {
    const a = easeOut(clamp((local - .1 - i * .16) / .4, 0, 1));
    ctx.globalAlpha = a;
    const text = ln.text.toUpperCase();
    if (ln.style === 'label') {
      ctx.font = '600 44px Oswald'; ctx.letterSpacing = '8px'; ctx.textAlign = 'center';
      ctx.fillStyle = C.sky; ctx.fillText(text, 540, y + 50); ctx.letterSpacing = '0px';
      y += 76;
    } else {
      const px = font(`${ln.size || 190}px Anton`, text, 980, 2);
      const hot = ln.color === 'hot';
      display(text, 540 + (1 - a) * 70 * (i % 2 ? 1 : -1), y + px * .9, px,
        hot ? { color: '#ff6b78', color2: '#ff3049', glow: 'rgba(255,60,80,.7)', spacing: 2 } : { spacing: 2 });
      y += px * .98;
    }
  });
  ctx.restore();
}

export function drawCTA(scene, local) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  const v = easeOut(clamp(local / .5, 0, 1));
  const veil = ctx.createLinearGradient(0, 0, 0, H);
  veil.addColorStop(0, `rgba(3,9,26,${.88 * v})`); veil.addColorStop(.62, `rgba(3,9,26,${.8 * v})`); veil.addColorStop(1, `rgba(3,9,26,${.35 * v})`);
  ctx.fillStyle = veil; ctx.fillRect(0, 0, W, H);
  const a = easeOut(clamp((local - .15) / .5, 0, 1));
  ctx.globalAlpha = a;
  ctx.translate(0, (1 - a) * 50);
  const s = 260, iy = 300;
  if (emblem && emblem.naturalWidth) {
    // the app's figure emblem on its own, no tile behind it: a soft brand-blue halo
    const halo = ctx.createRadialGradient(540, iy + s / 2, 10, 540, iy + s / 2, s * .95);
    halo.addColorStop(0, 'rgba(23,104,255,.45)'); halo.addColorStop(1, 'rgba(23,104,255,0)');
    ctx.fillStyle = halo; ctx.fillRect(540 - s, iy - s / 2, s * 2, s * 2);
    ctx.shadowColor = 'rgba(141,179,255,.8)'; ctx.shadowBlur = 40;
    ctx.drawImage(emblem, 540 - s / 2, iy, s, s);
    ctx.shadowBlur = 0;
  } else {
    ctx.shadowColor = 'rgba(23,104,255,.9)'; ctx.shadowBlur = 110;
    ctx.fillStyle = C.blue; ctx.beginPath(); ctx.roundRect(540 - s / 2, iy, s, s, 64); ctx.fill();
    ctx.shadowBlur = 0;
    ctx.save(); ctx.beginPath(); ctx.roundRect(540 - s / 2, iy, s, s, 64); ctx.clip();
    ctx.drawImage(icon, 540 - s / 2, iy, s, s); ctx.restore();
  }
  display('SQUEEZE FIT', 540, iy + s + 150, 120, { spacing: 3 });
  ctx.textAlign = 'center';
  ctx.fillStyle = 'rgba(221,232,255,.82)';
  (scene.lines || []).forEach((ln, i) => {
    const px = font(`500 42px J`, ln, 860);
    ctx.font = `500 ${px}px J`; ctx.fillText(ln, 540, iy + s + 262 + i * 58);
  });
  const by = iy + s + 262 + (scene.lines || []).length * 58 + 70;
  const pulse = 1 + .02 * Math.sin(local * 5);
  ctx.translate(540, by + 75); ctx.scale(pulse, pulse);
  if (badge && badge.naturalWidth) {
    // Google's "Get it on Google Play" badge, at its own proportions
    const bw = 470, bh = bw * badge.naturalHeight / badge.naturalWidth;
    ctx.shadowColor = 'rgba(23,104,255,.55)'; ctx.shadowBlur = 50;
    ctx.drawImage(badge, -bw / 2, -bh / 2, bw, bh);
    ctx.shadowBlur = 0;
  } else {
    const label = (scene.button || 'Free on Google Play').toUpperCase();
    ctx.font = '600 44px Oswald'; ctx.letterSpacing = '4px';
    const w = ctx.measureText(label).width + 120;
    ctx.fillStyle = C.blue; ctx.beginPath(); ctx.roundRect(-w / 2, -55, w, 110, 55); ctx.fill();
    ctx.fillStyle = C.white; ctx.textBaseline = 'middle'; ctx.fillText(label, 0, 3);
  }
  ctx.restore();
  ctx.letterSpacing = '0px';
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

// Rep counter: a ring that fills through the set, the count popping at the bottom of each rep.
export function drawReps(scene, local, cx = 905, cy = 560) {
  const reps = scene.reps || [];
  if (!reps.length) return;
  const n = reps.filter(r => r <= local).length;
  const last = n ? reps[n - 1] : -9;
  const pop = local - last < .22 ? lerp(1.35, 1, easeOut((local - last) / .22)) : 1;
  const a = easeOut(clamp((local - .3) / .4, 0, 1));
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  ctx.globalAlpha = a;
  ctx.translate(cx, cy);
  const r = 78;
  ctx.fillStyle = 'rgba(4,12,34,.72)';
  ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.fill();
  ctx.lineWidth = 10; ctx.strokeStyle = 'rgba(141,179,255,.25)';
  ctx.beginPath(); ctx.arc(0, 0, r - 6, 0, Math.PI * 2); ctx.stroke();
  ctx.strokeStyle = C.blue; ctx.shadowColor = C.blue; ctx.shadowBlur = 20; ctx.lineCap = 'round';
  const total = scene.repTarget || reps.length;
  ctx.beginPath(); ctx.arc(0, 0, r - 6, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Math.min(1, n / total)); ctx.stroke();
  ctx.shadowBlur = 0;
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.save(); ctx.scale(pop, pop);
  ctx.font = '84px Anton'; ctx.fillStyle = C.white; ctx.fillText(String(n), 0, -6);
  ctx.restore();
  ctx.font = '600 22px Oswald'; ctx.letterSpacing = '4px'; ctx.fillStyle = C.sky;
  ctx.fillText('REPS', 2, 44);
  ctx.restore();
  ctx.letterSpacing = '0px';
}

// Small brand mark, bottom-left of the video panel.
export function drawBrand(x = 70, y = 1430) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.save();
  const s = 54;
  ctx.beginPath(); ctx.roundRect(x, y, s, s, 14); ctx.save(); ctx.clip(); ctx.drawImage(icon, x, y, s, s); ctx.restore();
  ctx.font = '600 30px Oswald'; ctx.letterSpacing = '5px'; ctx.fillStyle = 'rgba(255,255,255,.92)';
  ctx.textBaseline = 'middle'; ctx.textAlign = 'left';
  ctx.shadowColor = 'rgba(0,0,0,.6)'; ctx.shadowBlur = 12;
  ctx.fillText('SQUEEZE FIT', x + s + 16, y + s / 2 + 2);
  ctx.restore();
  ctx.letterSpacing = '0px';
}
