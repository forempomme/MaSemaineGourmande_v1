package com.masemainegourmande.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Warm Gourmande palette ────────────────────────────────────

val PriOrange       = Color(0xFFD4622A)
val PriOrangeLight  = Color(0xFFFAEAE0)
val PriOrangeDark   = Color(0xFFA84B1F)
val AccGreen        = Color(0xFF5B8C5A)
val AccGreenLight   = Color(0xFFE8F4E8)
val BgCream         = Color(0xFFFDF8F0)
val SurfaceWhite    = Color(0xFFFFFFFF)
val TextBrown       = Color(0xFF2C1A0E)
val TextMuted       = Color(0xFF9A7B65)
val BorderBeige     = Color(0xFFEDE0D0)
val StarYellow      = Color(0xFFF5A623)

private val LightColorScheme = lightColorScheme(
    primary            = PriOrange,
    onPrimary          = Color.White,
    primaryContainer   = PriOrangeLight,
    onPrimaryContainer = PriOrangeDark,
    secondary          = AccGreen,
    onSecondary        = Color.White,
    secondaryContainer = AccGreenLight,
    onSecondaryContainer = Color(0xFF1A3C19),
    background         = BgCream,
    onBackground       = TextBrown,
    surface            = SurfaceWhite,
    onSurface          = TextBrown,
    surfaceVariant     = PriOrangeLight,
    onSurfaceVariant   = TextMuted,
    outline            = BorderBeige,
    error              = Color(0xFFE05050),
    onError            = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary            = Color(0xFFFFB68A),
    onPrimary          = Color(0xFF6A2300),
    primaryContainer   = PriOrangeDark,
    onPrimaryContainer = PriOrangeLight,
    secondary          = Color(0xFF9DC99B),
    onSecondary        = Color(0xFF1A3C19),
    background         = Color(0xFF1A1008),
    onBackground       = Color(0xFFF5DFC8),
    surface            = Color(0xFF2A1A0E),
    onSurface          = Color(0xFFF5DFC8),
)

@Composable
fun MsgTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content
    )
}
