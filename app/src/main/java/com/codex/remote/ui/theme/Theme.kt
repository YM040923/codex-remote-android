package com.codex.remote.ui.theme

import android.app.Activity
import com.codex.remote.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Monochrome surfaces: every panel is pure white with pure black content.
val ChatGPTBlue = Color(0xFF0B63CE)
val CodexGreen = Color(0xFF12805C)
val CodexRed = Color(0xFFC8442D)
private val Ink = Color.Black
private val Paper = Color.White

object CodexSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 24.dp
}

private val CodexSans = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val CodexShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Paper,
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Ink,
    onSecondaryContainer = Paper,
    error = CodexRed,
    errorContainer = Paper,
    onErrorContainer = Ink,
    tertiary = Ink,
    tertiaryContainer = Paper,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEFF0),
    onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceTint = Paper,
    surfaceBright = Paper,
    surfaceDim = Paper,
    surfaceContainer = Color(0xFFECECEE),
    surfaceContainerHigh = Color(0xFFE0E0E2),
    surfaceContainerHighest = Color(0xFFD4D4D6),
    surfaceContainerLow = Color(0xFFF4F4F5),
    surfaceContainerLowest = Paper,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Paper,
    outline = Color(0xFFD8D8D8),
    outlineVariant = Color(0xFFE0E0E0),
)

private val DarkColors = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Ink,
    onPrimaryContainer = Paper,
    secondary = Paper,
    onSecondary = Ink,
    secondaryContainer = Ink,
    onSecondaryContainer = Paper,
    error = Color(0xFFFF8A70),
    errorContainer = Ink,
    onErrorContainer = Paper,
    tertiary = Paper,
    tertiaryContainer = Ink,
    onTertiaryContainer = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceTint = Ink,
    surfaceBright = Ink,
    surfaceDim = Ink,
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    inversePrimary = Ink,
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF262626),
)

private val CodexTypography = Typography(
    displayLarge = TextStyle(fontFamily = CodexSans, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    displayMedium = TextStyle(fontFamily = CodexSans, fontSize = 33.sp, lineHeight = 39.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    displaySmall = TextStyle(fontFamily = CodexSans, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontFamily = CodexSans, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = CodexSans, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = CodexSans, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = CodexSans, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = CodexSans, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = CodexSans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = CodexSans, fontSize = 17.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = CodexSans, fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = CodexSans, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = CodexSans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = CodexSans, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = CodexSans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
)

val MonoText = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 18.sp,
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
