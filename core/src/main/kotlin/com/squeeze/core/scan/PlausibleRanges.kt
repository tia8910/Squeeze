package com.squeeze.core.scan

/**
 * Human limits for each measurement site, in centimetres.
 *
 * This is a correctness backstop, not a nicety. A photo pipeline has many ways to be
 * confidently wrong — a cropped body mis-scales everything, a mask that catches the
 * background traces the whole frame, an arm read as torso doubles a chest — and every one
 * of them produces a number that looks like a measurement. Writing such a number into the
 * trend is worse than failing, because the failure is silent and permanent: it bends the
 * user's history and there is nothing on screen to say it was nonsense.
 *
 * Ranges are deliberately generous. The job is to reject the physically impossible, not to
 * second-guess an unusual body — a bound that excludes real people is its own kind of bug.
 */
object PlausibleRanges {

    private val ranges: Map<ScanSite, ClosedFloatingPointRange<Double>> = mapOf(
        ScanSite.NECK to 25.0..55.0,
        ScanSite.CHEST to 60.0..160.0,
        ScanSite.WAIST to 45.0..200.0,
        ScanSite.HIP to 55.0..180.0,
        ScanSite.THIGH to 30.0..100.0,
        ScanSite.ARM to 15.0..65.0,
        ScanSite.CALF to 20.0..65.0,
    )

    fun rangeFor(site: ScanSite): ClosedFloatingPointRange<Double> =
        ranges[site] ?: 1.0..300.0

    fun isPlausible(site: ScanSite, centimetres: Double): Boolean =
        centimetres.isFinite() && centimetres in rangeFor(site)

    /**
     * Cross-site sanity: a chest narrower than a neck, or a waist wider than the range
     * allows relative to the hip, means the sites were mis-located even when each number is
     * individually inside its own bounds.
     *
     * Returns the sites that are inconsistent with the rest of the set.
     */
    fun inconsistentSites(measurements: Map<ScanSite, Double>): Set<ScanSite> {
        val bad = mutableSetOf<ScanSite>()

        val neck = measurements[ScanSite.NECK]
        val chest = measurements[ScanSite.CHEST]
        val waist = measurements[ScanSite.WAIST]

        // A chest is never smaller than the neck it sits below.
        if (neck != null && chest != null && chest <= neck) {
            bad += ScanSite.CHEST
        }

        // Nor is a neck ever larger than the waist beneath it.
        if (neck != null && waist != null && neck >= waist) {
            bad += ScanSite.NECK
        }

        return bad
    }
}
