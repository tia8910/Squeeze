package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LightingQualityTest {

    private val width = 40
    private val height = 40

    private fun even(level: Int = 140) = IntArray(width * height) { level }

    /** A lamp off to one side: brightness ramps across the crop. */
    private fun sideLit(level: Int = 140, swing: Int = 70) = IntArray(width * height) { i ->
        val x = i % width
        level + ((x - width / 2) * swing / width)
    }

    /** Overhead light: brightness ramps top to bottom. */
    private fun overhead(level: Int = 140, swing: Int = 80) = IntArray(width * height) { i ->
        val y = i / width
        level + ((height / 2 - y) * swing / height)
    }

    @Test
    fun `even light passes without advice`() {
        val verdict = LightingQuality.evaluate(even(), width)

        assertNotNull(verdict)
        assertNull(verdict.advice, "flat light is what the scan wants")
        assertTrue(verdict.usableForDefinition)
    }

    @Test
    fun `a side light is named, because it is what fakes definition`() {
        val verdict = LightingQuality.evaluate(sideLit(swing = 200), width)

        assertNotNull(verdict)
        assertNotNull(verdict.advice)
        assertTrue(verdict.advice.contains("one side"), verdict.advice)
    }

    @Test
    fun `overhead light is named separately, since the fix differs`() {
        val verdict = LightingQuality.evaluate(overhead(swing = 220), width)

        assertNotNull(verdict)
        assertNotNull(verdict.advice)
        assertTrue(verdict.advice.contains("above"), verdict.advice)
    }

    @Test
    fun `a dark crop says the flash will not help`() {
        // The one piece of advice the user most needs, because reaching for the flash is the
        // obvious move and it does nothing at scan distance.
        val verdict = LightingQuality.evaluate(even(level = 30), width)

        assertNotNull(verdict)
        assertNotNull(verdict.advice)
        assertTrue(verdict.advice.contains("flash will not reach"), verdict.advice)
        assertTrue(!verdict.usableForDefinition)
    }

    @Test
    fun `clipping is reported before mere dimness`() {
        // Blown highlights destroy detail outright, so that advice has to win over a
        // brightness complaint about the same photo.
        val blown = IntArray(width * height) { i -> if (i % 3 == 0) 255 else 120 }

        val verdict = LightingQuality.evaluate(blown, width)

        assertNotNull(verdict)
        assertNotNull(verdict.advice)
        assertTrue(verdict.advice.contains("pure white"), verdict.advice)
    }

    @Test
    fun `directional light is still measurable, just not comparable`() {
        // A side-lit scan is worth storing — it simply must not be compared against a
        // differently-lit one. Darkness is the case where there is nothing to measure.
        val verdict = LightingQuality.evaluate(sideLit(swing = 200), width)

        assertNotNull(verdict)
        assertTrue(verdict.usableForDefinition)
    }

    @Test
    fun `two scans lit alike are comparable`() {
        val first = LightingQuality.evaluate(even(level = 130), width)!!.signature
        val second = LightingQuality.evaluate(even(level = 150), width)!!.signature

        assertTrue(LightingQuality.comparable(first, second))
    }

    @Test
    fun `a changed lamp makes two scans incomparable`() {
        // Without this the app would report a new lamp as fat loss, which is the whole
        // reason the check exists.
        val windowLit = LightingQuality.evaluate(even(), width)!!.signature
        val lampLit = LightingQuality.evaluate(sideLit(swing = 200), width)!!.signature

        assertTrue(!LightingQuality.comparable(windowLit, lampLit))
    }

    @Test
    fun `a crop too small to judge returns nothing`() {
        assertNull(LightingQuality.evaluate(IntArray(6) { 140 }, 3))
    }
}
