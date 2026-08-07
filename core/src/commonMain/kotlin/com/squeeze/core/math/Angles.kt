package com.squeeze.core.math

import kotlin.math.PI

/**
 * Degree and radian conversion without `java.lang.Math`.
 *
 * `Math.toDegrees` is the trap that `java.*` grep does not catch: `Math` lives in
 * `java.lang`, so it needs no import and a search for `import java.` reports a module clean
 * while six JVM-only calls sit in it. That is exactly how this module reached a JS target
 * that would not compile.
 *
 * `kotlin.math` has no equivalent, which is why these are here rather than being replaced by
 * a standard call.
 */

/** Radians to degrees. */
fun Double.toDegrees(): Double = this * 180.0 / PI

/** Degrees to radians. */
fun Double.toRadians(): Double = this * PI / 180.0
