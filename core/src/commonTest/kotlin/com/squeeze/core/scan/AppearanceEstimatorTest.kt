package com.squeeze.core.scan

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arithmetic that turns an image embedding into a body-fat reading.
 *
 * No model runs here. The fixtures are orthonormal vectors standing in for text embeddings,
 * so every expected value can be worked out by hand — which is the point: the model is
 * tested on photographs, and this file tests that nothing between the model's output and
 * the number on screen bends it.
 */
class AppearanceEstimatorTest {

    private val dim = 8

    private fun axis(i: Int) = DoubleArray(dim).also { it[i] = 1.0 }

    private fun blend(a: Int, b: Int) = DoubleArray(dim).also {
        it[a] = 1.0 / sqrt(2.0)
        it[b] = 1.0 / sqrt(2.0)
    }

    /** Five reference bodies on five orthogonal directions, as a real set has five prompts. */
    private val ladder = PromptSet(
        anchors = doubleArrayOf(5.0, 10.0, 15.0, 20.0, 28.0),
        embeddings = (0 until 5).map { axis(it) },
    )

    @Test
    fun `a photograph that matches one description reads as that description`() {
        // Similarity 1 against 0 for the rest, times a logit scale of 100: the matching
        // anchor takes essentially all of the probability.
        val reading = AppearanceEstimator.estimate(axis(0), listOf(ladder))

        assertNotNull(reading)
        assertEquals(5.0, reading, 1e-6)
    }

    @Test
    fun `a photograph between two descriptions reads between them`() {
        val reading = AppearanceEstimator.estimate(blend(1, 2), listOf(ladder))

        assertNotNull(reading)
        assertEquals(12.5, reading, 1e-6)
    }

    @Test
    fun `phrasings are averaged rather than one being chosen`() {
        // The second set describes the same bodies with the anchors reversed, so the same
        // photograph reads 5% under one and 28% under the other.
        val reversed = PromptSet(
            anchors = doubleArrayOf(28.0, 20.0, 15.0, 10.0, 5.0),
            embeddings = (0 until 5).map { axis(it) },
        )

        val reading = AppearanceEstimator.estimate(axis(0), listOf(ladder, reversed))

        assertNotNull(reading)
        assertEquals((5.0 + 28.0) / 2.0, reading, 1e-6)
    }

    @Test
    fun `the length of the embedding does not matter`() {
        // Models do not all normalise their output, and this must not depend on which one.
        val unit = AppearanceEstimator.estimate(blend(1, 3), listOf(ladder))
        val long = AppearanceEstimator.estimate(blend(1, 3).map { it * 7.0 }.toDoubleArray(), listOf(ladder))

        assertNotNull(unit)
        assertNotNull(long)
        assertEquals(unit, long, 1e-9)
    }

    @Test
    fun `a strong match does not overflow`() {
        // exp(100 × 1) is 2.7e43; without the maximum subtracted a slightly larger scale or
        // an unnormalised text embedding overflows to infinity and the reading to NaN.
        val loud = PromptSet(
            anchors = doubleArrayOf(5.0, 28.0),
            embeddings = listOf(axis(0).map { it * 9.0 }.toDoubleArray(), axis(1)),
        )

        val reading = AppearanceEstimator.estimate(axis(0), listOf(loud))

        assertNotNull(reading)
        assertTrue(reading.isFinite())
        assertEquals(5.0, reading, 1e-6)
    }

    @Test
    fun `inputs that cannot produce a reading produce none`() {
        assertNull(AppearanceEstimator.estimate(axis(0), emptyList()))
        assertNull(AppearanceEstimator.estimate(DoubleArray(dim), listOf(ladder)))
        assertNull(AppearanceEstimator.estimate(DoubleArray(dim) { Double.NaN }, listOf(ladder)))
        // An embedding from a different model has a different width, and comparing across
        // them is meaningless rather than approximately right.
        assertNull(AppearanceEstimator.estimate(DoubleArray(dim + 1) { 1.0 }, listOf(ladder)))
    }

    @Test
    fun `the scanner's weights are the probabilities behind the figure`() {
        val reading = AppearanceEstimator.read(blend(0, 1), listOf(ladder, ladder))

        assertNotNull(reading)
        val weights = assertNotNull(reading.weights)
        val anchors = assertNotNull(reading.anchors)
        assertEquals(1.0, weights.sum(), 1e-9)
        assertEquals(reading.percent, anchors.indices.sumOf { anchors[it] * weights[it] }, 1e-9)
    }

    private fun pose(mouthY: Double?, shoulderY: Double, hipY: Double) = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.40, shoulderY),
        shoulderRight = PosePoint(0.60, shoulderY),
        hipLeft = PosePoint(0.44, hipY),
        hipRight = PosePoint(0.56, hipY),
        mouth = mouthY?.let { PosePoint(0.5, it) },
    )

    @Test
    fun `the model is shown the torso, head to just below the hips`() {
        val region = assertNotNull(AppearanceEstimator.region(pose(0.10, 0.20, 0.50)))

        // Chin to shoulders is 0.10, so the top sits one and a half of that above the chin.
        assertEquals(0.0, region.top, 1e-9)
        // A sixth of the trunk below the hips, and the legs below that left out.
        assertEquals(0.545, region.bottom, 1e-9)
        // Twice the shoulders across, centred on them.
        assertEquals(0.30, region.left, 1e-9)
        assertEquals(0.70, region.right, 1e-9)
    }

    @Test
    fun `a waist-up and a full-length photograph frame the same body the same way`() {
        // The same body taking up twice as much of the frame: every edge of what the model
        // sees moves with it, so the body sits in the crop at the same margins either way.
        val far = pose(0.30, 0.40, 0.70)
        val near = FrontPoseGeometry(
            shoulderLeft = PosePoint(0.30, 0.80),
            shoulderRight = PosePoint(0.70, 0.80),
            hipLeft = PosePoint(0.38, 1.40),
            hipRight = PosePoint(0.62, 1.40),
            mouth = PosePoint(0.5, 0.60),
        )
        val a = assertNotNull(AppearanceEstimator.region(far))
        val b = assertNotNull(AppearanceEstimator.region(near))

        assertEquals((a.top - 0.40) * 2.0, b.top - 0.80, 1e-9)
        assertEquals((a.left - 0.5) * 2.0, b.left - 0.5, 1e-9)
        assertEquals((a.right - 0.5) * 2.0, b.right - 0.5, 1e-9)
    }

    @Test
    fun `landmarks that are not a body give no region`() {
        assertNull(AppearanceEstimator.region(pose(0.10, 0.50, 0.40)))
        // Without a face landmark the head is placed from the trunk instead.
        assertNotNull(AppearanceEstimator.region(pose(null, 0.20, 0.50)))
    }
}
