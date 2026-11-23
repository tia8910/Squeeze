"""Turns a training clip into a branded 1080x1920 Squeeze Fit short.

    python3 tools/shorts/make_short.py tools/shorts/scripts/squat.json

The script JSON names the clip, how to crop it, and the scenes (an exercise scene with its
title, dose, muscles, narration and rep times, then a call to action). This builds:

  1. a neural voiceover per scene with word timings (edge-tts), for one-word captions
  2. the overlay layer (titles, tags, rep counter, captions, end card) as an alpha video
  3. the branded backdrop and the clip as a feathered square panel
  4. a soft synthesized music bed, ducked under the voice
and composites them into out/<id>.mp4 (H.264, AAC, 30 fps).
"""
import asyncio
import json
import pathlib
import re
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
W, H, FPS, SR = 1080, 1920, 30, 44100


def run(*cmd, **kw):
    subprocess.run([str(c) for c in cmd], check=True, **kw)


def probe_duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True, check=True).stdout
    return float(out.strip())


# ── voice ─────────────────────────────────────────────────────────────────────
async def tts(text, voice, rate, mp3):
    import certifi
    ca = pathlib.Path("/root/.ccr/ca-bundle.crt")
    if ca.exists():                         # behind the session's TLS proxy
        certifi.where = lambda: str(ca)
    import edge_tts
    words = []
    c = edge_tts.Communicate(text, voice, rate=rate, boundary="WordBoundary")
    with open(mp3, "wb") as f:
        async for ch in c.stream():
            if ch["type"] == "audio":
                f.write(ch["data"])
            elif ch["type"] == "WordBoundary":
                words.append({"text": ch["text"], "start": ch["offset"] / 1e7, "end": (ch["offset"] + ch["duration"]) / 1e7})
    return words


def decode(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


# ── music: a soft minor pulse at 100 bpm that sits under the voice ───────────
def music(seconds):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    beat = 60 / 100
    out = np.zeros(n, np.float32)
    roots = [57, 53, 48, 55]                                # A F C G
    for bar in range(int(seconds / (beat * 4)) + 1):
        f0 = 440 * 2 ** ((roots[bar % 4] - 69) / 12)
        s, e = int(bar * 4 * beat * SR), min(n, int((bar + 1) * 4 * beat * SR))
        if s >= n:
            break
        tt = t[s:e] - t[s]
        env = np.minimum(1, tt / .4) * np.minimum(1, (tt[-1] - tt + 1e-3) / .4)
        pad = sum(np.sin(2 * np.pi * f0 * m * tt) * a for m, a in ((1, .5), (1.5, .25), (2, .2), (3, .06)))
        out[s:e] += (pad * env * .09).astype(np.float32)
        for k in range(8):                                  # eighth-note bass pulse
            ps = s + int(k * beat / 2 * SR)
            pe = min(e, ps + int(beat / 2 * SR))
            pt = t[ps:pe] - t[ps]
            out[ps:pe] += (np.sin(2 * np.pi * f0 / 2 * pt) * np.exp(-pt * 9) * .16).astype(np.float32)
    for k in range(int(seconds / beat) + 1):                # kick
        ks = int(k * beat * SR); ke = min(n, ks + int(.25 * SR))
        if ks >= n:
            break
        kt = t[ks:ke] - t[ks]
        out[ks:ke] += (np.sin(2 * np.pi * (45 + 90 * np.exp(-kt * 30)) * kt) * np.exp(-kt * 14) * .5).astype(np.float32)
    rng = np.random.default_rng(7)
    for k in range(int(seconds / beat * 2) + 1):            # off-beat hat
        hs = int((k + .5) * beat / 2 * SR); he = min(n, hs + int(.05 * SR))
        if hs >= n:
            break
        out[hs:he] += (rng.standard_normal(he - hs) * np.exp(-np.arange(he - hs) / SR * 70) * .05).astype(np.float32)
    fade = int(1.2 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)
    out[:int(.3 * SR)] *= np.linspace(0, 1, int(.3 * SR))
    return out


def mixdown(voice, bed):
    env = np.abs(voice)
    k = int(.15 * SR)
    env = np.convolve(env, np.ones(k) / k, "same")
    duck = 1 - .7 * np.clip(env / .05, 0, 1)
    mix = voice * 1.0 + bed * .55 * duck
    return mix / max(1.0, np.abs(mix).max() / .95)


# ── backdrop ──────────────────────────────────────────────────────────────────
def backdrop(path):
    y = np.linspace(0, 1, H)[:, None]
    top, mid, bot = np.array([4, 12, 34]), np.array([10, 33, 96]), np.array([3, 8, 24])
    col = np.where(y < .45, top + (mid - top) * (y / .45), mid + (bot - mid) * ((y - .45) / .55))
    img = np.repeat(col[:, None, :], W, axis=1).reshape(H, W, 3)
    xx, yy = np.meshgrid(np.arange(W), np.arange(H))
    spot = np.exp(-(((xx - W / 2) / 520) ** 2 + ((yy - 960) / 620) ** 2))
    img = img + spot[..., None] * np.array([40, 80, 190]) * .55
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    # faint diagonal brand lines
    lines = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(lines)
    for x in range(-H, W, 90):
        d.line([(x, H), (x + H, 0)], fill=14, width=2)
    im = Image.composite(Image.new("RGB", (W, H), (141, 179, 255)), im, lines)
    im.save(path)


def panel_mask(path, size, feather):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle((feather, feather, size - feather, size - feather), radius=60, fill=255)
    m.filter(ImageFilter.GaussianBlur(feather / 2)).save(path)


# ── main ──────────────────────────────────────────────────────────────────────
def main(script_path):
    S = json.loads(pathlib.Path(script_path).read_text())
    clip = pathlib.Path(S["clip"])
    if not clip.is_absolute():
        clip = (pathlib.Path(script_path).parent / clip).resolve()
    out_dir = HERE / "out"; out_dir.mkdir(exist_ok=True)
    work = pathlib.Path(tempfile.mkdtemp(prefix="short-"))
    clip_dur = probe_duration(clip)
    emph = {w.lower() for w in S.get("emphasis", [])}

    # 1) voice, per scene
    words, voices, t = [], [], 0.0
    scenes = []
    for i, sc in enumerate(S["scenes"]):
        mp3 = work / f"vo{i}.mp3"
        ws = asyncio.run(tts(sc["narration"], S.get("voice", "en-US-AndrewNeural"), S.get("rate", "+6%"), mp3))
        vdur = probe_duration(mp3)
        lead = .25
        if sc["kind"] == "exercise":
            dur = max(clip_dur * S.get("speed", 1.0), vdur + lead + .3)
        else:
            dur = max(vdur + lead + 1.0, sc.get("min", 3.2))
        for w in ws:
            text = re.sub(r"[^\w'×-]", "", w["text"])
            words.append({"text": text, "start": t + lead + w["start"], "end": t + lead + w["end"],
                          "emph": text.lower() in emph or text.isdigit()})
        voices.append((t + lead, mp3))
        scenes.append({**sc, "start": round(t, 3), "end": round(t + dur, 3)})
        t += dur
    total = t
    for sc in scenes:
        sc.pop("narration", None)

    # 2) overlay layer
    tl = {"fps": FPS, "duration": total, "words": words, "scenes": scenes,
          "panel": True, "captionY": S.get("captionY", 1655), "repsAt": S.get("repsAt", [905, 560]),
          "brandAt": S.get("brandAt", [70, 1440])}
    tl_path = work / "timeline.json"; tl_path.write_text(json.dumps(tl))
    overlay = work / "overlay.mov"
    run("node", HERE / "render.js", tl_path, overlay, "--overlay", cwd=HERE)

    # 3) audio
    voice = np.zeros(int(total * SR) + SR, np.float32)
    for at, mp3 in voices:
        a = decode(mp3); s = int(at * SR)
        voice[s:s + len(a)] += a[:len(voice) - s]
    voice = voice[:int(total * SR)]
    mix = mixdown(voice, music(total))
    wav = work / "mix.wav"
    pcm = (np.clip(mix, -1, 1) * 32767).astype("<i2").tobytes()
    import wave
    with wave.open(str(wav), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm)

    # 4) composite: backdrop + feathered clip panel + overlay + audio
    bg = work / "bg.png"; backdrop(bg)
    P = S.get("panel", {"y": 440, "size": 1080})
    size, py = P["size"], P["y"]
    mask = work / "mask.png"; panel_mask(mask, size, 40)
    cx, cy, cw, ch = S["crop"]
    speed = S.get("speed", 1.0)
    vf = (f"[1:v]crop={cw}:{ch}:{cx}:{cy},scale={size}:{size}:flags=lanczos,unsharp=5:5:.6,"
          f"setpts={speed}*PTS,fps={FPS},tpad=stop_mode=clone:stop_duration={total},trim=duration={total},"
          f"eq=contrast=1.04:saturation=1.05,format=rgba[clip];"
          f"[2:v]format=gray,scale={size}:{size}[m];[clip][m]alphamerge[panel];"
          f"[0:v][panel]overlay=x=(W-w)/2:y={py}:shortest=0[base];"
          f"[base][3:v]overlay=0:0:format=auto,format=yuv420p[v]")
    out = out_dir / f"{S['id']}.mp4"
    run("ffmpeg", "-v", "error", "-y",
        "-loop", "1", "-framerate", FPS, "-t", total, "-i", bg,
        "-i", clip,
        "-loop", "1", "-t", total, "-i", mask,
        "-i", overlay,
        "-i", wav,
        "-filter_complex", vf, "-map", "[v]", "-map", "4:a",
        "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-r", FPS,
        "-c:a", "aac", "-b:a", "192k", "-t", total, "-movflags", "+faststart", out)
    print(f"{out}  ({total:.1f} s)")


if __name__ == "__main__":
    main(sys.argv[1])
