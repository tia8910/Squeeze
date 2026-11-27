import json, subprocess, sys, wave, re
import numpy as np
sys.path.insert(0, "/home/user/Squeeze/tools/shorts")
import make_short as M
from align import align
S = "/tmp/claude-0/-home-user-Squeeze/d02a8e9a-e602-5308-b3af-62ed8e5a01cb/scratchpad/v2"
A = "/root/.claude/uploads/d02a8e9a-e602-5308-b3af-62ed8e5a01cb/90c319b2-ElevenLabs_2026-11-27T18_41_19_Ziad_-_Dynamic_and_Vibrant_pvc_s50_sim75_v4.mp3"
OUT = "/home/user/Squeeze/tools/shorts/out/recut/squeeze-mirror-lies-short.mp4"
L = .25
vdur = M.probe_duration(A)
T_JOUR, T_RUN, T_PULL, T_PUSH, T_JUMP, T_CARRY, T_FINAL = 3.12, 10.58, 10.82, 11.45, 11.99, 12.57, 13.43
v = lambda t: round(t + L, 3)
total = round(vdur + L + 1.3, 3)

def run(*a): subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", *map(str, a)], check=True)
# background pieces (all 1080x1920, 30 fps)
pieces = []
def cut(src, ss, dur, name):
    o = f"{S}/b_{name}.mp4"; run("-ss", ss, "-i", src, "-t", dur, "-an", "-vf", "fps=30,setsar=1", "-c:v", "libx264", "-crf", "16", "-preset", "fast", "-pix_fmt", "yuv420p", o); pieces.append(o)
def still(frame_src, ss, dur, name):
    o = f"{S}/b_{name}.mp4"
    run("-ss", ss, "-i", frame_src, "-frames:v", "1", f"{S}/{name}.png")
    run("-loop", "1", "-framerate", "30", "-t", dur, "-i", f"{S}/{name}.png", "-vf", "boxblur=14:2,eq=brightness=-0.1,format=yuv420p",
        "-c:v", "libx264", "-crf", "16", "-preset", "fast", o); pieces.append(o)
hook = v(T_JOUR)
cut(f"{S}/f0.mp4", 0, 2.4, "hook1"); cut(f"{S}/f1.mp4", 0, .5, "hook2"); cut(f"{S}/f2.mp4", 0, round(hook - 2.9, 3), "hook3")
still(f"{S}/f3.mp4", .5, round(v(T_RUN) - hook, 3), "journey")
for name, src, ss, a, b in [("run", "f0", 1.2, T_RUN, T_PULL), ("pull", "f2", .8, T_PULL, T_PUSH), ("push", "f3", .2, T_PUSH, T_JUMP),
                            ("jump", "f4", .6, T_JUMP, T_CARRY), ("carry", "f5", .3, T_CARRY, T_FINAL)]:
    cut(f"{S}/{src}.mp4", ss, round(b - a, 3), name)
still(f"{S}/f5.mp4", 1.2, round(total - v(T_FINAL), 3), "final")
open(f"{S}/blist.txt", "w").write("".join(f"file '{p}'\n" for p in pieces))
run("-f", "concat", "-safe", "0", "-i", f"{S}/blist.txt", "-c", "copy", f"{S}/background.mp4")

# words from the voice
emph = {"doesn't", "carry", "free"}
words = [{"text": re.sub(r"[^\w'’]", "", w["text"]), "start": v(w["start"]), "end": v(w["end"]),
          "emph": w["text"].lower().strip(".!") in emph} for w in align(A, open(f"{S}/vo_text.txt").read())]
# the five movement words land exactly on their cuts
for w, t in zip([w for w in words if w["text"] in ("Run", "Pull", "Push", "Jump", "Carry")], [T_RUN, T_PULL, T_PUSH, T_JUMP, T_CARRY]):
    w["start"] = v(t)
step_words = [3.53, 5.49, 6.71, 7.33, 8.35]          # scans, tracks, training, meals, that photo
scenes = [{"kind": "clip", "start": 0, "end": hook},
          {"kind": "cta", "endStyle": "journey", "start": hook, "end": v(T_RUN), "headAt": 0,
           "steps": [round(v(t) - hook, 3) for t in step_words], "badge": 99}]
cuts = [T_RUN, T_PULL, T_PUSH, T_JUMP, T_CARRY, T_FINAL]
for a, b in zip(cuts, cuts[1:]):
    scenes.append({"kind": "clip", "start": v(a), "end": v(b)})
scenes.append({"kind": "cta", "endStyle": "journey", "start": v(T_FINAL), "end": total, "headAt": -1,
               "steps": [-1] * 5, "badge": round(v(13.75) - v(T_FINAL), 3)})
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
