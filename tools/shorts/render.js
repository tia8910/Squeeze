// Renders a timeline with the 3D studio in headless Chromium.
//   node render.js timeline.json video.mp4 [--workers N]   -> silent H.264 video
//   node render.js timeline.json dir --at 1.2,5,9.5         -> PNG stills, for checking poses
const { chromium } = require('playwright');
const { spawn, execFileSync } = require('node:child_process');
const { readFileSync, mkdirSync, writeFileSync, createReadStream, existsSync, rmSync } = require('node:fs');
const http = require('node:http');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '../..');
const MIME = { '.woff2': 'font/woff2', '.html': 'text/html', '.js': 'text/javascript', '.ttf': 'font/ttf', '.png': 'image/png', '.json': 'application/json' };

function serve() {
  const map = url => {
    if (url.startsWith('/font/')) return path.join(ROOT, 'app/src/main/res/font', path.basename(url));
    if (url === '/icon.png') return path.join(ROOT, 'store/icon-512.png');
    const scr = url.match(/^\/screen(\d)\.png$/);
    if (scr) return path.join(ROOT, 'store', ['', 'screenshot-1-scan.png', 'screenshot-2-physique.png', 'screenshot-3-privacy.png', 'screenshot-4-training.png', 'screenshot-5-nutrition.png'][+scr[1]]);
    if (url === '/emblem.png') return path.join(__dirname, 'brand/emblem.png');
    if (url === '/badge.png') return path.join(__dirname, 'brand/google-play-badge.png');
    if (url === '/') return path.join(__dirname, 'index.html');
    const p = path.join(__dirname, path.normalize(url));
    return p.startsWith(__dirname) ? p : null;
  };
  const server = http.createServer((req, res) => {
    const file = map(decodeURIComponent(req.url.split('?')[0]));
    if (!file || !existsSync(file)) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
    createReadStream(file).pipe(res);
  });
  return new Promise(r => server.listen(0, '127.0.0.1', () => r(server)));
}

async function openPage(port, tl, pagePath = '/') {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
  page.on('pageerror', e => console.error('page error:', e.message));
  page.on('console', m => { if (m.type() === 'error') console.error('console:', m.text()); });
  await page.goto(`http://127.0.0.1:${port}${pagePath}`);
  await page.waitForFunction(() => typeof window.setup === 'function');
  await page.evaluate(t => window.setup(t), tl);
  return { browser, page };
}

const decode = url => Buffer.from(url.slice(url.indexOf(',') + 1), 'base64');

async function renderRange(port, tl, from, to, out, label) {
  const fps = tl.fps || 30;
  const { browser, page } = await openPage(port, tl);
  const ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-c:v', 'mjpeg', '-framerate', String(fps), '-i', '-',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-pix_fmt', 'yuv420p', '-r', String(fps), out], { stdio: ['pipe', 'inherit', 'inherit'] });
  const t0 = Date.now();
  for (let i = from; i < to; i++) {
    const buf = decode(await page.evaluate(t => window.draw(t), i / fps));
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    if ((i - from) % 60 === 0) {
      const done = i - from, per = done ? (Date.now() - t0) / done / 1000 : 0;
      console.log(`[${label}] frame ${done}/${to - from}  ${per.toFixed(2)}s/frame`);
    }
  }
  ff.stdin.end();
  await new Promise((res, rej) => ff.on('close', c => (c === 0 ? res() : rej(new Error('ffmpeg exit ' + c)))));
  await browser.close();
}

(async () => {
  const args = process.argv.slice(2);
  const [tlPath, out] = args;
  const opt = k => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : null; };
  const tl = JSON.parse(readFileSync(tlPath, 'utf8'));
  const server = await serve();
  const port = server.address().port;

  if (args.includes('--overlay')) {
    // node render.js timeline.json overlay.mov --overlay   -> transparent overlay video (qtrle)
    const fps = tl.fps || 30, n = Math.ceil(tl.duration * fps);
    const { browser, page } = await openPage(port, tl, '/compose.html');
    await page.evaluate(() => { document.body.style.background = 'transparent'; });
    const ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-c:v', 'png', '-framerate', String(fps), '-i', '-',
      '-c:v', 'qtrle', '-pix_fmt', 'argb', out], { stdio: ['pipe', 'inherit', 'inherit'] });
    for (let i = 0; i < n; i++) {
      const buf = decode(await page.evaluate(t => window.draw(t), i / fps));
      if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
      if (i % 150 === 0) console.log(`overlay frame ${i}/${n}`);
    }
    ff.stdin.end();
    await new Promise((res, rej) => ff.on('close', c => (c === 0 ? res() : rej(new Error('ffmpeg exit ' + c)))));
    await browser.close();
  } else if (opt('--export')) {
    // node render.js timeline.json out.json --export 6.7
    const { browser, page } = await openPage(port, tl);
    writeFileSync(out, JSON.stringify(await page.evaluate(t => window.exportFrame(t), Number(opt('--export')))));
    await browser.close();
  } else if (opt('--over')) {
    // node render.js timeline.json out.png --over 6.7 --bg cycles.png
    const { browser, page } = await openPage(port, tl);
    const bg = 'data:image/png;base64,' + readFileSync(opt('--bg')).toString('base64');
    writeFileSync(out, decode(await page.evaluate(([t, b]) => window.drawOver(t, b), [Number(opt('--over')), bg])));
    await browser.close();
  } else if (opt('--at')) {
    mkdirSync(out, { recursive: true });
    const { browser, page } = await openPage(port, tl);
    for (const t of opt('--at').split(',').map(Number)) {
      writeFileSync(path.join(out, `t${t.toFixed(2).padStart(6, '0')}.png`), decode(await page.evaluate(t => window.draw(t, 'image/png'), t)));
    }
    await browser.close();
  } else {
    const fps = tl.fps || 30, n = Math.ceil(tl.duration * fps);
    const workers = Math.max(1, Number(opt('--workers') || 1));
    const parts = [];
    const jobs = [];
    for (let w = 0; w < workers; w++) {
      const from = Math.floor(n * w / workers), to = Math.floor(n * (w + 1) / workers);
      const part = `${out}.part${w}.mp4`;
      parts.push(part);
      jobs.push(renderRange(port, tl, from, to, part, `w${w}`));
    }
    await Promise.all(jobs);
    const list = `${out}.parts.txt`;
    writeFileSync(list, parts.map(p => `file '${path.resolve(p)}'`).join('\n'));
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', list, '-c', 'copy', out]);
    for (const p of [...parts, list]) rmSync(p);
  }
  server.close();
})().catch(e => { console.error(e); process.exit(1); });
