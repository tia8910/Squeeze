// The 3D studio: a sculpted ceramic mannequin, gym props and a lit stage, rendered
// with physically based materials, soft shadows and bloom.
import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { L } from './rig.js';

const W = 1080, H = 1920;
const BLUE = 0x1768ff;

// ── renderer + post ─────────────────────────────────────────────────────────────
export const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(1);
renderer.setSize(W, H);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.VSMShadowMap;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = .85;
renderer.outputColorSpace = THREE.SRGBColorSpace;

export const scene = new THREE.Scene();
export const camera = new THREE.PerspectiveCamera(30, W / H, .1, 60);

const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), .04).texture;
scene.environmentIntensity = .3;

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
  lg.addColorStop(0, '#06122f'); lg.addColorStop(.5, '#0a2160'); lg.addColorStop(1, '#030916');
  g.fillStyle = lg; g.fillRect(0, 0, w, h);
  const rg = g.createRadialGradient(w / 2, h * .36, 10, w / 2, h * .36, w * .9);
  rg.addColorStop(0, 'rgba(60,120,255,.55)'); rg.addColorStop(1, 'rgba(23,104,255,0)');
  g.fillStyle = rg; g.fillRect(0, 0, w, h);
});
scene.fog = new THREE.Fog(0x050f2c, 5.5, 13);

// ── lights ──────────────────────────────────────────────────────────────────────
scene.add(new THREE.HemisphereLight(0x9cb8ff, 0x050b1c, .25));
const key = new THREE.DirectionalLight(0xfff6ee, 1.7);
key.position.set(2.5, 4.5, 3.2);
key.castShadow = true;
key.shadow.mapSize.set(2048, 2048);
key.shadow.camera.left = -1.6; key.shadow.camera.right = 1.6;
key.shadow.camera.top = 2.6; key.shadow.camera.bottom = -.6;
key.shadow.camera.near = 1; key.shadow.camera.far = 12;
key.shadow.bias = -.0008; key.shadow.normalBias = .035;
scene.add(key);
const rimA = new THREE.SpotLight(0x2f6dff, 90, 12, .5, .6, 1.4);
rimA.position.set(-2.6, 3.2, -2.4);
const rimB = new THREE.SpotLight(0x4f85ff, 70, 12, .5, .6, 1.4);
rimB.position.set(2.2, 2.8, -2.8);
const fill = new THREE.PointLight(0xc6d6ff, 3, 8, 1.6);
fill.position.set(-1.5, 1.4, 2.4);
for (const l of [rimA, rimB]) { l.target.position.set(0, 1, 0); scene.add(l, l.target); }
scene.add(fill);

// ── stage ───────────────────────────────────────────────────────────────────────
const floorTex = canvasTex(1024, 1024, (g, w, h) => {
  g.fillStyle = '#0a1b48'; g.fillRect(0, 0, w, h);
  g.strokeStyle = 'rgba(120,165,255,.16)'; g.lineWidth = 3;
  for (let i = 0; i <= 16; i++) { const p = i * w / 16; g.beginPath(); g.moveTo(p, 0); g.lineTo(p, h); g.stroke(); g.beginPath(); g.moveTo(0, p); g.lineTo(w, p); g.stroke(); }
});
floorTex.wrapS = floorTex.wrapT = THREE.RepeatWrapping;
floorTex.repeat.set(6, 6);
const floor = new THREE.Mesh(new THREE.PlaneGeometry(40, 40),
  new THREE.MeshStandardMaterial({ map: floorTex, roughness: .28, metalness: .5, color: 0x6f86c8 }));
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
  new THREE.MeshBasicMaterial({ color: new THREE.Color(0x4c8cff).multiplyScalar(3.5) }));
ring.rotation.x = Math.PI / 2; ring.position.y = .003;
stage.add(ring);
scene.add(stage);

// neon bars far behind, for depth and bloom
for (const [x, z, h] of [[-3.6, -4.5, 3.4], [-1.6, -6.8, 4.4], [2.6, -5.5, 3.8], [5.5, -2.5, 3]]) {
  const bar = new THREE.Mesh(new THREE.BoxGeometry(.05, h, .05), new THREE.MeshBasicMaterial({ color: new THREE.Color(0x3f7dff).multiplyScalar(3), fog: false }));
  bar.position.set(x, h / 2 + .3, z);
  scene.add(bar);
}

// ── materials ───────────────────────────────────────────────────────────────────
const ceramic = new THREE.MeshPhysicalMaterial({
  color: 0xd3dbe9, roughness: .34, metalness: 0, clearcoat: .7, clearcoatRoughness: .22, envMapIntensity: 1,
});
const ceramicFar = ceramic;
const fibre = canvasTex(256, 256, (g, w, h) => {
  g.fillStyle = '#ffffff'; g.fillRect(0, 0, w, h);
  for (let i = 0; i < 60; i++) {
    g.strokeStyle = `rgba(120,10,30,${.15 + Math.random() * .25})`; g.lineWidth = 1 + Math.random() * 2.5;
    const x = Math.random() * w;
    g.beginPath(); g.moveTo(x, 0); g.bezierCurveTo(x + 10, h * .3, x - 10, h * .7, x + 4, h); g.stroke();
  }
});
fibre.wrapS = fibre.wrapT = THREE.RepeatWrapping;
const glowMat = () => new THREE.MeshPhysicalMaterial({
  color: 0xff3d55, map: fibre, emissive: 0xff2140, emissiveMap: fibre, emissiveIntensity: 2,
  roughness: .35, clearcoat: 1, clearcoatRoughness: .1,
});
const darkMetal = new THREE.MeshStandardMaterial({ color: 0x1a2236, roughness: .28, metalness: .85 });
const rubber = new THREE.MeshStandardMaterial({ color: 0x0c111d, roughness: .55, metalness: .2 });
const chrome = new THREE.MeshStandardMaterial({ color: 0xc8d2e6, roughness: .12, metalness: 1 });
const neon = new THREE.MeshBasicMaterial({ color: new THREE.Color(0x4f8dff).multiplyScalar(4) });

// ── geometry helpers ────────────────────────────────────────────────────────────
// A limb as a surface of revolution along +y, length l, radius r0 -> r1, rounded ends.
function limbGeo(l, r0, r1, belly = 0, bellyAt = .4) {
  const pts = [];
  const N = 28;
  for (let i = 0; i <= 6; i++) { const a = i / 6 * Math.PI / 2; pts.push(new THREE.Vector2(Math.sin(a) * r0, r0 - Math.cos(a) * r0 - r0 * .4)); }
  for (let i = 1; i < N; i++) {
    const s = i / N;
    const r = r0 + (r1 - r0) * s + belly * Math.exp(-Math.pow((s - bellyAt) / .3, 2));
    pts.push(new THREE.Vector2(r, s * l));
  }
  for (let i = 0; i <= 6; i++) { const a = i / 6 * Math.PI / 2; pts.push(new THREE.Vector2(Math.cos(a) * r1, l + Math.sin(a) * r1 * .7)); }
  const g = new THREE.LatheGeometry(pts, 40);
  g.computeVertexNormals();
  return g;
}
const sphere = new THREE.SphereGeometry(1, 48, 32);

function mesh(geo, mat) {
  const m = new THREE.Mesh(geo, mat);
  m.castShadow = true; m.receiveShadow = true;
  return m;
}
// An anatomical muscle: an ellipsoid partly sunk into the segment, ceramic until it works.
const muscles = [];
function muscle(parent, group, pos, radii, rot = [0, 0, 0]) {
  const m = mesh(sphere, ceramic);
  m.position.set(...pos); m.scale.set(...radii); m.rotation.set(...rot);
  m.userData.group = group;
  m.userData.glow = glowMat();
  parent.add(m);
  muscles.push(m);
  return m;
}

// A segment is a group whose local frame is x = front, y = along the bone, z = lateral.
function segment(geo) {
  const g = new THREE.Group();
  if (geo) g.add(mesh(geo, ceramic));
  scene.add(g);
  return g;
}
const _m = new THREE.Matrix4(), _x = new THREE.Vector3(), _y = new THREE.Vector3(), _z = new THREE.Vector3();
// Local x is the 2D "front" of the bone (its counter-clockwise normal in the sagittal
// plane); pass lateral = -z for a segment that points upward (the spine) to keep x forward.
function place(g, a, b, lateral = new THREE.Vector3(0, 0, 1)) {
  _y.subVectors(b, a).normalize();
  _z.copy(lateral).addScaledVector(_y, -lateral.dot(_y)).normalize();
  _x.crossVectors(_z, _y).normalize();
  _z.crossVectors(_x, _y).normalize();
  g.position.copy(a);
  g.quaternion.setFromRotationMatrix(_m.makeBasis(_x, _y, _z));
}

// ── the mannequin ──────────────────────────────────────────────────────────────
function buildLeg(side) {
  const thigh = segment(limbGeo(L.thigh, .088, .058, .012, .35));
  muscle(thigh, 'quads', [.042, .2, 0], [.055, .19, .062]);
  muscle(thigh, 'quads', [.03, .31, .035], [.04, .1, .045], [0, 0, -.2]);   // vastus medialis teardrop
  muscle(thigh, 'hamstrings', [-.045, .21, 0], [.05, .18, .058]);
  const shin = segment(limbGeo(L.shin, .058, .036, .004, .3));
  muscle(shin, 'calves', [-.035, .12, .018], [.042, .12, .04]);
  muscle(shin, 'calves', [-.03, .13, -.02], [.036, .1, .034]);
  const foot = segment(null);
  const fm = mesh(new RoundedBoxGeometry(.25, .07, .095, 4, .03), ceramic);
  fm.position.set(0, .127, 0);   // along the foot (local y), centred between heel and toe
  fm.rotation.z = Math.PI / 2;
  foot.add(fm);
  const knee = mesh(sphere, ceramic); knee.scale.setScalar(.058); scene.add(knee);
  const ankle = mesh(sphere, ceramic); ankle.scale.setScalar(.04); scene.add(ankle);
  return { thigh, shin, foot, knee, ankle, side };
}
function buildArm(side) {
  const upper = segment(limbGeo(L.upper, .05, .038, .008, .45));
  muscle(upper, 'shoulders', [0, .02, .0], [.07, .085, .07]);
  muscle(upper, 'arms', [.03, .15, 0], [.035, .09, .038]);
  muscle(upper, 'arms', [-.03, .14, 0], [.035, .1, .04]);
  const fore = segment(limbGeo(L.fore, .042, .028, .01, .25));
  const hand = segment(null);
  const hm = mesh(new RoundedBoxGeometry(.06, .1, .085, 4, .025), ceramic);
  hm.position.set(0, .04, 0);
  hand.add(hm);
  const elbow = mesh(sphere, ceramic); elbow.scale.setScalar(.04); scene.add(elbow);
  return { upper, fore, hand, elbow, side };
}
function buildTorso() {
  const t = segment(null);
  const part = (pos, r, rot) => { const m = mesh(sphere, ceramic); m.position.set(...pos); m.scale.set(...r); if (rot) m.rotation.set(...rot); t.add(m); return m; };
  part([-.005, .03, 0], [.12, .12, .165]);          // pelvis
  part([.0, .24, 0], [.105, .17, .14]);             // waist
  part([.0, .42, 0], [.125, .15, .185]);            // ribcage
  part([-.02, .515, 0], [.09, .045, .2]);           // shoulder girdle
  // sculpted muscles
  muscle(t, 'glutes', [-.07, .0, .068], [.07, .1, .075]);
  muscle(t, 'glutes', [-.07, .0, -.068], [.07, .1, .075]);
  for (let r = 0; r < 3; r++) for (const z of [.035, -.035]) muscle(t, 'core', [.095 - r * .004, .17 + r * .07, z], [.022, .03, .032]);
  muscle(t, 'core', [.06, .23, .09], [.03, .11, .03], [0, 0, 0]);          // obliques
  muscle(t, 'core', [.06, .23, -.09], [.03, .11, .03]);
  muscle(t, 'chest', [.085, .43, .075], [.045, .075, .085], [0, 0, -.15]);
  muscle(t, 'chest', [.085, .43, -.075], [.045, .075, .085], [0, 0, -.15]);
  muscle(t, 'back', [-.075, .38, .1], [.045, .12, .07]);
  muscle(t, 'back', [-.075, .38, -.1], [.045, .12, .07]);
  muscle(t, 'back', [-.06, .5, 0], [.05, .06, .12]);   // traps
  const neck = mesh(limbGeo(.12, .052, .046), ceramic); neck.position.set(-.005, .5, 0); t.add(neck);
  const head = new THREE.Group(); head.position.set(.005, .64, 0); t.add(head);
  const skull = mesh(sphere, ceramic); skull.scale.set(.093, .118, .085); head.add(skull);
  const jaw = mesh(sphere, ceramic); jaw.scale.set(.07, .06, .068); jaw.position.set(.025, -.06, 0); head.add(jaw);
  return { t, head };
}

const rig = {
  torso: buildTorso(),
  legs: { n: buildLeg(1), f: buildLeg(-1) },
  arms: { n: buildArm(1), f: buildArm(-1) },
  hip: { n: mesh(sphere, ceramic), f: mesh(sphere, ceramic) },
};
for (const k of ['n', 'f']) { rig.hip[k].scale.setScalar(.085); scene.add(rig.hip[k]); }

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
const V = (p, z) => new THREE.Vector3(p[0], p[1], z);
const Z = { hip: .095, shoulder: .2, ankle: .1 };

export function applyPose(R, move, hl, pulse) {
  const fwd = new THREE.Vector3(R.fwd[0], R.fwd[1], 0);
  // torso frame: y along the spine, x forward
  const pelvis = V(R.hip, 0);
  const neck = V(R.neck, 0);
  place(rig.torso.t, pelvis, neck, new THREE.Vector3(0, 0, -1));
  rig.torso.head.rotation.z = -(R.head || 0) * Math.PI / 180;

  for (const k of ['n', 'f']) {
    const s = k === 'n' ? 1 : -1;
    const lg = R[k + 'l'], leg = rig.legs[k];
    const hip = V(lg.hip, s * Z.hip), knee = V(lg.knee, s * Z.ankle), ankle = V(lg.ankle, s * Z.ankle);
    hip.addScaledVector(fwd, 0);
    rig.hip[k].position.copy(hip);
    place(leg.thigh, hip, knee); place(leg.shin, knee, ankle);
    leg.knee.position.copy(knee); leg.ankle.position.copy(ankle);
    const heel = V(lg.heel, s * Z.ankle), toe = V(lg.toe, s * Z.ankle);
    const footBase = heel.clone().add(new THREE.Vector3(0, -.035, 0));
    place(leg.foot, footBase, toe.clone().add(new THREE.Vector3(0, -.035, 0)));

    const ar = R[k + 'a'], arm = rig.arms[k];
    const handZ = move.handZ ?? Z.shoulder;
    const sh = V(ar.shoulder, s * Z.shoulder);
    const el = V(ar.elbow, s * (Z.shoulder + handZ) / 2 + s * .02);
    const wr = V(ar.wrist, s * handZ);
    const hd = V(ar.hand, s * handZ);
    place(arm.upper, sh, el); place(arm.fore, el, wr); place(arm.hand, wr, hd);
    arm.elbow.position.copy(el);
  }

  // working muscles glow, pulsing with the rep
  for (const m of muscles) {
    const on = hl[m.userData.group];
    m.material = on ? m.userData.glow : ceramic;
    if (on) m.userData.glow.emissiveIntensity = 1.4 + 2.2 * pulse;
  }

  // props
  const props = move.props || [];
  const has = t => props.find(p => p.type === t);
  const db = has('dumbbell');
  dbs.n.visible = !!db; dbs.f.visible = !!db && db.hand === 'both';
  for (const k of ['n', 'f']) {
    const s = k === 'n' ? 1 : -1, ar = R[k + 'a'];
    const handZ = move.handZ ?? Z.shoulder;
    dbs[k].position.set(ar.hand[0] - ar.foreDir[0] * .01, ar.hand[1] - ar.foreDir[1] * .01, s * handZ);
    dbs[k].rotation.z = Math.atan2(ar.foreDir[1], ar.foreDir[0]) + Math.PI / 2;
  }
  let kbPos = null;
  kb.visible = !!has('kettlebell');
  if (kb.visible) {
    const h = [(R.na.hand[0] + R.fa.hand[0]) / 2, (R.na.hand[1] + R.fa.hand[1]) / 2];
    kb.position.set(h[0], h[1], 0);
    kb.rotation.z = Math.atan2(R.na.foreDir[1], R.na.foreDir[0]) + Math.PI / 2;
    kbPos = new THREE.Vector3(0, -.17, 0).applyMatrix4(kb.updateMatrixWorld() || kb.matrixWorld);
  }
  const b = has('bench');
  bench.visible = !!b;
  if (b) {
    bench.position.set(b.x, b.h, -Z.ankle);
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
