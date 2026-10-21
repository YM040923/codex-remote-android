package com.codex.remote.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.codex.remote.R

// Neutral ramp measured from reference chat clients: the conversation itself carries no
// brand color, so contrast comes from ink weight and surface steps rather than hue.
object CodexPalette {
    val Ink = Color(0xFF0D0D0D)
    val InkMuted = Color(0xFF4C4C4C)
    val InkFaint = Color(0xFFB6B6B6)
    val Void = Color(0xFFFFFFFF)

    val Neutral50 = Color(0xFFFAFAFA)
    val Neutral100 = Color(0xFFF7F7F7)
    val Neutral150 = Color(0xFFF5F5F5)
    val Neutral200 = Color(0xFFEFEFEF)
    val Neutral300 = Color(0xFFE8E8E8)
    val Neutral400 = Color(0xFFD9D9D9)

    val DiffAdded = Color(0xFFE9F3EC)
    val OnDiffAdded = Color(0xFF1F6B45)
    val DiffRemoved = Color(0xFFFBEDEA)
    val OnDiffRemoved = Color(0xFF8C2F1F)

    val NightVoid = Color(0xFF0F0F0F)
    val Night50 = Color(0xFF141414)
    val Night100 = Color(0xFF191919)
    val Night150 = Color(0xFF1F1F1F)
    val Night200 = Color(0xFF262626)
    val Night300 = Color(0xFF3A3A3A)
    val Night400 = Color(0xFF242424)
    val NightInk = Color(0xFFEDEDED)
    val NightInkMuted = Color(0xFFA3A3A3)
}

val CodexGreen = Color(0xFF12805C)
val CodexRed = Color(0xFFC8442D)

object CodexSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 24.dp
    val xxLarge = 32.dp
}

// Inter ships 400/500/600 only; every weight below stays inside that range so CJK glyphs
// fall back to Noto Sans CJK instead of being synthetically emboldened.
private val CodexSans = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val CodexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val LightColors = lightColorScheme(
    primary = CodexPalette.Ink,
    onPrimary = CodexPalette.Void,
    primaryContainer = CodexPalette.Neutral150,
    onPrimaryContainer = CodexPalette.Ink,
    secondary = CodexPalette.Ink,
    onSecondary = CodexPalette.Void,
    secondaryContainer = CodexPalette.Neutral150,
    onSecondaryContainer = CodexPalette.Ink,
    tertiary = CodexPalette.InkMuted,
    tertiaryContainer = CodexPalette.DiffAdded,
    onTertiaryContainer = CodexPalette.OnDiffAdded,
    error = CodexRed,
    errorContainer = CodexPalette.DiffRemoved,
    onErrorContainer = CodexPalette.OnDiffRemoved,
    background = CodexPalette.Void,
    onBackground = CodexPalette.Ink,
    surface = CodexPalette.Void,
    onSurface = CodexPalette.Ink,
    surfaceVariant = CodexPalette.Neutral150,
    onSurfaceVariant = CodexPalette.InkMuted,
    surfaceTint = CodexPalette.Ink,
    surfaceBright = CodexPalette.Void,
    surfaceDim = CodexPalette.Neutral200,
    surfaceContainerLowest = CodexPalette.Void,
    surfaceContainerLow = CodexPalette.Neutral50,
    surfaceContainer = CodexPalette.Neutral100,
    surfaceContainerHigh = CodexPalette.Neutral150,
    surfaceContainerHighest = CodexPalette.Neutral200,
    inverseSurface = CodexPalette.Ink,
    inverseOnSurface = CodexPalette.Void,
    inversePrimary = CodexPalette.Void,
    outline = CodexPalette.Neutral400,
    outlineVariant = CodexPalette.Neutral300,
)

private val DarkColors = darkColorScheme(
    primary = CodexPalette.NightInk,
    onPrimary = CodexPalette.NightVoid,
    primaryContainer = CodexPalette.Night150,
    onPrimaryContainer = CodexPalette.NightInk,
    secondary = CodexPalette.NightInk,
    onSecondary = CodexPalette.NightVoid,
    secondaryContainer = CodexPalette.Night150,
    onSecondaryContainer = CodexPalette.NightInk,
    tertiary = CodexPalette.NightInkMuted,
    tertiaryContainer = Color(0xFF16301F),
    onTertiaryContainer = Color(0xFF9FD3AE),
    error = Color(0xFFFF8A70),
    errorContainer = Color(0xFF3A1A14),
    onErrorContainer = Color(0xFFF5B7A6),
    background = CodexPalette.NightVoid,
    onBackground = CodexPalette.NightInk,
    surface = CodexPalette.NightVoid,
    onSurface = CodexPalette.NightInk,
    surfaceVariant = CodexPalette.Night150,
    onSurfaceVariant = CodexPalette.NightInkMuted,
    surfaceTint = CodexPalette.NightInk,
    surfaceBright = CodexPalette.Night200,
    surfaceDim = CodexPalette.NightVoid,
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = CodexPalette.Night50,
    surfaceContainer = CodexPalette.Night100,
    surfaceContainerHigh = CodexPalette.Night150,
    surfaceContainerHighest = CodexPalette.Night200,
    inverseSurface = CodexPalette.NightInk,
    inverseOnSurface = CodexPalette.NightVoid,
    inversePrimary = CodexPalette.NightVoid,
    outline = CodexPalette.Night300,
    outlineVariant = CodexPalette.Night400,
)

private val CodexTypography = Typography(
    displayLarge = TextStyle(fontFamily = CodexSans, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontFamily = CodexSans, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = CodexSans, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = CodexSans, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = CodexSans, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = CodexSans, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = CodexSans, fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = CodexSans, fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = CodexSans, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = CodexSans, fontSize = 17.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = CodexSans, fontSize = 15.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = CodexSans, fontSize = 13.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = CodexSans, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = CodexSans, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = CodexSans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
)

val MonoText = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
)

@Composable
fun CodexRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = CodexTypography, shapes = CodexShapes, content = content)
}
