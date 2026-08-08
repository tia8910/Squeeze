package com.squeeze.core.lab

import com.squeeze.core.model.Sex
import com.squeeze.core.scan.FrontPoseGeometry
import com.squeeze.core.scan.PoseAnchors
import com.squeeze.core.scan.PosePoint
import com.squeeze.core.scan.SilhouetteBodyFat
import com.squeeze.core.scan.TrunkBands
import com.squeeze.core.scan.Uprightness
import com.squeeze.core.scan.WidthProfile
import kotlin.js.JsExport

/**
 * The measurement pipeline, reachable from a browser.
 *
 * This exists so the bands can be **drawn on the photograph they were read from**. Every
 * wrong answer this app has produced was a band in the wrong place — the rib notch, both
 * arms, a waistband, the length of a sideways body — and every one was diagnosed from a
 * screenshot of a number, because a row index is invisible and a percentage is the only thing
 * that ever reaches a person's eyes.
 *
 * What makes the overlay worth trusting is that nothing here reimplements anything. This file
 * marshals arrays across the JavaScript boundary and calls the same [SilhouetteBodyFat] the
 * APK calls, over the same [TrunkBands]. A lab that drew bands from a parallel TypeScript
 * port would agree with the app right up until the moment it mattered.
 *
 * **Only in `jsMain`.** None of this is compiled into the Android build, so an export surface
 * shaped for a debugging tool cannot become an accidental public API of the app.
 *
 * Every type crossing the boundary is a primitive, an array, or a class declared here.
 * Kotlin's `List`, `Double?` and `data class` do not survive `@JsExport`, hence the plain
 * shapes and the `NaN`-for-absent convention below.
 */
@JsExport
class BandRange(val fromRow: Int, val toRowInclusive: Int)

/**
 * One reading, with the rows it came from.
 *
 * [waistToHip] and [percent] are `NaN` rather than null when absent, because a nullable
 * `Double` is not exportable. `NaN` is the right sentinel and not merely the available one:
 * it propagates through arithmetic instead of silently reading as zero, so a lab that forgets
 * to check shows `NaN` on screen rather than a confident nought.
 */
@JsExport
class LabReading(
    val waistToShoulder: Double,
    val waistToHip: Double,
    val percent: Double,
    val standardErrorPercent: Double,
    val waist: BandRange,
    val shoulder: BandRange,
    val hip: BandRange,
)

@JsExport
object MeasurementLab {

    /**
     * Reads a width profile exactly as a scan does.
     *
     * @param torsoWidths per-row trunk width, as a fraction of image width — the same unit
     *   the Android extractor produces, so a profile lifted from either side is comparable
     * @param clippedRows rows whose width came from the pose bound rather than the
     *   silhouette, which the medians skip
     * @param pelvisSpan distance between the hip landmarks as a fraction of image width, or
     *   `NaN` to skip the clothing veto
     * @return null when the profile cannot support a reading, which is the same answer the
     *   app gives and for the same reasons
     */
    fun read(
        torsoWidths: DoubleArray,
        clippedRows: Array<Boolean>,
        topRow: Int,
        bottomRow: Int,
        shoulderRow: Int,
        hipRow: Int,
        kneeRow: Int,
        chinRow: Int,
        pelvisSpan: Double,
        male: Boolean,
    ): LabReading? {
        val anchors = runCatching {
            PoseAnchors(
                shoulderRow = shoulderRow,
                hipRow = hipRow,
                kneeRow = kneeRow,
                chinRow = chinRow,
            )
        }.getOrNull() ?: return null

        val profile = runCatching {
            WidthProfile(
                torsoWidths = torsoWidths,
                legWidths = DoubleArray(torsoWidths.size),
                topRow = topRow,
                bottomRow = bottomRow,
                clippedRows = BooleanArray(torsoWidths.size) {
                    clippedRows.getOrNull(it) ?: false
                },
            )
        }.getOrNull() ?: return null

        val bands = TrunkBands.from(anchors)
        val indices = SilhouetteBodyFat.indicesFrom(
            profile,
            anchors,
            pelvisSpan.takeIf { !it.isNaN() && it > 0.0 },
        ) ?: return null

        val estimate = SilhouetteBodyFat.estimate(
            indices,
            if (male) Sex.MALE else Sex.FEMALE,
        )

        return LabReading(
            waistToShoulder = indices.waistToShoulder,
            waistToHip = indices.waistToHip ?: Double.NaN,
            percent = estimate?.percent ?: Double.NaN,
            standardErrorPercent = estimate?.standardErrorPercent ?: Double.NaN,
            waist = BandRange(bands.waist.fromRow, bands.waist.toRowInclusive),
            shoulder = BandRange(bands.shoulder.fromRow, bands.shoulder.toRowInclusive),
            hip = BandRange(bands.hip.fromRow, bands.hip.toRowInclusive),
        )
    }

    /**
     * The bands alone, for drawing them before any width profile exists.
     *
     * Useful the moment the pose lands: the overlay can show where the app is *about* to
     * measure, which is when a badly placed band is easiest to see.
     *
     * @return null when the rows are not a plausible skeleton
     */
    fun bands(shoulderRow: Int, hipRow: Int, kneeRow: Int, chinRow: Int): Array<BandRange>? {
        val anchors = runCatching {
            PoseAnchors(shoulderRow, hipRow, kneeRow, chinRow)
        }.getOrNull() ?: return null

        val bands = TrunkBands.from(anchors)
        return arrayOf(
            BandRange(bands.waist.fromRow, bands.waist.toRowInclusive),
            BandRange(bands.shoulder.fromRow, bands.shoulder.toRowInclusive),
            BandRange(bands.hip.fromRow, bands.hip.toRowInclusive),
        )
    }

    /**
     * Quarter-turns clockwise that would stand this subject up; see [Uprightness].
     *
     * Coordinates are normalised 0..1 as the pose model reports them.
     */
    fun quarterTurnsToUpright(
        shoulderLeftX: Double,
        shoulderLeftY: Double,
        shoulderRightX: Double,
        shoulderRightY: Double,
        hipLeftX: Double,
        hipLeftY: Double,
        hipRightX: Double,
        hipRightY: Double,
    ): Int = Uprightness.quarterTurnsClockwise(
        FrontPoseGeometry(
            shoulderLeft = PosePoint(shoulderLeftX, shoulderLeftY),
            shoulderRight = PosePoint(shoulderRightX, shoulderRightY),
            hipLeft = PosePoint(hipLeftX, hipLeftY),
            hipRight = PosePoint(hipRightX, hipRightY),
        ),
    )
}
