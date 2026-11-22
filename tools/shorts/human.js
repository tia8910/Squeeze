// The sculpted human: MakeHuman's CC0 base mesh as a skinned mesh, posed from the
// sagittal rig in rig.js. Rig units: the human is scaled so hip-to-ankle matches the
// rig's thigh + shin, and rig.L is updated to this skeleton's real segment lengths.
import * as THREE from 'three';
import { L } from './rig.js';

const RIG_LEG = .87;   // thigh + shin the poses in rig.js were authored for

export async function loadHuman(scene) {
  const d = await (await fetch('/assets/human.json')).json();
  const byName = Object.fromEntries(d.bones.map((b, i) => [b.name, i]));
  const head = n => new THREE.Vector3(...d.bones[byName[n]].head);
  const dist = (a, b) => head(a).distanceTo(head(b));
  const leg = dist('upperleg01.R', 'lowerleg01.R') + dist('lowerleg01.R', 'foot.R');
  const k = RIG_LEG / leg;

  // rig segment lengths from this skeleton, in rig units
  L.thigh = dist('upperleg01.R', 'lowerleg01.R') * k;
  L.shin = dist('lowerleg01.R', 'foot.R') * k;
  L.upper = dist('upperarm01.R', 'lowerarm01.R') * k;
  L.fore = dist('lowerarm01.R', 'wrist.R') * k;

  // geometry
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(d.positions.map(v => v * k), 3));
  g.setIndex(d.index);
  g.setAttribute('skinIndex', new THREE.Uint16BufferAttribute(d.skinIndex, 4));
  g.setAttribute('skinWeight', new THREE.Float32BufferAttribute(d.skinWeight, 4));
  g.setAttribute('region', new THREE.Float32BufferAttribute(d.region, 1));
  g.setAttribute('rstr', new THREE.Float32BufferAttribute(d.regionStrength, 1));
  g.computeVertexNormals();

  // bones: rest rotations are identity, positions relative to the parent's head
  const bones = d.bones.map(b => { const bone = new THREE.Bone(); bone.name = b.name; return bone; });
  d.bones.forEach((b, i) => {
    const h = new THREE.Vector3(...b.head).multiplyScalar(k);
    if (b.parent) {
      const p = new THREE.Vector3(...d.bones[byName[b.parent]].head).multiplyScalar(k);
      bones[i].position.copy(h.sub(p));
      bones[byName[b.parent]].add(bones[i]);
    } else bones[i].position.copy(h);
  });
  const restDir = d.bones.map(b => new THREE.Vector3(...b.tail).sub(new THREE.Vector3(...b.head)).normalize());

  // ceramic with a per-region emissive glow for the working muscles
  const glow = { value: new Array(d.regions.length).fill(0) };
  const mat = new THREE.MeshPhysicalMaterial({
    color: 0xdfe5ef, roughness: .36, metalness: 0, clearcoat: .55, clearcoatRoughness: .3,
    sheen: .25, sheenRoughness: .6, sheenColor: new THREE.Color(0x8fb0ff),
  });
  mat.onBeforeCompile = sh => {
    sh.uniforms.uGlow = glow;
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', `#include <common>\nattribute float region;\nattribute float rstr;\nuniform float uGlow[${d.regions.length}];\nvarying float vGlow;`)
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvGlow = uGlow[int(region + .5)] * rstr;');
    sh.fragmentShader = sh.fragmentShader
      .replace('#include <common>', '#include <common>\nvarying float vGlow;')
      .replace('#include <color_fragment>', '#include <color_fragment>\nfloat gl = clamp(vGlow, 0., 1.5);\ndiffuseColor.rgb = mix(diffuseColor.rgb, vec3(.95, .12, .2), clamp(gl, 0., 1.) * .85);')
      .replace('#include <emissivemap_fragment>', '#include <emissivemap_fragment>\ntotalEmissiveRadiance += vec3(1., .1, .16) * gl * 1.6;');
  };

  const mesh = new THREE.SkinnedMesh(g, mat);
  mesh.castShadow = true; mesh.receiveShadow = true;
  mesh.frustumCulled = false;
  const roots = bones.filter((b, i) => !d.bones[i].parent);
  roots.forEach(r => mesh.add(r));
  mesh.bind(new THREE.Skeleton(bones));
  scene.add(mesh);

  // eyes: smooth ceramic globes so the sockets read as a sculpture, not hollows
  const eyeGeo = new THREE.SphereGeometry(.0125 * k / .95, 24, 16);
  for (const s of ['L', 'R']) {
    const e = new THREE.Mesh(eyeGeo, mat.clone());
    const hb = bones[byName['head']];
    e.position.copy(new THREE.Vector3(...d.eyes[s]).multiplyScalar(k).sub(new THREE.Vector3(...d.bones[byName['head']].head).multiplyScalar(k)));
    e.position.x -= .006;
    hb.add(e);
  }

  const B = n => bones[byName[n]];
  const idx = n => byName[n];
  const hipRest = head('upperleg01.R').add(head('upperleg01.L')).multiplyScalar(.5 * k);
  const rootRest = new THREE.Vector3(...d.bones[0].head).multiplyScalar(k);
  const zRest = n => head(n).z * k;

  const _q = new THREE.Quaternion(), _pw = new THREE.Quaternion(), _v = new THREE.Vector3();
  const Z = new THREE.Vector3(0, 0, 1);

  function aim(name, dir) {
    // rotate bone so its rest direction points along dir (world), whatever its parent does
    const b = B(name);
    b.parent.updateWorldMatrix(true, false);
    b.parent.getWorldQuaternion(_pw);
    const meshQ = mesh.getWorldQuaternion(new THREE.Quaternion());
    _pw.premultiply(meshQ.invert());
    _q.setFromUnitVectors(restDir[idx(name)], _v.copy(dir).normalize());
    b.quaternion.copy(_pw.invert().multiply(_q));
  }
  const local = (name, deg) => B(name).quaternion.setFromAxisAngle(Z, -deg * Math.PI / 180);

  const v3 = (p, z) => new THREE.Vector3(p[0], p[1], z);

  function pose(R, move) {
    for (const b of bones) b.quaternion.identity();
    const root = bones[idx(d.bones[0].name)];
    const lean = R.torso;
    // the pelvis takes part of a hinge, the spine the rest; the neck adds the head angle
    local(root.name, lean * .4);
    for (const s of ['spine05', 'spine04', 'spine03', 'spine02', 'spine01']) local(s, lean * .12);
    for (const s of ['neck01', 'neck02', 'neck03']) local(s, (R.head || 0) / 3);

    // put the hips on the rig's pelvis point
    root.position.copy(rootRest);
    mesh.updateMatrixWorld(true);
    const hipNow = B('upperleg01.R').getWorldPosition(new THREE.Vector3()).add(B('upperleg01.L').getWorldPosition(new THREE.Vector3())).multiplyScalar(.5);
    root.position.add(new THREE.Vector3(R.hip[0], R.hip[1], 0).sub(hipNow));
    mesh.updateMatrixWorld(true);

    for (const [s, k2, z] of [['R', 'n', 1], ['L', 'f', -1]]) {
      const lg = R[k2 + 'l'];
      const hipW = B('upperleg01.' + s).getWorldPosition(new THREE.Vector3());
      const kz = z * .11, az = z * .1;
      const knee = v3(lg.knee, kz), ankle = v3(lg.ankle, az);
      const thighDir = knee.clone().sub(v3([hipW.x, hipW.y], hipW.z));
      aim('upperleg01.' + s, thighDir); aim('upperleg02.' + s, thighDir);
      const shinDir = ankle.clone().sub(knee);
      aim('lowerleg01.' + s, shinDir); aim('lowerleg02.' + s, shinDir);
      // foot: rest direction tilted by the pose's foot angle
      const fr = restDir[idx('foot.' + s)].clone();
      const fa = Math.atan2(fr.y, fr.x) - lg.footDeg * Math.PI / 180;
      aim('foot.' + s, new THREE.Vector3(Math.cos(fa), Math.sin(fa), 0));

      const ar = R[k2 + 'a'];
      const handZ = move.handZ;
      const splayU = handZ != null ? -.25 : .14, splayF = handZ != null ? -.2 : .02;
      const ud = new THREE.Vector3(ar.elbow[0] - ar.shoulder[0], ar.elbow[1] - ar.shoulder[1], 0).normalize();
      ud.z = z * splayU;
      const fd = new THREE.Vector3(ar.wrist[0] - ar.elbow[0], ar.wrist[1] - ar.elbow[1], 0).normalize();
      fd.z = z * splayF;
      aim('upperarm01.' + s, ud); aim('upperarm02.' + s, ud);
      aim('lowerarm01.' + s, fd); aim('lowerarm02.' + s, fd);
      aim('wrist.' + s, fd);
    }
    mesh.updateMatrixWorld(true);
  }

  function setGlow(hl, pulse, regions = d.regions) {
    regions.forEach((r, i) => { glow.value[i] = hl[r] ? (.45 + .55 * pulse) : 0; });
  }

  function hand(side) {
    const s = side === 'n' ? 'R' : 'L';
    const w = B('wrist.' + s).getWorldPosition(new THREE.Vector3());
    const f = B('lowerarm02.' + s).getWorldPosition(new THREE.Vector3());
    const dir = w.clone().sub(f).normalize();
    return { pos: w.clone().addScaledVector(dir, .05), dir };
  }

  return { mesh, pose, setGlow, hand, scale: k };
}
