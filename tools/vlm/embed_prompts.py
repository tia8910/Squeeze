"""Writes app/src/main/assets/clip_prompts.json, clip_muscles.json and clip_equipment.json:
the text side of the on-device model.

The prompts are fixed, so their embeddings are computed once, here, and shipped as numbers.
The phone then needs only the image encoder — the text encoder is 254 MB and would do the
same arithmetic on the same fifteen sentences every time.

    pip install onnxruntime open_clip_torch
    python tools/vlm/embed_prompts.py
"""
import hashlib
import json
import pathlib
import sys
import urllib.request

import numpy as np
import onnxruntime as ort
import open_clip

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from prompts import PROMPT_SETS  # noqa: E402
from muscle_prompts import MUSCLE_PROMPTS  # noqa: E402
from equipment_prompts import EQUIPMENT_PROMPTS  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
URL = ("https://clip-as-service.s3.us-east-2.amazonaws.com/"
       "models-436c69702d61732d53657276696365/onnx/ViT-B-32-laion2b-s34b-b79k/textual.onnx")
SHA256 = "fc165a1458d398a241f7f01519d70f854ef0ec7a9685a54648ca9f829b9cb80e"
CACHE = pathlib.Path.home() / ".cache" / "squeeze" / "clip_vitb32_laion_textual.onnx"


def fetch():
    if CACHE.exists() and hashlib.sha256(CACHE.read_bytes()).hexdigest() == SHA256:
        return CACHE
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(URL, CACHE)
    actual = hashlib.sha256(CACHE.read_bytes()).hexdigest()
    if actual != SHA256:
        CACHE.unlink()
        sys.exit(f"checksum mismatch for the text encoder: {actual}")
    return CACHE


def embed(session, tokenizer, texts):
    ids = tokenizer(texts).numpy().astype(np.int32)
    out = session.run(None, {"input_ids": ids, "attention_mask": (ids != 0).astype(np.int32)})[0]
    out = out / np.linalg.norm(out, axis=1, keepdims=True)
    return [[round(float(x), 6) for x in row] for row in out]


def write(name, doc):
    target = ROOT / "app" / "src" / "main" / "assets" / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(doc, separators=(",", ":")) + "\n")
    print(f"wrote {target} ({target.stat().st_size} bytes)")


def main():
    session = ort.InferenceSession(str(fetch()))
    tokenizer = open_clip.get_tokenizer("ViT-B-32")
    sets = []
    for prompt_set in PROMPT_SETS:
        texts = [p for _, p in prompt_set]
        ids = tokenizer(texts).numpy().astype(np.int32)
        out = session.run(None, {"input_ids": ids, "attention_mask": (ids != 0).astype(np.int32)})[0]
        out = out / np.linalg.norm(out, axis=1, keepdims=True)
        sets.append({
            "anchors": [float(v) for v, _ in prompt_set],
            "prompts": texts,
            "embeddings": [[round(float(x), 6) for x in row] for row in out],
        })
    doc = {
        "model": "OpenCLIP ViT-B/32 trained on LAION-2B (MIT), ONNX export from Jina clip-as-service",
        "logitScale": 100.0,
        "sets": sets,
    }
    write("clip_prompts.json", doc)

    groups = {}
    for group, pairs in MUSCLE_PROMPTS.items():
        groups[group] = [
            {"developed": strong, "undeveloped": weak,
             "embeddings": embed(session, tokenizer, [strong, weak])}
            for strong, weak in pairs
        ]
    write("clip_muscles.json", {
        "model": doc["model"],
        "logitScale": 100.0,
        "groups": groups,
    })

    write("clip_equipment.json", {
        "model": doc["model"],
        "logitScale": 100.0,
        "machines": {
            machine: embed(session, tokenizer, prompts)
            for machine, prompts in EQUIPMENT_PROMPTS.items()
        },
    })


if __name__ == "__main__":
    main()
