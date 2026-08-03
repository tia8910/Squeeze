package com.squeeze.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Squeeze.fit brand tokens.
 *
 * The identity is built on a single idea: a measurement closing in on a body. The palette
 * pairs a cool teal with a violet, which is the same pair the charts use — deliberately, so
 * the chrome and the data speak one visual language rather than two.
 *
 * These are chrome colours: gradients, surfaces, accents. Data marks live in
 * [ChartPalette] and are held to stricter rules, because a colour that identifies a
 * quantity has to survive colour-vision deficiency and low contrast in a way a background
 * wash does not.
 */
object Brand {

    // Signature hues. Teal is the primary action colour, violet the counterweight; amber
    // exists only for warmth in gradients and is never used to encode a value.
    val Teal = Color(0xFF12A78A)
    val TealDeep = Color(0xFF0B6B58)
    val TealBright = Color(0xFF2FE0B8)

    val Violet = Color(0xFF8A6BEA)
    val VioletDeep = Color(0xFF5B3FC4)

    val Amber = Color(0xFFF0A742)
    val Coral = Color(0xFFFF6B6B)

    /**
     * Backgrounds are near-black and near-white rather than pure, so large surfaces do not
     * clip against the panel and elevation stays legible at both ends of the range.
     */
    val InkDeep = Color(0xFF07100E)
    val Ink = Color(0xFF0D1614)
    val InkRaised = Color(0xFF16211F)

    val Paper = Color(0xFFF7FAF9)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperSunken = Color(0xFFEDF3F1)

    /** Aurora blobs that drift behind the app. Kept low-alpha; they set mood, not content. */
    fun auroraColors(dark: Boolean): List<Color> = if (dark) {
        listOf(
            Teal.copy(alpha = 0.30f),
            Violet.copy(alpha = 0.26f),
            TealBright.copy(alpha = 0.16f),
        )
    } else {
        listOf(
            Teal.copy(alpha = 0.20f),
            Violet.copy(alpha = 0.16f),
            Amber.copy(alpha = 0.12f),
        )
    }

    /** The logo gradient, and the only place the two signature hues meet at full strength. */
    val LogoGradient = listOf(TealBright, Teal, Violet)
}
