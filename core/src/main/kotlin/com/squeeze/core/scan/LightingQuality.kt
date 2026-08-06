package com.squeeze.core.scan

import kotlin.math.abs

/**
 * What the light was doing when the photograph was taken.
 *
 * @param meanLuminance average brightness of the region, 0..255
 * @param sideGradient how much brighter one side is than the other, as a fraction of the
 *   mean. The signature of a lamp or window off to one side.
 * @param verticalGradient the same top to bottom. The signature of overhead lighting.
 * @param clippedFraction proportion of samples at the top or bottom of the range, where the
 *   sensor has stopped recording detail altogether
 */
data class LightingSignature(
    val meanLuminance: Double,
    val sideGradient: Double,
    val verticalGradient: Double,
    val clippedFraction: Double,
)

/** What to tell the user, and whether the definition reading can be trusted at all. */
data class LightingVerdict(
    val signature: LightingSignature,
    /** False when the reading should not be compared against another scan. */
    val usableForDefinition: Boolean,
    /** Null when the light is fine. */
    val advice: String?,
)

/**
 * Judges the light, because the app cannot supply it.
 *
 * A phone cannot light a body at scan distance. The framing this method needs puts the
 * camera two to four metres away, and illumination falls off with the square of distance,
 * so an LED that is useful at arm's length delivers about a hundredth as much at three
 * metres — nothing against the room. Screen-as-flash has the same problem, and on a mirror
 * shot the rear flash points away from the subject entirely. There is no version of this
 * the app can win by emitting more light.
 *
 * What it can do is stop treating the light as invisible. Brightness was never really the
 * problem; **direction** is. A lamp off to one side carves shadow into every abdominal ridge
 * and reads as definition that is not there, which is exactly the failure that made raw
 * texture useless across the reference photographs: the fifteen and twenty-five per cent
 * figures scored as more defined than the eight, because they were lit harder.
 *
 * Exposure can be divided out — [AbdominalDefinition] does. Direction cannot, because a
 * side-lit ridge and a real ridge produce the same local contrast. The only honest responses
 * are to tell the user, and to refuse to compare two scans lit differently.
 */
object LightingQuality {

    /** Below this the crop is underexposed and contrast means little. */
    private const val MIN_MEAN = 60.0

    /** Above this it is blown out and detail has been clipped away. */
    private const val MAX_MEAN = 215.0

    /**
     * Side-to-side brightness difference, as a fraction of the mean, above which the light
     * is directional enough to fake definition.
     *
     * A torso is curved, so some falloff toward the flanks is unavoidable and normal. This
     * is set above that: it catches a lamp, not a body.
     */
    const val MAX_SIDE_GRADIENT = 0.28

    /** The same top to bottom. Overhead light shadows under the ribs and the navel. */
    const val MAX_VERTICAL_GRADIENT = 0.32

    /** Beyond this proportion clipped, the sensor has thrown away the signal. */
    private const val MAX_CLIPPED = 0.06

    /**
     * How far two scans' lighting may differ before their definition readings stop being
     * comparable.
     *
     * This is the number that matters most. The absolute definition score is contaminated by
     * lighting and always will be, but a *change* in it between two scans is real signal —
     * provided the light did not change too. Comparing a window-lit scan against a
     * ceiling-lit one measures the room.
     */
    const val COMPARABLE_GRADIENT_DELTA = 0.15

    /**
     * @param luminance row-major luminance of the abdomen crop, as [AbdominalDefinition] uses
     * @param width row length
     */
    fun evaluate(luminance: IntArray, width: Int): LightingVerdict? {
        if (width < 4 || luminance.size < width * 4) return null
        val height = luminance.size / width

        var total = 0.0
        var left = 0.0
        var right = 0.0
        var top = 0.0
        var bottom = 0.0
        var clipped = 0
        var leftCount = 0
        var rightCount = 0
        var topCount = 0
        var bottomCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = luminance[y * width + x]
                total += v
                if (v <= 4 || v >= 251) clipped++

                if (x < width / 2) { left += v; leftCount++ } else { right += v; rightCount++ }
                if (y < height / 2) { top += v; topCount++ } else { bottom += v; bottomCount++ }
            }
        }

        val count = width * height
        val mean = total / count
        if (mean <= 0.0) return null

        val signature = LightingSignature(
            meanLuminance = mean,
            sideGradient = abs(left / leftCount - right / rightCount) / mean,
            verticalGradient = abs(top / topCount - bottom / bottomCount) / mean,
            clippedFraction = clipped.toDouble() / count,
        )

        // Ordered by how badly each one corrupts the reading, so the user is told about the
        // worst thing rather than the first thing.
        val advice = when {
            signature.clippedFraction > MAX_CLIPPED ->
                "Parts of the photo are pure white or pure black, so the detail there is " +
                    "gone rather than dim. Move out of direct sun or away from a bare bulb."

            signature.meanLuminance < MIN_MEAN ->
                "Too dark to read definition. Face a window, or turn on more light — the " +
                    "phone's flash will not reach you at this distance."

            signature.meanLuminance > MAX_MEAN ->
                "Overexposed. Step out of direct light so the camera can record shading."

            signature.sideGradient > MAX_SIDE_GRADIENT ->
                "The light is coming from one side, which carves shadows that look like " +
                    "muscle definition. Turn so the light is behind the phone, or use a " +
                    "window in front of you rather than beside you."

            signature.verticalGradient > MAX_VERTICAL_GRADIENT ->
                "The light is coming from above, which shadows under your ribs and reads as " +
                    "definition. A window or lamp at chest height gives a truer picture."

            else -> null
        }

        // Directional light is what fakes definition, so a scan carrying it is still stored
        // and still measured — it simply cannot be compared. Darkness and clipping are worse:
        // there is nothing to measure at all.
        val usable = signature.clippedFraction <= MAX_CLIPPED &&
            signature.meanLuminance >= MIN_MEAN &&
            signature.meanLuminance <= MAX_MEAN

        return LightingVerdict(signature, usableForDefinition = usable, advice = advice)
    }

    /**
     * Whether two scans were lit alike enough for their definition scores to be compared.
     *
     * The point of the whole feature. An absolute definition score is contaminated by
     * lighting and cannot be decontaminated; a change between two scans is real, but only if
     * the light held still. Without this check the app would report a new lamp as fat loss.
     */
    fun comparable(a: LightingSignature, b: LightingSignature): Boolean =
        abs(a.sideGradient - b.sideGradient) <= COMPARABLE_GRADIENT_DELTA &&
            abs(a.verticalGradient - b.verticalGradient) <= COMPARABLE_GRADIENT_DELTA
}
