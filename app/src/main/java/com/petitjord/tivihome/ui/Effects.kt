package com.petitjord.tivihome.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fond « aurore » : deux halos de couleur qui dérivent très lentement
 * derrière toute l'interface. Discret, mais donne de la profondeur.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    if (AppTheme.richEffects) AnimatedAurora(modifier) else StaticAurora(modifier)
}

/** Version fixe, dessinée une seule fois : aucun coût pendant la navigation. */
@Composable
private fun StaticAurora(modifier: Modifier) {
    val base = Bg
    val a1 = Accent
    val oled = AppTheme.oled
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        if (oled) return@Canvas
        val w = size.width; val h = size.height
        // Lumière douce en haut à gauche, assombrissement vers le bas
        drawRect(Brush.radialGradient(listOf(a1.copy(alpha = 0.10f), Color.Transparent), center = Offset(w * 0.08f, 0f), radius = w * 0.6f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)), startY = h * 0.4f, endY = h))
    }
}

@Composable
private fun AnimatedAurora(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "phase",
    )
    val base = Bg
    val a1 = Accent
    val a2 = Accent2
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        if (AppTheme.oled) return@Canvas
        val w = size.width; val h = size.height
        val c1 = Offset(w * (0.22f + 0.10f * cos(phase)), h * (0.18f + 0.12f * sin(phase)))
        val c2 = Offset(w * (0.82f + 0.08f * sin(phase * 0.8f)), h * (0.86f + 0.10f * cos(phase * 1.2f)))
        drawRect(Brush.radialGradient(listOf(a1.copy(alpha = 0.16f), Color.Transparent), center = c1, radius = w * 0.55f))
        drawRect(Brush.radialGradient(listOf(a2.copy(alpha = 0.13f), Color.Transparent), center = c2, radius = w * 0.50f))
    }
}

/** Panneau en verre : fond translucide, fine bordure lumineuse, coins arrondis. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), strong: Boolean = false): Modifier =
    this.clip(shape)
        .background(if (strong) GlassStrong else Glass, shape)
        .border(1.dp, GlassStroke, shape)

/** Pinceau scintillant pour les squelettes de chargement. */
@Composable
fun shimmerBrush(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -600f, targetValue = 1800f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    return Brush.linearGradient(
        listOf(Color(0x0FFFFFFF), Color(0x26FFFFFF), Color(0x0FFFFFFF)),
        start = Offset(x, 0f), end = Offset(x + 600f, 200f),
    )
}

@Composable
fun SkeletonBlock(width: Dp?, height: Dp, brush: Brush, radius: Dp = 10.dp) {
    val m = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    Box(m.height(height).clip(RoundedCornerShape(radius)).background(brush))
}

/** Squelette générique : une liste à gauche, des cartes à droite. */
@Composable
fun SkeletonScreen() {
    val b = shimmerBrush()
    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(8) { SkeletonBlock(null, 36.dp, b) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonBlock(260.dp, 28.dp, b)
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(5) { SkeletonBlock(110.dp, 150.dp, b, 14.dp) }
                }
            }
        }
    }
}

/** Petit point lumineux qui pulse (indicateur « en direct »). */
@Composable
fun LiveDot(size: Dp = 8.dp, color: Color = Color(0xFFFF4D5E)) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a",
    )
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(color.copy(alpha = a)))
}

// ---------- Défilement façon décodeur TV ----------

/**
 * Le focus reste à une position fixe (fraction de la zone visible) et c'est la liste
 * qui défile dessous : plus d'à-coups quand on parcourt une rangée d'affiches.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun pivotSpec(fraction: Float) = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = fraction * containerSize
        val leading = if (size <= containerSize && containerSize - target < size) containerSize - size else target
        return offset - leading
    }
}

/** Aucun défilement automatique : c'est l'écran qui décide (voir l'accueil). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
val NoAutoScroll = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ScrollSpec(spec: androidx.compose.foundation.gestures.BringIntoViewSpec, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec,
        content = content,
    )
}

/** Rangée horizontale dont l'élément sélectionné reste calé à gauche. */
@Composable
fun PivotRow(content: @Composable () -> Unit) = ScrollSpec(pivotSpec(0f), content)
