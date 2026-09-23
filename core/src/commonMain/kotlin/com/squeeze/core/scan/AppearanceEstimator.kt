package com.squeeze.core.scan

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * One phrasing of the reference bodies: five descriptions, the body fat each stands for, and
 * the vision-language model's embedding of each description.
 *
 * @param anchors body fat, in per cent, that each description represents
 * @param embeddings one text embedding per anchor, in the same order
 */
class PromptSet(
    val anchors: DoubleArray,
    val embeddings: List<DoubleArray>,
) {
    init {
        require(anchors.isNotEmpty()) { "a prompt set needs at least one anchor" }
        require(anchors.size == embeddings.size) { "one embedding per anchor" }
    }
}

/**
 * Body fat read from how a body looks, by an on-device vision-language model.
 *
 * **Why this exists.** Every other method in this app reads the body's *edge*: its width at
 * the waist, its outline against its shoulders, its girths through the tape equation. The
 * edge cannot tell a stage-lean bodybuilder from a man with a soft lower stomach whose waist
 * is the same width — both photographs this was built against came out at a waist-to-height
 * of 0.42 — because what separates them is inside the outline: whether the abdominal
 * muscles show through the skin. A coach sees that at a glance, and so, it turns out, does
 * CLIP — best of the models tried, the one OpenCLIP trained on LAION-2B.
 *
 * **How.** CLIP maps images and sentences into the same space. The app embeds the
 * photograph, compares it with descriptions of five bodies from stage-lean to overweight,
 * and turns the similarities into a probability over those five. The reading is the
 * expected body fat under that probability. Three differently worded sets of descriptions
 * are averaged, so the answer does not rest on one guess about how the model reads
 * language.
 *
 * **Its scale is the app's own ladder** — see VisualAssessment. The reference bodies are
 * valued at stage condition 5%, abs visible relaxed 8%, a soft lower stomach 15%, no visible
 * abs 20%, a rounded stomach 30%, so the AI and the ladder printed beneath it agree on what
 * a body that looks like this is worth.
 *
 * **What it was tested against, stated plainly.** Five photographs, against the owner's own
 * targets: a stage-lean bodybuilder (7%), a lean man with his abdominals visible relaxed
 * (8%) photographed both waist-up and full length, and two of a man with a soft lower
 * stomach (16%). Shown the [region] the app crops to, the shipped model reads them 8.4%,
 * 9.5%, 9.6%, 15.0% and 15.2% — right order, within about a point and a half, and the same
 * man the same whichever way he was framed. That is enough to trust the ordering and nowhere
 * near enough to call it validated, which is why [STANDARD_ERROR_PERCENT] is wide.
 *
 * **What it does not do.** Read numbers. "A man with 15 percent body fat" scored every
 * photograph within a point of 16%; CLIP learned from captions, and captions describe what
 * is visible, not what a DEXA scan said.
 */
object AppearanceEstimator {

    /**
     * CLIP's own logit scale, which turns cosine similarities of a few hundredths apart into
     * a usable probability. The model was trained with it; any other value would be tuning.
     */
    const val LOGIT_SCALE = 100.0

    /**
     * The interval this reading is printed with, in body-fat points.
     *
     * Set to match the appearance ladder rather than earned from a validation set, because
     * there is no validation set: four photographs is a check that it works, not a measure
     * of how well.
     */
    const val STANDARD_ERROR_PERCENT = 5.0

    /**
     * The body fat [image] looks like, or null when the inputs cannot produce one.
     *
     * @param image the model's embedding of the photograph; normalised here, so the caller
     *   need not
     * @param sets the reference descriptions, each already embedded and normalised
     */
    fun estimate(image: DoubleArray, sets: List<PromptSet>): Double? = read(image, sets)?.percent

    /**
     * The same reading as [estimate], with the probability the model gave each reference
     * body — which is what the scanner shows the user while it decides, so the number arrives
     * with the reason for it rather than on its own.
     */
    fun read(image: DoubleArray, sets: List<PromptSet>): AppearanceReading? {
        if (sets.isEmpty() || image.isEmpty() || image.any { !it.isFinite() }) return null

        val norm = sqrt(image.sumOf { it * it })
        if (norm == 0.0) return null
        val unit = DoubleArray(image.size) { image[it] / norm }

        val probabilities = sets.map { set ->
            if (set.embeddings.any { it.size != unit.size }) return null

            val logits = DoubleArray(set.anchors.size) { i ->
                LOGIT_SCALE * set.embeddings[i].indices.sumOf { set.embeddings[i][it] * unit[it] }
            }

            // Softmax with the maximum subtracted, because exp(100 × 0.3) is 1e13 and a
            // slightly larger similarity overflows a double outright.
            val max = logits.max()
            val weights = logits.map { exp(it - max) }
            val total = weights.sum()
            DoubleArray(weights.size) { weights[it] / total }
        }

        val percent = sets.indices
            .map { s -> sets[s].anchors.indices.sumOf { probabilities[s][it] * sets[s].anchors[it] } }
            .average()
            .takeIf { it.isFinite() } ?: return null

        // Averaged per body only when every phrasing describes the same bodies; otherwise
        // there is no single list to show, and the figure stands without it.
        val anchors = sets.first().anchors
        val shared = sets.all { it.anchors.contentEquals(anchors) }
        val weights = if (shared) {
            DoubleArray(anchors.size) { i -> probabilities.sumOf { it[i] } / sets.size }
        } else {
            null
        }

        return AppearanceReading(percent, anchors.takeIf { shared }, weights)
    }

    /**
     * The part of a front photograph the model is shown: head to just below the hips, twice
     * the shoulder width across.
     *
     * **Why not the whole photograph.** The same lean man read 9.1% photographed from the
     * waist up and 12.3% photographed full length. Nothing about his body changed; his legs
     * and the floor took half the model's 224 pixels, and the abdomen — the whole signal —
     * shrank to a few of them. Cropped like this the two photographs read 9.5% and 9.6%. A
     * reading that moves three points with where the user stood is not a reading.
     *
     * **Why these proportions.** Checked on a grid of alternatives around them, over the five
     * test photographs: this is the middle of a flat region, where nudging any edge moves no
     * reading more than about a point. The edges that were tried and lost: tighter than the
     * shoulders cut a flexing bodybuilder's arms and read him three points fatter; the full
     * width kept the background and brought back the framing effect.
     *
     * Null when the landmarks cannot place a torso, and the caller then shows the whole
     * photograph, which is what this did before.
     */
    fun region(geometry: FrontPoseGeometry): CropRegion? {
        val shoulderY = (geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0
        val hipY = (geometry.hipLeft.y + geometry.hipRight.y) / 2.0
        val span = hipY - shoulderY
        val shoulderWidth = abs(geometry.shoulderRight.x - geometry.shoulderLeft.x)
        if (span <= 0.0 || shoulderWidth <= 0.0) return null

        val chin = (geometry.mouth ?: geometry.nose)?.y?.takeIf { it < shoulderY }
            ?: (shoulderY - span * DEFAULT_NECK_TO_TRUNK)
        val neck = shoulderY - chin
        val centre = (geometry.shoulderLeft.x + geometry.shoulderRight.x) / 2.0
        val half = shoulderWidth * CROP_WIDTH_TO_SHOULDERS / 2.0

        val region = CropRegion(
            left = (centre - half).coerceIn(0.0, 1.0),
            top = (chin - neck * CROP_HEAD_TO_NECK).coerceIn(0.0, 1.0),
            right = (centre + half).coerceIn(0.0, 1.0),
            bottom = (hipY + span * CROP_BELOW_HIPS).coerceIn(0.0, 1.0),
        )
        return region.takeIf { it.right - it.left > MIN_CROP && it.bottom - it.top > MIN_CROP }
    }

    private const val CROP_WIDTH_TO_SHOULDERS = 2.0
    private const val CROP_HEAD_TO_NECK = 1.5
    private const val CROP_BELOW_HIPS = 0.15

    /** Chin to shoulders as a share of shoulders to hips, when no face landmark is found. */
    private const val DEFAULT_NECK_TO_TRUNK = 0.4

    /** Smaller than this, as a share of the frame, and the landmarks were not a body. */
    private const val MIN_CROP = 0.05
}

/**
 * What the model saw, in full.
 *
 * @param percent the body fat the photograph looks like
 * @param anchors the reference bodies, by the body fat each stands for; null when the prompt
 *   sets disagree about which bodies they describe
 * @param weights the probability given to each of [anchors], summing to one
 */
class AppearanceReading(
    val percent: Double,
    val anchors: DoubleArray?,
    val weights: DoubleArray?,
)

/** A rectangle in 0..1 fractions of a photograph, the same units the pose landmarks use. */
data class CropRegion(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)
