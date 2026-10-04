"""Puts the on-device image model into the APK's assets, reproducibly.

Downloads the CLIP ViT-B/32 image encoder that OpenCLIP trained on LAION-2B (MIT) as ONNX
from a pinned URL, verifies its SHA-256, compresses it and writes it where the app loads it
from.

**Why this model and not OpenAI's.** Same architecture and the same cost on the phone, but
trained on about five times as many web images, which is where photographs of trained and
untrained bodies come from. On the four test photographs it cut the average error from 2.0
points to 1.2 — the fit man at about 10% went from 13.1% to 10.8%. OpenAI's ViT-B/16 (finer
patches, four times the compute) was worse at 3.3; OpenCLIP's LAION-400M B/16 tied at 1.4 for
four times the compute. Run by CI
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
TARGET = ROOT / "app" / "src" / "main" / "assets" / "clip_vitb32_laion_q4.onnx"

SOURCE_URL = ("https://clip-as-service.s3.us-east-2.amazonaws.com/"
              "models-436c69702d61732d53657276696365/onnx/ViT-B-32-laion2b-s34b-b79k/visual.onnx")
SOURCE_SHA256 = "87f8c86517852321654a20652edd8e12524c5ac4d95ac2b34b7ecc9e895c8211"
OUTPUT_SHA256 = "8114804958dbbea3ff8f9954f852e23e1728fa5233b1bfa14d5b441ed9e6daad"


def gemm_to_matmul(model):
    """Rewrites Gemm(A, W, b) as MatMul(A, W') + b so the 4-bit pass can compress W.

    The LAION export writes each attention block's output projection as a Gemm, and the
    4-bit quantiser in onnxruntime 1.20 only compresses MatMul. Left alone, twelve 768x768
    layers stay 32-bit: 73 MB instead of 48.5. A no-op on exports without Gemm.
    """
    from onnx import helper, numpy_helper

    graph = model.graph
    inits = {i.name: i for i in graph.initializer}
    nodes = []
    for node in graph.node:
        attrs = {a.name: helper.get_attribute_value(a) for a in node.attribute}
        plain = (node.op_type == "Gemm" and node.input[1] in inits
                 and attrs.get("alpha", 1.0) == 1.0 and attrs.get("beta", 1.0) == 1.0
                 and not attrs.get("transA", 0))
        if not plain:
            nodes.append(node)
            continue
        weight = numpy_helper.to_array(inits[node.input[1]])
        if attrs.get("transB", 0):
            weight = weight.T.copy()
        name = node.input[1] + "_mm"
        graph.initializer.append(numpy_helper.from_array(weight, name))
        if len(node.input) > 2 and node.input[2]:
            product = node.output[0] + "_mm"
            nodes.append(helper.make_node("MatMul", [node.input[0], name], [product], name=node.name + "_mm"))
            nodes.append(helper.make_node("Add", [product, node.input[2]], [node.output[0]], name=node.name + "_add"))
        else:
            nodes.append(helper.make_node("MatMul", [node.input[0], name], [node.output[0]], name=node.name + "_mm"))
    del graph.node[:]
    graph.node.extend(nodes)
    used = {x for node in graph.node for x in node.input}
    kept = [i for i in graph.initializer if i.name in used]
    del graph.initializer[:]
    graph.initializer.extend(kept)
    return model


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
        print("Downloading CLIP ViT-B/32 (LAION-2B) image encoder")
        urllib.request.urlretrieve(SOURCE_URL, source)
        actual = sha256(source)
        if actual != SOURCE_SHA256:
            sys.exit(f"::error::checksum mismatch for the CLIP image encoder\n"
                     f"  expected {SOURCE_SHA256}\n  actual   {actual}")

        TARGET.parent.mkdir(parents=True, exist_ok=True)
        four_bit = pathlib.Path(tmp) / "visual_q4.onnx"
        model = gemm_to_matmul(onnx.load(str(source)))
        quantizer = MatMul4BitsQuantizer(model, block_size=128, is_symmetric=True)
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
