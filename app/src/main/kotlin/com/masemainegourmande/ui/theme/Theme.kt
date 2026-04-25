package com.masemainegourmande.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Minuit Profond × Ardoise ─────────────────────────────────
val PriOrange      = Color(0xFF6AAAF8)   // bleu ciel — accent principal
val PriOrangeLight = Color(0xFF6AAAF822) // accent transparent
val PriOrangeDark  = Color(0xFF3D7AE0)   // accent foncé (boutons)
val AccGreen       = Color(0xFF52D4A8)   // menthe — ✓ checks
val AccGreenLight  = Color(0xFF52D4A822)

// Fonds
val BgCream        = Color(0xFF090C14)   // fond principal — quasi-noir bleuté
val CardSurface    = Color(0xFF0F1420)   // cartes
val BorderBeige    = Color(0xFF1E2638)   // bordures

// Textes
val TextBrown      = Color(0xFFEDF0FF)   // texte principal — blanc bleuté
val TextMuted      = Color(0xFF6070A0)   // texte secondaire
val StarYellow     = Color(0xFFF5C542)   // étoiles notation

// ── Color scheme ─────────────────────────────────────────────
private val ColorScheme = lightColorScheme(
    primary              = PriOrange,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFF1A2848),
    onPrimaryContainer   = Color(0xFFBDD4FF),
    secondary            = AccGreen,
    onSecondary          = Color(0xFF003828),
    secondaryContainer   = Color(0xFF1A3830),
    onSecondaryContainer = Color(0xFFB0F0D8),
    background           = BgCream,
    onBackground         = TextBrown,
    surface              = CardSurface,
    onSurface            = TextBrown,
    surfaceVariant       = Color(0xFF161C2C),
    onSurfaceVariant     = TextMuted,
    outline              = BorderBeige,
    error                = Color(0xFFE57373),
    onError              = Color.White,
)

@Composable
fun MsgTheme(content: @Composable () -> Unit) {
    // Thème toujours "clair" (notre palette sombre est gérée manuellement)
    MaterialTheme(
        colorScheme = ColorScheme,
        typography  = Typography(),
        content     = content
    )
}
