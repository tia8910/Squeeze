package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LandmarkStatureTest {

    private fun nose(y: Double) = PosePoint(0.5, y)
    private fun ankle(y: Double) = PosePoint(0.5, y)

    @Test
    fun `recovers the framing a well-placed subject actually has`() {
        // Someone spanning the frame from 0.05 to 0.95 has a stature fraction of 0.90. Their
        // nose sits 0.075 of that below the crown and their ankles 0.035 above the floor.
        val statureTop = 0.05
        val statureBottom = 0.95
        val stature = statureBottom - statureTop

        val noseY = statureTop + stature * (1.0 - 0.925)
        val ankleY = statureBottom - stature * 0.039

        val recovered = LandmarkStature.frameFraction(nose(noseY), ankle(ankleY), ankle(ankleY))

        assertNotNull(recovered)
        assertTrue(
            kotlin.math.abs(recovered - stature) < 0.01,
            "recovered $recovered should be within a percent of the true $stature",
        )
    }

    @Test
    fun `takes the lower ankle rather than the average`() {
        // One foot forward lifts that ankle in the image. Averaging shortens the span and so
        // shrinks every circumference in the scan; the weight-bearing foot is on the floor.
        val withOneFootForward =
            LandmarkStature.frameFraction(nose(0.10), ankle(0.80), ankle(0.90))
        val withBothDown = LandmarkStature.frameFraction(nose(0.10), ankle(0.90), ankle(0.90))

        assertEquals(withBothDown, withOneFootForward)
    }

    @Test
    fun `is absent rather than guessed when landmarks are missing`() {
        assertNull(LandmarkStature.frameFraction(null, ankle(0.9), ankle(0.9)))
        assertNull(LandmarkStature.frameFraction(nose(0.1), null, null))
    }

    @Test
    fun `refuses an inverted or impossible span`() {
        // Ankles above the nose: the model has misread the pose entirely.
        assertNull(LandmarkStature.frameFraction(nose(0.9), ankle(0.1), ankle(0.1)))
        // A span this small cannot be a standing adult framed head to foot.
        assertNull(LandmarkStature.frameFraction(nose(0.50), ankle(0.51), ankle(0.51)))
    }
}

class ScaleCrossCheckTest {

    @Test
    fun `uses the mask when the two references agree`() {
        val decision = ScaleCrossCheck.resolve(maskFraction = 0.82, landmarkFraction = 0.80)

        assertNotNull(decision)
        assertEquals(ScaleSource.MASK, decision.source)
        assertEquals(0.82, decision.bodyHeightFraction)
    }

    @Test
    fun `falls back to landmarks when the mask has swallowed something`() {
        // The classic contamination: a shadow or a mirror frame extends the mask downward, so
        // the body appears to span more of the frame than it does.
        val decision = ScaleCrossCheck.resolve(maskFraction = 0.95, landmarkFraction = 0.80)

        assertNotNull(decision)
        assertEquals(ScaleSource.LANDMARK, decision.source)
        assertEquals(0.80, decision.bodyHeightFraction)
        assertNotNull(decision.disagreementPercent)
    }

    @Test
    fun `refuses when neither reference can be trusted`() {
        assertNull(ScaleCrossCheck.resolve(maskFraction = 0.99, landmarkFraction = 0.60))
    }

    @Test
    fun `uses the mask alone when no landmark estimate exists`() {
        val decision = ScaleCrossCheck.resolve(maskFraction = 0.75, landmarkFraction = null)

        assertNotNull(decision)
        assertEquals(ScaleSource.MASK, decision.source)
        assertNull(decision.disagreementPercent)
    }

    @Test
    fun `contamination that would have caused the observed waist swing is caught`() {
        // The failure this whole class exists for. Two photographs of one body produced
        // waists of 75.4 cm and 92.2 cm — a 22% spread, which is exactly what a 22% error in
        // scale looks like, because scale multiplies every measurement equally.
        //
        // Reproduced here: the true stature fraction is 0.72 in both shots, but in the second
        // the mask reached down into a shadow and read 0.59. Left alone that inflates the
        // waist by 0.72/0.59 = 22%.
        val truth = 0.72
        val contaminated = 0.59

        assertEquals(
            92.2,
            75.4 * (truth / contaminated),
            0.5,
            "sanity: a scale error of this size does produce the spread that was observed",
        )

        val clean = ScaleCrossCheck.resolve(maskFraction = truth, landmarkFraction = truth)
        val dirty = ScaleCrossCheck.resolve(maskFraction = contaminated, landmarkFraction = truth)

        assertNotNull(clean)
        assertNotNull(dirty)

        // Both scans now scale by the same number, which is the property the app's central
        // claim depends on: the trend can only mean anything if repeat scans agree.
        assertEquals(clean.bodyHeightFraction, dirty.bodyHeightFraction)
        assertEquals(ScaleSource.LANDMARK, dirty.source)
    }

    @Test
    fun `disagreement is reported relative to the larger estimate`() {
        val decision = ScaleCrossCheck.resolve(maskFraction = 1.0, landmarkFraction = 0.9)

        assertNotNull(decision)
        assertEquals(10.0, decision.disagreementPercent!!, 0.001)
    }
}
