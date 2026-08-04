package com.squeeze.core.scan

/**
 * A body silhouette reduced to per-row widths.
 *
 * Two arrays, because a silhouette row does not have one meaningful width.
 *
 * At chest height a front-on person is not a single blob: there is a torso with an arm
 * either side of it. Measuring leftmost-to-rightmost there returns the shoulder-and-arms
 * span, which is roughly twice a chest diameter and produces a chest circumference no human
 * has. At thigh height the same measurement spans both legs plus the gap between them.
 *
 * So [torsoWidths] holds the width of the run containing the body's midline — the torso
 * alone, arms excluded whenever a gap separates them — and [legWidths] holds the width of a
 * single leg. Anatomy is measured on whichever is actually the body part in question.
 *
 * @param torsoWidths central-run width per row, as a fraction of image width
 * @param legWidths width of one leg per row, 0 where a single leg cannot be isolated
 * @param topRow first row containing the body, i.e. the crown of the head
 * @param bottomRow last row containing the body, normally the feet
 */
data class WidthProfile(
    val torsoWidths: DoubleArray,
    val legWidths: DoubleArray,
    val topRow: Int,
    val bottomRow: Int,
) {
    init {
        require(torsoWidths.isNotEmpty()) { "profile cannot be empty" }
        require(legWidths.size == torsoWidths.size) { "width arrays must be the same length" }
        require(topRow in torsoWidths.indices) { "topRow $topRow outside profile" }
        require(bottomRow in torsoWidths.indices) { "bottomRow $bottomRow outside profile" }
        require(topRow < bottomRow) { "topRow must be above bottomRow" }
    }

    /** How much of the frame the body occupies vertically; the basis of scale recovery. */
    val bodyHeightFraction: Double
        get() = (bottomRow - topRow).toDouble() / torsoWidths.size.toDouble()

    fun torsoWidthAt(row: Int): Double = torsoWidths.getOrElse(row) { 0.0 }

    fun legWidthAt(row: Int): Double = legWidths.getOrElse(row) { 0.0 }

    fun heightFractionOf(row: Int): Double = row.toDouble() / torsoWidths.size.toDouble()

    // Explicit equals/hashCode: a data class compares DoubleArray by identity, which would
    // make two identical profiles unequal.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WidthProfile) return false
        return topRow == other.topRow &&
            bottomRow == other.bottomRow &&
            torsoWidths.contentEquals(other.torsoWidths) &&
            legWidths.contentEquals(other.legWidths)
    }

    override fun hashCode(): Int =
        ((torsoWidths.contentHashCode() * 31 + legWidths.contentHashCode()) * 31 + topRow) * 31 +
            bottomRow

    companion object {
        /** Convenience for callers with no leg data, e.g. a side-on view. */
        fun torsoOnly(widths: DoubleArray, topRow: Int, bottomRow: Int) =
            WidthProfile(widths, DoubleArray(widths.size), topRow, bottomRow)
    }
}

/**
 * Vertical positions of the skeletal landmarks a pose model reports, as image rows.
 *
 * Only the anchors needed to bound an anatomical search are kept. The pose model is good at
 * locating joints and poor at locating soft-tissue landmarks like the natural waist, so it
 * says roughly *where to look* and the silhouette decides exactly where the site is.
 */
data class PoseAnchors(
    val shoulderRow: Int,
    val hipRow: Int,
    val kneeRow: Int,
    /** Bottom of the chin, the lower bound of the head. */
    val chinRow: Int,
) {
    init {
        require(chinRow < shoulderRow) { "chin must sit above the shoulders" }
        require(shoulderRow < hipRow) { "shoulders must sit above the hips" }
        require(hipRow < kneeRow) { "hips must sit above the knees" }
    }
}

/**
 * Locates anatomical measurement sites on a silhouette.
 *
 * Each site is found by the geometric property that defines it, rather than by a fixed
 * fraction of height:
 *
 *  - **Neck** — narrowest torso row between chin and shoulders.
 *  - **Waist** — narrowest torso row between shoulders and hips. This is what "natural
 *    waist" means anatomically, and searching for it is far more robust than the common
 *    shortcut of taking a fixed proportion between two joints, which lands on the ribs for
 *    a long-torsoed person and on the hips for a short one.
 *  - **Hip** — widest torso row in the upper part of the hip-to-knee span.
 *  - **Chest** — widest torso row between shoulders and waist.
 *  - **Thigh** — widest *single-leg* row, below the hip band so the two cannot collide.
 */
object AnatomicalLevelFinder {

    /**
     * Hip search stops before the thigh band starts.
     *
     * These bands previously overlapped, so the same widest row satisfied both and hip and
     * thigh came back byte-identical — a bug visible in the app as two sites reporting the
     * same centimetre value. Disjoint ranges make that impossible by construction.
     */
    /**
     * Where within the chin-to-shoulder span the neck is genuinely narrowest.
     *
     * Trimmed at both ends: the jaw sits above it and the trapezius below, and the silhouette
     * is wider than the neck at both.
     */
    private const val NECK_BAND_START = 0.35
    private const val NECK_BAND_END = 0.80

    private const val HIP_BAND_END = 0.18

    /** Thigh is measured below the gluteal fold, where the legs have genuinely separated. */
    private const val THIGH_BAND_START = 0.28
    private const val THIGH_BAND_END = 0.48

    /**
     * Finds every site the silhouette supports.
     *
     * @return rows keyed by site. A site is absent when its search band is degenerate or
     *   the relevant width is unavailable; callers must treat absence as "not measured"
     *   rather than substituting a default.
     */
    fun detectSites(profile: WidthProfile, anchors: PoseAnchors): Map<ScanSite, Int> {
        val sites = mutableMapOf<ScanSite, Int>()

        // Not the whole chin-to-shoulder span. The anchor above the neck comes from the
        // mouth landmarks, so the top of that span is jaw and the bottom is where the
        // trapezius flares out into the shoulder. Both are wider than a neck, and including
        // them is how a 175 cm man ends up with a 52 cm neck — which drives the Navy
        // equation to a negative body fat and so produces no estimate at all.
        val neckSpan = anchors.shoulderRow - anchors.chinRow
        narrowestBetween(
            profile,
            anchors.chinRow + (neckSpan * NECK_BAND_START).toInt(),
            anchors.chinRow + (neckSpan * NECK_BAND_END).toInt(),
        )?.let { sites[ScanSite.NECK] = it }

        narrowestBetween(profile, anchors.shoulderRow, anchors.hipRow)
            ?.let { sites[ScanSite.WAIST] = it }

        // Chest sits above the waist, so it can only be searched once the waist is known.
        sites[ScanSite.WAIST]?.let { waistRow ->
            widestBetween(profile, anchors.shoulderRow, waistRow)
                ?.let { sites[ScanSite.CHEST] = it }
        }

        val hipSpan = anchors.kneeRow - anchors.hipRow

        widestBetween(profile, anchors.hipRow, anchors.hipRow + (hipSpan * HIP_BAND_END).toInt())
            ?.let { sites[ScanSite.HIP] = it }

        widestBetween(
            profile = profile,
            fromRow = anchors.hipRow + (hipSpan * THIGH_BAND_START).toInt(),
            toRow = anchors.hipRow + (hipSpan * THIGH_BAND_END).toInt(),
            useLegWidth = true,
        )?.let { sites[ScanSite.THIGH] = it }

        return sites
    }

    /**
     * Row with the smallest non-zero width in [fromRow, toRow].
     *
     * Zero-width rows are skipped rather than winning: a gap in the mask is missing data,
     * not an infinitely narrow waist. Ignoring this is how a segmentation hole becomes a
     * 20 cm waist measurement.
     */
    fun narrowestBetween(
        profile: WidthProfile,
        fromRow: Int,
        toRow: Int,
        useLegWidth: Boolean = false,
    ): Int? {
        val range = clampRange(profile, fromRow, toRow) ?: return null

        var bestRow: Int? = null
        var bestWidth = Double.MAX_VALUE
        for (row in range) {
            val width = if (useLegWidth) profile.legWidthAt(row) else profile.torsoWidthAt(row)
            if (width > 0.0 && width < bestWidth) {
                bestWidth = width
                bestRow = row
            }
        }
        return bestRow
    }

    /** Row with the largest width in [fromRow, toRow]. */
    fun widestBetween(
        profile: WidthProfile,
        fromRow: Int,
        toRow: Int,
        useLegWidth: Boolean = false,
    ): Int? {
        val range = clampRange(profile, fromRow, toRow) ?: return null

        var bestRow: Int? = null
        var bestWidth = 0.0
        for (row in range) {
            val width = if (useLegWidth) profile.legWidthAt(row) else profile.torsoWidthAt(row)
            if (width > bestWidth) {
                bestWidth = width
                bestRow = row
            }
        }
        return bestRow
    }

    private fun clampRange(profile: WidthProfile, fromRow: Int, toRow: Int): IntRange? {
        val start = maxOf(fromRow, profile.topRow, 0)
        val end = minOf(toRow, profile.bottomRow, profile.torsoWidths.lastIndex)
        return if (start > end) null else start..end
    }
}
