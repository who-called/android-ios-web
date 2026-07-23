package com.whocalled.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Who Called — vivid blue brand on clean white. A bright royal blue gives the
// app energy; the navy is kept only for the header gradient's deep end. No violet.
object WCColor {
    val Blue = Color(0xFF2563EB) // primary accent — vivid royal blue (the brand)
    val BlueDark = Color(0xFF1D4ED8) // pressed / deep accent
    val BlueBright = Color(0xFF3B82F6) // lighter accent (gradients, highlights)
    val NightBlue = Color(0xFF15294D) // header gradient deep end (blue, not violet)
    val NightBlueDark = Color(0xFF101F3C)
    // Aliases kept so existing references compile; all now point to the blue brand.
    val Indigo = Blue
    val IndigoDeep = BlueDark
    val Violet = Blue
    val Amber = Color(0xFFF59E0B) // warn / alert
    val Emerald = Color(0xFF10B981) // safe / legit
    val Coral = Color(0xFFEF4444) // blocked / danger
    val Ink = Color(0xFF0F172A)
    val Cloud = Color(0xFFFFFFFF)

    // Subtle structure: hairline borders + a barely-there blue tint, never gray fills.
    val Border = Color(0xFFE2E8F5) // subtle neutral-blue hairline
    val BorderSoft = Color(0xFFEEF2FB)
    val TintBlue = Color(0xFFEFF5FF) // light blue wash (cards, empty discs)
    val Muted = Color(0xFF64748B) // secondary text
    val Slate = Color(0xFF64748B) // community badge
}

private val LightColors = lightColorScheme(
    primary = WCColor.Blue,
    onPrimary = WCColor.Cloud,
    secondary = WCColor.Amber,
    error = WCColor.Coral,
    background = WCColor.Cloud,
    surface = WCColor.Cloud,
    surfaceVariant = WCColor.TintBlue,
    onSurfaceVariant = WCColor.Muted,
    outline = WCColor.Border,
    outlineVariant = WCColor.BorderSoft,
)

private val DarkColors = darkColorScheme(
    primary = WCColor.BlueBright, // bright blue for dark-mode contrast (not violet)
    secondary = WCColor.Amber,
    error = WCColor.Coral,
    background = WCColor.Ink,
    surface = Color(0xFF16202E), // neutral blue-slate (no violet tint)
    outline = Color(0xFF2A3850),
)

@Composable
fun WhoCalledTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
