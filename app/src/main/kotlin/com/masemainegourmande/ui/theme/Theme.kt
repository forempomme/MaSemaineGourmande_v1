package com.masemainegourmande.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Acier Nocturne — Ardoise 70% / Bleu 30% ──────────────────

val BgCream        = Color(0xFF111419)   // fond principal — gris ardoise-bleu
val CardSurface    = Color(0xFF181C24)   // cartes
val BorderBeige    = Color(0xFF2A3040)   // bordures

val PriOrange      = Color(0xFF7BA8E8)   // bleu acier doux — accent
val PriOrangeLight = Color(0x227BA8E8)
val PriOrangeDark  = Color(0xFF4878C8)   // bleu acier foncé — boutons
val AccGreen       = Color(0xFF5CC4A0)   // vert-bleu — checks
val AccGreenLight  = Color(0x225CC4A0)

val TextBrown      = Color(0xFFE8EAF2)   // texte principal — blanc acier
val TextMuted      = Color(0xFF7888A8)   // texte secondaire
val StarYellow     = Color(0xFFF5C542)

// Couleurs nav uniquement pour Navigation.kt
val NavGradientTop = Color(0xFF1E2B50)
val NavGradientBot = Color(0xFF131C38)

private val DarkScheme = darkColorScheme(
    primary              = Color(0xFF7BA8E8),
    onPrimary            = Color(0xFF0D1E40),
    primaryContainer     = Color(0xFF1A2848),
    onPrimaryContainer   = Color(0xFFCCDDFF),

    secondary            = Color(0xFF5CC4A0),
    onSecondary          = Color(0xFF003828),
    secondaryContainer   = Color(0xFF0A2422),
    onSecondaryContainer = Color(0xFFA8EED8),

    background           = Color(0xFF111419),
    onBackground         = Color(0xFFE8EAF2),
    surface              = Color(0xFF181C24),
    onSurface            = Color(0xFFE8EAF2),
    surfaceVariant       = Color(0xFF1F2430),
    onSurfaceVariant     = Color(0xFF7888A8),

    outline              = Color(0xFF2A3040),
    outlineVariant       = Color(0xFF384050),

    error                = Color(0xFFE57373),
    onError              = Color(0xFF410002),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),

    surfaceTint          = Color(0xFF7BA8E8),
    inverseSurface       = Color(0xFFE4E6F0),
    inverseOnSurface     = Color(0xFF1F2430),
    inversePrimary       = Color(0xFF3A68C0),
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
