package com.squeeze.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Squeeze.fit brand tokens, taken verbatim from the brand sheet.
 *
 * The light values are the design's CSS custom properties transcribed one for one, so a
 * screenshot of this app and a screenshot of the brand sheet sample the same pixels. Where
 * the design used a literal instead of a variable — the stat tile's `#fafbfe`, the history
 * row's `#f7f9fd` — that literal is named here rather than approximated with an existing
 * token, because those two greys are deliberately different and collapsing them would flatten
 * the card hierarchy the design is built on.
 *
 * Two colours carry the identity: an electric blue for anything actionable or measured, and
 * a deep navy for text and the mark. Everything else is a neutral. With only one accent, a
 * blue element always means "this is the data" or "this is the action".
 *
 * The brand sheet is light-only. The dark values below are a separate design against a dark
 * ground, not an inversion — inverting would push the blue too dark to still read as the
 * accent, which is the one thing it has to do.
 */
object Brand {

    // --- Light: transcribed from the brand sheet's :root ---

    /** `--navy`. Text and the mark. Never pure black. */
    val Navy = Color(0xFF081C45)

    /** `--blue`. The single accent. */
    val Blue = Color(0xFF1768FF)

    /** `--blue-2`. The far end of every blue gradient, and notice text. */
    val BlueDeep = Color(0xFF0E4BD8)

    /** `--ice`. Notice pills, value chips, the disc behind the full-colour mark. */
    val Ice = Color(0xFFF4F7FF)

    /** `--muted`. Secondary copy. */
    val Muted = Color(0xFF69738A)

    /** `--line`. Hairline borders. Depth comes from these rather than from elevation. */
    val Line = Color(0xFFE7EBF3)

    val Card = Color(0xFFFFFFFF)
    val Ground = Color(0xFFFFFFFF)

    /** `.stat` background. A hair off white — enough to separate, not enough to read as grey. */
    val Sunken = Color(0xFFFAFBFE)

    /** `.row` background. Deliberately a step darker than [Sunken]. */
    val RowFill = Color(0xFFF7F9FD)

    /** `.intro` copy. */
    val Body = Color(0xFF34415C)

    /** `.sub` copy, inside cards. */
    val Sub = Color(0xFF4E5A72)

    /** `.btn.secondary` border. A tinted blue, not the accent at low alpha. */
    val OutlineBlue = Color(0xFF8DB3FF)

    /** `.nav` inactive. */
    val NavIdle = Color(0xFF7A8396)

    // `.app-icon.blue` gradient stops.
    val IconBlueLight = Color(0xFF36A0FF)
    val IconBlueDeep = Color(0xFF0757EA)

    // --- Dark: designed against the dark ground, not derived by inversion ---

    val DarkGround = Color(0xFF07101F)
    val DarkCard = Color(0xFF101A2C)
    val DarkSunken = Color(0xFF16223A)
    val DarkRowFill = Color(0xFF16223A)
    val DarkLine = Color(0xFF22304A)
    val DarkMuted = Color(0xFF93A0BA)
    val DarkSub = Color(0xFFB4C0D6)
    val DarkInk = Color(0xFFE9EFFB)

    /** The accent lifted so it still carries against the dark ground. */
    val DarkBlue = Color(0xFF4C8CFF)
    /** The dark counterpart to [Ice]: a blue-tinted sunken fill for notices and chips. */
    val DarkIce = Color(0xFF13233F)

    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFDC2626)
}
