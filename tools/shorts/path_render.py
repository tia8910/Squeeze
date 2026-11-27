"""Path-traced render of one exported frame with Blender Cycles.

    node render.js timeline.json pose.json --export 6.7          # pose the frame
    python path_render.py pose.json out.png [--samples 96] [--size 1080x1920]

Run with a Python that has the `bpy` module (pip install bpy==5.3.0, Python 3.13).

The body is MakeHuman's CC0 base mesh, subdivided twice; muscle definition (abdominal
grooves, linea alba, pectoral and serratus lines, quad separations) is carved into the
subdivided surface from each vertex's rest-pose position, so it moves with the body.
"""
import json
import math
import pathlib
import sys

import bpy
import numpy as np
from mathutils import Matrix

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
FONT = ROOT / "app/src/main/res/font/jakarta_extrabold.ttf"

args = sys.argv[1:]
src, out = args[0], args[1]
opt = lambda k, d: args[args.index(k) + 1] if k in args else d
SAMPLES = int(opt("--samples", 96))
RES = [int(x) for x in opt("--size", "1080x1920").split("x")]
D = json.loads(pathlib.Path(src).read_text())

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene

# All imported geometry lives in three.js coordinates (y up) under one root that turns it
# into Blender's z-up world.
root = bpy.data.objects.new("three", None)
sc.collection.objects.link(root)
root.matrix_world = Matrix.Rotation(math.radians(90), 4, "X")


def link(obj, parent=root):
    sc.collection.objects.link(obj)
    obj.parent = parent
    return obj


# ── materials ──────────────────────────────────────────────────────────────────
def principled(name, color, rough=.4, metal=0., coat=0., coat_rough=.1, emit=None, emit_strength=0.):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    b.inputs["Coat Weight"].default_value = coat
    b.inputs["Coat Roughness"].default_value = coat_rough
    if emit:
        b.inputs["Emission Color"].default_value = (*emit, 1)
        b.inputs["Emission Strength"].default_value = emit_strength
    return m


def skin_material():
    """Realistic skin: tanned base, subsurface scattering, pore bump, a light oil sheen.
    Per-vertex attributes switch to short hair ("hair") and training shorts ("shorts"),
    and add the working-muscle glow ("glow")."""
    m = bpy.data.materials.new("skin")
    m.use_nodes = True
    nt = m.node_tree
    N, Lk = nt.nodes, nt.links.new
    b = N["Principled BSDF"]

    def attr(name):
        a = N.new("ShaderNodeAttribute"); a.attribute_name = name
        c = N.new("ShaderNodeClamp"); Lk(a.outputs["Fac"], c.inputs["Value"])
        return c.outputs["Result"]

    def mix_rgb(fac, a, b_):
        mx = N.new("ShaderNodeMix"); mx.data_type = "RGBA"
        Lk(fac, mx.inputs["Factor"])
        for sock, v in (("A", a), ("B", b_)):
            if isinstance(v, tuple): mx.inputs[sock].default_value = (*v, 1)
            else: Lk(v, mx.inputs[sock])
        return mx.outputs["Result"]

    def mix_f(fac, a, b_):
        mx = N.new("ShaderNodeMix"); mx.data_type = "FLOAT"
        Lk(fac, mx.inputs["Factor"])
        for sock, v in (("A", a), ("B", b_)):
            if isinstance(v, float): mx.inputs[sock].default_value = v
            else: Lk(v, mx.inputs[sock])
        return mx.outputs["Result"]

    hair, shorts, glow = attr("hair"), attr("shorts"), attr("glow")
    coord = N.new("ShaderNodeTexCoord")
    # subtle blotchy variation in skin tone
    var = N.new("ShaderNodeTexNoise"); var.inputs["Scale"].default_value = 18; var.inputs["Detail"].default_value = 3
    Lk(coord.outputs["Object"], var.inputs["Vector"])
    tone = N.new("ShaderNodeValToRGB")
    tone.color_ramp.elements[0].color = (.27, .135, .07, 1)
    tone.color_ramp.elements[1].color = (.38, .2, .11, 1)
    Lk(var.outputs["Fac"], tone.inputs["Fac"])
    col = mix_rgb(glow, tone.outputs["Color"], (.75, .06, .08))
    col = mix_rgb(hair, col, (.006, .0045, .004))
    col = mix_rgb(shorts, col, (.010, .011, .014))
    Lk(col, b.inputs["Base Color"])

    b.inputs["Subsurface Weight"].default_value = 1.0
    b.inputs["Subsurface Radius"].default_value = (1.0, .35, .2)
    b.inputs["Subsurface Scale"].default_value = .0035
    no_sss = mix_f(hair, .55, 0.0); no_sss = mix_f(shorts, no_sss, 0.0)
    Lk(no_sss, b.inputs["Subsurface Weight"])
    rough = mix_f(hair, .38, .9); rough = mix_f(shorts, rough, .62)
    Lk(rough, b.inputs["Roughness"])
    coat = mix_f(hair, .28, 0.0); coat = mix_f(shorts, coat, 0.0)
    Lk(coat, b.inputs["Coat Weight"])
    b.inputs["Coat Roughness"].default_value = .2
    spec = mix_f(hair, .5, .12); Lk(spec, b.inputs["Specular IOR Level"])
    sheen = mix_f(shorts, 0.0, .25); Lk(sheen, b.inputs["Sheen Weight"])
    b.inputs["Sheen Tint"].default_value = (.35, .38, .45, 1)
    b.inputs["Sheen Roughness"].default_value = .4

    # micro detail: pores on skin, stubble on the scalp, weave on the shorts
    pores = N.new("ShaderNodeTexNoise"); pores.inputs["Scale"].default_value = 900; pores.inputs["Detail"].default_value = 6
    Lk(coord.outputs["Object"], pores.inputs["Vector"])
    fine = N.new("ShaderNodeTexNoise"); fine.inputs["Scale"].default_value = 4000; fine.inputs["Detail"].default_value = 2
    Lk(coord.outputs["Object"], fine.inputs["Vector"])
    height = mix_f(hair, pores.outputs["Fac"], fine.outputs["Fac"])
    bump = N.new("ShaderNodeBump"); bump.inputs["Distance"].default_value = .0004
    strength = mix_f(hair, .12, 1.0); strength = mix_f(shorts, strength, .25)
    Lk(strength, bump.inputs["Strength"]); Lk(height, bump.inputs["Height"])
    Lk(bump.outputs["Normal"], b.inputs["Normal"])

    b.inputs["Emission Color"].default_value = (1, .08, .1, 1)
    em = N.new("ShaderNodeMath"); em.operation = "MULTIPLY"; em.inputs[1].default_value = 1.6
    Lk(glow, em.inputs[0]); Lk(em.outputs[0], b.inputs["Emission Strength"])
    return m


def eye_material():
    m = bpy.data.materials.new("eye"); m.use_nodes = True
    nt = m.node_tree; N, Lk = nt.nodes, nt.links.new
    b = N["Principled BSDF"]
    tc = N.new("ShaderNodeTexCoord"); sep = N.new("ShaderNodeSeparateXYZ")
    Lk(tc.outputs["Normal"], sep.inputs[0])            # object-space normal: +z looks forward
    ramp = N.new("ShaderNodeValToRGB")
    e = ramp.color_ramp.elements
    e[0].position = .80; e[0].color = (.75, .72, .70, 1)     # sclera
    e[1].position = .83; e[1].color = (.12, .06, .03, 1)     # iris
    e3 = e.new(.95); e3.color = (.01, .008, .006, 1)          # pupil
    Lk(sep.outputs["Z"], ramp.inputs["Fac"])
    Lk(ramp.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = .25
    b.inputs["Coat Weight"].default_value = 1.0; b.inputs["Coat Roughness"].default_value = .02
    return m


SKIN = skin_material()
EYE = eye_material()
NEON = principled("neon", (.1, .3, 1), emit=(.25, .5, 1), emit_strength=12)
RUBBER = principled("rubber", (.012, .016, .028), rough=.5, coat=.3)
STEEL = principled("steel", (.75, .78, .85), rough=.15, metal=1)
DARK = principled("dark", (.02, .03, .055), rough=.35, coat=.4)


# ── the body ───────────────────────────────────────────────────────────────────
def gauss(d, w):
    return np.exp(-(d / w) ** 2)


def seg_dist(py, pz, a, b):
    """Distance in the (y, |z|) plane from points to segment a-b."""
    ay, az = a; by, bz = b
    vy, vz = by - ay, bz - az
    t = np.clip(((py - ay) * vy + (pz - az) * vz) / (vy * vy + vz * vz), 0, 1)
    return np.hypot(py - (ay + vy * t), pz - (az + vz * t))


def poly_dist(py, pz, pts):
    return np.min([seg_dist(py, pz, pts[i], pts[i + 1]) for i in range(len(pts) - 1)], axis=0)


def band(v, lo, hi, soft):
    return np.clip((v - lo) / soft, 0, 1) * np.clip((hi - v) / soft, 0, 1)


def definition(R, N):
    """Surface offset in metres (negative carves a groove) from rest position and normal.

    Rest coordinates: x forward, y up, z to the figure's right; heights in rig units
    (the figure is 1.72 tall)."""
    x, y, z = R[:, 0], R[:, 1], R[:, 2]
    az = np.abs(z)
    front = np.clip((N[:, 0] - .25) / .35, 0, 1)
    back = np.clip((-N[:, 0] - .3) / .35, 0, 1)
    torso = (az < .2) & (y > .86) & (y < 1.46)
    h = np.zeros(len(R))

    # rectus abdominis: linea alba, three tendinous intersections, linea semilunaris
    abs_zone = front * band(y, .96, 1.245, .02) * torso
    h -= .0042 * gauss(az, .0065) * abs_zone
    for yc in (1.072, 1.128, 1.186):
        yy = yc + .010 * (az / .075) ** 2
        h -= .0036 * gauss(y - yy, .0058) * band(az, .006, .084, .012) * abs_zone
    h -= .0032 * gauss(az - (.088 + .012 * (1.12 - y)), .0075) * front * band(y, .97, 1.23, .03) * torso
    # ab bellies swell between the grooves
    h += .0016 * band(az, .008, .082, .02) * abs_zone
    # inguinal "V" lines
    h -= .0045 * gauss(poly_dist(y, az, [(1.03, .118), (.955, .085), (.895, .05)]), .009) * front * torso
    # chest: lower pectoral border, sternal groove, delto-pectoral groove
    chest = front * torso
    h -= .0065 * gauss(poly_dist(y, az, [(1.238, .018), (1.233, .07), (1.252, .125), (1.30, .17), (1.36, .19)]), .0105) * chest
    h += .0022 * band(y, 1.25, 1.39, .03) * band(az, .02, .17, .03) * chest      # pec mass
    h -= .004 * gauss(az, .008) * band(y, 1.235, 1.405, .02) * chest
    h -= .0035 * gauss(poly_dist(y, az, [(1.40, .135), (1.34, .175), (1.29, .195)]), .008) * chest
    # serratus anterior: three short slanted fingers on the ribs below the armpit
    side = np.clip((np.abs(N[:, 2]) - .25) / .4, 0, 1) * (N[:, 0] > -.2)
    for yc in (1.165, 1.2, 1.235):
        h -= .0026 * gauss(seg_dist(y, az, (yc, .128), (yc + .034, .16)), .0048) * side * torso
    # back: spinal groove
    h -= .0045 * gauss(az, .009) * back * band(y, .98, 1.4, .03) * torso

    # thighs (legs hang straight in the rest pose): rectus femoris edges, vastus medialis
    leg = (y > .5) & (y < .87) & (az > .02)
    t = np.clip((.858 - y) / (.858 - .512), 0, 1)
    u = az - (.089 + (.14 - .089) * t)              # + outward, - inward from the leg axis
    lf = front * leg
    h -= .0058 * gauss(u - .034, .0075) * band(y, .6, .8, .03) * lf
    h -= .0058 * gauss(u + .03, .0075) * band(y, .64, .8, .03) * lf
    h -= .0065 * gauss(poly_dist(y, u, [(.73, -.035), (.62, -.05), (.56, -.035), (.545, -.005)]), .008) * lf
    h += .002 * band(y, .56, .66, .02) * band(u, -.07, -.03, .01) * lf          # teardrop
    # IT band on the outer thigh
    outer = np.clip((N[:, 2] * np.sign(z) - .4) / .3, 0, 1) * leg
    h -= .0028 * gauss(np.abs(N[:, 0]) - .0, .25) * band(y, .58, .82, .04) * outer
    return h


def build_body():
    V = np.array(D["posed"], dtype=np.float32).reshape(-1, 3)
    faces = D["faces"]
    me = bpy.data.meshes.new("body")
    me.from_pydata(V.tolist(), [], faces)
    me.update()
    for name, vals, typ in (("rest", D["rest"], "FLOAT_VECTOR"), ("rnorm", D["restNormal"], "FLOAT_VECTOR"), ("glow", D["glow"], "FLOAT")):
        a = me.attributes.new(name, typ, "POINT")
        a.data.foreach_set("vector" if typ == "FLOAT_VECTOR" else "value", vals)
    ob = link(bpy.data.objects.new("body_base", me))
    mod = ob.modifiers.new("subd", "SUBSURF")
    mod.levels = mod.render_levels = 2
    dg = bpy.context.evaluated_depsgraph_get()
    hi = bpy.data.meshes.new_from_object(ob.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
    bpy.data.objects.remove(ob)

    n = len(hi.vertices)
    P = np.zeros(n * 3, np.float32); hi.vertices.foreach_get("co", P); P = P.reshape(-1, 3)
    R = np.zeros(n * 3, np.float32); hi.attributes["rest"].data.foreach_get("vector", R); R = R.reshape(-1, 3)
    RN = np.zeros(n * 3, np.float32); hi.attributes["rnorm"].data.foreach_get("vector", RN); RN = RN.reshape(-1, 3)
    RN /= np.linalg.norm(RN, axis=1, keepdims=True) + 1e-9
    NN = np.zeros(n * 3, np.float32); hi.vertices.foreach_get("normal", NN); NN = NN.reshape(-1, 3)
    h = definition(R, RN).astype(np.float32)
    # short hair on the scalp, training shorts from the waist to mid-thigh
    y, x, az = R[:, 1], R[:, 0], np.abs(R[:, 2])
    top = y.max()
    headx = x[y > top - .2].mean()
    hair = np.clip((y - (top - .072)) / .012, 0, 1)
    hair = np.maximum(hair, np.clip((y - (top - .155)) / .012, 0, 1) * np.clip((headx - .015 - x) / .012, 0, 1))
    hair *= (y > top - .2)
    shorts = band(y, .675, .995, .004) * (az < .26)
    shorts = shorts * np.clip((.245 - az) / .01, 0, 1)
    waist = band(y, .962, .992, .003) * (shorts > 0)
    h = h * (1 - shorts) + shorts * .006 + waist * .003
    h += hair * .0015
    P += NN * h[:, None]
    for name, vals in (("hair", hair), ("shorts", shorts)):
        a_ = hi.attributes.new(name, "FLOAT", "POINT"); a_.data.foreach_set("value", vals.astype(np.float32))
    hi.vertices.foreach_set("co", P.ravel())
    hi.update()
    hi.polygons.foreach_set("use_smooth", [True] * len(hi.polygons))
    body = link(bpy.data.objects.new("body", hi))
    body.data.materials.append(SKIN)
    # eyeballs: MakeHuman's eye spheres, carried from the rest pose onto the posed head by
    # the rigid transform that best maps the head's rest vertices onto their posed positions
    from mathutils import Vector, Matrix as M3
    Rv = np.array(D["rest"], np.float64).reshape(-1, 3)
    head = Rv[:, 1] > Rv[:, 1].max() - .17
    A, B = Rv[head], V.astype(np.float64)[head]
    ca, cb = A.mean(0), B.mean(0)
    U, _, Wt = np.linalg.svd((A - ca).T @ (B - cb))
    Rot = (U @ Wt).T
    if np.linalg.det(Rot) < 0:
        Wt[-1] *= -1; Rot = (U @ Wt).T
    fwd = Vector((Rot @ np.array([1., 0, 0])).tolist())
    for e in D["eyeballs"]:
        c = Rot @ (np.array(e["center"]) - ca) + cb
        bpy.ops.mesh.primitive_uv_sphere_add(radius=e["radius"], segments=48, ring_count=24)
        ob = bpy.context.active_object
        for cc in ob.users_collection: cc.objects.unlink(ob)
        link(ob)
        ob.location = Vector(c.tolist())
        ob.rotation_euler = fwd.to_track_quat("Z", "Y").to_euler()
        ob.data.materials.append(EYE)
        for p_ in ob.data.polygons: p_.use_smooth = True
    print(f"body: {n} vertices, definition depth {h.min()*1000:.1f}..{h.max()*1000:.1f} mm")
    return body


# ── props (three.js local coordinates, placed by the exported world matrix) ────
def mesh_obj(name, verts_faces_fn, mat, parent):
    bpy.ops.object.select_all(action="DESELECT")
    ob = verts_faces_fn()
    ob.name = name
    if ob.users_collection:
        for c in ob.users_collection:
            c.objects.unlink(ob)
    sc.collection.objects.link(ob)
    ob.data.materials.clear(); ob.data.materials.append(mat)
    ob.parent = parent
    for p in ob.data.polygons:
        p.use_smooth = True
    return ob


def prim(op, **kw):
    getattr(bpy.ops.mesh, op)(**kw)
    return bpy.context.active_object


def add_prop(p):
    holder = link(bpy.data.objects.new(p["type"], None))
    M = Matrix([p["matrix"][i::4] for i in range(4)])   # column-major -> rows
    holder.matrix_parent_inverse = Matrix.Identity(4)
    holder.matrix_basis = M
    t = p["type"]
    if t == "dumbbell":
        mesh_obj("handle", lambda: prim("primitive_cylinder_add", radius=.016, depth=.16, vertices=32), STEEL, holder)
        for zz in (-.11, .11):
            ob = mesh_obj("plate", lambda: prim("primitive_cylinder_add", radius=.075, depth=.07, vertices=6, location=(0, 0, zz)), RUBBER, holder)
            bev = ob.modifiers.new("b", "BEVEL"); bev.width = .008; bev.segments = 3
            mesh_obj("ring", lambda: prim("primitive_torus_add", major_radius=.069, minor_radius=.004, major_segments=6, minor_segments=8,
                                          location=(0, 0, zz + math.copysign(.036, zz))), NEON, holder)
    elif t == "kettlebell":
        ob = mesh_obj("bell", lambda: prim("primitive_uv_sphere_add", radius=.11, segments=48, ring_count=24, location=(0, -.17, 0)), RUBBER, holder)
        ob.scale = (1, .92, 1)
        h = mesh_obj("handle", lambda: prim("primitive_torus_add", major_radius=.06, minor_radius=.015, location=(0, -.05, 0)), STEEL, holder)
        h.rotation_euler = (0, math.pi / 2, 0)
        mesh_obj("band", lambda: prim("primitive_torus_add", major_radius=.111, minor_radius=.005, location=(0, -.17, 0), rotation=(math.pi / 2, 0, 0)), NEON, holder)
    elif t == "bench":
        ob = mesh_obj("pad", lambda: prim("primitive_cube_add", size=1, location=(0, -.04, 0)), DARK, holder)
        ob.scale = (1, .08, .3); bev = ob.modifiers.new("b", "BEVEL"); bev.width = .02; bev.segments = 4
        e = mesh_obj("edge", lambda: prim("primitive_cube_add", size=1, location=(0, -.002, .15)), NEON, holder); e.scale = (.98, .008, .008)
    elif t == "box":
        ob = mesh_obj("box", lambda: prim("primitive_cube_add", size=1), DARK, holder)
        bev = ob.modifiers.new("b", "BEVEL"); bev.width = .02; bev.segments = 4


# ── stage ──────────────────────────────────────────────────────────────────────
def stage():
    w = bpy.data.worlds.new("world"); sc.world = w
    w.use_nodes = True
    bg = w.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (.002, .006, .022, 1); bg.inputs["Strength"].default_value = 1

    # floor: glossy navy that fades to black away from the stage
    floor = prim("primitive_plane_add", size=60)
    fm = principled("floor", (.003, .008, .024), rough=.3, metal=.3, coat=.5, coat_rough=.08)
    floor.data.materials.append(fm)
    # stage disc with a glowing ring and the wordmark
    disc = prim("primitive_cylinder_add", radius=1.25, depth=.04, vertices=128, location=(0, 0, -.019))
    disc.data.materials.append(principled("disc", (.002, .005, .014), rough=.5, coat=.3, coat_rough=.2))
    ring = prim("primitive_torus_add", major_radius=1.255, minor_radius=.009, major_segments=256, minor_segments=8, location=(0, 0, .002))
    ring.data.materials.append(principled("ringneon", (.1, .3, 1), emit=(.25, .5, 1), emit_strength=8))
    if FONT.exists():
        bpy.ops.object.text_add(location=(.0, 0, .003))
        tx = bpy.context.active_object
        tx.data.body = "SQUEEZE"
        tx.data.font = bpy.data.fonts.load(str(FONT))
        tx.data.size = .19; tx.data.align_x = "CENTER"; tx.data.align_y = "CENTER"; tx.data.space_character = 1.3
        tx.data.materials.append(principled("mark", (.1, .2, .5), rough=.5, emit=(.2, .4, 1), emit_strength=.5))
        # place it on the disc, between the figure's feet and the camera
        cam = np.array(D["camera"]["pos"])
        ang = math.atan2(cam[2], cam[0])            # three.js azimuth of the camera
        r = .62
        tx.location = (r * math.cos(ang), -r * math.sin(ang), .003)
        tx.rotation_euler = (0, 0, -ang + math.pi / 2)

    # backdrop: a big curved cyclorama wall with a soft blue spotlight on it
    bpy.ops.mesh.primitive_cylinder_add(radius=7, depth=12, vertices=128, location=(0, 0, 6), end_fill_type="NOTHING")
    cyc = bpy.context.active_object
    cm = bpy.data.materials.new("cyc"); cm.use_nodes = True
    nt = cm.node_tree; nt.nodes.clear()
    outn = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission")
    geo = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(geo.outputs["Object"], sep.inputs[0])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0; ramp.color_ramp.elements[0].color = (.006, .02, .07, 1)
    ramp.color_ramp.elements[1].position = .55; ramp.color_ramp.elements[1].color = (.03, .09, .32, 1)
    e3 = ramp.color_ramp.elements.new(1.0); e3.color = (.004, .01, .035, 1)
    mapz = nt.nodes.new("ShaderNodeMapRange")
    mapz.inputs["From Min"].default_value = -6; mapz.inputs["From Max"].default_value = 6
    nt.links.new(sep.outputs["Z"], mapz.inputs["Value"])
    nt.links.new(mapz.outputs["Result"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], em.inputs["Color"])
    em.inputs["Strength"].default_value = 1.0
    nt.links.new(em.outputs[0], outn.inputs["Surface"])
    cyc.data.materials.append(cm)
    cyc.visible_shadow = False

    def area(name, loc, target, size, power, color):
        ld = bpy.data.lights.new(name, "AREA"); ld.size = size; ld.energy = power; ld.color = color
        ob = bpy.data.objects.new(name, ld); sc.collection.objects.link(ob)
        ob.location = loc
        d = np.array(target) - np.array(loc)
        from mathutils import Vector
        ob.rotation_euler = Vector(d.tolist()).to_track_quat("-Z", "Y").to_euler()
        return ob

    # lights are placed relative to the camera so every angle is lit the same way
    cam = np.array(D["camera"]["pos"])
    ang = math.atan2(cam[2], cam[0])
    def around(a, r, h):   # three.js azimuth -> blender xy
        return (r * math.cos(ang + a), -r * math.sin(ang + a), h)
    area("key", around(-.95, 3.0, 4.6), (0, 0, 1.0), .9, 420, (1, .97, .94))
    area("rimL", around(2.3, 2.8, 3.2), (0, 0, 1.1), 1.0, 2200, (.2, .42, 1))
    area("rimR", around(-2.3, 2.8, 3.0), (0, 0, 1.1), 1.0, 1900, (.3, .52, 1))
    area("fill", around(.8, 3.5, 1.2), (0, 0, 1.0), 2.5, 35, (.6, .72, 1))
    area("top", (0, 0, 5.5), (0, 0, 0), 1.2, 60, (.5, .65, 1))


def camera():
    c = D["camera"]
    cd = bpy.data.cameras.new("cam")
    cd.sensor_fit = "VERTICAL"; cd.angle_y = math.radians(c["fov"])
    cam = bpy.data.objects.new("cam", cd); sc.collection.objects.link(cam)
    from mathutils import Vector
    b = lambda v: Vector((v[0], -v[2], v[1]))   # three.js -> blender world
    pos, tgt = b(c["pos"]), b(c["target"])
    cam.location = pos
    cam.rotation_euler = (tgt - pos).to_track_quat("-Z", "Y").to_euler()
    sc.camera = cam


build_body()
for p in D["props"]:
    add_prop(p)
stage()
camera()

r = sc.render
r.engine = "CYCLES"
r.resolution_x, r.resolution_y = RES
r.resolution_percentage = 100
r.film_transparent = False
sc.cycles.device = "CPU"
sc.cycles.samples = SAMPLES
sc.cycles.use_denoising = True
sc.cycles.max_bounces = 6
sc.cycles.diffuse_bounces = 3
sc.cycles.glossy_bounces = 3
sc.cycles.transmission_bounces = 2
sc.view_settings.view_transform = "AgX"
sc.view_settings.look = "AgX - Medium High Contrast"
sc.view_settings.exposure = 0
r.threads_mode = "AUTO"
r.image_settings.file_format = "PNG"
r.filepath = str(pathlib.Path(out).resolve())
bpy.ops.render.render(write_still=True)
print("wrote", out)
