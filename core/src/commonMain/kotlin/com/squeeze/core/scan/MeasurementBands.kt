package com.squeeze.core.scan

/**
 * A contiguous run of rows that one measurement was taken over.
 *
 * [fromRow] and [toRowInclusive] are in the same coordinate space as [WidthProfile], which is
 * the segmentation mask's own rows rather than the photograph's — a segmenter is free to emit
 * its mask at the model's resolution, and everything downstream lives in that space.
 */
data class MeasurementBand(val fromRow: Int, val toRowInclusive: Int) {
    init {
        require(toRowInclusive >= fromRow) { "a band cannot end above where it starts" }
    }

    val rowCount: Int get() = toRowInclusive - fromRow + 1
}

/** Where each of the three trunk measurements was read. */
data class MeasurementBands(
    val waist: MeasurementBand,
    val shoulder: MeasurementBand,
    val hip: MeasurementBand,
)

/**
 * Where the trunk measurements are taken, as row ranges rather than as widths.
 *
 * These ranges were computed inline inside [SilhouetteBodyFat.indicesFrom] and discarded the
 * instant a width came out of them, which made them the least inspectable quantity in the
 * pipeline and, not coincidentally, the source of nearly every wrong answer it has produced:
 *
 * | reading | the band was actually on |
 * |---|---|
 * | 8.00% | the rib notch, above the belly |
 * | 6.22% | both arms, merged into the shoulder run |
 * | 6.83% | a waistband, well below the hip |
 * | 5.0% | the length of a body lying sideways in frame |
 *
 * Every one of those is obvious the moment the band is drawn on the photograph, and invisible
 * in every other form. Extracting them here is what lets the measurement lab draw *the bands
 * that were measured* rather than bands that resemble them — the two are only the same thing
 * if they come from one function, which is this one.
 *
 * The estimator calls it too, so there is no second implementation to drift.
 */
object TrunkBands {

    /**
     * Not nullable, and that is [PoseAnchors]' doing rather than an omission here.
     *
     * A trunk of zero or negative height would make every band degenerate, and the obvious
     * shape for this function is one that returns null on it. But `PoseAnchors` requires
     * `shoulderRow < hipRow` in its own `init`, so anchors with such a trunk cannot be
     * constructed — the invariant is enforced at the type boundary, one layer up. Returning
     * an optional here would add a branch that cannot be reached and a null every caller
     * would have to handle for no reason.
     *
     * @param anchors the pose landmark rows, already projected into the mask's space
     */
    fun from(anchors: PoseAnchors): MeasurementBands {
        val trunk = anchors.hipRow - anchors.shoulderRow

        val waist = MeasurementBand(
            fromRow = anchors.shoulderRow + (trunk * SilhouetteBodyFat.WAIST_BAND_START).toInt(),
            toRowInclusive = anchors.shoulderRow +
                (trunk * SilhouetteBodyFat.WAIST_BAND_END).toInt(),
        )

        val shoulder = MeasurementBand(
            fromRow = anchors.shoulderRow,
            toRowInclusive = anchors.shoulderRow +
                (trunk * SilhouetteBodyFat.SHOULDER_BAND).toInt(),
        )

        // Capped against the trunk as well as against hip-to-knee, because at
        // [ScanFraming.TORSO] the knee row is synthesised from the trunk length — so a depth
        // defined only as a fraction of hip-to-knee is a fraction of an assumption.
        val hipDepth = minOf(
            ((anchors.kneeRow - anchors.hipRow) * SilhouetteBodyFat.HIP_BAND).toInt(),
            (trunk * SilhouetteBodyFat.HIP_BAND_TRUNK_CAP).toInt(),
        ).coerceAtLeast(1)

        val hip = MeasurementBand(
            fromRow = anchors.hipRow,
            toRowInclusive = anchors.hipRow + hipDepth,
        )

        return MeasurementBands(waist = waist, shoulder = shoulder, hip = hip)
    }
}
