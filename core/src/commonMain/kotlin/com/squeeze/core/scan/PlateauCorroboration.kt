package com.squeeze.core.scan

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.EstimationMethod

/**
 * What the abdomen's surface says about a reading the outline could not resolve.
 *
 * **The gap this closes.** [SilhouetteBodyFat] stops below [SilhouetteBodyFat.LEAN_PLATEAU_RATIO]
 * and returns a bound — 11.6% for a man, carrying ±9 — because the outline genuinely cannot
 * separate eight per cent from fifteen. Measured off the reference charts it reads 0.586 at
 * eight, 0.592 at twelve and 0.580 at fifteen: flat. So the app says "not resolved by the
 * photo", and it has been saying that to photographs with visible abdominal separation in
 * them, which is a picture a person can read at a glance.
 *
 * The outline was never going to read it. An outline knows where the body ends and nothing
 * about the surface inside, and the surface is the entire difference between those bodies.
 *
 * **Why the surface is admissible here and not as a figure of its own.**
 * [AbdominalDefinition]'s score has no meaning across people — a model's visibly sharper
 * abdomen measured 21.9 against another man's soft one at 16.5, where the bodies were nine
 * points apart — so it cannot produce a percentage unanchored, and [DefinitionScale] refuses
 * to. But the question asked here is far weaker than "what percentage is this": it is "does
 * this abdomen have visible structure or not", which is the one thing the score does
 * separate, and separates by roughly a factor of two.
 *
 * **The two failures are independent, which is the whole argument.** Every way the outline
 * fails makes a denominator wider and the reading leaner — an arm against the waist, trousers
 * at the hip, two thighs together. None of those put grooves on a stomach. So a lean outline
 * *with* a defined abdomen is two measurements agreeing by different mechanisms, and a lean
 * outline with a smooth abdomen is the signature of exactly the contamination this project has
 * shipped four times.
 *
 * **What it may change, and what it may not.** It moves the interval and the label. It never
 * moves the percentage. That restriction is deliberate and is what makes it shippable on the
 * evidence there is: the thresholds below come from three photographs, and a threshold fitted
 * to three photographs must not be allowed to decide a number. Getting one wrong costs a
 * sentence, not a figure.
 */
object PlateauCorroboration {

    /**
     * Definition score at or above which an abdomen counts as visibly structured.
     *
     * **Three photographs, and they are named rather than dressed up.** Two abdomens with
     * visible separation scored 32.5 and 21.9; one smooth belly on the same man as the first
     * scored 16.5. Twenty sits between the lowest defined reading and the smooth one with
     * room either side.
     *
     * It decides a label and an interval and nothing else — see the class header — which is
     * the only reason a constant from three observations is allowed to exist here at all. The
     * corpus replaces it, and when it does this becomes a fitted boundary rather than a gap in
     * a small sample.
     */
    const val CLEARLY_DEFINED = 20.0

    /**
     * Below this the abdomen is smooth, and a lean outline over it is a contradiction.
     *
     * Set under [CLEARLY_DEFINED] rather than equal to it so there is a band in the middle
     * where the photograph is allowed to say nothing. Forcing every reading into "defined" or
     * "smooth" would turn the noise between them into a verdict.
     */
    const val SMOOTH = 18.0

    /** What the abdomen's surface had to say. */
    enum class Verdict {
        /** Visible structure. The outline's lean reading is corroborated. */
        DEFINED,

        /** No visible structure. A lean outline over this is evidence of contamination. */
        SMOOTH,

        /** Between the two, or the crop could not be read. The photograph says nothing. */
        UNCERTAIN,
    }

    fun verdict(reading: DefinitionReading?): Verdict = when {
        reading == null || !reading.usable -> Verdict.UNCERTAIN
        reading.score >= CLEARLY_DEFINED -> Verdict.DEFINED
        reading.score < SMOOTH -> Verdict.SMOOTH
        else -> Verdict.UNCERTAIN
    }

    /**
     * Applies the surface's verdict to an outline reading.
     *
     * @param estimate whatever [SilhouetteBodyFat] produced
     * @param reading the abdomen's definition from the same photograph
     * @return the same percentage, with the interval and therefore the label the pair of
     *   signals earns. Off the plateau nothing happens: the outline resolved the body on its
     *   own and a second opinion about the surface is not needed to believe it.
     */
    fun apply(estimate: BodyFatEstimate?, reading: DefinitionReading?): BodyFatEstimate? {
        if (estimate == null) return null

        val bounded = estimate.standardErrorPercent >= SilhouetteBodyFat.PLATEAU_ERROR_PERCENT
        if (!bounded) return estimate

        return when (verdict(reading)) {
            // The outline says lean and cannot say how lean; the abdomen says the muscle is
            // visible through the skin. Nothing that corrupts an outline puts grooves on a
            // stomach, so these agree for independent reasons and the reading stops being a
            // bound. The figure is unchanged — it is the plateau's own value, which is what
            // both signals point at — but it is now a measurement of this body rather than a
            // statement about the method's limits.
            Verdict.DEFINED -> estimate.copy(
                standardErrorPercent = EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
            )

            // A lean outline over a smooth abdomen. This is the shape of every wrong answer
            // this project has shipped — 4.93%, 6.22%, 6.83%, 4.76%, 3.00% — and in all five
            // the body in the photograph had no abdominal definition whatsoever. The bound
            // stays, and it stays at its full width.
            Verdict.SMOOTH -> estimate

            Verdict.UNCERTAIN -> estimate
        }
    }
}
