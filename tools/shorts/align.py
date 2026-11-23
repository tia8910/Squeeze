"""Word timings for a recorded voiceover (e.g. from ElevenLabs) whose script is known.

No speech model needed: the audio is split where the speaker pauses, the script is split
into phrases at line breaks, "..." and sentence ends, phrase boundaries are snapped to
the nearest pause (in order), and each phrase's words are spread over its speech by
syllable count. Good to within a word or two, which is what one-word captions need.

    python3 align.py voice.mp3 script.txt    # prints JSON [{text, start, end}, ...]
"""
import json
import re
import subprocess
import sys

import numpy as np

SR = 16000
HOP = .01


def load(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32)


def clean(text):
    text = re.sub(r"\[[^\]]*\]", " ", text)          # ElevenLabs v3 audio tags
    return text


def phrases(text):
    out = []
    for line in clean(text).splitlines():
        for part in re.split(r"\.\.\.|…|(?<=[.!?])\s+", line):
            words = re.findall(r"[\w'’×-]+", part)
            if words:
                out.append(words)
    return out


def syllables(word):
    w = word.lower()
    if w.isdigit():
        return max(1, len(w))
    groups = re.findall(r"[aeiouy]+", w)
    n = len(groups) - (1 if w.endswith("e") and len(groups) > 1 and not w.endswith("le") else 0)
    return max(1, n)


def speech_segments(audio, min_gap=.16):
    hop = int(SR * HOP)
    frames = len(audio) // hop
    e = np.array([np.sqrt(np.mean(audio[i * hop:(i + 1) * hop] ** 2) + 1e-12) for i in range(frames)])
    db = 20 * np.log10(e)
    thresh = max(np.percentile(db, 95) - 32, np.percentile(db, 10) + 6)
    speech = db > thresh
    # close tiny gaps inside words, drop tiny blips
    k = int(min_gap / HOP)
    segs, start = [], None
    for i, s in enumerate(np.append(speech, False)):
        if s and start is None:
            start = i
        elif not s and start is not None:
            segs.append([start, i]); start = None
    merged = []
    for s in segs:
        if merged and s[0] - merged[-1][1] < k:
            merged[-1][1] = s[1]
        else:
            merged.append(s)
    merged = [s for s in merged if s[1] - s[0] >= 4]
    return [(a * HOP, b * HOP) for a, b in merged]


def align(audio_path, text):
    audio = load(audio_path)
    segs = speech_segments(audio)
    ph = phrases(text)
    if not segs or not ph:
        return []
    weights = [sum(syllables(w) for w in p) for p in ph]
    total_w = sum(weights)
    seg_len = [b - a for a, b in segs]
    total_s = sum(seg_len)
    # cumulative speech time at the end of each segment (candidate phrase boundaries)
    cum = np.cumsum(seg_len)
    # each phrase boundary goes to the pause nearest its expected position, in order
    bounds = [0]
    used = 0
    for i in range(1, len(ph)):
        target = total_s * sum(weights[:i]) / total_w
        best, best_d = None, 1e9
        for j in range(used, len(segs) - 1):
            d = abs(cum[j] - target)
            if d < best_d:
                best, best_d = j, d
        if best is not None and best_d < .35 * total_s / len(ph) + .4:
            bounds.append(best + 1); used = best + 1
        else:
            bounds.append(None)
    bounds.append(len(segs))

    words = []

    def place(ws, t0, t1):
        syl = [syllables(w) for w in ws]
        tot = sum(syl); t = t0
        for w, s in zip(ws, syl):
            d = (t1 - t0) * s / tot
            words.append({"text": w, "start": round(t, 3), "end": round(t + d, 3)})
            t += d

    # phrases whose boundary did not snap share the span up to the next snapped one
    i = 0
    while i < len(ph):
        j = i + 1
        while bounds[j] is None:
            j += 1
        a, b = bounds[i], bounds[j]
        span = segs[a:b] if b > a else [segs[min(a, len(segs) - 1)]]
        group = ph[i:j]
        # split the span's speech time across the grouped phrases by syllables
        flat = [(w, k) for k, p in enumerate(group) for w in p]
        syl = [syllables(w) for w, _ in flat]
        tot = sum(syl)
        speech = sum(e - s for s, e in span)
        pos = 0.0
        for (w, _), s in zip(flat, syl):
            d = speech * s / tot
            # map speech-time offset to real time across the span's segments
            def real(off):
                acc = 0.0
                for s0, s1 in span:
                    if off <= acc + (s1 - s0):
                        return s0 + (off - acc)
                    acc += s1 - s0
                return span[-1][1]
            words.append({"text": w, "start": round(real(pos), 3), "end": round(real(pos + d), 3)})
            pos += d
        i = j
    return words


if __name__ == "__main__":
    print(json.dumps(align(sys.argv[1], open(sys.argv[2]).read()), indent=1))
