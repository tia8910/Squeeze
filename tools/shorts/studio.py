"""Renders the Squeeze Fit studio set with Blender Cycles, empty, as a backdrop and a
start frame for AI video tools.

    python studio.py out.png [--samples 96] [--size 1080x1920]

Writes out.png and out.json (the screen position of a 1.82 m figure standing on the
stage, used to place the sculpture into the set).
"""
import json
import math
import pathlib
import sys

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
FONT = ROOT / "app/src/main/res/font/jakarta_extrabold.ttf"
ICON = ROOT / "store/icon-512.png"

args = sys.argv[1:]
out = args[0]
opt = lambda k, d: args[args.index(k) + 1] if k in args else d
SAMPLES = int(opt("--samples", 96))
RES = [int(x) for x in opt("--size", "1080x1920").split("x")]

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
BLUE = (.09, .41, 1.0)          # #1768FF
SKY = (.55, .70, 1.0)           # #8DB3FF


def mat(name, color, rough=.4, metal=0., coat=0., coat_rough=.1, emit=None, strength=0.):
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
        b.inputs["Emission Strength"].default_value = strength
    return m


def obj():
    return bpy.context.active_object


def text(body, size, loc, rot, material, extrude=.0, spacing=1.0):
    bpy.ops.object.text_add(location=loc, rotation=rot)
    t = obj()
    t.data.body = body
    t.data.font = bpy.data.fonts.load(str(FONT))
    t.data.size = size
    t.data.align_x = "CENTER"; t.data.align_y = "CENTER"
    t.data.extrude = extrude
    t.data.space_character = spacing
    t.data.materials.append(material)
    return t


# ── materials ─────────────────────────────────────────────────────────────────
STYLE = opt("--style", "classic")
FLOOR = mat("floor", (.004, .009, .026), rough=.22, metal=.2, coat=.6, coat_rough=.04)
WALL = mat("wall", (.0012, .0028, .009), rough=.85)
STAGE = mat("stage", (.003, .006, .018), rough=.3, coat=.7, coat_rough=.05)
NEON = mat("neon", BLUE, emit=BLUE, strength=5)
NEON_SOFT = mat("neon_soft", BLUE, emit=SKY, strength=1.1)
LETTER = mat("letter", (.85, .88, .95), rough=.22, metal=.85, coat=.6, coat_rough=.05, emit=(.78, .85, 1), strength=1.6)
SILVER = mat("silver", (.82, .84, .88), rough=.28, metal=1.0)
DARK_METAL = mat("dark_metal", (.03, .035, .045), rough=.32, metal=.9)
LETTER_FLOOR = mat("letter_floor", (.1, .2, .5), rough=.5, emit=SKY, strength=1.2)

# ── world: deep navy ───────────────────────────────────────────────────────────
w = bpy.data.worlds.new("w"); sc.world = w
w.use_nodes = True
w.node_tree.nodes["Background"].inputs["Color"].default_value = (.002, .005, .016, 1)


def picture(path, size, loc, glow, rot=(math.radians(90), 0, 0)):
    """A flat image in its true colours: self-lit like a screen, so the studio lights do
    not wash it out."""
    im = bpy.data.materials.new(pathlib.Path(path).stem); im.use_nodes = True
    nt = im.node_tree; N = nt.nodes; N.clear()
    out_ = N.new("ShaderNodeOutputMaterial")
    tex = N.new("ShaderNodeTexImage"); tex.image = bpy.data.images.load(str(path))
    em = N.new("ShaderNodeEmission"); em.inputs["Strength"].default_value = glow
    tr = N.new("ShaderNodeBsdfTransparent")
    mix = N.new("ShaderNodeMixShader")
    nt.links.new(tex.outputs["Color"], em.inputs["Color"])
    nt.links.new(tex.outputs["Alpha"], mix.inputs["Fac"])
    nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out_.inputs["Surface"])
    w_, h_ = tex.image.size
    bpy.ops.mesh.primitive_plane_add(size=1, location=loc, rotation=rot)
    o = obj(); o.scale = (size, size * h_ / w_, 1); o.data.materials.append(im)
    o.visible_shadow = False
    return o


def area(name, loc, target, size, power, color, shape=None, size_y=None):
    ld = bpy.data.lights.new(name, "AREA"); ld.size = size; ld.energy = power; ld.color = color
    if shape:
        ld.shape = shape; ld.size_y = size_y
    o = bpy.data.objects.new(name, ld); sc.collection.objects.link(o)
    o.location = loc
    o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    return o


def spot(name, loc, target, power, angle, blend, color=(1, 1, 1)):
    ld = bpy.data.lights.new(name, "SPOT"); ld.energy = power; ld.spot_size = math.radians(angle)
    ld.spot_blend = blend; ld.color = color; ld.shadow_soft_size = .3
    o = bpy.data.objects.new(name, ld); sc.collection.objects.link(o)
    o.location = loc
    o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()


EMBLEM = HERE / "brand/emblem.png"
BADGE = HERE / "brand/google-play-badge.png"
LOCK_Z = 2.62


def lockup(letters, y=2.985, emblem_glow=1.7, halo=False, badge=True):
    """Emblem + SQUEEZE FIT + the Google Play badge, above head height."""
    picture(EMBLEM, .5, (-.78, y + .005, LOCK_Z + .02), emblem_glow)
    text("SQUEEZE FIT", .27, (.32, y, LOCK_Z + .12), (math.radians(90), 0, 0), letters, extrude=.015, spacing=1.06)
    if halo:
        # halo-lit signage: glowing copies just behind the letters wash the wall in blue
        glow = mat("halo", BLUE, emit=BLUE, strength=5)
        t = text("SQUEEZE FIT", .27, (.32, y + .022, LOCK_Z + .12), (math.radians(90), 0, 0), glow, spacing=1.06)
        t.visible_camera = False; t.visible_glossy = False
    if badge and BADGE.exists():
        picture(BADGE, .52, (.32, y + .005, LOCK_Z - .17), .85)


def wall(material):
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 3.2, 3)); o = obj()
    o.scale = (14, 6, 1); o.rotation_euler = (math.radians(90), 0, 0)
    o.data.materials.append(material)
    return o


def stage(top, ring=NEON, ring_r=1.355, inner=True, z=0.0, words=True):
    bpy.ops.mesh.primitive_cylinder_add(radius=1.35, depth=.08, vertices=160, location=(0, 0, .04 + z))
    obj().data.materials.append(top)
    if ring:
        bpy.ops.mesh.primitive_torus_add(major_radius=ring_r, minor_radius=.012, major_segments=256, minor_segments=10, location=(0, 0, .08 + z))
        obj().data.materials.append(ring)
    if inner:
        bpy.ops.mesh.primitive_torus_add(major_radius=1.15, minor_radius=.004, major_segments=256, minor_segments=8, location=(0, 0, .081 + z))
        obj().data.materials.append(NEON_SOFT)
    if words:
        text("SQUEEZE", .28, (0, -.82, .0815 + z), (0, 0, 0), LETTER_FLOOR, spacing=1.4)


def marble():
    m = bpy.data.materials.new("marble"); m.use_nodes = True
    nt = m.node_tree; N = nt.nodes; b = N["Principled BSDF"]
    wave = N.new("ShaderNodeTexWave"); wave.inputs["Scale"].default_value = 1.1
    wave.inputs["Distortion"].default_value = 4; wave.inputs["Detail"].default_value = 6
    ramp = N.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = .0; ramp.color_ramp.elements[0].color = (.006, .007, .01, 1)
    ramp.color_ramp.elements[1].position = .9; ramp.color_ramp.elements[1].color = (.006, .007, .01, 1)
    e = ramp.color_ramp.elements.new(1.0); e.color = (.06, .065, .08, 1)
    nt.links.new(wave.outputs["Fac"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = .12; b.inputs["Coat Weight"].default_value = 1; b.inputs["Coat Roughness"].default_value = .03
    return m


# ── floor (all styles) ────────────────────────────────────────────────────────
bpy.ops.mesh.primitive_plane_add(size=40); floor = obj(); floor.data.materials.append(FLOOR)

if STYLE == "classic":
    wall(WALL)
    lockup(LETTER)
    for x in (-1.2, 1.2):
        bpy.ops.mesh.primitive_cylinder_add(radius=.03, depth=3.4, location=(x, 1.9, 1.7), vertices=24)
        obj().data.materials.append(NEON)
    stage(STAGE)
    area("key", (-2.2, -3.0, 4.6), (0, 0, 1.0), 1.6, 380, (1, .97, .94))
    area("rimL", (-2.6, 2.2, 3.2), (0, 0, 1.1), 1.0, 1400, (.2, .42, 1))
    area("rimR", (2.6, 2.2, 3.0), (0, 0, 1.1), 1.0, 1300, (.3, .52, 1))
    area("top", (0, .5, 5.5), (0, .5, 0), 2.5, 160, (.55, .68, 1))

elif STYLE == "gallery":
    # Midnight Gallery: matte black, one soft spotlight beam through light haze
    w.node_tree.nodes["Background"].inputs["Color"].default_value = (.0008, .001, .002, 1)
    wall(mat("black_wall", (.0015, .0017, .0022), rough=.95))
    floor.data.materials[0] = mat("concrete", (.004, .0045, .006), rough=.38, coat=.35, coat_rough=.12)
    lockup(SILVER, emblem_glow=1.1)
    stage(mat("black_stage", (.002, .002, .003), rough=.25, coat=.8, coat_rough=.05),
          ring=mat("edge", (1, 1, 1), emit=(.75, .82, 1), strength=2.2), inner=False, words=False)
    spot("beam", (0, -.3, 5.8), (0, 0, .9), 4200, 24, .35, (1, .97, .93))
    area("logo_wash", (0, 2.4, 3.4), (0, 3.2, 2.6), .8, 18, (.8, .86, 1))
    area("rimL", (-2.0, 1.4, 2.6), (0, 0, 1.1), .6, 380, (.35, .5, 1))
    area("rimR", (2.0, 1.4, 2.6), (0, 0, 1.1), .6, 380, (.35, .5, 1))
    # haze, only inside a box around the stage so the beam shows
    # a cone of haze under the spotlight only, so the beam shows and the room stays black
    bpy.ops.mesh.primitive_cone_add(radius1=1.5, radius2=.12, depth=5.6, vertices=64, location=(0, -.1, 2.85)); hz = obj()
    vm = bpy.data.materials.new("haze"); vm.use_nodes = True; vn = vm.node_tree.nodes
    vn.remove(vn["Principled BSDF"]); pv = vn.new("ShaderNodeVolumePrincipled")
    pv.inputs["Density"].default_value = .035
    vm.node_tree.links.new(pv.outputs[0], vn["Material Output"].inputs["Volume"])
    hz.data.materials.append(vm)

elif STYLE == "halo":
    # Halo: a large glowing ring on the wall frames the athlete, launch-stage style
    wall(mat("navy_wall", (.0018, .0035, .011), rough=.7))
    lockup(SILVER, emblem_glow=1.5)
    bpy.ops.mesh.primitive_torus_add(major_radius=1.12, minor_radius=.022, major_segments=256, minor_segments=12,
                                     location=(0, 3.1, 1.12), rotation=(math.radians(90), 0, 0))
    obj().data.materials.append(mat("halo_ring", BLUE, emit=BLUE, strength=9))
    bpy.ops.mesh.primitive_torus_add(major_radius=1.19, minor_radius=.006, major_segments=256, minor_segments=8,
                                     location=(0, 3.12, 1.12), rotation=(math.radians(90), 0, 0))
    obj().data.materials.append(mat("halo_outer", SKY, emit=SKY, strength=2.5))
    stage(STAGE, words=True)
    area("key", (-2.0, -3.0, 4.6), (0, 0, 1.0), 1.4, 340, (1, .97, .94))
    area("rimL", (-2.4, 2.0, 3.0), (0, 0, 1.1), .9, 900, (.25, .45, 1))
    area("rimR", (2.4, 2.0, 3.0), (0, 0, 1.1), .9, 900, (.25, .45, 1))

elif STYLE == "fluted":
    # Fluted wall: vertical ribs lit from above, halo-lit metal letters, black marble stage
    wall(mat("rib_back", (.002, .003, .008), rough=.6))
    rib = mat("ribs", (.006, .011, .028), rough=.42, coat=.2)
    bpy.ops.mesh.primitive_cylinder_add(radius=.045, depth=6.5, vertices=24, location=(-3.2, 3.15, 3.0))
    r_ = obj(); r_.data.materials.append(rib)
    arr = r_.modifiers.new("ribs", "ARRAY"); arr.count = 72; arr.relative_offset_displace = (1.0, 0, 0)
    arr.use_relative_offset = True
    lockup(LETTER, y=3.03, emblem_glow=1.5)
    stage(marble(), ring=mat("edge", BLUE, emit=BLUE, strength=4), inner=False, words=False)
    area("graze", (0, 2.75, 5.2), (0, 3.15, 0), 7.0, 900, (.75, .83, 1), shape="RECTANGLE", size_y=.25)
    area("key", (-2.0, -3.0, 4.6), (0, 0, 1.0), 1.4, 320, (1, .97, .94))
    area("rimL", (-2.4, 2.0, 3.0), (0, 0, 1.1), .9, 800, (.25, .45, 1))
    area("rimR", (2.4, 2.0, 3.0), (0, 0, 1.1), .9, 800, (.25, .45, 1))

elif STYLE == "cove":
    # Infinity cove: seamless blue-to-black backdrop, a floating glossy stage, a giant faint emblem
    bpy.ops.mesh.primitive_cylinder_add(radius=9, depth=14, vertices=128, location=(0, 0, 7), end_fill_type="NOTHING")
    cyc = obj()
    cm = bpy.data.materials.new("cove"); cm.use_nodes = True; nt = cm.node_tree; N = nt.nodes; N.clear()
    o_ = N.new("ShaderNodeOutputMaterial"); em = N.new("ShaderNodeEmission")
    tc = N.new("ShaderNodeTexCoord"); sp = N.new("ShaderNodeSeparateXYZ"); mr = N.new("ShaderNodeMapRange")
    ramp = N.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0; ramp.color_ramp.elements[0].color = (.0015, .004, .014, 1)
    ramp.color_ramp.elements[1].position = .22; ramp.color_ramp.elements[1].color = (.02, .07, .28, 1)
    e3 = ramp.color_ramp.elements.new(.6); e3.color = (.001, .002, .007, 1)
    nt.links.new(tc.outputs["Object"], sp.inputs[0]); nt.links.new(sp.outputs["Z"], mr.inputs["Value"])
    mr.inputs["From Min"].default_value = -7; mr.inputs["From Max"].default_value = 7
    nt.links.new(mr.outputs["Result"], ramp.inputs["Fac"]); nt.links.new(ramp.outputs["Color"], em.inputs["Color"])
    nt.links.new(em.outputs[0], o_.inputs["Surface"])
    cyc.data.materials.append(cm); cyc.visible_shadow = False
    floor.data.materials[0] = mat("gloss_black", (.002, .003, .008), rough=.08, coat=1, coat_rough=.02)
    lockup(SILVER, y=3.0, emblem_glow=1.5)
    stage(mat("float_stage", (.002, .002, .004), rough=.15, coat=1, coat_rough=.03),
          ring=mat("edge", BLUE, emit=BLUE, strength=6), inner=False, words=False, z=.14)
    area("key", (-2.0, -3.0, 4.6), (0, 0, 1.0), 1.4, 340, (1, .97, .94))
    area("rimL", (-2.4, 2.0, 3.0), (0, 0, 1.1), .9, 900, (.25, .45, 1))
    area("rimR", (2.4, 2.0, 3.0), (0, 0, 1.1), .9, 900, (.25, .45, 1))

# ── camera: front, chest height, full body on the stage with room above ──────
cd = bpy.data.cameras.new("cam"); cd.sensor_fit = "VERTICAL"; cd.angle_y = math.radians(34)
cam = bpy.data.objects.new("cam", cd); sc.collection.objects.link(cam)
cam.location = (0, -6.88, 1.4)
cam.rotation_euler = (Vector((0, 0, 1.46)) - cam.location).to_track_quat("-Z", "Y").to_euler()
sc.camera = cam

r = sc.render
r.engine = "CYCLES"; r.resolution_x, r.resolution_y = RES; r.resolution_percentage = 100
sc.cycles.device = "CPU"; sc.cycles.samples = SAMPLES; sc.cycles.use_denoising = True
sc.cycles.max_bounces = 6
sc.view_settings.view_transform = "AgX"; sc.view_settings.look = "AgX - Medium High Contrast"
r.image_settings.file_format = "PNG"; r.filepath = str(pathlib.Path(out).resolve())

bpy.context.view_layer.update()
# where a 1.82 m figure standing on the stage lands on screen
def px(p):
    v = world_to_camera_view(sc, cam, Vector(p))
    return [v.x * RES[0], (1 - v.y) * RES[1]]
lift = .14 if STYLE == "cove" else 0
place = {"feet": px((0, 0, .08 + lift)), "head": px((0, 0, 1.90 + lift))}
pathlib.Path(out).with_suffix(".json").write_text(json.dumps(place))

bpy.ops.render.render(write_still=True)
print("wrote", out, place)
