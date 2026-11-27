"""Puts the figure from a training clip into the rendered Squeeze Fit studio.

    python3 in_studio.py clip.mp4 out/squeeze-studio.png out.mp4

The clip is the white sculpture (black shorts) on a blue backdrop. Each frame is keyed by
colour (see highlight.masks), scaled so the standing figure matches a 1.82 m person on
the stage (from the studio's .json), feet planted on the stage, with a contact shadow and
a soft reflection. Output is 1080x1920 at the clip's frame rate.
"""
import json
import pathlib
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage as ndi

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from highlight import masks  # noqa: E402


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
                          "stream=width,height,r_frame_rate", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True, check=True).stdout.strip().split(",")
    n, d = out[2].split("/")
    return int(out[0]), int(out[1]), float(n) / float(d)


def key(img):
    fig, shorts, _ = masks(img)
    m = fig | shorts
    lab, n = ndi.label(m)
    if n:
        sizes = ndi.sum(m, lab, range(1, n + 1))
        m = lab == (1 + int(np.argmax(sizes)))
    m = ndi.binary_closing(m, iterations=2)
    m = ndi.binary_fill_holes(m)
    return m


def main(clip, studio_path, out):
    W, H, fps = probe(clip)
    studio = Image.open(studio_path).convert("RGB")
    place = json.loads(pathlib.Path(studio_path).with_suffix(".json").read_text())
    (fx, fy), (_, hy) = place["feet"], place["head"]

    rd = subprocess.Popen(["ffmpeg", "-v", "error", "-i", str(clip), "-map", "0:v:0", "-f", "rawvideo",
                           "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
    frames = []
    while True:
        buf = rd.stdout.read(W * H * 3)
        if len(buf) < W * H * 3:
            break
        frames.append(np.frombuffer(buf, np.uint8).reshape(H, W, 3))
    rd.wait()

    # scale and anchor from the first (standing) frame
    m0 = key(frames[0])
    ys, xs = np.nonzero(m0)
    top, foot = ys.min(), ys.max()
    feet_x = xs[ys > foot - (foot - top) * .06].mean()
    k = (fy - hy) / (foot - top)
    SW, SH = int(W * k), int(H * k)
    ox, oy = int(fx - feet_x * k), int(fy - foot * k)

    base = studio.copy().convert("RGBA")
    sh = Image.new("L", studio.size, 0)
    sw = (xs.max() - xs.min()) * k * .5
    ImageDraw.Draw(sh).ellipse((fx - sw, fy - 12, fx + sw, fy + 20), fill=175)
    sh = sh.filter(ImageFilter.GaussianBlur(14))
    base = Image.composite(Image.new("RGBA", studio.size, (0, 2, 8, 255)), base, sh)

    wr = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
                           "-s", f"{studio.width}x{studio.height}", "-r", f"{fps}", "-i", "-",
                           "-c:v", "libx264", "-crf", "14", "-preset", "slow", "-pix_fmt", "yuv420p", str(out)],
                          stdin=subprocess.PIPE)
    for img in frames:
        m = key(img).astype(np.float32)
        alpha = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(.8))
        rgba = Image.fromarray(img).convert("RGBA"); rgba.putalpha(alpha)
        rgba = rgba.resize((SW, SH), Image.LANCZOS)
        frame = base.copy()
        # soft reflection on the glossy stage
        refl = rgba.transpose(Image.FLIP_TOP_BOTTOM)
        ra = np.asarray(refl.getchannel("A")).astype(np.float32)
        ry = int(SH - foot * k)                       # where the feet land in the flipped image
        fade = np.zeros(SH, np.float32)
        span = int(SH * .18)
        fade[ry:ry + span] = np.linspace(.2, 0, max(1, min(span, SH - ry)))[:len(fade[ry:ry + span])]
        refl.putalpha(Image.fromarray((ra * fade[:, None]).astype(np.uint8)))
        frame.paste(refl, (ox, int(fy) - ry), refl)
        frame.paste(rgba, (ox, oy), rgba)
        wr.stdin.write(np.asarray(frame.convert("RGB")).tobytes())
    wr.stdin.close(); wr.wait()
    print(f"{out}: {len(frames)} frames, figure scale {k:.2f}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
