# On-device vision-language model

The app reads body fat from **how a body looks** — whether the abdominal muscles show
through the skin — with OpenAI's CLIP ViT-B/32 running on the phone. It is the one
reading taken from inside the outline, and the only one that can tell a stage-lean
bodybuilder from a man with a soft lower stomach whose waist is the same width.

Nothing leaves the phone. The app holds no `INTERNET` permission.

## What ships

| file | size | where it comes from |
|---|---|---|
| `clip_vitb32_visual_q4.onnx` | 48.5 MB | CLIP image encoder, fetched by `prepare_clip.py` from a pinned URL, SHA-256 verified; matrix weights to 4-bit (blocks of 128), patch convolution to 8-bit. Not in git. |
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

Three photographs, all run locally; no photograph went through CI or anywhere else.

| | true (approx.) | fp32, 351 MB | 8-bit, 88 MB | **as shipped, 48.5 MB** | ResNet-50 8-bit, 39 MB |
|---|---|---|---|---|---|
| stage-lean bodybuilder | ~5% | 7.1% | 7.3% | **7.8%** | 14.8% ✗ |
| man with a soft lower stomach, day 1 | ~16% | 15.2% | 14.6% | **16.3%** | 19.0% |
| same man, day 2 | ~16% | 14.0% | 13.0% | **14.2%** | 20.0% |

(Readings through the phone's preprocessing: pad to square, bilinear resize. The ResNet was
smaller still and read a stage-lean body as ordinary, so it was not used.)

It ranks them correctly under every phrasing tried and with every image mirrored, and it
compresses towards the middle, as zero-shot readings do. That is enough to separate bodies
the outline cannot. It is **not** a validation — three photographs cannot measure an error
— so it carries ±5 and never outranks a tape reading or a band the user picks.

Two things that were tried and do not work: prompts containing numbers ("a man with 15
percent body fat" scored every photo within a point of 16%), and using it for women (the
descriptions are of men; it is switched off for women until there are some for women).

## Licences

CLIP weights: MIT (OpenAI). ONNX export: Jina `clip-as-service` (Apache-2.0).
ONNX Runtime: MIT.

## Regenerating

```
pip install onnx==1.17.0 onnxruntime==1.20.1   # the version the app runs
python tools/vlm/prepare_clip.py   # image encoder into app/src/main/assets
pip install open_clip_torch
python tools/vlm/embed_prompts.py  # after any change to prompts.py
```
