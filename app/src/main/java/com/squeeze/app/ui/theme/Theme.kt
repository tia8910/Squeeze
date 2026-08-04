package com.squeeze.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** What the user chose in Settings. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Whether the app is currently rendering dark.
 *
 * Exposed as a composition local because several brand surfaces need the answer and must
 * not each re-derive it from the system setting, which would ignore an explicit override.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Brand.Ice,
    onPrimaryContainer = Brand.BlueDeep,

    secondary = Brand.Navy,
    onSecondary = Color.White,
    secondaryContainer = Brand.Sunken,
    onSecondaryContainer = Brand.Navy,

    tertiary = Brand.BlueDeep,
    onTertiary = Color.White,

    background = Brand.Ground,
    onBackground = Brand.Navy,
    surface = Brand.Card,
    onSurface = Brand.Navy,

    // The pale disc behind the full-colour mark, and any inset that should read as
    // "recessed but still white-ish".
    surfaceVariant = Brand.Ice,
    onSurfaceVariant = Brand.Muted,

    // Material derives these from the primary when they are left unset, which tints every
    // default Card a pale lavender — visibly off-brand against the flat white the design is
    // built on. Pinning them to the real neutrals is what keeps a stock Card looking like
    // the rest of the app.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Brand.Sunken,
    surfaceContainerHigh = Brand.Sunken,
    surfaceContainerHighest = Brand.Ice,
    surfaceBright = Color.White,
    surfaceDim = Brand.Sunken,

    // Elevation overlay colour. Left at its default, a raised surface picks up a blue wash.
    surfaceTint = Color.Transparent,

    error = Brand.Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),

    outline = Brand.Line,
    outlineVariant = Brand.Line,
)

private val DarkColors = darkColorScheme(
    primary = Brand.DarkBlue,
    onPrimary = Color(0xFF04122B),
    primaryContainer = Brand.DarkIce,
    onPrimaryContainer = Color(0xFFCFE0FF),

    secondary = Color(0xFFCFE0FF),
    onSecondary = Brand.Navy,
    secondaryContainer = Brand.DarkSunken,
    onSecondaryContainer = Color(0xFFCFE0FF),

    tertiary = Brand.DarkBlue,
    onTertiary = Color(0xFF04122B),

    background = Brand.DarkGround,
    onBackground = Brand.DarkInk,
    surface = Brand.DarkCard,
    onSurface = Brand.DarkInk,

    surfaceVariant = Brand.DarkIce,
    onSurfaceVariant = Brand.DarkMuted,

    surfaceContainerLowest = Brand.DarkGround,
    surfaceContainerLow = Brand.DarkCard,
    surfaceContainer = Brand.DarkCard,
    surfaceContainerHigh = Brand.DarkSunken,
    surfaceContainerHighest = Brand.DarkSunken,
    surfaceBright = Brand.DarkSunken,
    surfaceDim = Brand.DarkGround,
    surfaceTint = Color.Transparent,

    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Brand.DarkLine,
    outlineVariant = Brand.DarkLine,
)

/**
 * The brand sheet's type scale.
 *
 * Sizes are the design's own, taken from its phone frame rather than its desktop hero: the
 * mockup's 355px phone is close to a real handset, so `.metric` at 68px maps to 68sp and
 * `.stat b` at 18px maps to 18sp directly. The one exception is the hero wordmark, where the
 * design's own `max-width:720px` breakpoint drops 74px to 48px — that is the design telling
 * us what it wants at phone width, so [Typography.displayMedium] uses 48sp.
 *
 * Tracking is scaled with the size rather than copied, since -5px at 74px and -5px at 48px
 * are very different tightnesses. The ratio (about -0.068em) is what stays constant.
 */
private val SqueezeTypography = Typography().run {
    copy(
        // `.metric` — the hero number, the whole point of the home screen.
        displayLarge = displayLarge.copy(
            fontWeight = FontWeight.Black,
            fontSize = 68.sp,
            lineHeight = 72.sp,
            letterSpacing = (-4).sp,
        ),
        // `.wordmark` at the design's phone breakpoint.
        displayMedium = displayMedium.copy(
            fontWeight = FontWeight.Black,
            fontSize = 48.sp,
            lineHeight = 50.sp,
            letterSpacing = (-3.2).sp,
        ),
        displaySmall = displaySmall.copy(
            fontWeight = FontWeight.Black,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = (-2).sp,
        ),
        // `.celebrate h2`
        headlineMedium = headlineMedium.copy(
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-1.2).sp,
        ),
        // `.panel h3`
        headlineSmall = headlineSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 27.sp,
            lineHeight = 33.sp,
            letterSpacing = (-0.9).sp,
        ),
        // `.metric small` — the unit riding beside the hero number.
        titleLarge = titleLarge.copy(
            fontWeight = FontWeight.Black,
            fontSize = 25.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        // `.stat b`, `.mini-brand`
        titleMedium = titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.3).sp,
        ),
        // `.feature h4`, `.history h4`
        titleSmall = titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            letterSpacing = (-0.2).sp,
        ),
        // `.eyebrow`, and the label on both buttons.
        labelLarge = labelLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
        bodyMedium = bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
        // `.sub`, `.row`, `.feature p`
        bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        // `.nav`
        labelMedium = labelMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.1.sp,
        ),
        // `.stat span`
        labelSmall = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.2.sp,
        ),
    )
}

/**
 * Dark and light are separately designed schemes, not one flipped.
 *
 * Material You dynamic colour is deliberately not used. The identity is two specific
 * colours; letting the wallpaper repaint them would mean the same screenshot looks like a
 * different product on every phone.
 */
@Composable
fun SqueezeTheme(
    // Matches the stored default, so a preview shows what a new user actually gets.
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = SqueezeTypography,
            content = content,
        )
    }
}
