package com.squeeze.core.scan

/**
 * Whether the arms were far enough from the body to measure it.
 *
 * This is the pose requirement that every serious photo-anthropometry protocol states first,
 * and the app had been silently tolerating its violation.
 *
 * A front-on silhouette cannot separate an arm from a torso it is touching: the two are one
 * continuous run of subject pixels, and no amount of processing recovers a boundary the image
 * does not contain. [TrunkBounds] handles that by cutting the run back to where the skeleton
 * says the trunk can reach, which correctly stops an arm being measured as a waist — but what
 * remains is then the bound rather than the body.
 *
 * The consequence is worse than a noisy reading, because the bound has a shape of its own.
 * Interpolated between the shoulder and hip landmark spans and widened by fixed margins, it
 * narrows steadily from shoulders to hips. So a fully clipped trunk yields a waist-to-shoulder
 * ratio determined by the subject's *skeleton* and by two constants in this codebase, and it
 * lands in the high teens on the body-fat scale for almost any adult male frame. That is not
 * an estimate that happens to be wrong; it is the same wrong answer for everybody, and it is
 * why a lean body and a heavy one both came back near nineteen per cent.
 *
 * The measurement code already refuses clipped rows one at a time. This object exists for the
 * other half: telling the user what to do about it, because unlike lighting or framing, this
 * one is fixed by a change of stance that takes a second.
 */
object ArmClearance {

    /**
     * How much of the trunk may be clipped before the user is told to move their arms.
     *
     * Not zero. A hand brushing the hip clips a few rows at the bottom of the band and leaves
     * the waist itself perfectly measurable, and warning about that would train the user to
     * ignore the warning. A third is roughly where enough of the band is gone that whichever
     * rows survived were not chosen for anatomical reasons.
     */
    const val TOLERATED_CLIPPED_FRACTION = 0.35

    /**
     * @return an instruction when too much of the trunk was obscured by the arms, or null
     *   when the silhouette was clean enough to measure
     */
    fun verdict(profile: WidthProfile, anchors: PoseAnchors): String? {
        val clipped = profile.clippedFractionBetween(anchors.shoulderRow, anchors.hipRow)
        if (clipped <= TOLERATED_CLIPPED_FRACTION) return null

        return "Your arms were resting against your sides, so the app could not tell where " +
            "your waist ended and your arms began. Stand with your arms held clear of your " +
            "body — about a hand's width away, palms forward — and scan again. This is the " +
            "single largest thing you can do for accuracy."
    }
}
