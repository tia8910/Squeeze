"""Paints the working muscles red on a white-figure training clip.

    python3 highlight.py in.mp4 out.mp4 --crop x,y,w,h --muscles quads

Works on clips like the Squeeze sculpture videos: a neutral grey/white figure in black
shorts on a blue backdrop. Per frame it finds the figure (neutral, bright), the shorts
(neutral, dark), drops the thin arms with a morphological opening, and marks the leg
area within thigh length of the shorts as quads. The mask is smoothed over time, tinted
red with luminance kept (so the muscle shading shows through), given a soft bloom, and
pulses harder at the bottom of each rep.
"""
import argparse
import subprocess

import numpy as np
from scipy import ndimage as ndi


def disk(r):
    y, x = np.ogrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


def masks(img):
    f = img.astype(np.float32)
    r, g, b = f[..., 0], f[..., 1], f[..., 2]
    lum = (r + g + b) / 3
    neutral = (b - r) < 12
    fig = neutral & (lum > 62)
    shorts = neutral & (lum < 52) & ((b - r) < 9)
    lab, n = ndi.label(shorts)
    if n:
        sizes = ndi.sum(shorts, lab, range(1, n + 1))
        shorts = lab == (1 + int(np.argmax(sizes)))
        shorts = ndi.binary_closing(shorts, disk(4))
        shorts = ndi.binary_fill_holes(shorts)
    return fig, shorts, lum


def quads(fig, shorts, reach):
    if not shorts.any():
        return np.zeros(fig.shape, np.float32)
    body = ndi.binary_opening(fig, disk(10))            # arms and fingers are thinner than legs
    ys = np.nonzero(shorts)[0]
    top, bot = ys.min(), ys.max()
    yy = np.arange(fig.shape[0])[:, None]
    dist = ndi.distance_transform_edt(~shorts)
    w = np.clip((reach - dist) / (reach * .25), 0, 1)
    w *= (yy > top + .45 * (bot - top)) & body & ~shorts
    w = ndi.gaussian_filter(w.astype(np.float32), 2.5)
    return w * ndi.binary_dilation(fig, disk(1))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src"); ap.add_argument("dst")
    ap.add_argument("--crop", default=None)
    ap.add_argument("--muscles", default="quads")
    ap.add_argument("--reach", type=float, default=0.085, help="thigh length as a fraction of frame height")
    a = ap.parse_args()

    probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height,r_frame_rate",
                            "-of", "csv=p=0", a.src], capture_output=True, text=True, check=True).stdout.strip().split(",")
    W, H = int(probe[0]), int(probe[1])
    num, den = probe[2].split("/"); fps = float(num) / float(den)
    cx, cy, cw, ch = [int(v) for v in a.crop.split(",")] if a.crop else (0, 0, W, H)

    rd = subprocess.Popen(["ffmpeg", "-v", "error", "-i", a.src, "-map", "0:v:0", "-vf", f"crop={cw}:{ch}:{cx}:{cy}",
                           "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
    wr = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{cw}x{ch}",
                           "-r", f"{fps}", "-i", "-", "-c:v", "libx264", "-crf", "10", "-preset", "slow",
                           "-pix_fmt", "yuv444p", a.dst], stdin=subprocess.PIPE)
    frames = []
    while True:
        buf = rd.stdout.read(cw * ch * 3)
        if len(buf) < cw * ch * 3:
            break
        frames.append(np.frombuffer(buf, np.uint8).reshape(ch, cw, 3))

    # rep depth from the head height: 0 standing, 1 at the bottom of the squat
    tops = []
    for img in frames:
        fig, _, _ = masks(img)
        rows = np.nonzero(fig.sum(1) > 3)[0]
        tops.append(rows.min() if len(rows) else 0)
    tops = np.convolve(np.array(tops, float), np.ones(5) / 5, "same")
    depth = np.clip((tops - tops.min()) / max(1, np.ptp(tops)), 0, 1)

    reach = a.reach * ch
    prev = None
    red = np.array([1.32, .30, .34], np.float32)
    for i, img in enumerate(frames):
        fig, shorts, lum = masks(img)
        w = quads(fig, shorts, reach) if "quads" in a.muscles else np.zeros(fig.shape, np.float32)
        w = w if prev is None else .55 * w + .45 * prev          # steady across frames
        prev = w
        k = (.62 + .38 * depth[i]) * w
        f = img.astype(np.float32)
        tint = np.clip(lum[..., None] * red, 0, 255)
        out = f * (1 - .85 * k[..., None]) + tint * (.85 * k[..., None])
        glow = ndi.gaussian_filter(k, 7)[..., None] * np.array([255, 40, 60], np.float32) * .35
        out = np.clip(out + glow, 0, 255).astype(np.uint8)
        wr.stdin.write(out.tobytes())
    wr.stdin.close(); wr.wait(); rd.wait()
    print(f"{a.dst}: {len(frames)} frames")


if __name__ == "__main__":
    main()
