package com.squeeze.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colours for data marks.
 *
 * Deliberately fixed rather than drawn from the Material You dynamic scheme that themes the
 * rest of the app. A data colour identifies a quantity, so it has to mean the same thing
 * every time it appears; letting it follow the user's wallpaper would repaint body fat a
 * different hue on someone else's phone and break that association for no benefit.
 *
 * Light and dark are separately chosen steps rather than one set flipped, and both were
 * checked against the accessibility criteria that matter for marks: lightness band, chroma
 * floor (so nothing reads as grey), separation under deuteranopia and tritanopia, normal
 * vision separation, and at least 3:1 contrast against the chart surface.
 *
 * Colour is never the only channel carrying identity here — each series is directly
 * labelled and lives in its own chart — so the palette is reinforcement, not the message.
 */
data class ChartPalette(
    /** Body fat percentage. */
    val bodyFat: Color,
    /** Lean mass in kilograms. Never plotted on the same axis as body fat. */
    val leanMass: Color,
    /** Bodyweight. */
    val weight: Color,
    /** Confidence bands: the series hue at low opacity. */
    val bandAlpha: Float,
    /** Raw, unfiltered observations behind the trend line. */
    val rawMark: Color,
    /** Grid and axis rules. Recessive by design; they orient, they do not compete. */
    val grid: Color,
)

private val LightChartPalette = ChartPalette(
    bodyFat = Color(0xFF0F8A72),
    leanMass = Color(0xFF6A4FC4),
    weight = Color(0xFFB35A2C),
    bandAlpha = 0.16f,
    rawMark = Color(0x593C3C3C),
    grid = Color(0x1A000000),
)

private val DarkChartPalette = ChartPalette(
    bodyFat = Color(0xFF12A78A),
    leanMass = Color(0xFF8A6BEA),
    weight = Color(0xFFD2703C),
    bandAlpha = 0.22f,
    rawMark = Color(0x59D6D6D6),
    grid = Color(0x1FFFFFFF),
)

val chartPalette: ChartPalette
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DarkChartPalette else LightChartPalette
