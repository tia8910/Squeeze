# Does a vision model read body fat without being taught?

One experiment, and everything about the AI addon turns on its answer.

## The question

Every attempt in this app to read adiposity from a photograph has been a hand-built
statistic, and each has failed in a new way: a texture score that tracked the lighting, a
contrast score with no zero that read a blank wall at 21.3, a silhouette ratio whose
denominator is the deltoid and so measures training rather than fat. The app currently
reports the same 11.6% for a competition bodybuilder and for a man with a soft midsection,
because none of those signals reaches the answer.

The obvious fix is a model. The obvious objection is that a model needs labelled
photographs, and this corpus starts at zero.

**A zero-shot vision-language model needs none.** CLIP was trained on hundreds of millions
of image-caption pairs and already knows what "visible abs, deeply separated, striated"
looks like. You describe the anchors in words and it scores a photograph against them. The
supervision lives in the prompts, not in a training set.

So: can it order a competition bodybuilder below a soft midsection? If not, a small model
fitted to twenty of one user's photographs certainly cannot, and the corpus is the only
road. If it can, the labelling requirement disappears.

## Running it

Not runnable in the sandbox this was written in: the agent proxy denies `huggingface.co`,
`download.pytorch.org`, GitHub release assets and `dl.fbaipublicfiles.com`, which is every
host the weights live on. It needs a machine with ordinary network access.

```
pip install open_clip_torch torch torchvision pillow
python zeroshot.py                      # ViT-B-32, laion2b
python zeroshot.py ViT-L-14 laion2b_s32b_b82k   # larger, slower, usually sharper
```

It expects three images beside it, which you supply from your own scans:

| file | what it should be |
|---|---|
| `subj_builder.png` | a visibly shredded body, roughly 5% |
| `subj_user_sept20.png` | a soft-midsection body, roughly 16% |
| `subj_user_sept21.png` | the same body on another day |

Crop to the person, loosely. The model wants a photograph, not a landmark box.

## Reading the result

It prints an estimate per subject and the distribution over the five anchors, then says
whether the gap cleared three points. What matters is **the ordering and the size of the
gap**, not whether any single figure is right — an offset that applies to everyone is
removable with one anchor per person, which is the machinery `PersonalCalibration` already
holds. An ordering that is wrong is not removable by anything.

## If it works

Only the image encoder ships. The prompts are fixed, so their text embeddings are computed
once, offline, and travel as five vectors of 512 floats — about 10 KB. The image encoder
quantises to tens of megabytes, which is an APK this size can carry, and MobileCLIP-style
backbones are smaller again.

Inference stays on the device, which is not a promise but a property: this app holds no
`INTERNET` permission, so a photograph cannot leave the phone whatever any code intends.

## If it does not

Then the answer is the corpus, `DefinitionThreshold` is the stage that works at twenty
labelled photographs, and a MobileNet embedder fine-tuned on a hundred is the stage after
that. `storage.googleapis.com/mediapipe-models/` serves those backbones and is reachable
from the sandbox, so that path can be built here in a way this one cannot.
