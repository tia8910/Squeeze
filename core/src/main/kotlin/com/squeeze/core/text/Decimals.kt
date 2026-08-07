package com.squeeze.core.text

import kotlin.math.abs
import kotlin.math.round

/**
 * Fixed-point number formatting that does not need a JVM.
 *
 * `"%.1f".format(x)` is `java.lang.String.format` behind a Kotlin extension, and it is the
 * only thing in this module that ties it to a JVM. Everything else here is arithmetic and
 * data classes — no `java.*` import appears anywhere in `core` — which means the measurement
 * logic can compile to JavaScript and run in a browser beside the same MediaPipe models the
 * app uses, instead of being reachable only through an APK on a physical phone.
 *
 * That matters more than a formatting helper normally would. Every measurement bug this
 * pipeline has had was invisible by construction: the waist band sitting under the ribs, the
 * shoulder run swallowing both arms, the hip band landing on a waistband, the whole frame
 * lying on its side. Each was diagnosed from a screenshot of a number, one round trip at a
 * time. Drawing those bands on the photograph makes all four obvious at a glance, and getting
 * this code into a browser is what makes that possible.
 *
 * Locale-independent by construction, which is also a fix rather than a side effect:
 * `String.format` follows the default locale, so on a device set to most of Europe the app
 * was already printing `18,4 %`.
 */
object Decimals {

    /**
     * [value] rounded to [digits] decimal places, always with a full stop.
     *
     * Rounds half away from zero, matching `%.1f`, so no printed figure in the app changes.
     *
     * @param digits how many decimal places, zero or more
     */
    fun fixed(value: Double, digits: Int): String {
        require(digits >= 0) { "digits must not be negative" }
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

        var factor = 1L
        repeat(digits) { factor *= 10L }

        val scaled = round(abs(value) * factor).toLong()
        val whole = scaled / factor
        val fraction = scaled % factor

        val body = if (digits == 0) {
            whole.toString()
        } else {
            "$whole." + fraction.toString().padStart(digits, '0')
        }

        // A value that rounds to zero prints as zero rather than as minus zero.
        return if (value < 0 && scaled != 0L) "-$body" else body
    }
}

/** Shorthand for [Decimals.fixed]; reads better inside a string template. */
fun Double.fixed(digits: Int): String = Decimals.fixed(this, digits)
