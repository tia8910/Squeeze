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
        // A three-pixel notch at row 30. A plain minimum would take it and report a neck
        // under half the real width; the three-row median leaves the answer where it was.
        val labels = standing().apply {
            fill(30..30, 29..35, BodyPartMap.HAIR)
            fill(30..30, 31..33, BodyPartMap.BODY_SKIN)
        }

        val reading = BodyPartMap.readNeck(labels, dim, dim, shoulderRow = 35)

        assertNotNull(reading)
        assertEquals(7.0 / 64.0, reading.widthFraction, 1e-9)
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
