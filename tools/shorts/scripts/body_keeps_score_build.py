import json, subprocess, sys, wave, re
import numpy as np
sys.path.insert(0, "/home/user/Squeeze/tools/shorts")
import make_short as M
from align import align
S = "/tmp/claude-0/-home-user-Squeeze/d02a8e9a-e602-5308-b3af-62ed8e5a01cb/scratchpad/v2"
A = "/root/.claude/uploads/d02a8e9a-e602-5308-b3af-62ed8e5a01cb/90c319b2-ElevenLabs_2026-11-27T18_41_19_Ziad_-_Dynamic_and_Vibrant_pvc_s50_sim75_v4.mp3"
OUT = "/home/user/Squeeze/tools/shorts/out/recut/squeeze-body-keeps-score-short.mp4"
L = .25
vdur = M.probe_duration(A)
v = lambda t: round(t + L, 3)
total = round(vdur + L + 1.3, 3)

def run(*a): subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", *map(str, a)], check=True)
pieces = []
def cut(src, ss, dur, name):
    o = f"{S}/b_{name}.mp4"; run("-ss", ss, "-i", src, "-t", dur, "-an", "-vf", "fps=30,setsar=1", "-c:v", "libx264", "-crf", "16", "-preset", "fast", "-pix_fmt", "yuv420p", o); pieces.append(o)
def still(frame_src, ss, dur, name):
    o = f"{S}/b_{name}.mp4"
    run("-ss", ss, "-i", frame_src, "-frames:v", "1", f"{S}/{name}.png")
    run("-loop", "1", "-framerate", "30", "-t", dur, "-i", f"{S}/{name}.png", "-vf", "boxblur=14:2,eq=brightness=-0.1,format=yuv420p",
        "-c:v", "libx264", "-crf", "16", "-preset", "fast", o); pieces.append(o)

# Every sprint / Every pull / Every push / your body keeps (jump) the score (carry) / app
C = [0, v(1.07), v(2.04), v(3.12), v(3.9), v(4.81)]
for (name, src, ss), a, b in zip([("sprint", "f0", 0), ("pull", "f2", .2), ("push", "f3", .02), ("jump", "f4", .4), ("carry", "f5", .2)], C, C[1:]):
    cut(f"{S}/{src}.mp4", ss, round(b - a, 3), name)
still(f"{S}/f4.mp4", 1.5, round(total - C[-1], 3), "journey")
open(f"{S}/blist.txt", "w").write("".join(f"file '{p}'\n" for p in pieces))
run("-f", "concat", "-safe", "0", "-i", f"{S}/blist.txt", "-c", "copy", f"{S}/background.mp4")

emph = {"sprint", "pull", "push", "score", "free"}
words = [{"text": re.sub(r"[^\w'’]", "", w["text"]), "start": v(w["start"]), "end": v(w["end"]),
          "emph": re.sub(r"[^\w]", "", w["text"].lower()) in emph} for w in align(A, open(f"{S}/vo_text.txt").read())]
scenes = [{"kind": "clip", "start": a, "end": b} for a, b in zip(C, C[1:])]
J = C[-1]
scenes.append({"kind": "cta", "endStyle": "journey", "start": J, "end": total, "headAt": 0,
               "steps": [round(v(t) - J, 3) for t in (5.258, 6.6, 8.614, 9.285, 10.786)], "badge": round(v(13.43) - J, 3)})
tl = {"fps": 30, "duration": total, "words": words, "scenes": scenes, "panel": True, "studio": True, "captionY": 1300}
json.dump(tl, open(f"{S}/final_tl.json", "w"))
subprocess.run(["node", "/home/user/Squeeze/tools/shorts/render.js", f"{S}/final_tl.json", f"{S}/final_overlay.mov", "--overlay"],
               check=True, stdout=subprocess.DEVNULL)

# audio: the voice, a soft bed ducked under it
SR = M.SR
voice = np.zeros(int(total * SR), np.float32)
a = M.decode(A); s0 = int(L * SR); n = min(len(a), len(voice) - s0); voice[s0:s0 + n] += a[:n]
mix = M.mixdown(voice, M.music(total) * .8)
with wave.open(f"{S}/final_mix.wav", "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((np.clip(mix, -1, 1) * 32767).astype("<i2").tobytes())

run("-i", f"{S}/background.mp4", "-i", f"{S}/final_overlay.mov", "-i", f"{S}/final_mix.wav",
    "-filter_complex", "[0:v][1:v]overlay=0:0:format=auto,format=yuv420p[v]", "-map", "[v]", "-map", "2:a",
    "-c:v", "libx264", "-crf", "17", "-preset", "slow", "-r", "30", "-c:a", "aac", "-b:a", "192k",
    "-t", total, "-movflags", "+faststart", OUT)
print(OUT, total)
