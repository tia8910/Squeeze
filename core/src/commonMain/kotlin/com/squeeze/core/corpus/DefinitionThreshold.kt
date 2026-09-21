package com.squeeze.core.corpus

/**
 * One labelled photograph: what the metric measured, and what a person said was there.
 *
 * @param score whatever [com.squeeze.core.scan.AbdominalDefinition] read from the crop
 * @param visible the human judgement — separation visible with the subject relaxed
 */
data class DefinitionSample(
    val score: Double,
    val visible: Boolean,
)

/**
 * A fitted boundary, and how well it actually separates.
 *
 * @param threshold scores at or above this are called visible
 * @param balancedAccuracy the honest figure — see [DefinitionThreshold.fit], which measures it
 *   by leaving each sample out in turn rather than by scoring the fit against its own data
 * @param sampleCount how many labelled photographs it rests on
 * @param visibleCount how many of those were judged visible, so a caller can see the balance
 */
data class ThresholdFit(
    val threshold: Double,
    val balancedAccuracy: Double,
    val sampleCount: Int,
    val visibleCount: Int,
)

/**
 * Turns labelled photographs into the one number [com.squeeze.core.scan.AbdominalDefinition]
 * has never had: a score above which abdominal separation is actually visible.
 *
 * **Why a threshold and not a model.** The obvious answer to "this app cannot read a body" is a
 * neural network, and it is the right answer eventually. It is the wrong answer first, because
 * a small convolutional model needs a few hundred labelled photographs before it beats a coin,
 * and this corpus starts at zero and grows one scan at a time. A threshold is a single
 * parameter. Twenty labelled photographs fit it honestly; twenty would not fit the bias vector
 * of a model's first layer.
 *
 * So this is the staged version of the same idea, and the stage that can exist today. When the
 * corpus passes a few hundred, a learned classifier replaces the arithmetic below behind the
 * same question — is separation visible in this photograph — and everything downstream is
 * unchanged.
 *
 * **Why this is allowed when the last threshold was not.** A constant called
 * `CLEARLY_DEFINED = 20.0` shipped here once, fitted by eye to three photographs, and it fired
 * on a patch of blank wall. Two things are different now. The metric has a measured zero, so a
 * featureless crop scores near nothing instead of somewhere in the twenties. And this boundary
 * is fitted to labelled data and **reports how badly it separates**, which is what lets it be
 * refused rather than believed. A threshold nobody can evaluate is a guess with a decimal
 * point on it.
 */
object DefinitionThreshold {

    /**
     * Fewest labelled photographs a fit may rest on.
     *
     * Twenty is not a statistical ceremony, it is where leave-one-out stops being theatre: with
     * ten samples, removing one moves the boundary far enough that the validation says more
     * about which sample was dropped than about the boundary.
     */
    const val MIN_SAMPLES = 20

    /**
     * Fewest of each answer required.
     *
     * A corpus of thirty photographs that are all "visible" cannot locate a boundary — every
     * threshold below the lowest score scores full marks on it. Without this the fit would
     * return a confident number derived from having seen only one side of the question.
     */
    const val MIN_PER_CLASS = 8

    /**
     * Balanced accuracy below which the fit is refused rather than returned.
     *
     * Balanced rather than plain, because plain accuracy on a lopsided corpus is maximised by
     * ignoring the score and always answering with the commoner label — which would look like
     * 0.85 and mean nothing. Balanced accuracy is the mean of the two per-class rates, so that
     * strategy scores 0.5 and is correctly identified as useless.
     *
     * Three-quarters is the point where the metric is separating the two groups rather than
     * brushing against them. Below it the honest output is that this score does not answer
     * this question on this corpus, which is a finding worth surfacing rather than a failure
     * worth hiding behind a returned number.
     */
    const val MIN_BALANCED_ACCURACY = 0.75

    /**
     * Fits the boundary, or refuses.
     *
     * @return null when the corpus is too small, too one-sided, or when the best available
     *   boundary does not separate the two groups well enough to be worth believing
     */
    fun fit(samples: List<DefinitionSample>): ThresholdFit? {
        val usable = samples.filter { it.score.isFinite() }
        if (usable.size < MIN_SAMPLES) return null

        val visible = usable.count { it.visible }
        val smooth = usable.size - visible
        if (visible < MIN_PER_CLASS || smooth < MIN_PER_CLASS) return null

        val threshold = bestThreshold(usable) ?: return null

        // **Scored by leaving each photograph out, not by re-reading the ones it was fitted
        // to.** A boundary chosen to split this exact set will always look good on this exact
        // set — with one parameter the flattery is small, but it is in the direction that
        // matters, and the whole purpose of the number is to decide whether to trust the fit.
        // Refitting without each sample and testing on the one withheld costs n fits of an
        // O(n log n) routine, which is nothing, and it is the difference between a quality
        // figure and a restatement of the input.
        var correctVisible = 0
        var correctSmooth = 0
        for (index in usable.indices) {
            val heldOut = usable[index]
            val rest = usable.filterIndexed { i, _ -> i != index }
            val boundary = bestThreshold(rest) ?: continue

            val predicted = heldOut.score >= boundary
            if (predicted == heldOut.visible) {
                if (heldOut.visible) correctVisible++ else correctSmooth++
            }
        }

        val balanced = (correctVisible.toDouble() / visible + correctSmooth.toDouble() / smooth) / 2.0
        if (balanced < MIN_BALANCED_ACCURACY) return null

        return ThresholdFit(
            threshold = threshold,
            balancedAccuracy = balanced,
            sampleCount = usable.size,
            visibleCount = visible,
        )
    }

    /**
     * The boundary that best separates the two groups, by balanced accuracy on [samples].
     *
     * Candidates are the midpoints between neighbouring distinct scores, because a threshold
     * can only ever change its mind between two observed values — testing anything else is
     * testing the same partition twice. Ties go to the lower boundary, which is the
     * conservative direction here: it calls more photographs visible, and a false "visible"
     * shows up immediately as a reading that contradicts the body, where a false "smooth"
     * quietly withholds an answer.
     */
    private fun bestThreshold(samples: List<DefinitionSample>): Double? {
        val scores = samples.map { it.score }.distinct().sorted()
        if (scores.size < 2) return null

        val visible = samples.count { it.visible }
        val smooth = samples.size - visible
        if (visible == 0 || smooth == 0) return null

        var best: Double? = null
        var bestScore = -1.0

        for (i in 0 until scores.size - 1) {
            val candidate = (scores[i] + scores[i + 1]) / 2.0

            var truePositive = 0
            var trueNegative = 0
            for (sample in samples) {
                val predicted = sample.score >= candidate
                if (predicted && sample.visible) truePositive++
                if (!predicted && !sample.visible) trueNegative++
            }

            val balanced =
                (truePositive.toDouble() / visible + trueNegative.toDouble() / smooth) / 2.0
            if (balanced > bestScore) {
                bestScore = balanced
                best = candidate
            }
        }

        return best
    }

    /**
     * How many more labelled photographs are needed before [fit] will return anything.
     *
     * For the labelling screen, so it can say "eleven more" rather than leaving someone to
     * guess whether the work is nearly done. Counts the binding constraint, which is usually
     * the rarer answer rather than the total.
     */
    fun shortfall(samples: List<DefinitionSample>): Int {
        val visible = samples.count { it.visible }
        val smooth = samples.size - visible

        return maxOf(
            MIN_SAMPLES - samples.size,
            MIN_PER_CLASS - visible,
            MIN_PER_CLASS - smooth,
            0,
        )
    }
}
