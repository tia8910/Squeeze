package com.squeeze.core.scan

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
 * **What it was tested against, stated plainly.** Four photographs: a stage-lean
 * bodybuilder (about 5%), a lean man with his abdominals visible relaxed (about 10%), and two
 * of a man with a soft lower stomach (about 16%). The shipped model reads them 7.7%, 10.8%,
 * 16.8% and 15.5% — right order, within about two points, unmoved by mirroring. That is
 * enough to trust the ordering and nowhere near enough to call it validated, which is why
 * [STANDARD_ERROR_PERCENT] is wide.
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
     * there is no validation set: three photographs is a check that it works, not a measure
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
    fun estimate(image: DoubleArray, sets: List<PromptSet>): Double? {
        if (sets.isEmpty() || image.isEmpty() || image.any { !it.isFinite() }) return null

        val norm = sqrt(image.sumOf { it * it })
        if (norm == 0.0) return null
        val unit = DoubleArray(image.size) { image[it] / norm }

        val readings = sets.map { set ->
            if (set.embeddings.any { it.size != unit.size }) return null

            val logits = DoubleArray(set.anchors.size) { i ->
                LOGIT_SCALE * set.embeddings[i].indices.sumOf { set.embeddings[i][it] * unit[it] }
            }

            // Softmax with the maximum subtracted, because exp(100 × 0.3) is 1e13 and a
            // slightly larger similarity overflows a double outright.
            val max = logits.max()
            val weights = logits.map { exp(it - max) }
            val total = weights.sum()
            weights.indices.sumOf { weights[it] / total * set.anchors[it] }
        }

        return readings.average().takeIf { it.isFinite() }
    }
}
