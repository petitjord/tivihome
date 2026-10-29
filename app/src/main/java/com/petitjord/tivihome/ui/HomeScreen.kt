package com.petitjord.tivihome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Section(val label: String, val icon: ImageVector)

/** Index de « TV en direct » : seule section qui garde l'aperçu vidéo actif. */
private const val LIVE = 1

private val sections = listOf(
    Section("Accueil", Icons.Filled.Home),
    Section("TV en direct", Icons.Filled.PlayArrow),
    Section("Recherche", Icons.Filled.Search),
    Section("Guide TV", Icons.Filled.DateRange),
    Section("Replay", Icons.Filled.Refresh),
    Section("Films", Icons.Filled.Star),
    Section("Séries", Icons.Filled.List),
    Section("Favoris", Icons.Filled.Favorite),
    Section("Paramètres", Icons.Filled.Settings),
)

@Composable
fun HomeScreen(nav: Nav, focusContent: Boolean) {
    val tv = LocalTvPlayer.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var railOpen by remember { mutableStateOf(false) }
    val railFocus = rememberInitialFocus(!focusContent)
    val railWidth by animateDpAsState(if (railOpen) 220.dp else 72.dp, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 420f), label = "rail")

    // Seule la section « TV en direct » garde l'aperçu vidéo en marche.
    LaunchedEffect(selected) {
        if (selected != LIVE && !tv.fullscreen) tv.stop()
    }

    Box(Modifier.fillMaxSize()) {
        // Contenu, décalé de la largeur du menu replié
        Box(Modifier.fillMaxSize().padding(start = 72.dp)) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (fadeIn(tween(260)) + androidx.compose.animation.slideInVertically(tween(320)) { it / 24 }) togetherWith
                        fadeOut(tween(140))
                },
                label = "section",
            ) { s ->
                when (s) {
                    0 -> HomeSection(nav, focusContent)
                    1 -> LiveSection(nav, focusContent)
                    2 -> SearchSection(nav)
                    3 -> GuideSection(nav, focusContent)
                    4 -> ReplaySection(nav, focusContent)
                    5 -> MoviesSection(nav, focusContent)
                    6 -> SeriesSection(nav, focusContent)
                    7 -> FavoritesSection(nav, focusContent)
                    else -> SettingsSection(nav)
                }
            }
        }

        // Menu latéral flottant en verre : replié en icônes, il s'ouvre quand on y entre.
        // Un indicateur lumineux glisse jusqu'à la section active.
        val itemH = 40.dp
        val gap = 4.dp
        val headerH = 54.dp
        val indicatorY by animateDpAsState(
            headerH + (itemH + gap) * selected,
            androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 380f),
            label = "indicator",
        )
        Box(
            Modifier
                .padding(start = 8.dp, top = 10.dp, bottom = 10.dp)
                .width(railWidth - 8.dp)
                .fillMaxHeight()
                .then(
                    if (railOpen && AppTheme.richEffects) Modifier.shadow(30.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    else Modifier,
                )
                .glass(RoundedCornerShape(24.dp), strong = railOpen)
                .background(if (railOpen) GlassDark else Color.Transparent)
                .onFocusChanged { railOpen = it.hasFocus }
                .focusGroup()
                .padding(horizontal = 8.dp, vertical = 14.dp),
        ) {
            // Indicateur de la section active
            Box(
                Modifier
                    .offset(y = indicatorY)
                    .fillMaxWidth()
                    .height(itemH)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Accent.copy(alpha = 0.38f), Accent2.copy(alpha = 0.20f)))),
            )
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.height(headerH - gap).padding(start = 6.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoMark(28.dp)
                        AnimatedVisibility(railOpen, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            Text(
                                "Tivihome", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp, modifier = Modifier.padding(start = 10.dp), maxLines = 1,
                            )
                        }
                    }
                }
                sections.forEachIndexed { i, s ->
                    val isSel = i == selected
                    FocusBox(
                        onClick = { selected = i },
                        focusRequester = if (isSel) railFocus else null,
                        background = Color.Transparent,
                        focusedBackground = Color(0x1FFFFFFF),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(itemH),
                    ) { focused ->
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                s.icon, s.label, modifier = Modifier.size(20.dp),
                                tint = if (isSel || focused) Color.White else TextDim,
                            )
                            AnimatedVisibility(railOpen, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                                Text(
                                    s.label, maxLines = 1, fontSize = 15.sp,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSel || focused) Color.White else TextDim,
                                    modifier = Modifier.padding(start = 14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Logo Tivihome : une tuile en dégradé avec une maison stylisée
 * dont la porte est un bouton lecture (TV + maison).
 */
@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp = 26.dp) {
    val a1 = Accent
    val a2 = Accent2
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val r = w * 0.27f
        // Tuile
        drawRoundRect(
            brush = Brush.linearGradient(listOf(a1, a2), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, w)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        // Reflet discret en haut
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = w * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        // Maison : toit + corps
        val house = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.50f, w * 0.20f)
            lineTo(w * 0.80f, w * 0.46f)
            lineTo(w * 0.73f, w * 0.46f)
            lineTo(w * 0.73f, w * 0.78f)
            quadraticBezierTo(w * 0.73f, w * 0.80f, w * 0.71f, w * 0.80f)
            lineTo(w * 0.29f, w * 0.80f)
            quadraticBezierTo(w * 0.27f, w * 0.80f, w * 0.27f, w * 0.78f)
            lineTo(w * 0.27f, w * 0.46f)
            lineTo(w * 0.20f, w * 0.46f)
            close()
        }
        drawPath(house, Color.White)
        // Porte = bouton lecture, couleur de la tuile
        val play = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.44f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.72f)
            lineTo(w * 0.61f, w * 0.62f)
            close()
        }
        drawPath(play, Brush.linearGradient(listOf(a1, a2), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, w)))
    }
}
