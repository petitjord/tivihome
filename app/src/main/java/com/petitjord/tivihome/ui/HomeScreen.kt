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
    val railWidth by animateDpAsState(if (railOpen) 210.dp else 72.dp, tween(220), label = "rail")

    // Seule la section « TV en direct » garde l'aperçu vidéo en marche.
    LaunchedEffect(selected) {
        if (selected != LIVE && !tv.fullscreen) tv.stop()
    }

    Box(Modifier.fillMaxSize()) {
        // Contenu, décalé de la largeur du menu replié
        Box(Modifier.fillMaxSize().padding(start = 72.dp)) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
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

        // Menu latéral : replié en icônes, il s'ouvre par-dessus le contenu quand on y entre.
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        if (railOpen) listOf(Panel, Panel, Color(0xF0141922)) else listOf(Panel, Panel),
                    ),
                )
                .onFocusChanged { railOpen = it.hasFocus }
                .focusGroup()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.height(56.dp).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMark()
                    AnimatedVisibility(railOpen, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(
                            "Tivihome", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp), maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            sections.forEachIndexed { i, s ->
                val isSel = i == selected
                FocusBox(
                    onClick = { selected = i },
                    focusRequester = if (isSel) railFocus else null,
                    background = if (isSel) AccentDim else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) { focused ->
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            s.icon, s.label, modifier = Modifier.size(22.dp),
                            tint = if (isSel || focused) Color.White else TextDim,
                        )
                        AnimatedVisibility(railOpen, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            Text(
                                s.label, maxLines = 1, fontSize = 15.sp,
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

/** Petit logo maison : un écran stylisé. */
@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp = 26.dp) {
    Box(
        Modifier.size(size).background(
            Brush.linearGradient(listOf(Accent, Color(0xFF7C5CFF))),
            androidx.compose.foundation.shape.RoundedCornerShape(size / 4),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text("T", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.55f).sp)
    }
}
