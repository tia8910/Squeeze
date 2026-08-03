package com.squeeze.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colours for data marks.
 *
 * Deliberately fixed rather than drawn from a Material You dynamic scheme. A data colour
 * identifies a quantity, so it has to mean the same thing every time it appears; letting it
 * follow the user's wallpaper would repaint body fat a different hue on someone else's phone
 * and break that association for no benefit.
 *
 * Body fat takes the brand blue, because it is the number the app is about and blue is what
 * means "this is the data" everywhere else in the UI. Lean mass takes navy. Those two are
 * close in hue and are still safely distinguishable, because they differ sharply in
 * *lightness* — and lightness contrast survives every form of colour vision deficiency,
 * where hue contrast does not.
 *
 * The separation matters less here than it would elsewhere in any case: body fat and lean
 * mass are never drawn on the same axis, each chart carries a single series, and each is
 * directly titled. Colour is reinforcement, not the message.
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
    bodyFat = Brand.Blue,
    leanMass = Brand.Navy,
    weight = Color(0xFFB35A2C),
    bandAlpha = 0.16f,
    rawMark = Color(0x593C3C3C),
    grid = Color(0x14081C45),
)

private val DarkChartPalette = ChartPalette(
    bodyFat = Brand.DarkBlue,
    leanMass = Color(0xFF9FB6DE),
    weight = Color(0xFFD2703C),
    bandAlpha = 0.22f,
    rawMark = Color(0x59D6D6D6),
    grid = Color(0x1FFFFFFF),
)

/**
 * Reads the app's own dark-mode state rather than the system's, so an explicit Light or
 * Dark choice in Settings repaints the charts too. Asking the system directly would leave
 * dark chart colours on a light background for anyone who overrode the theme.
 */
val chartPalette: ChartPalette
    @Composable @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkChartPalette else LightChartPalette
