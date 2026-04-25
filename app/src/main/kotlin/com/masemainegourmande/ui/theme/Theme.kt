package com.masemainegourmande.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Palette Minuit Profond × Ardoise ─────────────────────────

// Fonds (du plus foncé au plus clair)
val BgCream        = Color(0xFF090C14)   // fond principal
val CardSurface    = Color(0xFF0F1420)   // cartes
val BorderBeige    = Color(0xFF1E2638)   // bordures

// Accents
val PriOrange      = Color(0xFF6AAAF8)   // bleu ciel — accent principal
val PriOrangeLight = Color(0x226AAAF8)   // bleu transparent
val PriOrangeDark  = Color(0xFF3D7AE0)   // bleu boutons
val AccGreen       = Color(0xFF52D4A8)   // menthe — checks
val AccGreenLight  = Color(0x2252D4A8)   // menthe transparent

// Textes
val TextBrown      = Color(0xFFEDF0FF)   // texte principal
val TextMuted      = Color(0xFF6070A0)   // texte secondaire
val StarYellow     = Color(0xFFF5C542)   // étoiles

// ── Dark color scheme — toutes les couleurs explicites ────────
private val DarkScheme = darkColorScheme(
    // Primary (bleu ciel)
    primary              = Color(0xFF6AAAF8),
    onPrimary            = Color(0xFF003060),
    primaryContainer     = Color(0xFF1A3060),
    onPrimaryContainer   = Color(0xFFBDD8FF),

    // Secondary (menthe)
    secondary            = Color(0xFF52D4A8),
    onSecondary          = Color(0xFF003828),
    secondaryContainer   = Color(0xFF0A2C22),
    onSecondaryContainer = Color(0xFF9EFADA),

    // Background & surface — couleurs sombres
    background           = Color(0xFF090C14),
    onBackground         = Color(0xFFEDF0FF),
    surface              = Color(0xFF0F1420),
    onSurface            = Color(0xFFEDF0FF),
    surfaceVariant       = Color(0xFF161C2C),
    onSurfaceVariant     = Color(0xFF6070A0),

    // Outline
    outline              = Color(0xFF1E2638),
    outlineVariant       = Color(0xFF252E44),

    // Error
    error                = Color(0xFFE57373),
    onError              = Color(0xFF410002),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),

    // Surface tones
    surfaceTint          = Color(0xFF6AAAF8),
    inverseSurface       = Color(0xFFE8ECFF),
    inverseOnSurface     = Color(0xFF161C2C),
    inversePrimary       = Color(0xFF2C72E0),

    // Scrim
    scrim                = Color(0xFF000000),
)

@Composable
fun MsgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography  = Typography(),
        content     = content
    )
}
