package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The neck, which this app could not measure at all until a model was asked what a pixel was.
 *
 * Every fixture here is a 64-square label grid standing in for the part segmenter's output,
 * because that is exactly what [BodyPartMap] consumes: a class index per pixel and nothing
 * else. No model runs, no photograph is needed, and the arithmetic is the same arithmetic
 * that runs on the device.
 *
 * The grid is a person facing the camera — hair over a face, a neck below it, shoulders below
 * that — and the hair deliberately falls either side of the neck, because that single detail
 * is the whole reason the silhouette failed. In a one-bit mask those hair pixels are body,
 * so the narrowest row between chin and shoulders is head-width, not neck-width, and what
 * reached the Navy equation was either a neck near the size of a head or no neck at all.
 */
class BodyPartMapTest {

    /** Grid edge. Not named `size`: inside a ByteArray extension that is the array's. */
    private val dim = 64

    private fun blank() = ByteArray(dim * dim) { BodyPartMap.BACKGROUND.toByte() }

    private fun ByteArray.fill(rows: IntRange, columns: IntRange, value: Int) {
        for (row in rows) for (column in columns) this[row * dim + column] = value.toByte()
    }

    /**
     * A man facing the camera, shoulders at row 35.
     *
     * Face skin spans columns 26..38 (thirteen wide) and ends at row 24. The neck is bare skin
     * at columns 29..35 (seven wide) down to row 34. Hair falls over columns 26..38 beside it,
     * which is what a silhouette measures instead of the neck.
     */
    private fun standing(hairBesideNeck: Boolean = true) = blank().apply {
        fill(0..9, 24..40, BodyPartMap.HAIR)
        fill(10..24, 24..40, BodyPartMap.HAIR)
        fill(10..24, 26..38, BodyPartMap.FACE_SKIN)
        if (hairBesideNeck) fill(25..34, 26..38, BodyPartMap.HAIR)
        fill(25..34, 29..35, BodyPartMap.BODY_SKIN)
        fill(35..63, 10..54, BodyPartMap.BODY_SKIN)
    }

    /** Narrowest row of the one-bit silhouette over the same band, for comparison. */
    private fun silhouetteNeckPixels(labels: ByteArray, fromRow: Int, toRow: Int): Int =
        (fromRow..toRow).minOf { row ->
            val columns = (0 until dim)
                .filter { labels[row * dim + it].toInt() != BodyPartMap.BACKGROUND }
            if (columns.isEmpty()) dim else columns.last() - columns.first() + 1
        }

    @Test
    fun `the neck is bare skin between the chin and the shoulders`() {
        val reading = BodyPartMap.readNeck(standing(), dim, dim, shoulderRow = 35)

        assertNotNull(reading)
        assertEquals(7.0 / 64.0, reading.widthFraction, 1e-9)
        // Below the chin at row 24 and above the shoulders at row 35.
        assertTrue(reading.heightFraction > 24.0 / 64.0, "${reading.heightFraction}")
        assertTrue(reading.heightFraction < 35.0 / 64.0, "${reading.heightFraction}")
        assertEquals(1.0, reading.bandCoverage, 1e-9)
    }

    @Test
    fun `hair falling beside the neck is not the neck`() {
        // **The failure this file was written for.** The same grid read as a silhouette gives
        // thirteen pixels — the width of the head — because hair and skin are both "person".
        // The model gives seven. That is not a refinement: it is the difference between a
        // neck the Navy equation can use and one that drives it below two per cent, where it
        // returns null and the app prints the outline method's constant instead.
        val labels = standing(hairBesideNeck = true)

        assertEquals(13, silhouetteNeckPixels(labels, 25, 34), "the outline sees head width")

        val reading = BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 35)
        assertNotNull(reading)
        assertEquals(7.0 / 64.0, reading.widthFraction, 1e-9)
    }

    @Test
    fun `the answer does not depend on whether hair happens to be there`() {
        // The corollary, and the property that makes the reading a measurement of the person:
        // a haircut must not move it. The silhouette's neck moves by six pixels out of
        // thirteen between these two grids.
        val withHair = BodyPartMap.readNeck(standing(true), dim, dim, 35)
        val without = BodyPartMap.readNeck(standing(false), dim, dim, 35)

        assertNotNull(withHair)
        assertNotNull(without)
        assertEquals(withHair.widthFraction, without.widthFraction, 1e-9)
        assertEquals(withHair.heightFraction, without.heightFraction, 1e-9)
    }

    @Test
    fun `a collar is refused rather than measured`() {
        // Rows 25 to 32 are clothing, leaving two rows of skin under the jaw. Those two rows
        // have a perfectly good width and it is not a neck — it is where the collar starts.
        // Coverage falls to two rows in eleven and the reading is refused.
        val labels = standing().apply { fill(25..32, 29..35, BodyPartMap.CLOTHES) }

        assertNull(BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 35))
    }

    @Test
    fun `one bad row cannot decide the neck`() {
        // A three-pixel notch at row 30, which a plain minimum would take and report as a neck
        // under half the real width. Three pixels is 0.23 of the face's thirteen, well under
        // the 0.45 a neck has to reach, so the row is not eligible and the answer does not
        // move.
        val labels = standing().apply {
            fill(30..30, 29..35, BodyPartMap.HAIR)
            fill(30..30, 31..33, BodyPartMap.BODY_SKIN)
        }

        val reading = BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 35)

        assertNotNull(reading)
        assertEquals(7.0 / 64.0, reading.widthFraction, 1e-9)
    }

    /**
     * The photograph that broke the first version of this, at the proportions the model
     * actually returned for it.
     *
     * A front-double-biceps pose: arms up, so they close in beside the head immediately below
     * the jaw. Running the shipped model over that photograph gave a bare face 25 pixels wide
     * and, in the rows under the chin, midline skin runs of **23, 88, 148, 147** — one row of
     * neck and then arms. Scaled to this 64-square grid: face 13, neck 12, arms 45.
     */
    private fun armsRaised() = blank().apply {
        fill(0..9, 24..40, BodyPartMap.HAIR)
        fill(10..24, 24..40, BodyPartMap.HAIR)
        fill(10..24, 26..38, BodyPartMap.FACE_SKIN)
        // One row of neck, and that is all there is.
        fill(25..25, 26..37, BodyPartMap.BODY_SKIN)
        // Arms merged with the shoulders from here down.
        fill(26..63, 10..54, BodyPartMap.BODY_SKIN)
    }

    @Test
    fun `one row of skin under the chin is not a neck`() {
        // **This test used to assert the opposite**, and the reversal is the finding.
        //
        // The fixture is built from the front-double-biceps photograph at the proportions the
        // model returned: face 25 pixels, then under the chin 23, 88, 148, 147. That single
        // 23-pixel row was taken for a one-row neck — 0.92 of the face, a perfectly neck-like
        // width — and a rule was rewritten to keep it. Looking at the photograph settled it:
        // the chin rests on the flexed trapezius and the camera is a little low, so there is
        // no neck in the picture at all. The row is the top of the trapezius meeting the jaw,
        // and on the phone it became a 54 cm neck that the plausibility ranges threw out.
        //
        // Width cannot tell those apart. Height can: a visible neck runs a third or more of
        // the face's height, and this one runs a fifteenth.
        val outcome = BodyPartMap.readNeckOutcome(armsRaised(), dim, dim, shoulderRow = 35)

        assertEquals(NeckOutcome.Refused(NeckRefusal.HIDDEN), outcome)
        assertNull(BodyPartMap.readNeck(armsRaised(), dim, dim, shoulderRow = 35))
    }

    @Test
    fun `a hidden neck stays hidden wherever the shoulder landmark lands`() {
        // The refusal is read off the body, so the landmark cannot talk it round.
        listOf(28, 35, 45, 60, 63).forEach {
            assertEquals(
                NeckOutcome.Refused(NeckRefusal.HIDDEN),
                BodyPartMap.readNeckOutcome(armsRaised(), dim, dim, shoulderRow = it),
                "shoulder row $it",
            )
        }
    }

    @Test
    fun `each refusal is named for what the user has to change`() {
        val collar = standing().apply { fill(25..32, 29..35, BodyPartMap.CLOTHES) }
        assertEquals(
            NeckOutcome.Refused(NeckRefusal.COVERED),
            BodyPartMap.readNeckOutcome(collar, dim, dim, shoulderRow = 35),
        )

        val faceless = standing().apply { fill(10..24, 26..38, BodyPartMap.HAIR) }
        assertEquals(
            NeckOutcome.Refused(NeckRefusal.NO_FACE),
            BodyPartMap.readNeckOutcome(faceless, dim, dim, shoulderRow = 35),
        )

        val found = BodyPartMap.readNeckOutcome(standing(), dim, dim, shoulderRow = 35)
        assertTrue(found is NeckOutcome.Found, "a visible neck must still be found: $found")
    }

    @Test
    fun `a chain across the throat does not cost the row`() {
        // Accessories are their own class, so a necklace on the midline reads as "not skin"
        // exactly where the run is picked up. Bridged, because the alternative is to count
        // the row as covered and edge the whole reading towards refusal.
        val labels = standing().apply { fill(29..29, 31..33, BodyPartMap.ACCESSORY) }

        val reading = BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 35)

        assertNotNull(reading)
        assertEquals(7.0 / 64.0, reading.widthFraction, 1e-9)
        assertEquals(1.0, reading.bandCoverage, 1e-9)
    }

    @Test
    fun `the walk stops at the shoulders instead of searching past them`() {
        // **The 51.8 cm reading.** The pose model's shoulder landmark can sit a few rows below
        // the acromion, which puts the top of the trapezius inside the band. It is wider than
        // the neck but not wildly so, so a width guard tuned to reject a whole trapezius lets
        // it through — and a minimum over the whole band takes it whenever it happens to be
        // narrower than the neck rows are wide, which it never is here, but it is close enough
        // that the margin is luck rather than design.
        //
        // Walking down and stopping at the first substantial rise removes the luck: the turn
        // in the profile is the shoulder line, read off the body rather than from a landmark.
        val creeping = blank().apply {
            fill(0..9, 24..40, BodyPartMap.HAIR)
            fill(10..24, 24..40, BodyPartMap.HAIR)
            fill(10..24, 26..38, BodyPartMap.FACE_SKIN)
            fill(25..27, 29..35, BodyPartMap.BODY_SKIN)   // neck, 7 wide
            fill(28..30, 24..40, BodyPartMap.BODY_SKIN)   // trapezius, 17 wide
            fill(31..63, 10..54, BodyPartMap.BODY_SKIN)   // shoulders and arms
        }

        val reading = BodyPartMap.readNeck(creeping, dim, dim, shoulderRow = 35)

        assertNotNull(reading)
        assertEquals(7.0 / 64.0, reading.widthFraction, 1e-9, "the trapezius must not be reached")
    }

    @Test
    fun `the face it was judged against comes back with the reading`() {
        // So the result screen can print the pair. A neck is about nine tenths of bare face
        // width — 0.92 and 0.83 measured on a real photograph — and anything near 1.3 is
        // shoulder. Three separate neck failures were indistinguishable from a single
        // centimetre figure; the ratio tells them apart at a glance.
        val reading = BodyPartMap.readNeck(standing(), dim, dim, shoulderRow = 35)

        assertNotNull(reading)
        assertEquals(13.0 / 64.0, reading.faceWidthFraction, 1e-9)
        assertTrue(
            reading.widthFraction / reading.faceWidthFraction < BodyPartMap.MAX_NECK_TO_FACE_WIDTH,
        )
    }

    @Test
    fun `no face means no neck`() {
        // The top of the band is the bottom of the face. Without a face there is no band, and
        // guessing one from the trunk span is how the search used to land on the trapezius.
        val labels = standing().apply { fill(10..24, 26..38, BodyPartMap.HAIR) }

        assertNull(BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 35))
    }

    @Test
    fun `a run as wide as the shoulders is not a neck`() {
        // No neck in the picture at all: the shoulders start directly under the face, as they
        // do when a subject leans back or the shoulder landmark lands low. The widest thing
        // this could return is a trapezius, and a trapezius read as a neck is what produced a
        // fifty-two centimetre reading and a negative body fat.
        val labels = blank().apply {
            fill(0..9, 24..40, BodyPartMap.HAIR)
            fill(10..24, 24..40, BodyPartMap.HAIR)
            fill(10..24, 26..38, BodyPartMap.FACE_SKIN)
            fill(25..63, 10..54, BodyPartMap.BODY_SKIN)
        }

        assertNull(BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 35))
    }

    @Test
    fun `the shoulder line bounds the search from below`() {
        // Skin alone cannot say where a neck stops — a bare chest is body-skin too. With the
        // shoulder row pushed down past the torso the band fills with forty-five-pixel rows,
        // and only the pose landmark keeps the minimum inside the neck.
        val labels = standing()

        val bounded = BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 35)
        val unbounded = BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 63)

        assertNotNull(bounded)
        assertNotNull(unbounded)
        // On this grid the minimum survives the wider band, because forty-five is not less
        // than seven. It survives by luck of the arithmetic rather than by design: nothing in
        // a skin mask says the torso below is not neck, which is why the landmark bound is
        // there and why the previous test's grid — shoulders directly under the face — comes
        // back null instead of measuring one.
        assertEquals(bounded.widthFraction, unbounded.widthFraction, 1e-9)
        assertEquals(bounded.heightFraction, unbounded.heightFraction, 1e-9)
    }

    @Test
    fun `the waist is read from the same mask as the neck`() {
        // Not to display — the silhouette's waist keeps that job, with the trunk bound behind
        // it. This exists so the pair can be compared inside one coordinate space, because
        // the Navy equation subtracts one from the other and two masks are two rulers.
        //
        // Skin and clothing together: a waistband is at the waist, and the question is where
        // the body's outline is, not what is covering it.
        // A torso 45 wide that narrows to 25 across rows 45..55, the narrow part covered by
        // shorts. The reading has to be the 25, and cloth must not change it: a first version
        // of this test put cloth over half a full-width torso and expected a narrower answer,
        // which is not what a waistband does to anybody's outline.
        val clothed = standing().apply {
            fill(45..55, 10..54, BodyPartMap.BACKGROUND)
            fill(45..55, 20..44, BodyPartMap.CLOTHES)
        }

        val waist = BodyPartMap.readWaist(clothed, dim, dim, shoulderRow = 35, hipRow = 60)

        assertNotNull(waist)
        assertEquals(25.0 / 64.0, waist, 1e-9)

        // And the same body bare reads the same width.
        val bare = standing().apply {
            fill(45..55, 10..54, BodyPartMap.BACKGROUND)
            fill(45..55, 20..44, BodyPartMap.BODY_SKIN)
        }
        assertEquals(
            waist,
            BodyPartMap.readWaist(bare, dim, dim, shoulderRow = 35, hipRow = 60)!!,
            1e-9,
        )
    }

    @Test
    fun `a waist band with nothing in it reports nothing`() {
        assertNull(BodyPartMap.readWaist(blank(), dim, dim, shoulderRow = 35, hipRow = 60))
        // Degenerate band: the hips at or above the shoulders is a broken pose, not a waist.
        assertNull(BodyPartMap.readWaist(standing(), dim, dim, shoulderRow = 40, hipRow = 40))
    }

    @Test
    fun `an empty or undersized buffer returns nothing`() {
        assertNull(BodyPartMap.readNeck(ByteArray(0), dim, dim, 35))
        assertNull(BodyPartMap.readNeck(standing(), 0, dim, 35))
        assertNull(BodyPartMap.bareSkinFraction(ByteArray(4), dim, dim, 0, 5))
    }

    @Test
    fun `bare skin is told apart from clothing`() {
        val bare = standing()
        assertEquals(1.0, BodyPartMap.bareSkinFraction(bare, dim, dim, 35, 60)!!, 1e-9)

        // Sixteen rows of shirt over ten rows of skin, forty-five pixels wide each.
        val clothed = standing().apply { fill(45..60, 10..54, BodyPartMap.CLOTHES) }
        assertEquals(
            450.0 / 1170.0,
            BodyPartMap.bareSkinFraction(clothed, dim, dim, 35, 60)!!,
            1e-9,
        )

        // A band that is neither skin nor cloth has no share to report, and reporting zero
        // would read as "fully clothed" to every caller.
        assertNull(BodyPartMap.bareSkinFraction(blank(), dim, dim, 0, 5))
    }

    @Test
    fun `a clothed midsection falls below the bar the definition metric needs`() {
        // The gate in the view model. A t-shirt reaching the hips leaves well under three
        // fifths of the band as skin, so the definition score is withheld instead of being
        // published as a reading of a body it never saw.
        val clothed = standing().apply { fill(45..60, 10..54, BodyPartMap.CLOTHES) }
        val fraction = BodyPartMap.bareSkinFraction(clothed, dim, dim, 35, 60)!!

        assertTrue(fraction < BodyPartMap.MIN_BARE_ABDOMEN, "got $fraction")
        assertTrue(
            BodyPartMap.bareSkinFraction(standing(), dim, dim, 35, 60)!! >=
                BodyPartMap.MIN_BARE_ABDOMEN,
        )
    }
}
