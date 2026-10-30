package com.ltq.count.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ltq.count.data.prefs.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Sage,
    onPrimary = PaperRaised,
    primaryContainer = SageSoft,
    onPrimaryContainer = SageDeep,
    inversePrimary = SageDark,
    secondary = Color(0xFF7A7468),
    onSecondary = PaperRaised,
    secondaryContainer = Color(0xFFEDE6D9),
    onSecondaryContainer = Color(0xFF2B2723),
    tertiary = PositiveLight,
    onTertiary = PaperRaised,
    tertiaryContainer = Color(0xFFDDE8DC),
    onTertiaryContainer = Color(0xFF23301F),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E1D4),
    onSurfaceVariant = InkMuted,
    surfaceTint = Sage,
    inverseSurface = Color(0xFF2B2824),
    inverseOnSurface = Paper,
    error = NegativeLight,
    onError = PaperRaised,
    errorContainer = Color(0xFFF3DDD6),
    onErrorContainer = Color(0xFF4A1F17),
    outline = Color(0xFFCFC6B7),
    outlineVariant = Hairline,
    scrim = Color.Black,
    surfaceBright = PaperRaised,
    surfaceDim = Color(0xFFE8E0D3),
    surfaceContainerLowest = Color(0xFFFCF9F3),
    surfaceContainerLow = Color(0xFFFAF6EF),
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = Color(0xFFF3EDE3),
    surfaceContainerHighest = PaperSunken,
)

private val DarkScheme = darkColorScheme(
    primary = SageDark,
    onPrimary = Color(0xFF1B241A),
    primaryContainer = SageDarkContainer,
    onPrimaryContainer = Color(0xFFD3E0CD),
    inversePrimary = Sage,
    secondary = Color(0xFFB9B2A4),
    onSecondary = Color(0xFF2B2722),
    secondaryContainer = Color(0xFF35312B),
    onSecondaryContainer = Color(0xFFE4DCCE),
    tertiary = PositiveDark,
    onTertiary = Color(0xFF11220F),
    tertiaryContainer = Color(0xFF263323),
    onTertiaryContainer = Color(0xFFCFE5C8),
    background = InkBackground,
    onBackground = Parchment,
    surface = InkBackground,
    onSurface = Parchment,
    surfaceVariant = Color(0xFF3A3F45),
    onSurfaceVariant = ParchmentMuted,
    surfaceTint = SageDark,
    inverseSurface = Parchment,
    inverseOnSurface = InkBackground,
    error = NegativeDark,
    onError = Color(0xFF2B120C),
    errorContainer = Color(0xFF4E2A21),
    onErrorContainer = Color(0xFFF3D2C7),
    outline = Color(0xFF4A5056),
    outlineVariant = Color(0xFF2E3439),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A3036),
    surfaceDim = InkSunken,
    surfaceContainerLowest = Color(0xFF0F1113),
    surfaceContainerLow = Color(0xFF181B1E),
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkRaised,
    surfaceContainerHighest = Color(0xFF2A3036),
)

/** Colors used only by deltas, the net-delta heatmap and the heat legend. */
data class CountExtraColors(
    val positive: Color,
    val negative: Color,
    val cardShadowAmbient: Color,
    val cardShadowSpot: Color,
    val hairline: Color,
)

private val LightExtras = CountExtraColors(
    positive = PositiveLight,
    negative = NegativeLight,
    cardShadowAmbient = Color(0x14000000),
    cardShadowSpot = Color(0x10000000),
    hairline = Color(0xFFE6DDCE),
)

private val DarkExtras = CountExtraColors(
    positive = PositiveDark,
    negative = NegativeDark,
    cardShadowAmbient = Color(0x40000000),
    cardShadowSpot = Color(0x30000000),
    hairline = Color(0xFF2C3137),
)

private val LocalExtras = staticCompositionLocalOf { LightExtras }

val MaterialTheme.extras: CountExtraColors
    @Composable get() = LocalExtras.current

@Composable
fun CountTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    val extras = if (dark) DarkExtras else LightExtras

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalExtras provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CountTypography,
            shapes = CountShapes,
            content = content,
        )
    }
}
