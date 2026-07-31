package com.squeeze.core.scan

/**
 * A body silhouette reduced to its width at every row of the image.
 *
 * This is what a segmentation mask becomes once the pixels stop mattering: for each row,
 * how wide the body is, as a fraction of image width. Reducing the mask to this before any
 * anatomy is inferred is what lets the hard part — deciding *where* the waist is — be
 * ordinary numeric code that can be tested against synthetic bodies, with no model, camera
 * or device involved.
 *
 * @param widths one entry per image row, 0.0 where no body pixels were found
 * @param topRow first row containing the body, i.e. the crown of the head
 * @param bottomRow last row containing the body, normally the feet
 */
data class WidthProfile(
    val widths: DoubleArray,
    val topRow: Int,
    val bottomRow: Int,
) {
    init {
        require(widths.isNotEmpty()) { "profile cannot be empty" }
        require(topRow in widths.indices) { "topRow $topRow outside profile" }
        require(bottomRow in widths.indices) { "bottomRow $bottomRow outside profile" }
        require(topRow < bottomRow) { "topRow must be above bottomRow" }
    }

    /** How much of the frame the body occupies vertically; the basis of scale recovery. */
    val bodyHeightFraction: Double
        get() = (bottomRow - topRow).toDouble() / widths.size.toDouble()

    fun widthAt(row: Int): Double = widths.getOrElse(row) { 0.0 }

    fun heightFractionOf(row: Int): Double = row.toDouble() / widths.size.toDouble()

    // Explicit equals/hashCode: the compiler-generated versions for a data class compare
    // DoubleArray by identity, which would make two identical profiles unequal.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WidthProfile) return false
        return topRow == other.topRow &&
            bottomRow == other.bottomRow &&
            widths.contentEquals(other.widths)
    }

    override fun hashCode(): Int =
        (widths.contentHashCode() * 31 + topRow) * 31 + bottomRow
}

/**
 * Vertical positions of the skeletal landmarks a pose model reports, as image rows.
 *
 * Only the anchors needed to bound an anatomical search are kept. The pose model is good at
 * locating joints and poor at locating soft-tissue landmarks like the natural waist, so it
 * is used to say roughly *where to look* and the silhouette is used to decide exactly where
 * the site is.
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
 *  - **Neck** — the narrowest point between the chin and the shoulders.
 *  - **Waist** — the narrowest point between the shoulders and the hips. This is what
 *    "natural waist" means anatomically, and searching for it is far more robust than the
 *    common shortcut of taking a fixed proportion between two joints, which lands on the
 *    ribs for a long-torsoed person and on the hips for a short one.
 *  - **Hip** — the widest point at or below the hip joint, which is the gluteal maximum.
 *  - **Chest** — the widest point between the shoulders and the waist.
 *  - **Thigh** — the widest point just below the hip, where a thigh is conventionally taken.
 *
 * Every one of these is a search over the width profile, so they adapt to the individual
 * body rather than assuming average proportions.
 */
object AnatomicalLevelFinder {

    /** Thigh measurement sits in the upper portion of the hip-to-knee span. */
    private const val THIGH_BAND_START = 0.10
    private const val THIGH_BAND_END = 0.35

    /** Hip search continues below the hip joint far enough to include the gluteal maximum. */
    private const val HIP_SEARCH_EXTENSION = 0.35

    /**
     * Finds every site the silhouette supports.
     *
     * @return rows keyed by site. A site is absent when its search band is degenerate,
     *   which happens on a badly cropped photo; callers must treat absence as "not
     *   measured" rather than substituting a default.
     */
    fun detectSites(profile: WidthProfile, anchors: PoseAnchors): Map<ScanSite, Int> {
        val sites = mutableMapOf<ScanSite, Int>()

        narrowestBetween(profile, anchors.chinRow, anchors.shoulderRow)
            ?.let { sites[ScanSite.NECK] = it }

        narrowestBetween(profile, anchors.shoulderRow, anchors.hipRow)
            ?.let { sites[ScanSite.WAIST] = it }

        // Chest is above the waist, so it can only be searched once the waist is known.
        sites[ScanSite.WAIST]?.let { waistRow ->
            widestBetween(profile, anchors.shoulderRow, waistRow)
                ?.let { sites[ScanSite.CHEST] = it }
        }

        val hipSpan = anchors.kneeRow - anchors.hipRow
        val hipSearchEnd = anchors.hipRow + (hipSpan * HIP_SEARCH_EXTENSION).toInt()
        widestBetween(profile, anchors.hipRow, hipSearchEnd)
            ?.let { sites[ScanSite.HIP] = it }

        val thighStart = anchors.hipRow + (hipSpan * THIGH_BAND_START).toInt()
        val thighEnd = anchors.hipRow + (hipSpan * THIGH_BAND_END).toInt()
        widestBetween(profile, thighStart, thighEnd)
            ?.let { sites[ScanSite.THIGH] = it }

        return sites
    }

    /**
     * Row with the smallest non-zero width in [fromRow, toRow].
     *
     * Zero-width rows are skipped rather than winning the search: a gap in the mask is
     * missing data, not an infinitely narrow waist. Ignoring this is how a segmentation
     * hole becomes a 20 cm waist measurement.
     */
    fun narrowestBetween(profile: WidthProfile, fromRow: Int, toRow: Int): Int? {
        val range = clampRange(profile, fromRow, toRow) ?: return null

        var bestRow: Int? = null
        var bestWidth = Double.MAX_VALUE
        for (row in range) {
            val width = profile.widthAt(row)
            if (width > 0.0 && width < bestWidth) {
                bestWidth = width
                bestRow = row
            }
        }
        return bestRow
    }

    /** Row with the largest width in [fromRow, toRow]. */
    fun widestBetween(profile: WidthProfile, fromRow: Int, toRow: Int): Int? {
        val range = clampRange(profile, fromRow, toRow) ?: return null

        var bestRow: Int? = null
        var bestWidth = 0.0
        for (row in range) {
            val width = profile.widthAt(row)
            if (width > bestWidth) {
                bestWidth = width
                bestRow = row
            }
        }
        return bestRow
    }

    private fun clampRange(profile: WidthProfile, fromRow: Int, toRow: Int): IntRange? {
        val start = maxOf(fromRow, profile.topRow, 0)
        val end = minOf(toRow, profile.bottomRow, profile.widths.lastIndex)
        return if (start > end) null else start..end
    }
}
