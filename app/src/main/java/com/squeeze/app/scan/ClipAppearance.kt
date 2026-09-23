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
import com.squeeze.core.scan.AppearanceReading
import com.squeeze.core.scan.CropRegion
import com.squeeze.core.scan.MuscleGroup
import com.squeeze.core.scan.MusclePromptPair
import com.squeeze.core.scan.MuscleScorer
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
    private var musclePrompts: Map<MuscleGroup, List<MusclePromptPair>>? = null
    private var unavailable = false

    /**
     * What the photograph looks like to the model, or null when it cannot say.
     *
     * @param region the part of [photo] to show it — see [AppearanceEstimator.region]; null
     *   shows the whole photograph
     */
    @Synchronized
    fun read(photo: Bitmap, region: CropRegion? = null): AppearanceReading? {
        if (unavailable) return null
        return runCatching {
            val loaded = session ?: load() ?: return null
            val sets = prompts ?: return null
            val input = region?.let { crop(photo, it) } ?: photo
            val embedding = try {
                embed(loaded, input)
            } finally {
                if (input !== photo) input.recycle()
            } ?: return null
            AppearanceEstimator.read(embedding, sets)
        }.getOrNull()
    }

    /**
     * How developed each muscle group looks, 0 to 1, judged on its own crop — see
     * [com.squeeze.core.scan.PhysiqueRegions] and [com.squeeze.core.scan.PhysiqueAnalysis].
     *
     * @param onGroup called as each group starts, so the scanner can show which one the model
     *   is looking at; runs on the caller's thread
     * @param onScored called as each group's score is known
     * @return the groups that could be read; empty when the model is unavailable
     */
    @Synchronized
    fun readMuscles(
        photo: Bitmap,
        regions: Map<MuscleGroup, List<CropRegion>>,
        onGroup: (MuscleGroup) -> Unit = {},
        onScored: (MuscleGroup, Double) -> Unit = { _, _ -> },
    ): Map<MuscleGroup, Double> {
        if (unavailable) return emptyMap()
        return runCatching {
            val loaded = session ?: load() ?: return emptyMap()
            val pairs = musclePrompts ?: readMusclePrompts().also { musclePrompts = it }
            regions.mapNotNull { (group, boxes) ->
                val wordings = pairs[group] ?: return@mapNotNull null
                onGroup(group)
                val embeddings = boxes.mapNotNull { box ->
                    val input = crop(photo, box) ?: return@mapNotNull null
                    try {
                        embed(loaded, input)
                    } finally {
                        if (input !== photo) input.recycle()
                    }
                }
                MuscleScorer.score(embeddings, wordings)?.let {
                    onScored(group, it)
                    group to it
                }
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    private fun readMusclePrompts(): Map<MuscleGroup, List<MusclePromptPair>> {
        val json = context.assets.open(MUSCLES_ASSET).bufferedReader().use { it.readText() }
        val groups = JSONObject(json).getJSONObject("groups")
        return MuscleGroup.entries.mapNotNull { group ->
            val pairs = groups.optJSONArray(group.name) ?: return@mapNotNull null
            group to (0 until pairs.length()).map { i ->
                val embeddings = pairs.getJSONObject(i).getJSONArray("embeddings")
                fun row(r: Int) = embeddings.getJSONArray(r).let { values ->
                    DoubleArray(values.length()) { values.getDouble(it) }
                }
                MusclePromptPair(developed = row(0), undeveloped = row(1))
            }
        }.toMap()
    }

    private fun crop(photo: Bitmap, region: CropRegion): Bitmap? {
        val left = (region.left * photo.width).toInt().coerceIn(0, photo.width - 1)
        val top = (region.top * photo.height).toInt().coerceIn(0, photo.height - 1)
        val right = (region.right * photo.width).toInt().coerceIn(left + 1, photo.width)
        val bottom = (region.bottom * photo.height).toInt().coerceIn(top + 1, photo.height)
        return Bitmap.createBitmap(photo, left, top, right - left, bottom - top)
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
     * **Padded to a square, not centre-cropped.** CLIP's usual preparation centre-crops, which
     * cuts wherever the middle of the frame happens to be. The body is cropped by its own
     * landmarks before it gets here; what remains is letterboxed onto black, as it was when
     * the readings were checked, and changing it changes every reading.
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
        const val MUSCLES_ASSET = "clip_muscles.json"
        const val INPUT_NAME = "pixel_values"
        const val SIZE = 224
        val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}
