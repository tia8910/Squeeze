"""Places the sculpture figure into the rendered studio: a start frame for AI video tools.

    python3 place_figure.py figure.jpg out/squeeze-studio.png out/start-frame.png

The figure image is the white sculpture on a navy background. It is cut out by colour
(neutral white body and black shorts against the blue backdrop), scaled so the head and
feet land where studio.py says a 1.82 m figure stands, and given a contact shadow and a
faint reflection on the glossy stage.
"""
import json
import pathlib
import sys

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage as ndi


def cutout(img):
    a = np.asarray(img.convert("RGB")).astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    lum = (r + g + b) / 3
    m = ((b - r) < 12) & (lum > 16)
    lab, n = ndi.label(m)
    sizes = ndi.sum(m, lab, range(1, n + 1))
    m = lab == (1 + int(np.argmax(sizes)))
    m = ndi.binary_closing(m, iterations=3)
    m = ndi.binary_fill_holes(m)
    # the blue rim light sits on the silhouette edge: grow back over it a little
    m = ndi.binary_dilation(m, iterations=2)
    alpha = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
    out = img.convert("RGBA"); out.putalpha(alpha)
    return out.crop(alpha.getbbox())


def main(fig_path, studio_path, out_path):
    studio = Image.open(studio_path).convert("RGBA")
    place = json.loads(pathlib.Path(studio_path).with_suffix(".json").read_text())
    fig = cutout(Image.open(fig_path))
    (fx, fy), (hx, hy) = place["feet"], place["head"]
    h = int(fy - hy)
    w = int(fig.width * h / fig.height)
    fig = fig.resize((w, h), Image.LANCZOS)
    x0, y0 = int(fx - w / 2), int(fy - h)

    # contact shadow on the stage
    sh = Image.new("L", studio.size, 0)
    from PIL import ImageDraw
    ImageDraw.Draw(sh).ellipse((fx - w * .42, fy - 14, fx + w * .42, fy + 22), fill=170)
    sh = sh.filter(ImageFilter.GaussianBlur(14))
    studio = Image.composite(Image.new("RGBA", studio.size, (0, 2, 8, 255)), studio, sh)

    # faint reflection on the glossy stage, fading quickly
    refl = fig.transpose(Image.FLIP_TOP_BOTTOM)
    fade = np.linspace(.22, 0, refl.height // 3)
    ra = np.asarray(refl.getchannel("A")).astype(np.float32)
    ra[:len(fade)] *= fade[:, None]; ra[len(fade):] = 0
    refl.putalpha(Image.fromarray(ra.astype(np.uint8)))
    studio.alpha_composite(refl, (x0, int(fy) - 4))

    studio.alpha_composite(fig, (x0, y0))
    studio.convert("RGB").save(out_path, quality=95)
    print(out_path, f"figure {w}x{h} at {x0},{y0}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
