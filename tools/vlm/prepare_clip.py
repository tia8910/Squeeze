"""Puts the on-device image model into the APK's assets, reproducibly.

Downloads OpenAI's CLIP ViT-B/32 image encoder (MIT) as ONNX from a pinned URL, verifies
its SHA-256, compresses it and writes it where the app loads it from. Run by CI
before the Android build, the same way mediapipe-models.gradle.kts fetches the vision
models — 88 MB of immutable binary does not belong in git.

Why compressed, and why this way: the float model is 351 MB. Its matrix multiplications
go to 4-bit weights in blocks of 128 and the patch-embedding convolution to 8-bit, which
leaves 48.5 MB. On the three photographs it was tested against it reads the stage-lean
bodybuilder at 7.8% and the softer man at 16.3% / 14.2%, where the float model gave 7.1%
and 15.2% / 14.0% — the same order, and nearer the truth than the 88 MB 8-bit build this
replaced (7.3%, 14.6% / 13.0%). A smaller ResNet-50 build (39 MB) was tried and rejected:
it read the bodybuilder at 14.8%.

**Pinned to onnxruntime 1.20.1** because the app runs ONNX Runtime 1.20, and 4-bit weights
are a newer feature: the model is produced by the same version that will run it.

Quantisation is deterministic for pinned versions of onnx and onnxruntime, so the output
has a checksum too. A mismatch there is a warning rather than a failure: the input is what
has to be trusted, and it is verified strictly.

    pip install onnx==1.17.0 onnxruntime==1.20.1
    python tools/vlm/prepare_clip.py
"""
import hashlib
import pathlib
import sys
import tempfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
TARGET = ROOT / "app" / "src" / "main" / "assets" / "clip_vitb32_visual_q4.onnx"

SOURCE_URL = ("https://clip-as-service.s3.us-east-2.amazonaws.com/"
              "models-436c69702d61732d53657276696365/onnx/ViT-B-32/visual.onnx")
SOURCE_SHA256 = "06395063c0a5c28b1a8d4bd585261501a878c8f52d1216db6c4cbb651f7c13f1"
OUTPUT_SHA256 = "66c8bdcbe08915628c4d3f6c682e1a71f821dec87d67321d119f25d7ba7e3da9"


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

    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic
    from onnxruntime.quantization.matmul_4bits_quantizer import MatMul4BitsQuantizer

    with tempfile.TemporaryDirectory() as tmp:
        source = pathlib.Path(tmp) / "visual.onnx"
        print("Downloading CLIP ViT-B/32 image encoder")
        urllib.request.urlretrieve(SOURCE_URL, source)
        actual = sha256(source)
        if actual != SOURCE_SHA256:
            sys.exit(f"::error::checksum mismatch for the CLIP image encoder\n"
                     f"  expected {SOURCE_SHA256}\n  actual   {actual}")

        TARGET.parent.mkdir(parents=True, exist_ok=True)
        four_bit = pathlib.Path(tmp) / "visual_q4.onnx"
        quantizer = MatMul4BitsQuantizer(onnx.load(str(source)), block_size=128, is_symmetric=True)
        quantizer.process()
        quantizer.model.save_model_to_file(str(four_bit), use_external_data_format=False)

        partial = TARGET.with_suffix(".part")
        quantize_dynamic(
            str(four_bit), str(partial), weight_type=QuantType.QUInt8, op_types_to_quantize=["Conv"],
        )
        partial.replace(TARGET)

    produced = sha256(TARGET)
    if produced != OUTPUT_SHA256:
        print(f"::warning::quantised model differs from the pinned build ({produced}); "
              "check the onnx and onnxruntime versions")
    print(f"{TARGET.name}: {TARGET.stat().st_size // 1_000_000} MB, {produced}")


if __name__ == "__main__":
    main()
