"""Builds assets/human.json: the MakeHuman base mesh shaped as an athletic male, with its
default skeleton and skin weights, ready for a three.js SkinnedMesh.

The MakeHuman base mesh, targets and rig are CC0 1.0 (see the MakeHuman LICENSE.md,
section C). They are downloaded here, not committed.

Run from the repository root: python3 tools/shorts/build_human.py
"""
import json
import pathlib
import urllib.request
from collections import defaultdict

HERE = pathlib.Path(__file__).resolve().parent
SRC = HERE / "assets" / "makehuman"
OUT = HERE / "assets" / "human.json"
BASE = "https://raw.githubusercontent.com/makehumancommunity/makehuman/master/makehuman/data/"

# Target -> weight. An athletic young man: maximal muscle pushed past 1.0 for definition, lean,
# ideal proportions, a little taller than average; the face is a blend of the three
# ethnic templates so it reads as a neutral sculpt rather than a particular person.
TARGETS = {
    "targets/macrodetails/caucasian-male-young.target": 1 / 3,
    "targets/macrodetails/african-male-young.target": 1 / 3,
    "targets/macrodetails/asian-male-young.target": 1 / 3,
    "targets/macrodetails/universal-male-young-maxmuscle-averageweight.target": 1.0,
    "targets/macrodetails/universal-male-young-maxmuscle-minweight.target": 0.3,
    "targets/macrodetails/universal-male-young-maxmuscle-maxweight.target": 0.25,
    "targets/macrodetails/proportions/male-young-maxmuscle-averageweight-idealproportions.target": 1.0,
    "targets/macrodetails/height/male-young-maxmuscle-averageweight-maxheight.target": 0.1,
    # Stage-ready physique: a strong V-taper with real mass. Broad lats and capped delts,
    # a full chest, big arms, a narrow toned waist, and defined legs.
    "targets/torso/torso-vshape-incr.target": 1.0,
    "targets/torso/torso-muscle-dorsi-incr.target": 1.0,
    "targets/torso/torso-muscle-pectoral-incr.target": 1.0,
    "targets/torso/torso-scale-horiz-incr.target": 0.3,
    "targets/stomach/stomach-tone-incr.target": 1.0,
    "targets/stomach/stomach-pregnant-decr.target": 0.5,
    "targets/hip/hip-scale-horiz-decr.target": 0.3,
    "targets/buttocks/buttocks-volume-incr.target": 0.3,
    "targets/neck/neck-scale-horiz-incr.target": 0.6,
    **{f"targets/armslegs/{s}-{part}-incr.target": w for s in "lr" for part, w in (
        ("upperarm-shoulder-muscle", 1.0), ("upperarm-muscle", 1.0), ("lowerarm-muscle", 0.9),
        ("upperleg-muscle", 0.9), ("lowerleg-muscle", 0.8), ("upperarm-scale-horiz", 0.35), ("upperleg-scale-horiz", 0.2))},
}
FILES = ["3dobjs/base.obj", "rigs/default.mhskel", "rigs/default_weights.mhw", *TARGETS]


def fetch():
    SRC.mkdir(parents=True, exist_ok=True)
    for f in FILES:
        dst = SRC / pathlib.Path(f).name
        if not dst.exists():
            print("fetching", f)
            urllib.request.urlretrieve(BASE + f, dst)


def main():
    fetch()
    verts, body_faces, group = [], [], None
    for line in (SRC / "base.obj").read_text().splitlines():
        if line.startswith("v "):
            verts.append([float(x) for x in line.split()[1:4]])
        elif line.startswith("g "):
            group = line[2:].strip()
        elif line.startswith("f ") and group == "body":
            body_faces.append([int(p.split("/")[0]) - 1 for p in line.split()[1:]])

    for f, w in TARGETS.items():
        for line in (SRC / pathlib.Path(f).name).read_text().splitlines():
            if not line or line[0] == "#":
                continue
            i, dx, dy, dz = line.split()
            v = verts[int(i)]
            v[0] += float(dx) * w; v[1] += float(dy) * w; v[2] += float(dz) * w

    # MakeHuman: y up, decimetres, facing +z, character's left at +x.
    # Ours: metres, facing +x. (x, y, z) -> (z, y, -x), so the character's right is +z.
    conv = lambda v: [v[2] * .1, v[1] * .1, -v[0] * .1]
    world = [conv(v) for v in verts]

    used = sorted({i for f in body_faces for i in f})
    remap = {old: new for new, old in enumerate(used)}
    floor = min(world[i][1] for i in used)
    for v in world:
        v[1] -= floor

    skel = json.loads((SRC / "default.mhskel").read_text())
    joint = {name: [sum(world[i][k] for i in idx) / len(idx) for k in range(3)] for name, idx in skel["joints"].items()}

    names = list(skel["bones"])
    # parents before children
    order, seen = [], set()
    def visit(n):
        if n in seen:
            return
        p = skel["bones"][n]["parent"]
        if p:
            visit(p)
        seen.add(n); order.append(n)
    for n in names:
        visit(n)
    bones = [{"name": n, "parent": skel["bones"][n]["parent"],
              "head": [round(c, 5) for c in joint[skel["bones"][n]["head"]]],
              "tail": [round(c, 5) for c in joint[skel["bones"][n]["tail"]]]} for n in order]
    bidx = {b["name"]: i for i, b in enumerate(bones)}

    weights = json.loads((SRC / "default_weights.mhw").read_text())["weights"]
    per = defaultdict(list)
    for bone, pairs in weights.items():
        for v, w in pairs:
            if v in remap:
                per[remap[v]].append((w, bidx[bone]))
    skin_i, skin_w = [], []
    for v in range(len(used)):
        top = sorted(per[v], reverse=True)[:4] or [(1.0, bidx["root"])]
        s = sum(w for w, _ in top)
        top += [(0.0, 0)] * (4 - len(top))
        skin_i += [b for _, b in top]
        skin_w += [round(w / s, 4) for w, _ in top]

    # Muscle regions for the "working muscle" glow: an id and a soft strength per vertex,
    # from the skin weights and where the vertex sits around its bone in the rest pose.
    REGIONS = ["none", "quads", "hamstrings", "glutes", "calves", "core", "chest", "back", "shoulders", "arms"]
    B = {b["name"]: b for b in bones}
    def around(p, bone):
        h, t = B[bone]["head"], B[bone]["tail"]
        d = [t[k] - h[k] for k in range(3)]
        L2 = sum(c * c for c in d) or 1
        u = max(0, min(1, sum((p[k] - h[k]) * d[k] for k in range(3)) / L2))
        o = [p[k] - (h[k] + d[k] * u) for k in range(3)]
        n = sum(c * c for c in o) ** .5 or 1
        return u, [c / n for c in o]
    wmap = [dict() for _ in used]
    for v in range(len(used)):
        for w, b in per[v]:
            wmap[v][bones[b]["name"]] = wmap[v].get(bones[b]["name"], 0) + w
    clamp = lambda x: max(0.0, min(1.0, x))
    region, rstr = [], []
    for v, old in enumerate(used):
        p = world[old]; wv = wmap[v]
        side = "R" if p[2] >= 0 else "L"
        cand = []
        thigh = wv.get(f"upperleg02.{side}", 0) + wv.get(f"upperleg01.{side}", 0)
        if thigh > .3:
            u, o = around(p, f"upperleg02.{side}")
            if .08 < u < .92:
                fade = clamp(min(u - .08, .92 - u) * 6)
                cand.append((1, clamp((o[0] - .05) * 2.2) * fade * clamp(thigh)))
                cand.append((2, clamp((-o[0] - .25) * 2.2) * fade * clamp(thigh)))
        hipw = wv.get(f"upperleg01.{side}", 0) + wv.get(f"pelvis.{side}", 0) + wv.get("root", 0) + wv.get(f"upperleg02.{side}", 0)
        if p[0] < -.02 and .78 < p[1] < 1.12 and abs(p[2]) > .015:
            cand.append((3, clamp((-p[0] - .03) * 12) * clamp(hipw) * clamp(min(p[1] - .78, 1.12 - p[1]) * 9)))
        shin = wv.get(f"lowerleg01.{side}", 0) + wv.get(f"lowerleg02.{side}", 0)
        if shin > .3:
            u, o = around(p, f"lowerleg01.{side}")
            cand.append((4, clamp((-o[0] - .15) * 2) * clamp(shin) * clamp((.95 - u) * 4)))
        sp_low = sum(wv.get(b, 0) for b in ("spine03", "spine04", "spine05", "root"))
        if sp_low > .3 and p[0] > .0 and 1.02 < p[1] < 1.42:
            cand.append((5, clamp((p[0] - .02) * 14) * clamp(sp_low) * clamp((.17 - abs(p[2])) * 12)))
        sp_up = sum(wv.get(b, 0) for b in ("spine01", "spine02", f"clavicle.{side}", f"breast.{side}"))
        if sp_up > .3 and p[0] > .0 and 1.38 < p[1] < 1.62:
            cand.append((6, clamp((p[0] - .03) * 12) * clamp(sp_up) * clamp((.25 - abs(p[2])) * 10)))
        if sp_up + sp_low > .3 and p[0] < -.06 and 1.2 < p[1] < 1.66:
            cand.append((7, clamp((-p[0] - .07) * 12) * clamp(sp_up + sp_low)))
        sh = wv.get(f"shoulder01.{side}", 0) + wv.get(f"upperarm01.{side}", 0)
        if sh > .3:
            cand.append((8, clamp(sh * 1.4)))
        arm = wv.get(f"upperarm02.{side}", 0) + wv.get(f"lowerarm01.{side}", 0)
        if arm > .3:
            cand.append((9, clamp(arm)))
        best = max(cand, key=lambda c: c[1], default=(0, 0.0))
        region.append(best[0] if best[1] > .02 else 0)
        rstr.append(round(best[1], 3) if best[1] > .02 else 0)

    tris = []
    for f in body_faces:
        f = [remap[i] for i in f]
        tris += [f[0], f[1], f[2]]
        if len(f) == 4:
            tris += [f[0], f[2], f[3]]

    # Eyeballs: MakeHuman's eye helper spheres (centre and radius, rest pose).
    eye_v = {"R": set(), "L": set()}
    group = None
    for line in (SRC / "base.obj").read_text().splitlines():
        if line.startswith("g "):
            group = line[2:].strip()
        elif line.startswith("f ") and group in ("helper-r-eye", "helper-l-eye"):
            # helper-r-eye is the character's right, which is +z in our frame
            eye_v["R" if group == "helper-r-eye" else "L"].update(int(p.split("/")[0]) - 1 for p in line.split()[1:])
    eyeballs = {}
    for k, vs in eye_v.items():
        pts = [world[i] for i in vs]
        c = [sum(p[j] for p in pts) / len(pts) for j in range(3)]
        r = sum(sum((p[j] - c[j]) ** 2 for j in range(3)) ** .5 for p in pts) / len(pts)
        eyeballs[k] = {"center": [round(x, 5) for x in c], "radius": round(r, 5)}

    data = {
        "license": "MakeHuman base mesh, targets and rig: CC0 1.0",
        "positions": [round(c, 5) for i in used for c in world[i]],
        "index": tris,
        "faces": [[remap[i] for i in f] for f in body_faces],
        "bones": bones,
        "skinIndex": skin_i,
        "skinWeight": skin_w,
        "regions": REGIONS,
        "region": region,
        "regionStrength": rstr,
        "eyeballs": eyeballs,
        "eyes": {k: [round(c, 5) for c in joint[skel["bones"][f"oculi01.{k}"]["head"]]] for k in ("L", "R")},
    }
    OUT.write_text(json.dumps(data, separators=(",", ":")))
    h = max(world[i][1] for i in used)
    print(f"{len(used)} vertices, {len(tris) // 3} triangles, {len(bones)} bones, height {h:.2f} m -> {OUT}")


if __name__ == "__main__":
    main()
