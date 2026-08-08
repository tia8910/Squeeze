package com.squeeze.core.scan

/**
 * Assumed depth-to-width ratios, used when only a front photograph exists.
 *
 * A circumference needs two axes. A front view supplies the coronal width; the sagittal
 * depth comes from a side view. With no side view the depth has to be assumed, and these
 * are the population figures used to do it.
 *
 * **Why a front-only scan is still worth taking.** The assumption is wrong for any given
 * person by some amount, but it is wrong by *the same* amount every time, because a
 * person's build does not change between Tuesday and the following Tuesday. That makes it
 * a systematic offset rather than random scatter — exactly the error that cancels out when
 * comparing someone against themselves. So a front-only scan tracks change nearly as well
 * as a two-photo scan, and is worse only at the absolute number. Entering one reference
 * scan removes even that, since personal calibration absorbs any constant offset.
 *
 * The honest limits, stated because the UI has to repeat them:
 *
 *  - Abdominal fat accumulates more in depth than in width, so a front-only waist
 *    under-reads for heavier subjects and the error grows with adiposity.
 *  - A side photograph always beats these numbers. They are a fallback, not a shortcut.
 */
object DepthRatios {

    /**
     * Sagittal depth as a fraction of coronal width.
     *
     * The neck is nearly circular. The torso is consistently deeper-than-wide at the hips
     * and flatter at the chest. Limbs are close to round.
     */
    private val ratios: Map<ScanSite, Double> = mapOf(
        ScanSite.NECK to 0.95,
        ScanSite.CHEST to 0.70,
        ScanSite.WAIST to 0.72,
        ScanSite.HIP to 0.76,
        ScanSite.THIGH to 0.90,
        ScanSite.ARM to 0.95,
        ScanSite.CALF to 0.90,
    )

    fun depthToWidth(site: ScanSite): Double = ratios[site] ?: DEFAULT_RATIO

    /** Estimated sagittal depth for a measured coronal width. */
    fun estimateDepth(site: ScanSite, widthFraction: Double): Double =
        widthFraction * depthToWidth(site)

    /**
     * Roughly circular is the safest assumption for an unlisted site: it cannot produce the
     * extreme eccentricity that a bad guess in either direction would.
     */
    private const val DEFAULT_RATIO = 0.85
}
