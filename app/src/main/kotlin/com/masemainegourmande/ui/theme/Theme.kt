package com.masemainegourmande.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Palette warm & deep ──────────────────────────────────────

val PriOrange      = Color(0xFFD4622A)
val PriOrangeLight = Color(0xFFF0D4BF)
val PriOrangeDark  = Color(0xFF9E3E14)
val AccGreen       = Color(0xFF4A7A49)
val AccGreenLight  = Color(0xFFD4EDD3)

// Fond principal — ocre profond, même que l'onglet Options affiché par le Scaffold
val BgCream        = Color(0xFFD9A96A)   // ambre foncé marqué
// Cartes — crème clair, fort contraste avec BgCream
val CardSurface    = Color(0xFFFAEDD8)   // crème chaud
// Textes
val TextBrown      = Color(0xFF2C1A0E)
val TextMuted      = Color(0xFF7A5A42)
// Bordures des cartes — bien visible sur fond ambre
val BorderBeige    = Color(0xFFB8895A)
val StarYellow     = Color(0xFFF0A010)

// ── Light color scheme ───────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary              = PriOrange,
    onPrimary            = Color.White,
    primaryContainer     = PriOrangeLight,
    onPrimaryContainer   = PriOrangeDark,
    secondary            = AccGreen,
    onSecondary          = Color.White,
    secondaryContainer   = AccGreenLight,
    onSecondaryContainer = Color(0xFF1A3C19),
    background           = BgCream,
    onBackground         = TextBrown,
    surface              = CardSurface,
    onSurface            = TextBrown,
    surfaceVariant       = Color(0xFFEDD5B0),
    onSurfaceVariant     = TextMuted,
    outline              = BorderBeige,
    error                = Color(0xFFCC3333),
    onError              = Color.White,
)

// ── Dark color scheme ────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary              = Color(0xFFFFB070),
    onPrimary            = Color(0xFF5C1E00),
    primaryContainer     = PriOrangeDark,
    onPrimaryContainer   = PriOrangeLight,
    secondary            = Color(0xFF88BB86),
    onSecondary          = Color(0xFF0D2B0D),
    background           = Color(0xFF1C1008),
    onBackground         = Color(0xFFF5DFC8),
    surface              = Color(0xFF2A1A0C),
    onSurface            = Color(0xFFF5DFC8),
    surfaceVariant       = Color(0xFF3D2618),
    onSurfaceVariant     = Color(0xFFB89070),
    outline              = Color(0xFF5A3D25),
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
