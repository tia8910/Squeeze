// The 3D studio: a sculpted ceramic mannequin, gym props and a lit stage, rendered
// with physically based materials, soft shadows and bloom.
import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { loadHuman } from './human.js';

const W = 1080, H = 1920;
const BLUE = 0x1768ff;

// ── renderer + post ─────────────────────────────────────────────────────────────
export const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(1);
renderer.setSize(W, H);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = .85;
renderer.outputColorSpace = THREE.SRGBColorSpace;

export const scene = new THREE.Scene();
export const camera = new THREE.PerspectiveCamera(30, W / H, .1, 60);

const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), .04).texture;
scene.environmentIntensity = .16;

const rt = new THREE.WebGLRenderTarget(W, H, { type: THREE.HalfFloatType, samples: 4 });
const composer = new EffectComposer(renderer, rt);
composer.addPass(new RenderPass(scene, camera));
const bloom = new UnrealBloomPass(new THREE.Vector2(W / 2, H / 2), .9, .5, 1.6);
composer.addPass(bloom);
composer.addPass(new OutputPass());

// ── backdrop: navy gradient with a soft spotlight, fog into the distance ───────
function canvasTex(w, h, paint, srgb = true) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  paint(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c);
  if (srgb) t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  return t;
}
scene.background = canvasTex(540, 960, (g, w, h) => {
  const lg = g.createLinearGradient(0, 0, 0, h);
  lg.addColorStop(0, '#040c22'); lg.addColorStop(.42, '#0a1d52'); lg.addColorStop(.62, '#050f2c'); lg.addColorStop(1, '#050f2c');
  g.fillStyle = lg; g.fillRect(0, 0, w, h);
  const rg = g.createRadialGradient(w / 2, h * .36, 10, w / 2, h * .36, w * .9);
  rg.addColorStop(0, 'rgba(60,120,255,.55)'); rg.addColorStop(1, 'rgba(23,104,255,0)');
  g.fillStyle = rg; g.fillRect(0, 0, w, h);
});
scene.fog = new THREE.Fog(0x050f2c, 5.5, 13);

// ── lights ──────────────────────────────────────────────────────────────────────
scene.add(new THREE.HemisphereLight(0x9cb8ff, 0x050b1c, .12));
const key = new THREE.DirectionalLight(0xfff4ea, 3.2);
key.position.set(.6, 5, 3.4);   // high and to the side: rakes across the muscles
key.castShadow = true;
key.shadow.mapSize.set(2048, 2048);
key.shadow.camera.left = -1.6; key.shadow.camera.right = 1.6;
key.shadow.camera.top = 2.6; key.shadow.camera.bottom = -.6;
key.shadow.camera.near = 1; key.shadow.camera.far = 12;
key.shadow.bias = -.0005; key.shadow.normalBias = .02;
scene.add(key);
const rimA = new THREE.SpotLight(0x2f6dff, 160, 12, .5, .5, 1.4);
rimA.position.set(-2.6, 3.2, -2.4);
const rimB = new THREE.SpotLight(0x5a8dff, 130, 12, .5, .5, 1.4);
rimB.position.set(2.2, 2.8, -2.8);
const fill = new THREE.PointLight(0xc6d6ff, 1.2, 8, 1.6);
fill.position.set(-1.5, 1.4, 2.4);
for (const l of [rimA, rimB]) { l.target.position.set(0, 1, 0); scene.add(l, l.target); }
scene.add(fill);

// ── stage ───────────────────────────────────────────────────────────────────────
const floorTex = canvasTex(1024, 1024, (g, w, h) => {
  g.fillStyle = '#0a1b48'; g.fillRect(0, 0, w, h);
  g.strokeStyle = 'rgba(120,165,255,.07)'; g.lineWidth = 3;
  for (let i = 0; i <= 16; i++) { const p = i * w / 16; g.beginPath(); g.moveTo(p, 0); g.lineTo(p, h); g.stroke(); g.beginPath(); g.moveTo(0, p); g.lineTo(w, p); g.stroke(); }
});
floorTex.wrapS = floorTex.wrapT = THREE.RepeatWrapping;
floorTex.repeat.set(6, 6);
const floor = new THREE.Mesh(new THREE.PlaneGeometry(40, 40),
  new THREE.MeshStandardMaterial({ map: floorTex, roughness: .42, metalness: .45, color: 0x2a3a66 }));
floor.rotation.x = -Math.PI / 2;
floor.receiveShadow = true;
scene.add(floor);
export function scrollFloor(x) { floorTex.offset.x = x / (40 / 6); }

function paintMat() {
  return canvasTex(1024, 1024, (g, w, h) => {
  const rg = g.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2);
  rg.addColorStop(0, '#0b1530'); rg.addColorStop(1, '#050b1a');
  g.fillStyle = rg; g.fillRect(0, 0, w, h);
  g.strokeStyle = 'rgba(141,179,255,.35)'; g.lineWidth = 6;
  g.beginPath(); g.arc(w / 2, h / 2, w * .44, 0, Math.PI * 2); g.stroke();
  g.fillStyle = 'rgba(141,179,255,.30)'; g.font = '800 120px J'; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.letterSpacing = '18px';
  g.fillText('SQUEEZE', w / 2, h * .8);
});
}
const matTop = new THREE.MeshStandardMaterial({ roughness: .5, metalness: .25 });
export function initTextures() { matTop.map = paintMat(); matTop.needsUpdate = true; }
const stage = new THREE.Group();
const disc = new THREE.Mesh(new THREE.CylinderGeometry(1.25, 1.28, .05, 96),
  [new THREE.MeshStandardMaterial({ color: 0x0b1430, roughness: .5, metalness: .3 }),
    matTop,
    new THREE.MeshStandardMaterial({ color: 0x0b1430 })]);
disc.position.y = .025 - .05;   // flush with the floor: feet stand on y = 0
disc.receiveShadow = true;
disc.rotation.y = -Math.PI * .32;  // wordmark reads from the default camera angle
stage.add(disc);
const ring = new THREE.Mesh(new THREE.TorusGeometry(1.27, .012, 12, 160),
  new THREE.MeshBasicMaterial({ color: new THREE.Color(0x4c8cff).multiplyScalar(2.2) }));
ring.rotation.x = Math.PI / 2; ring.position.y = .003;
stage.add(ring);
scene.add(stage);

// ── materials ───────────────────────────────────────────────────────────────────
const darkMetal = new THREE.MeshStandardMaterial({ color: 0x1a2236, roughness: .28, metalness: .85 });
const rubber = new THREE.MeshStandardMaterial({ color: 0x0c111d, roughness: .55, metalness: .2 });
const chrome = new THREE.MeshStandardMaterial({ color: 0xc8d2e6, roughness: .12, metalness: 1 });
const neon = new THREE.MeshBasicMaterial({ color: new THREE.Color(0x4f8dff).multiplyScalar(4) });

// ── helpers ─────────────────────────────────────────────────────────────────────
const sphere = new THREE.SphereGeometry(1, 48, 32);
function mesh(geo, mat) {
  const m = new THREE.Mesh(geo, mat);
  m.castShadow = true; m.receiveShadow = true;
  return m;
}

// ── the human ───────────────────────────────────────────────────────────────────
let human = null;
export async function initHuman() { human = await loadHuman(scene); }

// ── props ───────────────────────────────────────────────────────────────────────
function dumbbell() {
  const g = new THREE.Group();
  const handle = mesh(new THREE.CylinderGeometry(.016, .016, .16, 24), chrome);
  handle.rotation.x = Math.PI / 2; g.add(handle);
  for (const z of [-.11, .11]) {
    const plate = mesh(new THREE.CylinderGeometry(.075, .075, .07, 6), rubber);
    plate.rotation.x = Math.PI / 2; plate.rotation.y = Math.PI / 6; plate.position.z = z; g.add(plate);
    const glow = new THREE.Mesh(new THREE.TorusGeometry(.069, .005, 8, 6), neon);
    glow.position.z = z + Math.sign(z) * .036; glow.rotation.z = Math.PI / 6; g.add(glow);
  }
  scene.add(g); g.visible = false;
  return g;
}
const dbs = { n: dumbbell(), f: dumbbell() };

const kb = new THREE.Group();
{
  const bell = mesh(sphere, rubber); bell.scale.set(.11, .1, .11); bell.position.y = -.17; kb.add(bell);
  const base = mesh(new THREE.CylinderGeometry(.07, .07, .02, 32), rubber); base.position.y = -.265; kb.add(base);
  const handle = mesh(new THREE.TorusGeometry(.06, .015, 16, 32, Math.PI), darkMetal);
  handle.rotation.z = Math.PI; handle.rotation.y = Math.PI / 2; handle.position.y = -.03; kb.add(handle);
  const band = new THREE.Mesh(new THREE.TorusGeometry(.111, .006, 8, 48), neon);
  band.rotation.x = Math.PI / 2; band.position.y = -.17; kb.add(band);
  kb.visible = false; scene.add(kb);
}

const bench = new THREE.Group();
{
  const pad = mesh(new RoundedBoxGeometry(1, .08, .3, 4, .03), new THREE.MeshPhysicalMaterial({ color: 0x0d1424, roughness: .45, clearcoat: .6 }));
  pad.position.y = -.04; bench.add(pad);
  const edge = new THREE.Mesh(new THREE.BoxGeometry(.98, .008, .008), neon); edge.position.set(0, -.002, .15); bench.add(edge);
  for (const x of [-.38, .38]) {
    const leg = mesh(new THREE.BoxGeometry(.05, 1, .26), darkMetal); leg.position.x = x; leg.name = 'leg'; bench.add(leg);
  }
  bench.visible = false; scene.add(bench);
}

const box = new THREE.Group();
{
  const body = mesh(new RoundedBoxGeometry(1, 1, 1, 4, .03), new THREE.MeshPhysicalMaterial({ color: 0x111a2e, roughness: .5, clearcoat: .5 }));
  box.add(body);
  for (const [x, y, z, sx, sy, sz] of [[.5, 0, .5, .01, 1, .01], [-.5, 0, .5, .01, 1, .01], [0, .5, .5, 1, .01, .01], [.5, .5, 0, .01, .01, 1], [-.5, .5, 0, .01, .01, 1], [0, .5, -.5, 1, .01, .01]]) {
    const s = new THREE.Mesh(new THREE.BoxGeometry(sx * (sx > .5 ? 1 : 1), sy, sz), neon);
    s.position.set(x, y, z); box.add(s);
  }
  box.visible = false; scene.add(box);
}

// ── pose application ───────────────────────────────────────────────────────────
export function applyPose(R, move, hl, pulse) {
  human.pose(R, move);
  human.setGlow(hl, pulse);

  const props = move.props || [];
  const has = t => props.find(p => p.type === t);
  const db = has('dumbbell');
  dbs.n.visible = !!db; dbs.f.visible = !!db && db.hand === 'both';
  for (const k of ['n', 'f']) {
    const h = human.hand(k);
    dbs[k].position.copy(h.pos);
    dbs[k].rotation.set(0, 0, Math.atan2(h.dir.y, h.dir.x) + Math.PI / 2);
  }
  let kbPos = null;
  kb.visible = !!has('kettlebell');
  if (kb.visible) {
    const a = human.hand('n'), b = human.hand('f');
    const p = a.pos.clone().add(b.pos).multiplyScalar(.5), d = a.dir.clone().add(b.dir).normalize();
    kb.position.copy(p);
    kb.rotation.set(0, 0, Math.atan2(d.y, d.x) + Math.PI / 2);
    kb.updateMatrixWorld();
    kbPos = new THREE.Vector3(0, -.17, 0).applyMatrix4(kb.matrixWorld);
  }
  const b = has('bench');
  bench.visible = !!b;
  if (b) {
    bench.position.set(b.x, b.h, -.1);
    bench.scale.set(b.w, 1, 1);
    for (const c of bench.children) if (c.name === 'leg') { c.scale.y = b.h - .08; c.position.y = -.08 - (b.h - .08) / 2; }
  }
  const bx = has('box');
  box.visible = !!bx;
  if (bx) { box.position.set(bx.x, bx.h / 2, 0); box.scale.set(bx.w, bx.h, .62); }
  return { kbPos };
}

// ── camera + frame ─────────────────────────────────────────────────────────────
export function setCamera(c, t) {
  const az = (c.az ?? 58) * Math.PI / 180, dist = c.dist ?? 5.6, h = c.h ?? 1.35;
  const tx = c.x ?? .05, ty = c.y ?? .78;
  camera.fov = c.fov ?? 30;
  camera.position.set(tx + Math.cos(az) * dist, h, Math.sin(az) * dist);
  camera.lookAt(tx, ty, 0);
  camera.updateProjectionMatrix();
  stage.position.x = 0;
}

export function project(v) {
  const p = (v.isVector3 ? v : new THREE.Vector3(v[0], v[1], v[2] ?? 0)).clone().project(camera);
  return [(p.x + 1) / 2 * W, (1 - p.y) / 2 * H];
}

export function render() { composer.render(); }
export { THREE };
