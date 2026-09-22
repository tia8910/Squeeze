# On-device vision-language model

The app reads body fat from **how a body looks** — whether the abdominal muscles show
through the skin — with a CLIP ViT-B/32 model (OpenCLIP, trained on LAION-2B) running on the phone. It is the one
reading taken from inside the outline, and the only one that can tell a stage-lean
bodybuilder from a man with a soft lower stomach whose waist is the same width.

Nothing leaves the phone. The app holds no `INTERNET` permission.

## What ships

| file | size | where it comes from |
|---|---|---|
| `clip_vitb32_laion_q4.onnx` | 48.5 MB | CLIP image encoder (LAION-2B), fetched by `prepare_clip.py` from a pinned URL, SHA-256 verified; matrix weights to 4-bit (blocks of 128), patch convolution to 8-bit. Not in git. |
| `clip_prompts.json` | 73 KB | Text embeddings of the fifteen descriptions in `prompts.py`, made once by `embed_prompts.py`. In git. |

Native code ships for arm64-v8a only (see `app/build.gradle.kts`), which is every phone
Play has required 64-bit support on since 2019.

The text encoder (254 MB) does not ship: the sentences never change, so their embeddings
are computed here instead of on every phone.

## How it reads

The photo is padded to a square, resized to 224 and embedded. Each of three phrasings of
five bodies (stage-lean 5% … overweight 28%) turns the similarities into a probability;
the reading is the expected body fat, averaged over the three phrasings. See
`AppearanceEstimator` in `:core`.

## What it was tested on — and what that does not show

Four photographs, all run locally; no photograph went through CI or anywhere else.

| | true (approx.) | OpenAI B/32 | OpenAI B/16 | LAION-400M B/16 | **LAION-2B B/32, as shipped (48.5 MB)** |
|---|---|---|---|---|---|
| stage-lean bodybuilder | ~5% | 7.1 | 9.6 | 7.0 | **7.7** |
| lean man, abs visible relaxed | ~10% | 13.1 | 12.7 | 11.7 | **10.8** |
| man with a soft lower stomach, day 1 | ~16% | 15.2 | 12.8 | 16.9 | **16.8** |
| same man, day 2 | ~16% | 14.0 | 13.1 | 16.9 | **15.5** |
| average error | | 2.0 | 3.3 | 1.4 | **1.2** |

(First four columns full precision; the last is the compressed file the app ships, through the
phone's preprocessing and onnxruntime 1.20. Mirroring every photo moves no reading by more than
a point.)

The LAION-2B model has OpenAI's architecture and cost but learned from about five times as many
web images — which is where photographs of trained and untrained bodies come from. The B/16
models look at the image in finer patches for four times the compute and were no better.

Four photographs rank correctly and land within about two points; that is enough to trust the
ordering and nowhere near a validation, so the reading carries ±5.

Two things that were tried and do not work: prompts containing numbers ("a man with 15
percent body fat" scored every photo within a point of 16%), and using it for women (the
descriptions are of men; it is switched off for women until there are some for women).

## Licences

CLIP weights: OpenCLIP ViT-B/32 laion2b_s34b_b79k, MIT. ONNX export: Jina `clip-as-service` (Apache-2.0).
ONNX Runtime: MIT.

## Regenerating

```
pip install onnx==1.17.0 onnxruntime==1.20.1   # the version the app runs
python tools/vlm/prepare_clip.py   # image encoder into app/src/main/assets
pip install open_clip_torch
python tools/vlm/embed_prompts.py  # after any change to prompts.py
```
