package com.petitjord.tivihome.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Couleurs d'accent proposées dans les réglages. */
val AccentChoices = listOf(
    "Bleu" to Color(0xFF3DB2FF),
    "Violet" to Color(0xFF9B7BFF),
    "Vert" to Color(0xFF3DDC97),
    "Orange" to Color(0xFFFF9F43),
    "Rose" to Color(0xFFFF5C8A),
    "Rouge" to Color(0xFFFF5252),
)

/** Thème vivant : changer une valeur redessine toute l'appli. */
object AppTheme {
    var accentIndex by mutableStateOf(0)
    /** Noir pur pour les écrans OLED. */
    var oled by mutableStateOf(false)
    /** Effets complets (fond animé, halos) : désactivés par défaut pour la fluidité. */
    var richEffects by mutableStateOf(false)
}

val Accent: Color get() = AccentChoices[AppTheme.accentIndex.coerceIn(0, AccentChoices.lastIndex)].second
val AccentDim: Color get() = Accent.copy(alpha = 0.32f).compositeOn(if (AppTheme.oled) Color.Black else Color(0xFF141922))
val Bg: Color get() = if (AppTheme.oled) Color.Black else Color(0xFF0B0E13)
val Panel: Color get() = if (AppTheme.oled) Color(0xFF0A0A0A) else Color(0xFF141922)
val Panel2: Color get() = if (AppTheme.oled) Color(0xFF161616) else Color(0xFF1C2330)
val TextDim = Color(0xFF8A94A6)

/** Seconde teinte d'accent pour les dégradés (décalée vers le violet). */
val Accent2: Color get() = Color(
    red = (Accent.red * 0.55f + 0.49f * 0.45f),
    green = (Accent.green * 0.55f + 0.36f * 0.45f),
    blue = (Accent.blue * 0.55f + 1.0f * 0.45f),
)

// Surfaces « verre » : translucides, laissent voir le fond animé
val Glass: Color get() = if (AppTheme.oled) Color(0xFF0E0E0E) else Color(0x14FFFFFF)
val GlassStrong: Color get() = if (AppTheme.oled) Color(0xFF151515) else Color(0x1FFFFFFF)
val GlassStroke = Color(0x1AFFFFFF)
val GlassDark = Color(0xB30A0D12)
val Gold = Color(0xFFFFC94D)

/** Mélange une couleur semi-transparente sur un fond opaque. */
private fun Color.compositeOn(bg: Color): Color {
    val a = alpha
    return Color(
        red = red * a + bg.red * (1 - a),
        green = green * a + bg.green * (1 - a),
        blue = blue * a + bg.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
fun TiviTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Bg,
            onBackground = Color.White,
            surface = Panel,
            onSurface = Color.White,
            surfaceVariant = Panel2,
            onSurfaceVariant = TextDim,
        ),
        content = content,
    )
}
