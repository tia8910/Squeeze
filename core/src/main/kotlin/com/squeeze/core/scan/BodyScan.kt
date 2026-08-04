package com.squeeze.core.scan

import com.squeeze.core.model.Circumferences

/**
 * Anatomical levels a scan can measure, and where each one sits.
 *
 * The Navy equations need neck and waist for men, plus hip for women, so those three are
 * what a scan must produce to be useful. The rest are tracked for their own sake, because
 * a lifter cares whether an arm grew even when body fat did not move.
 */
enum class ScanSite {
    NECK, CHEST, WAIST, HIP, THIGH, ARM, CALF;

    /**
     * Whether a body-fat estimate can be produced without this site.
     *
     * Used by the capture UI to decide what to insist on: a scan missing the waist is not
     * a partial result, it is no result.
     */
    val requiredForBodyFat: Boolean
        get() = this == NECK || this == WAIST || this == HIP
}

/**
 * One measured level, before conversion to real units.
 *
 * @param depthAssumed true when no side photograph was taken and the sagittal depth came
 *   from [DepthRatios] rather than from a measurement. Tracked per marker rather than per
 *   scan because it changes what the number may be claimed to be.
 */
data class ScanMarker(
    val site: ScanSite,
    val slice: BodySlice,
    val depthAssumed: Boolean = false,
)

/**
 * A problem with the capture that the user can fix by retaking the photo.
 *
 * Surfaced rather than silently absorbed, because every one of these produces a number that
 * looks perfectly reasonable and is wrong — which is precisely the failure mode that makes
 * users stop trusting a measurement app.
 */
sealed interface ScanWarning {
    /** The subject fills too much of the frame for perspective distortion to be ignorable. */
    data object FramingTooTight : ScanWarning

    /** A marker sits at a different height in the front and side photos. */
    data class LevelMismatch(val site: ScanSite) : ScanWarning

    /** The cross-section is far from any plausible human shape. */
    data class ImplausibleShape(val site: ScanSite, val ratio: Double) : ScanWarning

    /** A site needed for a body-fat estimate was not marked. */
    data class MissingRequiredSite(val site: ScanSite) : ScanWarning

    /**
     * A measurement fell outside human limits and was discarded.
     *
     * Carried rather than swallowed so the user learns the scan went wrong and why, instead
     * of seeing a shorter list of results and assuming the rest are sound.
     */
    data class ImplausibleMeasurement(val site: ScanSite, val centimetres: Double) : ScanWarning
}

/**
 * @param circumferences the measured sites, ready to feed the same equations tape
 *   measurements use
 * @param warnings capture problems worth showing the user
 * @param usableForBodyFat false when a required site is missing, in which case the scan is
 *   still worth storing for its individual measurements but cannot produce a percentage
 */
data class ScanResult(
    val circumferences: Circumferences,
    val warnings: List<ScanWarning>,
    val usableForBodyFat: Boolean,
    /**
     * True when depth was assumed rather than photographed.
     *
     * Carried out of the analyser so the estimate can be stored with the wider error a
     * front-only scan deserves, and so the UI can say which kind of scan produced it.
     */
    val depthAssumed: Boolean = false,
)

/**
 * Turns marked-up photographs into circumferences.
 *
 * The pipeline is deliberately split so that where the markers came from is irrelevant to
 * the geometry. Today the user drags them onto the photo; a segmentation model can produce
 * the same [BodySlice] values later without any of this changing. That also means the
 * geometry is unit tested on plain numbers, with no camera, model or device involved.
 *
 * Output feeds [com.squeeze.core.bodycomp.BodyFatCalculator] exactly as a tape measurement
 * does — the only difference is that the estimate is tagged as a photo scan, which carries
 * its own repeatability figure because scale recovery adds error the tape does not have.
 */
class BodyScanAnalyser(
    private val scale: ScaleRecovery,
    private val imageAspectRatio: Double,
) {

    fun analyse(markers: List<ScanMarker>): ScanResult {
        val warnings = mutableListOf<ScanWarning>()

        if (scale.isFramingTooTight()) {
            warnings += ScanWarning.FramingTooTight
        }

        val measured = mutableMapOf<ScanSite, Double>()

        for (marker in markers) {
            val slice = marker.slice

            // Two views can disagree about where a level sits; one view cannot.
            if (!marker.depthAssumed &&
                CircumferenceEstimator.isLevelMismatched(
                    slice.frontHeightFraction,
                    slice.sideHeightFraction,
                )
            ) {
                warnings += ScanWarning.LevelMismatch(marker.site)
            }

            val frontCm = scale.widthToCm(slice.frontWidthFraction, imageAspectRatio)
            val sideCm = scale.widthToCm(slice.sideWidthFraction, imageAspectRatio)

            // An assumed depth is derived from the width, so its eccentricity is fixed by
            // construction and flagging it would only ever report our own constant back.
            val ratio = CircumferenceEstimator.eccentricityRatio(frontCm, sideCm)
            if (!marker.depthAssumed && ratio > MAX_PLAUSIBLE_RATIO) {
                warnings += ScanWarning.ImplausibleShape(marker.site, ratio)
            }

            val circumference = CircumferenceEstimator.circumference(frontCm, sideCm)

            // Discarded, not clamped. A value outside human limits is not a slightly-wrong
            // measurement to be nudged back into range — it is evidence the site was
            // mis-located, and the honest thing is to have no number rather than a
            // plausible-looking one.
            if (PlausibleRanges.isPlausibleForHeight(marker.site, circumference, scale.heightCm)) {
                measured[marker.site] = circumference
            } else {
                warnings += ScanWarning.ImplausibleMeasurement(marker.site, circumference)
            }
        }

        // Individually-legal numbers can still be collectively impossible; a chest narrower
        // than the neck above it means the search landed on the wrong rows.
        PlausibleRanges.inconsistentSites(measured).forEach { site ->
            measured[site]?.let { warnings += ScanWarning.ImplausibleMeasurement(site, it) }
            measured.remove(site)
        }

        val missingRequired = ScanSite.entries
            .filter { it.requiredForBodyFat && it !in measured }

        // Hip is only required for the female equation, so its absence is not fatal on its
        // own. The caller knows the profile; here we only report what was not measured.
        missingRequired.forEach { warnings += ScanWarning.MissingRequiredSite(it) }

        val hasCore = ScanSite.NECK in measured && ScanSite.WAIST in measured

        return ScanResult(
            circumferences = Circumferences(
                neckCm = measured[ScanSite.NECK],
                waistCm = measured[ScanSite.WAIST],
                hipCm = measured[ScanSite.HIP],
                chestCm = measured[ScanSite.CHEST],
                thighCm = measured[ScanSite.THIGH],
                armCm = measured[ScanSite.ARM],
                calfCm = measured[ScanSite.CALF],
            ),
            warnings = warnings,
            usableForBodyFat = hasCore,
            depthAssumed = markers.isNotEmpty() && markers.all { it.depthAssumed },
        )
    }

    private companion object {
        /**
         * A torso cross-section is wider than it is deep, but not unboundedly so. Beyond
         * this, the likeliest explanation is a misplaced marker rather than an unusual body.
         */
        const val MAX_PLAUSIBLE_RATIO = 2.6
    }
}

/**
 * Builds scan markers automatically from two segmented photographs.
 *
 * This is the path the app actually uses: the camera produces a front and a side image, a
 * segmentation model reduces each to a [WidthProfile], a pose model supplies [PoseAnchors],
 * and [AnatomicalLevelFinder] locates each site on its own silhouette. Nothing is measured
 * by hand.
 *
 * Sites are detected independently in each view rather than assuming the two photos are
 * aligned, because they are not: a person shifts between shots, and the camera may sit at a
 * different distance. Each view finds its own waist, and the pairing step then checks the
 * two agree about where that waist is before trusting the slice.
 */
object AutomaticScanBuilder {

    /**
     * @param sideProfile optional. When present the sagittal depth is measured; when absent
     *   it is assumed from [DepthRatios] and every marker is flagged [ScanMarker.depthAssumed].
     * @param backProfile optional. A back view supplies a second, independent coronal
     *   measurement of the same body, so averaging it with the front reduces random error by
     *   roughly root-two. It adds no depth information and cannot substitute for a side view.
     * @return markers for every site the available views support. A site needing a width
     *   that was not found is dropped rather than guessed.
     */
    fun build(
        frontProfile: WidthProfile,
        frontAnchors: PoseAnchors,
        sideProfile: WidthProfile? = null,
        sideAnchors: PoseAnchors? = null,
        backProfile: WidthProfile? = null,
        backAnchors: PoseAnchors? = null,
    ): List<ScanMarker> {
        val frontSites = AnatomicalLevelFinder.detectSites(frontProfile, frontAnchors)
        val sideSites = if (sideProfile != null && sideAnchors != null) {
            AnatomicalLevelFinder.detectSites(sideProfile, sideAnchors)
        } else {
            emptyMap()
        }
        val backSites = if (backProfile != null && backAnchors != null) {
            AnatomicalLevelFinder.detectSites(backProfile, backAnchors)
        } else {
            emptyMap()
        }

        return frontSites.mapNotNull { (site, frontRow) ->
            val useLeg = site == ScanSite.THIGH

            val frontWidth = frontProfile.widthFor(frontRow, useLeg)
            if (frontWidth <= 0.0) return@mapNotNull null

            // A back view measures the same axis as the front, so the two are averaged
            // rather than treated as different quantities.
            val coronalWidth = backSites[site]
                ?.let { backProfile?.widthFor(it, useLeg) }
                ?.takeIf { it > 0.0 }
                ?.let { (frontWidth + it) / 2.0 }
                ?: frontWidth

            val sideRow = sideSites[site]
            val measuredDepth = sideRow
                ?.let { sideProfile?.widthFor(it, useLeg) }
                ?.takeIf { it > 0.0 }

            val depth = measuredDepth ?: DepthRatios.estimateDepth(site, coronalWidth)

            ScanMarker(
                site = site,
                slice = BodySlice(
                    frontWidthFraction = coronalWidth,
                    sideWidthFraction = depth,
                    // Heights are normalised against each photo's own body span, so a level
                    // detected at the same point on the body compares equal even when the
                    // subject was framed differently between shots.
                    frontHeightFraction = relativeBodyHeight(frontProfile, frontRow),
                    sideHeightFraction = if (measuredDepth != null && sideRow != null && sideProfile != null) {
                        relativeBodyHeight(sideProfile, sideRow)
                    } else {
                        // No side view to disagree with, so the level trivially matches.
                        relativeBodyHeight(frontProfile, frontRow)
                    },
                ),
                depthAssumed = measuredDepth == null,
            )
        }
    }

    private fun WidthProfile.widthFor(row: Int, useLeg: Boolean): Double =
        if (useLeg) legWidthAt(row) else torsoWidthAt(row)

    /** Where a row sits along the body itself, 0.0 at the crown and 1.0 at the feet. */
    private fun relativeBodyHeight(profile: WidthProfile, row: Int): Double {
        val span = (profile.bottomRow - profile.topRow).toDouble()
        return if (span > 0.0) (row - profile.topRow) / span else 0.0
    }
}
