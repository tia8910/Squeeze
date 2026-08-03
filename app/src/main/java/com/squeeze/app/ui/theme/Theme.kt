package com.squeeze.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
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
 * Exposed as a composition local because several brand surfaces — the aurora, the logo,
 * chart marks — need the answer and must not each re-derive it from the system setting,
 * which would ignore an explicit user override.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

private val DarkColors = darkColorScheme(
    primary = Brand.TealBright,
    onPrimary = Color(0xFF00281F),
    primaryContainer = Brand.TealDeep,
    onPrimaryContainer = Color(0xFFB6F5E4),

    secondary = Brand.Violet,
    onSecondary = Color(0xFF1E0F45),
    secondaryContainer = Color(0xFF2A1F52),
    onSecondaryContainer = Color(0xFFDDD2FF),

    tertiary = Brand.Amber,
    onTertiary = Color(0xFF3A2400),

    background = Brand.InkDeep,
    onBackground = Color(0xFFE6EFEC),
    surface = Brand.Ink,
    onSurface = Color(0xFFE6EFEC),
    surfaceVariant = Brand.InkRaised,
    onSurfaceVariant = Color(0xFF9FB3AE),

    error = Brand.Coral,
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF32423E),
    outlineVariant = Color(0xFF22302D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F8A72),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F5E4),
    onPrimaryContainer = Color(0xFF00281F),

    secondary = Color(0xFF6A4FC4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6DFFF),
    onSecondaryContainer = Color(0xFF20124D),

    tertiary = Color(0xFFB3701A),
    onTertiary = Color.White,

    background = Brand.Paper,
    onBackground = Color(0xFF0D1614),
    surface = Brand.PaperRaised,
    onSurface = Color(0xFF0D1614),
    surfaceVariant = Brand.PaperSunken,
    onSurfaceVariant = Color(0xFF4C5F5A),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    outline = Color(0xFFC3D0CC),
    outlineVariant = Color(0xFFDCE6E3),
)

/**
 * Type scale tuned for a data app: display sizes are tight and heavy so a hero number reads
 * as a headline, while body copy stays generous enough to explain what the number means.
 */
private val SqueezeTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(
            fontWeight = FontWeight.Black,
            fontSize = 64.sp,
            lineHeight = 64.sp,
            letterSpacing = (-2).sp,
        ),
        displayMedium = displayMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp,
        ),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelSmall = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.6.sp,
        ),
    )
}

/**
 * Dark and light are separately designed schemes, not one flipped.
 *
 * Material You dynamic colour is deliberately not used. This app has an identity of its
 * own, and letting the wallpaper repaint it would mean the same screenshot looks like a
 * different product on every phone — which is the opposite of a recognisable app.
 */
@Composable
fun SqueezeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
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

/** Brand gradient stops for the current theme. */
val auroraPalette: List<Color>
    @Composable @ReadOnlyComposable
    get() = Brand.auroraColors(LocalIsDarkTheme.current)
