"""Zero-shot body-fat reading with CLIP, tested on the three photographs in this thread.

The question this answers: can a general-purpose vision model, with no labelled corpus at
all, order a competition-lean bodybuilder below a man with a soft midsection? If it cannot,
the AI addon does not work and nothing downstream matters. If it can, the labels stop being
a blocker, because the prompts carry the supervision instead.
"""
import sys
import torch
import open_clip
from PIL import Image

MODEL = sys.argv[1] if len(sys.argv) > 1 else "ViT-B-32"
PRETRAINED = sys.argv[2] if len(sys.argv) > 2 else "laion2b_s34b_b79k"

# Anchors, each with the body fat it represents. The weighted average over the softmax is
# the estimate, so the wording matters as much as a training label would.
ANCHORS = [
    (5.0, "a photograph of a competition bodybuilder with about 5 percent body fat, "
          "extremely lean, deeply separated abdominal muscles, visible veins, striated"),
    (10.0, "a photograph of a very lean athletic man with about 10 percent body fat, "
           "a clearly visible six pack, defined and hard midsection"),
    (15.0, "a photograph of a fit man with about 15 percent body fat, upper abs faintly "
           "visible, flat but soft lower stomach"),
    (20.0, "a photograph of an average man with about 20 percent body fat, no visible abs, "
           "a soft rounded stomach"),
    (28.0, "a photograph of an overweight man with about 28 percent body fat, a large "
           "belly, no muscle definition at all"),
]

SUBJECTS = [
    ("bodybuilder  (truth ~5-6%)", "subj_builder.png"),
    ("user 20 Sept (truth ~16-17%)", "subj_user_sept20.png"),
    ("user 21 Sept (truth ~16-17%)", "subj_user_sept21.png"),
]


def main():
    model, _, preprocess = open_clip.create_model_and_transforms(MODEL, pretrained=PRETRAINED)
    model.eval()
    tokenizer = open_clip.get_tokenizer(MODEL)

    with torch.no_grad():
        text = tokenizer([p for _, p in ANCHORS])
        text_features = model.encode_text(text)
        text_features /= text_features.norm(dim=-1, keepdim=True)

    print(f"model {MODEL} / {PRETRAINED}")
    print()
    print(f"{'subject':<30} {'estimate':>9}   distribution over anchors")
    print(f"{'':<30} {'':>9}   " + "  ".join(f"{p:>4.0f}%" for p, _ in ANCHORS))

    results = []
    for label, path in SUBJECTS:
        image = preprocess(Image.open(path).convert("RGB")).unsqueeze(0)
        with torch.no_grad():
            features = model.encode_image(image)
            features /= features.norm(dim=-1, keepdim=True)
            # 100 is CLIP's usual logit scale; it sharpens the softmax to something usable.
            probs = (100.0 * features @ text_features.T).softmax(dim=-1)[0]

        estimate = sum(p * float(w) for (p, _), w in zip(ANCHORS, probs))
        results.append((label, estimate))
        bars = "  ".join(f"{float(w) * 100:>4.0f}%" for w in probs)
        print(f"{label:<30} {estimate:>8.1f}%   {bars}")

    print()
    builder = results[0][1]
    user = (results[1][1] + results[2][1]) / 2
    print(f"bodybuilder {builder:.1f}%  vs  user {user:.1f}%   gap {user - builder:+.1f} points")
    print("ORDERED CORRECTLY" if builder < user - 3.0 else "NOT SEPARATED — this does not work")


if __name__ == "__main__":
    main()
