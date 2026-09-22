"""Puts the on-device image model into the APK's assets, reproducibly.

Downloads OpenAI's CLIP ViT-B/32 image encoder (MIT) as ONNX from a pinned URL, verifies
its SHA-256, quantises it to 8-bit and writes it where the app loads it from. Run by CI
before the Android build, the same way mediapipe-models.gradle.kts fetches the vision
models — 88 MB of immutable binary does not belong in git.

Why quantised: the float model is 351 MB. The 8-bit one is 88 MB and, on the photographs
it was tested against, ranks bodies in the same order (7.3% against 13.5–14.6%, where the
float model gave 6.3% against 14.1–14.7%).

Quantisation is deterministic for pinned versions of onnx and onnxruntime, so the output
has a checksum too. A mismatch there is a warning rather than a failure: the input is what
has to be trusted, and it is verified strictly.

    pip install onnx==1.23.0 onnxruntime==1.30.0
    python tools/vlm/prepare_clip.py
"""
import hashlib
import pathlib
import sys
import tempfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
TARGET = ROOT / "app" / "src" / "main" / "assets" / "clip_vitb32_visual_int8.onnx"

SOURCE_URL = ("https://clip-as-service.s3.us-east-2.amazonaws.com/"
              "models-436c69702d61732d53657276696365/onnx/ViT-B-32/visual.onnx")
SOURCE_SHA256 = "06395063c0a5c28b1a8d4bd585261501a878c8f52d1216db6c4cbb651f7c13f1"
OUTPUT_SHA256 = "587c769760c3c9fdb80c5283bd7875f674d8beff788a4605175910e85c4b9876"


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    if TARGET.exists() and sha256(TARGET) == OUTPUT_SHA256:
        print(f"{TARGET.name}: already present and verified")
        return

    from onnxruntime.quantization import QuantType, quantize_dynamic

    with tempfile.TemporaryDirectory() as tmp:
        source = pathlib.Path(tmp) / "visual.onnx"
        print("Downloading CLIP ViT-B/32 image encoder")
        urllib.request.urlretrieve(SOURCE_URL, source)
        actual = sha256(source)
        if actual != SOURCE_SHA256:
            sys.exit(f"::error::checksum mismatch for the CLIP image encoder\n"
                     f"  expected {SOURCE_SHA256}\n  actual   {actual}")

        TARGET.parent.mkdir(parents=True, exist_ok=True)
        partial = TARGET.with_suffix(".part")
        quantize_dynamic(str(source), str(partial), weight_type=QuantType.QUInt8)
        partial.replace(TARGET)

    produced = sha256(TARGET)
    if produced != OUTPUT_SHA256:
        print(f"::warning::quantised model differs from the pinned build ({produced}); "
              "check the onnx and onnxruntime versions")
    print(f"{TARGET.name}: {TARGET.stat().st_size // 1_000_000} MB, {produced}")


if __name__ == "__main__":
    main()
