package com.squeeze.app.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.squeeze.core.scan.AppearanceEstimator
import com.squeeze.core.scan.PromptSet
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the on-device vision-language model over a photograph and returns what body fat it
 * looks like. See [AppearanceEstimator] for what the reading is and how far to trust it.
 *
 * The model is a CLIP ViT-B/32 image encoder that OpenCLIP trained on LAION-2B, compressed to
 * 48.5 MB and shipped inside
 * the APK. It runs through ONNX Runtime on the phone's CPU. The app still holds no INTERNET
 * permission, so this adds a neural network without adding any way for a photograph to
 * leave the device — the only kind of AI this app is allowed to have.
 *
 * Only the image half of CLIP ships. The text half embeds fifteen fixed sentences, so those
 * embeddings were computed once and travel as `clip_prompts.json`; see tools/vlm.
 *
 * Never throws. Every failure — the model missing from a build, a phone that cannot load it,
 * a corrupt asset — returns null, and the scan carries on exactly as it did before this
 * existed. A body-fat app that crashes on "measure" has lost the whole scan.
 */
@Singleton
class ClipAppearance @Inject constructor(
    private val context: Context,
) {
    private var session: OrtSession? = null
    private var prompts: List<PromptSet>? = null
    private var unavailable = false

    /** The body fat the photograph looks like, in per cent, or null when it cannot say. */
    @Synchronized
    fun estimate(photo: Bitmap): Double? {
        if (unavailable) return null
        return runCatching {
            val loaded = session ?: load() ?: return null
            val sets = prompts ?: return null
            val embedding = embed(loaded, photo) ?: return null
            AppearanceEstimator.estimate(embedding, sets)
        }.getOrNull()
    }

    private fun load(): OrtSession? = runCatching {
        prompts = readPrompts()

        // Copied out of the APK once, because ONNX Runtime can map a file but not an asset,
        // and reading 48 MB into a byte array to hand it over would double the memory the
        // model needs at exactly the moment the scan is also holding a full photograph.
        //
        // The copy is named for the model's checksum, so a build that ships a different model
        // copies afresh instead of loading the old one. Not checked against the asset's size:
        // AssetManager.openFd throws on a compressed asset, Android compresses .onnx by
        // default, and the first version of this that asked would have disabled the model on
        // every phone without a word.
        val file = File(context.filesDir, MODEL_COPY)
        if (!file.exists()) {
            context.filesDir.listFiles { f -> f.name.startsWith("clip_vitb32") }
                ?.forEach { it.delete() }
            val partial = File(context.filesDir, "$MODEL_COPY.part")
            context.assets.open(MODEL_ASSET).use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            // Renamed only once whole, so an interrupted copy is never mistaken for a model.
            if (!partial.renameTo(file)) return@runCatching null
        }

        OrtEnvironment.getEnvironment()
            .createSession(file.absolutePath, OrtSession.SessionOptions())
            .also { session = it }
    }.getOrElse {
        // A debug build without the model, or a device that cannot load it. Remembered, so
        // the cost of finding out is paid once per process rather than on every scan.
        unavailable = true
        null
    }

    private fun readPrompts(): List<PromptSet> {
        val json = context.assets.open(PROMPTS_ASSET).bufferedReader().use { it.readText() }
        val sets = JSONObject(json).getJSONArray("sets")
        return (0 until sets.length()).map { s ->
            val set = sets.getJSONObject(s)
            val anchors = set.getJSONArray("anchors")
            val embeddings = set.getJSONArray("embeddings")
            PromptSet(
                anchors = DoubleArray(anchors.length()) { anchors.getDouble(it) },
                embeddings = (0 until embeddings.length()).map { row ->
                    val values = embeddings.getJSONArray(row)
                    DoubleArray(values.length()) { values.getDouble(it) }
                },
            )
        }
    }

    private fun embed(session: OrtSession, photo: Bitmap): DoubleArray? {
        val env = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(preprocess(photo)), shape).use { input ->
            session.run(mapOf(INPUT_NAME to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = result[0].value as Array<FloatArray>
                output.firstOrNull()?.let { row -> DoubleArray(row.size) { row[it].toDouble() } }
            }
        }
    }

    /**
     * The photograph as CLIP expects it, prepared the way it was when this was tested.
     *
     * **Padded to a square, not cropped.** CLIP's usual preparation centre-crops, which on a
     * standing full-length photograph keeps the midriff and discards the head and legs. The
     * readings this was checked against came from the whole photograph letterboxed onto
     * black, so that is what happens here too; changing it changes every reading.
     *
     * **Halved before the final resize.** Android's filtered scaling reads four pixels per
     * output pixel whatever the ratio, so taking a twelve-megapixel photograph straight to
     * 224 samples it rather than averaging it — fine detail aliases, and fine detail is the
     * whole signal here. Halving until within a factor of two first is what an averaging
     * resize would have done.
     */
    private fun preprocess(photo: Bitmap): FloatArray {
        var image = photo
        while (maxOf(image.width, image.height) > SIZE * 2) {
            val next = Bitmap.createScaledBitmap(
                image,
                (image.width / 2).coerceAtLeast(1),
                (image.height / 2).coerceAtLeast(1),
                true,
            )
            if (image !== photo) image.recycle()
            image = next
        }

        val square = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        canvas.drawColor(Color.BLACK)
        val scale = SIZE.toFloat() / maxOf(image.width, image.height)
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        val left = (SIZE - w) / 2
        val top = (SIZE - h) / 2
        val target = Rect(left, top, left + w, top + h)
        canvas.drawBitmap(image, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        if (image !== photo) image.recycle()

        val argb = IntArray(SIZE * SIZE)
        square.getPixels(argb, 0, SIZE, 0, 0, SIZE, SIZE)
        square.recycle()

        // Channel-first, normalised with CLIP's own training statistics.
        val plane = SIZE * SIZE
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = argb[i]
            out[i] = (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
            out[plane + i] = (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
            out[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        return out
    }

    private companion object {
        const val MODEL_ASSET = "clip_vitb32_laion_q4.onnx"

        /** Must change whenever the model does; tools/vlm/prepare_clip.py pins the same hash. */
        const val MODEL_COPY = "clip_vitb32_laion_q4-81148049.onnx"
        const val PROMPTS_ASSET = "clip_prompts.json"
        const val INPUT_NAME = "pixel_values"
        const val SIZE = 224
        val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}
