package com.squeeze.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The replacement for `String.format`, which is the one thing that tied `core` to a JVM.
 *
 * Two things are checked: that no figure the app already prints changes, and that the two
 * bugs the old call carried are gone — a locale that prints `18,4` instead of `18.4`, and a
 * negative value that rounds to zero printing as `-0.0`.
 */
class DecimalsTest {

    @Test
    fun `prints what the format strings it replaced printed`() {
        assertEquals("18.4", 18.42.fixed(1))
        assertEquals("0.49", 0.4899.fixed(2))
        assertEquals("3.16", 3.1550001.fixed(2))
        assertEquals("2450", 2450.4.fixed(0))
        assertEquals("68", 68.0.fixed(0))
        assertEquals("1.234", 1.2344.fixed(3))
    }

    @Test
    fun `rounds half away from zero, as the format strings did`() {
        assertEquals("0.5", 0.45.fixed(1))
        assertEquals("2", 1.5.fixed(0))
        assertEquals("3", 2.5.fixed(0))
        assertEquals("-0.5", (-0.45).fixed(1))
    }

    @Test
    fun `pads the fraction so the decimal places are always there`() {
        // "%.2f" of 5.0 is "5.00", not "5.0". Anything narrower would make a column of
        // figures ragged and a value look more precise than its neighbours.
        assertEquals("5.00", 5.0.fixed(2))
        assertEquals("5.10", 5.1.fixed(2))
        assertEquals("0.05", 0.05.fixed(2))
        assertEquals("0.00", 0.0.fixed(2))
    }

    @Test
    fun `a value that rounds to zero is not printed as minus zero`() {
        // "%.1f".format(-0.001) gives "-0.0", which reads as a real loss of nothing.
        assertEquals("0.0", (-0.001).fixed(1))
        assertEquals("0", (-0.4).fixed(0))
        // A value that survives rounding keeps its sign.
        assertEquals("-0.1", (-0.06).fixed(1))
    }

    @Test
    fun `the separator is a full stop whatever the device is set to`() {
        // The actual reason this exists beyond portability: String.format follows the
        // default locale, so on a device set to most of Europe the app printed "18,4 %".
        assertEquals("18.4", 18.4.fixed(1))
        assertEquals(1, 18.4.fixed(1).count { it == '.' })
        assertEquals(0, 18.4.fixed(1).count { it == ',' })
    }

    @Test
    fun `large values keep every digit`() {
        assertEquals("2450.75", 2450.75.fixed(2))
        assertEquals("1000000.0", 1_000_000.0.fixed(1))
    }

    @Test
    fun `non-finite values say so rather than throwing`() {
        assertEquals("NaN", Double.NaN.fixed(1))
        assertEquals("Infinity", Double.POSITIVE_INFINITY.fixed(1))
        assertEquals("-Infinity", Double.NEGATIVE_INFINITY.fixed(1))
    }

    @Test
    fun `a negative digit count is a programming error`() {
        assertFailsWith<IllegalArgumentException> { 1.0.fixed(-1) }
    }
}
