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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
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

/** Index de « TV » : seule section qui garde l'aperçu vidéo actif. */
private const val LIVE = 1

private val sections = listOf(
    Section("Accueil", Icons.Filled.Home),
    Section("TV", Icons.Filled.PlayArrow),
    Section("Recherche", Icons.Filled.Search),
    Section("Replay", Icons.Filled.Refresh),
    Section("Films", Icons.Filled.Star),
    Section("Séries", Icons.Filled.List),
    Section("Favoris", Icons.Filled.Favorite),
)

@Composable
fun HomeScreen(nav: Nav, focusContent: Boolean) {
    val tv = LocalTvPlayer.current
    var selected by rememberSaveable { mutableIntStateOf(LIVE) }
    var railOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val railFocus = rememberInitialFocus(!focusContent)
    val settingsFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val contentFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    // Seule la section « TV » garde l'aperçu vidéo en marche.
    LaunchedEffect(selected) {
        if (selected != LIVE && !tv.fullscreen) tv.stop()
    }
    // Retour depuis le contenu : on revient au menu (confort de navigation)
    androidx.activity.compose.BackHandler(enabled = !railOpen && !settingsOpen) {
        runCatching { railFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        // Contenu, décalé de la largeur du menu replié (marges de sécurité TV en haut, bas et droite)
        Box(Modifier.fillMaxSize().padding(start = 28.dp, top = 18.dp, bottom = 12.dp, end = 24.dp).focusRequester(contentFocus).focusGroup()) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "section",
            ) { s ->
                when (s) {
                    0 -> HomeSection(nav, focusContent, onSeeAll = { section -> selected = section })
                    1 -> GuideSection(nav, focusContent)
                    2 -> SearchSection(nav)
                    3 -> ReplaySection(nav, focusContent)
                    4 -> MoviesSection(nav, focusContent)
                    5 -> SeriesSection(nav, focusContent)
                    else -> FavoritesSection(nav, focusContent)
                }
            }
        }

        // Heure discrète en haut à droite
        SmallClock(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 26.dp))

        // Dock flottant en verre, centré en bas. Il se range sous l'écran quand on navigue dans le
        // contenu, et revient avec Retour (ou en descendant tout en bas).
        val dockShift by animateDpAsState(if (railOpen) 0.dp else 120.dp, tween(220), label = "dock")
        var dockLabel by remember { mutableStateOf("") }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                .graphicsLayer { translationY = dockShift.toPx(); alpha = if (dockShift >= 119.dp) 0f else 1f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Nom de l'icône sélectionnée, au-dessus du dock
            Box(
                Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(GlassDark)
                    .border(1.dp, GlassStroke, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(dockLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(GlassDark)
                    .border(1.dp, GlassStroke, RoundedCornerShape(30.dp))
                    .onFocusChanged { railOpen = it.hasFocus }
                    .focusGroup()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.padding(horizontal = 4.dp)) { LogoMark(30.dp) }
                DockDivider()
                sections.forEachIndexed { i, s ->
                    DockItem(
                        icon = s.icon, label = s.label, selected = i == selected,
                        requester = if (i == selected) railFocus else null,
                        // Un simple passage sur l'icône change de section
                        onFocus = { dockLabel = s.label; if (selected != i) selected = i },
                    ) { selected = i; runCatching { contentFocus.requestFocus() } }
                }
                DockDivider()
                DockItem(
                    icon = Icons.Filled.Settings, label = "Paramètres", selected = false,
                    requester = settingsFocus, onFocus = { dockLabel = "Paramètres" },
                ) { settingsOpen = true }
            }
        }

        // Paramètres : panneau qui glisse depuis la droite
        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(tween(160)) + androidx.compose.animation.slideInHorizontally(tween(220)) { it / 3 },
            exit = fadeOut(tween(140)) + androidx.compose.animation.slideOutHorizontally(tween(180)) { it / 3 },
        ) {
            SettingsPanel(nav, onClose = {
                settingsOpen = false
                runCatching { settingsFocus.requestFocus() }
            })
        }
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    requester: androidx.compose.ui.focus.FocusRequester?,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusBox(
            onClick = onClick,
            onFocus = onFocus,
            focusRequester = requester,
            background = if (selected) Color(0x2EFFFFFF) else Color(0x14FFFFFF),
            focusedBackground = Color.White,
            scaleOnFocus = 1.18f,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(54.dp),
        ) { focused ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, label, modifier = Modifier.size(24.dp), tint = if (focused) Bg else Color.White)
            }
        }
        // Point sous la section active, comme une appli ouverte
        Box(
            Modifier.padding(top = 5.dp).size(4.dp).clip(RoundedCornerShape(2.dp))
                .background(if (selected) Color.White else Color.Transparent),
        )
    }
}

@Composable
private fun DockDivider() {
    Box(Modifier.width(1.dp).height(36.dp).background(Color(0x33FFFFFF)))
}

/** Heure discrète, sans fond, lisible sur n'importe quelle image grâce à une légère ombre. */
@Composable
fun SmallClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000 - now % 60_000)
        }
    }
    Text(
        hm(now), modifier = modifier,
        color = Color(0xE6FFFFFF), fontSize = 16.sp, fontWeight = FontWeight.Medium,
        style = androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), blurRadius = 8f),
        ),
    )
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
