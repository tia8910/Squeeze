package com.squeeze.core.render

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The drawn body.
 *
 * A figure is checked differently from a number. Nobody can assert that a drawing looks right,
 * so what is asserted here is that it is *about this person*: that a wider waist draws a wider
 * body, that a woman is not drawn as a narrow man, that nothing is claimed as measured which
 * was not, and that no polygon folds through itself — which is what produced a bow-tie foot
 * the first time this was rendered.
 */
class BodyFigureTest {

    private val man = Profile(heightCm = 175.0, birthYear = 1990, sex = Sex.MALE)
    private val womanProfile = Profile(heightCm = 165.0, birthYear = 1990, sex = Sex.FEMALE)

    private val leanMan = Circumferences(
        neckCm = 38.0,
        waistCm = 80.0,
        hipCm = 95.0,
        chestCm = 100.0,
        thighCm = 55.0,
        armCm = 32.0,
        calfCm = 37.0,
    )

    private fun build(
        profile: Profile = man,
        circumferences: Circumferences = leanMan,
        bodyFat: Double? = 12.0,
        view: BodyView = BodyView.FRONT,
    ) = BodyFigureBuilder.build(profile, circumferences, bodyFat, view)

    /** The widest point the figure reaches, in stature units. */
    private fun halfWidth(figure: BodyFigure) =
        figure.outline.flatten().maxOf { abs(it.x) }

    /** The widest point of the trunk polygon alone, which is the third one built. */
    private fun trunkHalfWidthAt(figure: BodyFigure, y: Double, tolerance: Double = 0.02) =
        figure.outline[2].filter { abs(it.y - y) < tolerance }.maxOfOrNull { abs(it.x) }

    @Test
    fun `a record with no waist draws nothing`() {
        // Every other site has a population substitute. The waist does not, because the waist
        // is the measurement the reader came for — filling it in would draw a stranger and
        // label it with this person's name.
        assertNull(build(circumferences = Circumferences(chestCm = 100.0, hipCm = 95.0)))
    }

    @Test
    fun `a waist alone is enough`() {
        val figure = build(circumferences = Circumferences(waistCm = 80.0))

        assertNotNull(figure)
        assertTrue(figure.outline.isNotEmpty())
    }

    @Test
    fun `a bigger waist draws a bigger body`() {
        val widths = listOf(70.0, 85.0, 100.0, 115.0).map { waist ->
            val figure = build(circumferences = leanMan.copy(waistCm = waist))
            assertNotNull(figure)
            trunkHalfWidthAt(figure, 0.380)!!
        }

        assertTrue(widths.zipWithNext().all { (a, b) -> b > a }, "$widths")
    }

    @Test
    fun `every site the record did not measure is named`() {
        val figure = build(circumferences = Circumferences(waistCm = 80.0, chestCm = 100.0))

        assertNotNull(figure)
        assertTrue("Neck" in figure.estimatedSites)
        assertTrue("Hip" in figure.estimatedSites)
        assertTrue("Thigh" in figure.estimatedSites)
        // Never measured by this app at all, so always declared.
        assertTrue("Shoulders" in figure.estimatedSites)
        // Both of these came from the record and must not be claimed as estimates.
        assertTrue("Waist" !in figure.estimatedSites)
        assertTrue("Chest" !in figure.estimatedSites)
    }

    @Test
    fun `a complete record still admits the shoulders were never measured`() {
        val figure = build()

        assertNotNull(figure)
        assertEquals(listOf("Shoulders"), figure.estimatedSites)
    }

    @Test
    fun `a woman is drawn hip-dominant and a man shoulder-dominant`() {
        // The single property that decides whether the female figure reads as female. Both
        // are given only a waist, so everything else comes from the sex's own proportions.
        val waistOnly = Circumferences(waistCm = 72.0)

        val female = build(womanProfile, waistOnly)
        val male = build(man, waistOnly)

        assertNotNull(female)
        assertNotNull(male)

        val femaleHip = trunkHalfWidthAt(female, 0.487)!!
        val femaleShoulder = trunkHalfWidthAt(female, 0.230, tolerance = 0.05)!!
        val maleHip = trunkHalfWidthAt(male, 0.470)!!
        val maleShoulder = trunkHalfWidthAt(male, 0.230, tolerance = 0.05)!!

        assertTrue(femaleHip > femaleShoulder * 0.82, "$femaleHip vs $femaleShoulder")
        assertTrue(maleHip < maleShoulder * 0.85, "$maleHip vs $maleShoulder")
    }

    @Test
    fun `the profile deepens at the belly as body fat rises`() {
        val depths = listOf(10.0, 20.0, 30.0, 35.0).map { percent ->
            val figure = build(bodyFat = percent, view = BodyView.SIDE)
            assertNotNull(figure)
            // Furthest point forward on the trunk at the waist.
            figure.outline[2].filter { abs(it.y - 0.380) < 0.02 }.maxOf { it.x }
        }

        assertTrue(depths.zipWithNext().all { (a, b) -> b > a }, "$depths")
    }

    @Test
    fun `front and back share an outline, because a girth cannot tell them apart`() {
        val front = build(view = BodyView.FRONT)
        val back = build(view = BodyView.BACK)

        assertNotNull(front)
        assertNotNull(back)
        assertEquals(front.outline, back.outline)
        // What differs is decoration, and only decoration.
        assertTrue(front.detail != back.detail)
    }

    @Test
    fun `no polygon folds through itself`() {
        // The bow-tie test. A foot appended onto its leg's point list closed through the
        // leg's own edge and filled as an hourglass; separating them fixed it, and this is
        // what stops it coming back.
        BodyView.entries.forEach { view ->
            val figure = build(view = view)
            assertNotNull(figure)
            figure.outline.forEachIndexed { index, polygon ->
                assertTrue(polygon.size >= 3, "$view polygon $index has ${polygon.size} points")
                assertFalse(selfIntersects(polygon), "$view polygon $index folds through itself")
            }
        }
    }

    @Test
    fun `the figure stays inside its own frame`() {
        BodyView.entries.forEach { view ->
            val figure = build(view = view)
            assertNotNull(figure)
            figure.outline.flatten().forEach { point ->
                assertTrue(point.y >= -1e-9 && point.y <= 1.0 + 1e-9, "$view y=${point.y}")
                assertTrue(point.x.isFinite() && point.y.isFinite(), "$view $point")
            }
            // A body half a stature wide is a drawing bug, not a person.
            assertTrue(halfWidth(figure) < 0.30, "$view width ${halfWidth(figure)}")
        }
    }

    @Test
    fun `an ellipse solved from its perimeter has that perimeter`() {
        listOf(1.0, 1.22, 1.28, 1.42, 1.45).forEach { ratio ->
            val (width, depth) = BodyFigureBuilder.ellipseAxes(0.457, ratio)
            val a = width / 2.0
            val b = depth / 2.0
            val perimeter = PI * (3.0 * (a + b) - sqrt((3.0 * a + b) * (a + 3.0 * b)))

            assertEquals(0.457, perimeter, 1e-9, "ratio $ratio")
            assertEquals(ratio, width / depth, 1e-9, "ratio $ratio")
        }
    }

    @Test
    fun `the belly fraction is read on each sex's own scale`() {
        // 25% is lean-ish on a woman and heavy on a man, so the same number must not draw the
        // same profile.
        val male = BodyFigureBuilder.bellyForwardFraction(25.0, Sex.MALE)
        val female = BodyFigureBuilder.bellyForwardFraction(25.0, Sex.FEMALE)

        assertTrue(male > female, "$male vs $female")
    }

    /** Brute-force segment crossing test; the polygons here are a few dozen points at most. */
    private fun selfIntersects(polygon: List<FigurePoint>): Boolean {
        val n = polygon.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                // Skip shared endpoints, which touch by construction.
                if (j == i || (j + 1) % n == i || (i + 1) % n == j) continue
                if (crosses(
                        polygon[i], polygon[(i + 1) % n],
                        polygon[j], polygon[(j + 1) % n],
                    )
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun crosses(
        a1: FigurePoint,
        a2: FigurePoint,
        b1: FigurePoint,
        b2: FigurePoint,
    ): Boolean {
        fun side(p: FigurePoint, q: FigurePoint, r: FigurePoint): Int {
            val value = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
            return when {
                value > 1e-12 -> 1
                value < -1e-12 -> -1
                else -> 0
            }
        }

        val d1 = side(a1, a2, b1)
        val d2 = side(a1, a2, b2)
        val d3 = side(b1, b2, a1)
        val d4 = side(b1, b2, a2)
        return d1 != d2 && d3 != d4 && d1 != 0 && d2 != 0 && d3 != 0 && d4 != 0
    }
}
