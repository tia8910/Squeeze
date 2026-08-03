package com.squeeze.core.scan

import com.squeeze.core.model.Circumferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DepthRatiosTest {

    @Test
    fun `assumed depth is always less than the width it derives from`() {
        // A body is never deeper than it is wide at any of these sites; a ratio above 1
        // would silently invert the ellipse's axes.
        ScanSite.entries.forEach { site ->
            val ratio = DepthRatios.depthToWidth(site)
            assertTrue(ratio in 0.5..1.0, "$site ratio $ratio outside a sane range")
        }
    }

    @Test
    fun `the neck is treated as near-circular and the chest as flattest`() {
        assertTrue(
            DepthRatios.depthToWidth(ScanSite.NECK) > DepthRatios.depthToWidth(ScanSite.CHEST),
            "a neck is rounder in cross-section than a chest",
        )
    }

    @Test
    fun `an assumed depth produces a believable circumference`() {
        // 32 cm across the waist, depth assumed. The result must land where a real waist
        // lands, or the fallback is worse than no fallback.
        val depth = DepthRatios.estimateDepth(ScanSite.WAIST, 32.0)
        val circumference = CircumferenceEstimator.circumference(32.0, depth)

        assertTrue(circumference in 80.0..100.0, "got $circumference")
    }

    @Test
    fun `assuming depth shifts the number but not its response to change`() {
        // The whole argument for allowing a front-only scan: the assumption is a constant
        // personal offset, so a real 3 cm waist reduction still reads as very nearly 3 cm.
        fun assumed(widthCm: Double) = CircumferenceEstimator.circumference(
            widthCm,
            DepthRatios.estimateDepth(ScanSite.WAIST, widthCm),
        )

        fun measured(widthCm: Double) = CircumferenceEstimator.circumference(widthCm, widthCm * 0.62)

        val assumedChange = assumed(34.0) - assumed(31.0)
        val measuredChange = measured(34.0) - measured(31.0)

        // The absolute numbers differ, because the assumed ratio is wrong for this person.
        assertTrue(abs(assumed(34.0) - measured(34.0)) > 2.0)
        // The detected change barely does, which is what a trend is built from.
        assertEquals(measuredChange, assumedChange, 1.5)
    }

    private fun abs(v: Double) = kotlin.math.abs(v)
}

class BodyProportionsTest {

    private val typical = Circumferences(
        neckCm = 38.0,
        chestCm = 100.0,
        waistCm = 84.0,
        hipCm = 96.0,
    )

    @Test
    fun `waist to hip is computed and interpreted`() {
        val proportions = BodyProportions.analyse(typical, heightCm = 178.0)
        val whr = proportions.firstOrNull { it.name == "Waist-to-hip" }

        assertNotNull(whr)
        assertEquals(84.0 / 96.0, whr.value, 1e-9)
        assertTrue(whr.interpretation.isNotBlank())
    }

    @Test
    fun `waist to height flags above the half-height guideline`() {
        val healthy = BodyProportions.analyse(typical, heightCm = 178.0)
            .first { it.name == "Waist-to-height" }
        assertFalse(healthy.flagged, "84/178 is 0.47, below the 0.5 threshold")

        val raised = BodyProportions.analyse(typical.copy(waistCm = 100.0), heightCm = 178.0)
            .first { it.name == "Waist-to-height" }
        assertTrue(raised.flagged, "100/178 is 0.56, above the threshold")
    }

    @Test
    fun `ratios are immune to a scale error that ruins the raw numbers`() {
        // The reason ratios are the most trustworthy output of a photo scan: both
        // measurements come from one photograph at one scale, so the error divides out.
        val inflated = Circumferences(
            neckCm = 38.0 * 1.2,
            chestCm = 100.0 * 1.2,
            waistCm = 84.0 * 1.2,
            hipCm = 96.0 * 1.2,
        )

        val correct = BodyProportions.analyse(typical, heightCm = null)
            .first { it.name == "Waist-to-hip" }.value
        val wrong = BodyProportions.analyse(inflated, heightCm = null)
            .first { it.name == "Waist-to-hip" }.value

        assertEquals(correct, wrong, 1e-9)
    }

    @Test
    fun `waist to height is omitted without a height rather than guessed`() {
        val proportions = BodyProportions.analyse(typical, heightCm = null)
        assertTrue(proportions.none { it.name == "Waist-to-height" })
    }

    @Test
    fun `missing measurements simply yield fewer proportions`() {
        val sparse = BodyProportions.analyse(Circumferences(waistCm = 84.0), heightCm = 178.0)
        assertTrue(sparse.any { it.name == "Waist-to-height" })
        assertTrue(sparse.none { it.name == "Waist-to-hip" })
    }
}

class SymmetryAnalysisTest {

    @Test
    fun `a small difference is not reported as a finding`() {
        val finding = SymmetryFinding(ScanSite.ARM, leftCm = 35.0, rightCm = 35.6)

        assertFalse(
            finding.notable,
            "under two percent is within what a slight turn toward the camera produces",
        )
    }

    @Test
    fun `a real asymmetry is flagged`() {
        val finding = SymmetryFinding(ScanSite.ARM, leftCm = 33.0, rightCm = 37.0)

        assertTrue(finding.notable)
        assertEquals(4.0, finding.differenceCm, 1e-9)
        assertEquals(11.4, finding.percentDifference, 0.2)
    }

    @Test
    fun `findings are ordered by how large the difference is`() {
        val findings = SymmetryAnalysis.analyse(
            mapOf(
                ScanSite.CALF to (36.0 to 36.4),
                ScanSite.ARM to (33.0 to 37.0),
                ScanSite.THIGH to (55.0 to 57.0),
            ),
        )

        assertEquals(ScanSite.ARM, findings.first().site)
        assertEquals(ScanSite.CALF, findings.last().site)
    }
}

class PostureAnalysisTest {

    private fun geometry(
        shoulderLeftY: Double = 0.30,
        shoulderRightY: Double = 0.30,
        hipLeftY: Double = 0.55,
        hipRightY: Double = 0.55,
    ) = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.40, shoulderLeftY),
        shoulderRight = PosePoint(0.60, shoulderRightY),
        hipLeft = PosePoint(0.44, hipLeftY),
        hipRight = PosePoint(0.56, hipRightY),
    )

    @Test
    fun `level shoulders and hips are reported as level`() {
        val findings = PostureAnalysis.analyse(geometry())

        assertTrue(findings.none { it.notable })
        assertTrue(findings.any { it.name == "Shoulder level" })
        assertTrue(findings.any { it.name == "Hip level" })
    }

    @Test
    fun `a clear shoulder tilt is flagged`() {
        // Right shoulder 4% of frame height lower across a 20% width: about 11 degrees.
        val findings = PostureAnalysis.analyse(geometry(shoulderRightY = 0.34))
        val shoulders = findings.first { it.name == "Shoulder level" }

        assertTrue(shoulders.notable, "got ${shoulders.degrees} degrees")
    }

    @Test
    fun `opposing shoulder and hip tilts are called out separately`() {
        val findings = PostureAnalysis.analyse(
            geometry(shoulderRightY = 0.34, hipRightY = 0.51),
        )

        assertTrue(
            findings.any { it.name == "Counter-rotation" },
            "shoulders and hips tilted opposite ways is a distinct observation from stance",
        )
    }

    @Test
    fun `tilts in the same direction are not counter-rotation`() {
        val findings = PostureAnalysis.analyse(
            geometry(shoulderRightY = 0.34, hipRightY = 0.59),
        )

        assertTrue(findings.none { it.name == "Counter-rotation" })
    }

    @Test
    fun `a horizontal line reads as zero tilt`() {
        assertEquals(
            0.0,
            PostureAnalysis.tiltDegrees(PosePoint(0.4, 0.5), PosePoint(0.6, 0.5)),
            1e-9,
        )
    }
}
