package com.codex.remote.ui.theme

import android.app.Activity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// A quiet, low-contrast palette inspired by Codex workspace surfaces.
// Most emphasis comes from hierarchy and spacing; color remains semantic.
val CodexGreen = Color(0xFF52745F)
val CodexRed = Color(0xFFB44B40)
private val Ink = Color(0xFF1D1C1A)
private val WarmBackground = Color(0xFFF7F7F5)
private val Panel = Color(0xFFF0F0EE)
private val Stroke = Color(0xFFDEDDD9)

object CodexSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 24.dp
}

private val CodexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(11.dp),
    large = RoundedCornerShape(15.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E2),
    onPrimaryContainer = Ink,
    secondary = CodexGreen,
    onSecondary = Color.White,
    error = CodexRed,
    errorContainer = Color(0xFFF6E8E5),
    onErrorContainer = Color(0xFF5A211B),
    tertiary = CodexGreen,
    tertiaryContainer = Color(0xFFEAF1EB),
    onTertiaryContainer = Color(0xFF203A2B),
    background = WarmBackground,
    onBackground = Ink,
    surface = Color(0xFFFCFCFA),
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = Color(0xFF565650),
    outline = Stroke,
    outlineVariant = Color(0xFFE8E7E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFECEBE7),
    onPrimary = Color(0xFF1A1918),
    primaryContainer = Color(0xFF343331),
    onPrimaryContainer = Color(0xFFECEBE7),
    secondary = Color(0xFF89AE94),
    onSecondary = Color(0xFF14281B),
    error = Color(0xFFE89A91),
    errorContainer = Color(0xFF3A2421),
    onErrorContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFF89AE94),
    tertiaryContainer = Color(0xFF223429),
    onTertiaryContainer = Color(0xFFD2E8D6),
    background = Color(0xFF151515),
    onBackground = Color(0xFFECEBE7),
    surface = Color(0xFF1B1B1A),
    onSurface = Color(0xFFECEBE7),
    surfaceVariant = Color(0xFF232322),
    onSurfaceVariant = Color(0xFFA8A6A1),
    outline = Color(0xFF3D3C39),
    outlineVariant = Color(0xFF2D2C2A),
)

private val CodexTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
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
