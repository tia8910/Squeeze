package com.squeeze.core.scan

import com.squeeze.core.bodycomp.PlateauPrior
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A lean body, photographed properly, is measured rather than overwritten.
 *
 * The failure this file exists for: a clear front-on trunk-framed photograph of a man with
 * visible abdominal separation, arms clear of his sides, plain background. The outline read
 * him correctly at single digits. The floor refused the reading, the interval widened to nine
 * points, [PlateauPrior] recognised that signature as an unresolved reading and replaced it
 * with a height-and-weight figure of 17.3% — seven points higher than the body in the
 * photograph. The app measured him right and then threw the measurement away.
 *
 * **The floor was measured on the other ratio.** [SilhouetteBodyFat.LEAN_PLATEAU_RATIO] and
 * the ceiling derived from it come from one observation: waist-to-*shoulder* runs 0.586 at
 * eight per cent, 0.592 at twelve and 0.580 at fifteen. Flat. That is a fact about the
 * shoulder denominator and there is an anatomical reason it is one — the arms attach at the
 * shoulder line. Nothing equivalent was ever measured on waist-to-hip, and the hip has none
 * of the properties that produce it: it sits below the arms, it is pelvic breadth rather than
 * muscle, and it is read by the same torso-run rule as the waist.
 *
 * So the floor now applies where its evidence applies. The tests below pin both halves: a
 * checked hip stands on its own, and an unchecked one does not.
 */
class CorroboratedHipTest {

    private val rows = 400
    private val shoulderRow = 80
    private val hipRow = 280

    private val anchors = PoseAnchors(
        shoulderRow = shoulderRow,
        hipRow = hipRow,
        kneeRow = hipRow + ((hipRow - shoulderRow) * 1.2).toInt(),
        chinRow = 30,
    )

    /**
     * A lean trunk: narrow waist, shoulders well clear of it, hips wider than the waist.
     *
     * The waist band runs 58% to 74% of the shoulder-to-hip span and the hip band starts at
     * the hip row, so the widths are laid down against those rather than against round
     * numbers that happen to look right.
     */
    private fun leanTrunk(
        shoulderWidth: Double = 0.40,
        waistWidth: Double = 0.245,
        hipWidth: Double = 0.30,
    ): WidthProfile {
        val widths = DoubleArray(rows) { 0.05 }
        val span = hipRow - shoulderRow
        for (row in shoulderRow..shoulderRow + (span * 0.20).toInt()) widths[row] = shoulderWidth
        for (row in shoulderRow + (span * 0.50).toInt()..shoulderRow + (span * 0.80).toInt()) {
            widths[row] = waistWidth
        }
        for (row in hipRow until rows) widths[row] = hipWidth
        return WidthProfile(widths, DoubleArray(rows), 20, 390)
    }

    /** Hip joints well inside the hip's own silhouette, at a ratio the veto accepts. */
    private val pelvisSpan = 0.30 / 1.6

    @Test
    fun `a hip the scan could check is corroborated`() {
        val indices = SilhouetteBodyFat.indicesFrom(leanTrunk(), anchors, pelvisSpan)

        assertNotNull(indices)
        assertNotNull(indices.waistToHip)
        assertTrue(indices.hipCorroborated)
    }

    @Test
    fun `and the lean reading it produces is allowed to stand`() {
        // 0.245 / 0.30 = 0.817. Through the hip anchors — 0.80 at eight per cent, 1.06 at
        // thirty-five — that is 9.7, less the two-point observed offset: 7.7. Under the old
        // rule this became 11.6 with a nine-point interval, which PlateauPrior then replaced
        // outright.
        val indices = SilhouetteBodyFat.indicesFrom(leanTrunk(), anchors, pelvisSpan)
        assertNotNull(indices)

        val estimate = SilhouetteBodyFat.estimate(indices, Sex.MALE)

        assertNotNull(estimate)
        assertTrue(
            estimate.percent < SilhouetteBodyFat.leanestClaimable(Sex.MALE),
            "a checked hip should not be floored, got ${estimate.percent}",
        )
        // And it carries the method's ordinary interval, not the plateau's — which is the
        // property that stops it being substituted downstream.
        assertEquals(
            EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
            estimate.standardErrorPercent,
            1e-9,
        )
    }

    @Test
    fun `the build figure no longer overwrites it`() {
        // The whole complaint, end to end. Same profile and weight that produced 17.3% on the
        // screenshot this was reported from.
        val man = Profile(heightCm = 175.0, birthYear = 1993, sex = Sex.MALE)
        val indices = SilhouetteBodyFat.indicesFrom(leanTrunk(), anchors, pelvisSpan)
        assertNotNull(indices)

        val measured = SilhouetteBodyFat.estimate(indices, Sex.MALE)
        assertNotNull(measured)

        assertFalse(
            PlateauPrior.isBounded(measured.percent, measured.standardErrorPercent, man, 70.0),
        )

        val resolved = PlateauPrior.resolve(measured, man, weightKg = 70.0)
        assertNotNull(resolved)
        assertEquals(measured.percent, resolved.percent, 1e-9)
    }

    @Test
    fun `an unchecked hip keeps the floor`() {
        // No pelvis span, so hipIsSkin waved the hip through without looking at it. That is
        // the right default for a ratio that is only being tempered, and the wrong basis for
        // lifting a floor — absence of evidence is not evidence of absence.
        val indices = SilhouetteBodyFat.indicesFrom(leanTrunk(), anchors, pelvisSpan = null)
        assertNotNull(indices)
        assertFalse(indices.hipCorroborated)

        val estimate = SilhouetteBodyFat.estimate(indices, Sex.MALE)

        assertNotNull(estimate)
        assertEquals(SilhouetteBodyFat.leanestClaimable(Sex.MALE), estimate.percent, 1e-9)
        assertEquals(SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, estimate.standardErrorPercent, 1e-9)
    }

    @Test
    fun `arms against the body keep the floor too`() {
        // The other half of the corroboration, and the one that matters most: the trunk bound
        // had to cut the waist band back, so what survived was chosen by the bound rather
        // than for anatomical reasons. A lean reading off that is exactly the reading the
        // floor exists to refuse.
        val profile = leanTrunk()
        val clipped = BooleanArray(rows)
        // Most of the waist band, not all of it: a fully clipped band yields no median at
        // all and the scan returns nothing, which is a different outcome from this one.
        // The rows come from TrunkBands rather than being recomputed, so this stays the band
        // that is actually measured.
        val waistBand = TrunkBands.from(anchors).waist
        for (row in waistBand.fromRow..waistBand.fromRow + (waistBand.rowCount * 0.7).toInt()) {
            clipped[row] = true
        }
        val contaminated = WidthProfile(
            torsoWidths = profile.torsoWidths,
            legWidths = profile.legWidths,
            topRow = profile.topRow,
            bottomRow = profile.bottomRow,
            clippedRows = clipped,
        )

        val indices = SilhouetteBodyFat.indicesFrom(contaminated, anchors, pelvisSpan)

        assertNotNull(indices)
        assertFalse(indices.hipCorroborated, "a clipped waist band cannot corroborate anything")
    }

    @Test
    fun `the shoulder branch is untouched by any of this`() {
        // The 4.93% failure came through here — waist-to-shoulder 0.686, both arms inside the
        // band, no hip at all — and nothing in this change may reopen it. The plateau was
        // measured on this ratio, so the floor belongs to it.
        val estimate = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = 0.686, waistToHip = null, hipCorroborated = true),
            Sex.MALE,
        )

        assertNotNull(estimate)
        assertEquals(SilhouetteBodyFat.leanestClaimable(Sex.MALE), estimate.percent, 1e-9)
        assertEquals(SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, estimate.standardErrorPercent, 1e-9)
    }

    @Test
    fun `a soft body is not made lean by corroboration`() {
        // The reassurance that this is not a general downward shift. The scan that reads
        // 0.87 waist-to-hip — the reconstruction of a real body with a soft midsection — is
        // above the floor already, so lifting the floor cannot move it at all.
        val soft = ShapeIndices(
            waistToShoulder = 0.687,
            waistToHip = 0.87,
            hipCorroborated = true,
        )
        val uncorroborated = soft.copy(hipCorroborated = false)

        val a = SilhouetteBodyFat.estimate(soft, Sex.MALE)
        val b = SilhouetteBodyFat.estimate(uncorroborated, Sex.MALE)

        assertNotNull(a)
        assertNotNull(b)
        assertEquals(b.percent, a.percent, 1e-9)
        assertTrue(a.percent > 13.0, "got ${a.percent}")
    }

    @Test
    fun `a clothed hip still cannot corroborate, because the veto rejects it first`() {
        // Loose shorts widen the mask at the hip, which is the 6.83% failure. hipIsSkin nulls
        // the ratio outright, so there is no hip reading to lift a floor from and the scan
        // falls back to the shoulder — floor and all.
        val widths = DoubleArray(rows) { 0.05 }
        val span = hipRow - shoulderRow
        for (row in shoulderRow..shoulderRow + (span * 0.20).toInt()) widths[row] = 0.40
        for (row in shoulderRow + (span * 0.50).toInt()..shoulderRow + (span * 0.80).toInt()) {
            widths[row] = 0.245
        }
        for (row in hipRow until rows) widths[row] = 0.60
        val baggy = WidthProfile(widths, DoubleArray(rows), 20, 390)

        val indices = SilhouetteBodyFat.indicesFrom(baggy, anchors, pelvisSpan)

        assertNotNull(indices)
        assertNull(indices.waistToHip)
        assertFalse(indices.hipCorroborated)
    }
}
