package com.petitjord.zaptv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0E13)
val Panel = Color(0xFF141922)
val Panel2 = Color(0xFF1C2330)
val Accent = Color(0xFF3DB2FF)
val AccentDim = Color(0xFF1B4A6B)
val TextDim = Color(0xFF8A94A6)
val Gold = Color(0xFFFFC94D)

@Composable
fun ZapTheme(content: @Composable () -> Unit) {
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
