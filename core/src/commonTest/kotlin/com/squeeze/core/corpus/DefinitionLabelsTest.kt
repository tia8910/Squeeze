package com.squeeze.core.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The corpus format, and the gate a classifier has to pass to say anything.
 *
 * This exists because of a specific failure. The abdominal texture score ran 5.76 at eight per
 * cent body fat, 6.15 at ten, 7.44 at fifteen, 6.03 at twenty and 4.51 at thirty-five — not a
 * weak signal, no signal, and not even monotonic. It reached users because nothing in the
 * repository could score a candidate against labelled photographs. This is that thing.
 */
class DefinitionLabelsTest {

    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    private fun photo(
        hash: String = hashA,
        day: Long = 20_000,
        abdomenVisible: Boolean = true,
        unusable: Boolean = false,
    ) = PhotoLabels(
        photoHash = hash,
        capturedEpochDay = day,
        regions = listOf(
            RegionLabel(DefinitionRegion.ABDOMEN, abdomenVisible, unusable),
            RegionLabel(DefinitionRegion.CHEST_AND_DELTS, visible = false),
        ),
    )

    @Test
    fun `a label round-trips through the file format`() {
        val original = listOf(photo(hashB), photo(hashA, day = 19_500, abdomenVisible = false))

        val parsed = LabelFile.read(LabelFile.write(original))

        assertEquals(2, parsed.size)
        assertEquals(original.sortedBy { it.photoHash }, parsed)
    }

    @Test
    fun `the file is sorted, so two labellers produce the same bytes`() {
        // The whole point of committing labels rather than a database: a pull request has to
        // show which judgements changed. Order-dependent output would show every line as
        // changed whenever anyone labelled anything.
        val one = LabelFile.write(listOf(photo(hashA), photo(hashB)))
        val other = LabelFile.write(listOf(photo(hashB), photo(hashA)))

        assertEquals(one, other)
    }

    @Test
    fun `a malformed line throws rather than being skipped`() {
        // A corpus that quietly drops what it cannot read reports a smaller set and a better
        // score, and gives no sign which is which.
        assertFailsWith<IllegalArgumentException> {
            LabelFile.read("""{"day":20000,"regions":[]}""")
        }
    }

    @Test
    fun `blank lines are fine`() {
        assertEquals(1, LabelFile.read("\n\n${LabelFile.write(listOf(photo()))}\n\n").size)
    }

    @Test
    fun `the hash has to be a hash`() {
        assertFailsWith<IllegalArgumentException> { photo(hash = "not-a-hash") }
        assertFailsWith<IllegalArgumentException> { photo(hash = "A".repeat(64)) }
    }

    @Test
    fun `a region cannot be judged twice in one photograph`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoLabels(
                photoHash = hashA,
                capturedEpochDay = 1,
                regions = listOf(
                    RegionLabel(DefinitionRegion.ABDOMEN, visible = true),
                    RegionLabel(DefinitionRegion.ABDOMEN, visible = false),
                ),
            )
        }
    }

    @Test
    fun `scoring counts only the region asked about`() {
        val labels = listOf(photo(hashA, abdomenVisible = true), photo(hashB, abdomenVisible = false))

        val scores = CorpusScore.score(labels) { _, region ->
            region == DefinitionRegion.ABDOMEN
        }

        val abdomen = scores.single { it.region == DefinitionRegion.ABDOMEN }
        assertEquals(2, abdomen.total)
        assertEquals(1, abdomen.correct)

        // ARMS was never labelled on either photograph, so it has nothing to score against.
        assertEquals(0, scores.single { it.region == DefinitionRegion.ARMS }.total)
    }

    @Test
    fun `an unusable photograph scores neither way`() {
        val labels = listOf(photo(hashA, unusable = true))

        val abdomen = CorpusScore.score(labels) { _, _ -> true }
            .single { it.region == DefinitionRegion.ABDOMEN }

        assertEquals(0, abdomen.total)
    }

    @Test
    fun `abstaining does not buy accuracy`() {
        // A model that answers only the easy photographs would otherwise score 1.00 on the
        // three it was sure about. The label floor is what stops that being shippable.
        val labels = (0 until 200).map {
            photo(hash = it.toString(16).padStart(64, '0'), abdomenVisible = it % 2 == 0)
        }

        val shy = CorpusScore.score(labels) { photo, _ ->
            true.takeIf { photo.photoHash.endsWith("0") }
        }.single { it.region == DefinitionRegion.ABDOMEN }

        assertEquals(1.0, shy.accuracy, 1e-9)
        assertFalse(shy.shippable, "answered only ${shy.total}")
    }

    @Test
    fun `a coin flip is not shippable, and a good classifier is`() {
        val labels = (0 until 200).map {
            photo(hash = it.toString(16).padStart(64, '0'), abdomenVisible = it % 2 == 0)
        }

        val coinFlip = CorpusScore.score(labels) { _, _ -> true }
            .single { it.region == DefinitionRegion.ABDOMEN }
        assertEquals(0.5, coinFlip.accuracy, 1e-9)
        assertFalse(coinFlip.shippable)

        // Right on nine photographs in ten.
        val good = CorpusScore.score(labels) { photo, _ ->
            val truth = photo.labelFor(DefinitionRegion.ABDOMEN)!!.visible
            if (photo.photoHash.last() == '9') !truth else truth
        }.single { it.region == DefinitionRegion.ABDOMEN }

        assertTrue(good.accuracy > RegionScore.MIN_ACCURACY, "got ${good.accuracy}")
        assertTrue(good.shippable)
    }

    @Test
    fun `labelFor answers only what was asked`() {
        val labels = photo()

        assertNotNull(labels.labelFor(DefinitionRegion.ABDOMEN))
        assertEquals(null, labels.labelFor(DefinitionRegion.ARMS))
    }
}
