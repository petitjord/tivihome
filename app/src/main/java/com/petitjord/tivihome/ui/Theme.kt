package com.petitjord.tivihome.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

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
val Bg: Color get() = if (AppTheme.oled) Color.Black else Color(0xFF0D1524)
val Panel: Color get() = if (AppTheme.oled) Color(0xFF0C0C0C) else Color(0xFF152036)
val Panel2: Color get() = if (AppTheme.oled) Color(0xFF181818) else Color(0xFF1D2B46)
/** Barre de menu, un ton plus sombre que le contenu. */
val RailBg: Color get() = if (AppTheme.oled) Color(0xFF080808) else Color(0xFF0C1422)
val TextDim = Color(0xFF8A94A6)

/** Seconde teinte d'accent pour les dégradés (décalée vers le violet). */
val Accent2: Color get() = Color(
    red = (Accent.red * 0.55f + 0.49f * 0.45f),
    green = (Accent.green * 0.55f + 0.36f * 0.45f),
    blue = (Accent.blue * 0.55f + 1.0f * 0.45f),
)

// Surfaces « verre » : translucides, laissent voir le fond animé
val Glass: Color get() = Panel
val GlassStrong: Color get() = Panel2
val GlassStroke = Color(0x00FFFFFF)
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

/** Police de l'appli : Inter si elle a été embarquée, sinon la police système. */
object Fonts {
    var family: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
        private set

    fun init(ctx: android.content.Context) {
        @Suppress("DiscouragedApi")
        val id = ctx.resources.getIdentifier("inter_variable", "font", ctx.packageName)
        if (id == 0) return
        family = runCatching {
            androidx.compose.ui.text.font.FontFamily(
                listOf(300, 400, 500, 600, 700, 800).map { w ->
                    androidx.compose.ui.text.font.Font(id, androidx.compose.ui.text.font.FontWeight(w))
                },
            )
        }.getOrDefault(androidx.compose.ui.text.font.FontFamily.Default)
    }
}

private fun typography(): androidx.compose.material3.Typography {
    val f = Fonts.family
    val t = androidx.compose.material3.Typography()
    return androidx.compose.material3.Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f), headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f, letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f),
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
        typography = typography(),
        content = content,
    )
}
