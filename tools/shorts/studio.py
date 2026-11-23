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
FLOOR = mat("floor", (.004, .009, .026), rough=.22, metal=.2, coat=.6, coat_rough=.04)
WALL = mat("wall", (.006, .014, .04), rough=.55)
PANEL = mat("panel", (.002, .004, .013), rough=.45, coat=.2)
STAGE = mat("stage", (.003, .006, .018), rough=.3, coat=.7, coat_rough=.05)
NEON = mat("neon", BLUE, emit=BLUE, strength=9)
NEON_SOFT = mat("neon_soft", BLUE, emit=SKY, strength=2.5)
LETTER = mat("letter", (.9, .93, 1), rough=.3, emit=(.82, .88, 1), strength=4.5)
LETTER_FLOOR = mat("letter_floor", (.1, .2, .5), rough=.5, emit=SKY, strength=1.2)

# ── world: deep navy ───────────────────────────────────────────────────────────
w = bpy.data.worlds.new("w"); sc.world = w
w.use_nodes = True
w.node_tree.nodes["Background"].inputs["Color"].default_value = (.002, .005, .016, 1)

# ── floor and the back wall ───────────────────────────────────────────────────
bpy.ops.mesh.primitive_plane_add(size=40); obj().data.materials.append(FLOOR)
bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 3.2, 3)); wall = obj()
wall.scale = (14, 1, 6); wall.rotation_euler = (math.radians(90), 0, 0)
wall.data.materials.append(WALL)
# two thin neon seams on the wall
for x in (-.95, .95):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x, 3.15, 2.2)); s_ = obj()
    s_.scale = (.014, .02, 4.4); s_.data.materials.append(NEON_SOFT)


def picture(path, size, loc, glow):
    """A flat image on the wall in its true colours: self-lit like a screen, so the studio
    lights do not wash it out."""
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
    w, h = tex.image.size
    bpy.ops.mesh.primitive_plane_add(size=1, location=loc, rotation=(math.radians(90), 0, 0))
    o = obj(); o.scale = (size, size * h / w, 1); o.data.materials.append(im)
    o.visible_shadow = False
    return o


# logo lockup above head height, straight on the wall: full-colour app icon, the
# wordmark, and the Google Play badge underneath
LOCK_Z = 3.0
if ICON.exists():
    picture(ICON, .56, (-.93, 2.99, LOCK_Z + .02), .9)
text("SQUEEZE FIT", .27, (.3, 2.985, LOCK_Z + .19), (math.radians(90), 0, 0), LETTER, extrude=.015, spacing=1.06)
BADGE = HERE / "brand/google-play-badge.png"
if BADGE.exists():
    picture(BADGE, .86, (.3, 2.99, LOCK_Z - .17), .9)

# tall neon light bars framing the stage
for x in (-1.2, 1.2):
    bpy.ops.mesh.primitive_cylinder_add(radius=.03, depth=3.4, location=(x, 1.9, 1.7), vertices=24)
    obj().data.materials.append(NEON)

# round stage with a glowing ring and the wordmark facing the camera
bpy.ops.mesh.primitive_cylinder_add(radius=1.35, depth=.08, vertices=160, location=(0, 0, .04))
obj().data.materials.append(STAGE)
bpy.ops.mesh.primitive_torus_add(major_radius=1.355, minor_radius=.014, major_segments=256, minor_segments=10, location=(0, 0, .08))
obj().data.materials.append(NEON)
bpy.ops.mesh.primitive_torus_add(major_radius=1.15, minor_radius=.004, major_segments=256, minor_segments=8, location=(0, 0, .081))
obj().data.materials.append(NEON_SOFT)
text("SQUEEZE", .28, (0, -.82, .0815), (0, 0, 0), LETTER_FLOOR, spacing=1.4)

# ── lights ────────────────────────────────────────────────────────────────────
def area(name, loc, target, size, power, color):
    ld = bpy.data.lights.new(name, "AREA"); ld.size = size; ld.energy = power; ld.color = color
    o = bpy.data.objects.new(name, ld); sc.collection.objects.link(o)
    o.location = loc
    o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()


area("key", (-2.2, -3.0, 4.6), (0, 0, 1.0), 1.6, 380, (1, .97, .94))
area("rimL", (-2.6, 2.2, 3.2), (0, 0, 1.1), 1.0, 1400, (.2, .42, 1))
area("rimR", (2.6, 2.2, 3.0), (0, 0, 1.1), 1.0, 1300, (.3, .52, 1))
area("top", (0, .5, 5.5), (0, .5, 0), 2.5, 160, (.55, .68, 1))
area("wallwash", (0, 1.4, .2), (0, 3.2, 2.4), 3.0, 25, (.15, .35, 1))

# ── camera: front, chest height, full body on the stage with room above ──────
cd = bpy.data.cameras.new("cam"); cd.sensor_fit = "VERTICAL"; cd.angle_y = math.radians(34)
cam = bpy.data.objects.new("cam", cd); sc.collection.objects.link(cam)
cam.location = (0, -6.2, 1.3)
cam.rotation_euler = (Vector((0, 0, 1.25)) - cam.location).to_track_quat("-Z", "Y").to_euler()
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
place = {"feet": px((0, 0, .08)), "head": px((0, 0, 1.90))}
pathlib.Path(out).with_suffix(".json").write_text(json.dumps(place))

bpy.ops.render.render(write_still=True)
print("wrote", out, place)
