package com.petitjord.tivihome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import kotlinx.coroutines.delay

/**
 * TV en direct en plein écran.
 *  ▲ / ▼ ou CH+ / CH− : zapper      OK ou ◀ : liste des chaînes par-dessus la vidéo
 *  ▶ : bandeau d'infos              Retour : ferme la liste, puis revient à l'accueil
 */
@Composable
fun LiveFullScreen(nav: Nav) {
    val tv = LocalTvPlayer.current
    val ctx = LocalContext.current
    var listOpen by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(true) }
    var activity by remember { mutableIntStateOf(0) } // relance les minuteurs à chaque touche
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(20) { delay(40); if (runCatching { rootFocus.requestFocus() }.isSuccess) return@LaunchedEffect }
    }
    // Bandeau : 5 s ; liste : 10 s sans action
    LaunchedEffect(infoVisible, activity) { if (infoVisible) { delay(5_000); infoVisible = false } }
    LaunchedEffect(listOpen, activity) {
        if (listOpen) { delay(10_000); listOpen = false }
    }
    LaunchedEffect(listOpen) {
        if (!listOpen) { delay(30); runCatching { rootFocus.requestFocus() } }
    }
    // Nouveau programme / nouvelle chaîne : on montre le bandeau
    LaunchedEffect(tv.channel) { infoVisible = true; activity++ }

    BackHandler {
        if (listOpen) listOpen = false else nav.back()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { activity++; false }
            .focusRequester(rootFocus)
            .onKeyEvent { ev ->
                if (listOpen || ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { tv.zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { tv.zap(1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft, Key.Menu -> {
                        listOpen = true; infoVisible = false; true
                    }
                    Key.DirectionRight, Key.Info -> { infoVisible = !infoVisible; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        VideoSurface(Modifier.fillMaxSize())

        if (tv.buffering && tv.error == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }
        tv.error?.let {
            ErrorBox("$it\nEssaie une autre chaîne avec ▲ / ▼", Modifier.align(Alignment.Center))
        }

        val ch = tv.channel
        AnimatedVisibility(
            visible = infoVisible && !listOpen && ch != null,
            enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (ch != null) LiveInfoBar(ch, tv.channels.indexOf(ch), tv.channels.size)
        }

        AnimatedVisibility(
            visible = listOpen,
            enter = slideInHorizontally(tween(220)) { -it } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(180)) { -it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ChannelOverlay(
                onPick = { list, i ->
                    tv.playChannel(list[i], list)
                    listOpen = false
                },
                onFav = { c -> toggleFav(ctx, c.favKey, c.name) },
            )
        }
    }
}

/** Liste des chaînes affichée par-dessus la vidéo, la lecture continue derrière. */
@Composable
private fun ChannelOverlay(onPick: (List<Channel>, Int) -> Unit, onFav: (Channel) -> Unit) {
    val tv = LocalTvPlayer.current
    val list = tv.channels
    val start = list.indexOf(tv.channel).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val focus = rememberInitialFocus(true)
    val favs = AppRepo.favorites

    Column(
        Modifier
            .width(440.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xF20B0E13), Color(0xE60B0E13), Color(0x000B0E13))))
            .padding(start = 24.dp, top = 24.dp, end = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            LogoMark(22.dp)
            Spacer(Modifier.width(10.dp))
            Text("Chaînes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text("${list.size}", color = TextDim, fontSize = 14.sp)
        }
        LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(list) { i, c ->
                ChannelRow(
                    ch = c,
                    favorite = c.favKey in favs,
                    playing = c == tv.channel,
                    onClick = { onPick(list, i) },
                    onLongClick = { onFav(c) },
                    onFocus = {},
                    requester = if (i == start) focus else null,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun LiveInfoBar(ch: Channel, index: Int, total: Int) {
    val epg = rememberLoad("row-${ch.streamId}") { AppRepo.shortEpg(ch.streamId, 4) }
    val progs = when (val s = epg.state) {
        is Load.Ok -> s.data
        else -> emptyList()
    }
    val now = System.currentTimeMillis()
    val cur = progs.firstOrNull { it.isNow(now) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC05070A), Color(0xF205070A))))
            .padding(start = 48.dp, end = 48.dp, top = 70.dp, bottom = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(ch.icon, ch.name, Modifier.size(width = 150.dp, height = 96.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ch.num > 0) {
                    Box(Modifier.background(Accent, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("${ch.num}", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(ch.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ch.favKey in AppRepo.favorites) {
                    Spacer(Modifier.width(10.dp)); Text("★", color = Gold, fontSize = 20.sp)
                }
            }
            if (cur != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cur.title, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(12.dp))
                    Text("${hm(cur.start)} – ${hm(cur.end)}", color = TextDim, fontSize = 14.sp)
                }
                LinearProgressIndicator(
                    progress = { cur.progress(now) },
                    color = Accent, trackColor = Color(0x33FFFFFF),
                    modifier = Modifier.fillMaxWidth(0.7f).height(4.dp),
                )
                progs.firstOrNull { it.start >= cur.end }?.let {
                    Text("Ensuite  ${hm(it.start)}   ${it.title}", color = TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else if (epg.state is Load.Ok) {
                Text("Pas d'information de programme", color = TextDim, fontSize = 14.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (index >= 0) Text("${index + 1} / $total", color = Color.White, fontSize = 14.sp)
            Text("▲▼ zapper   OK liste", color = TextDim, fontSize = 12.sp)
        }
    }
}

/** Film ou épisode en plein écran, avec les commandes de lecture. */
@Composable
fun VodScreen(title: String, url: String) {
    val tv = LocalTvPlayer.current
    LaunchedEffect(url) { tv.playUrl(url) }
    DisposableEffect(Unit) { onDispose { tv.stop() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(Modifier.fillMaxSize(), withControls = true)
        if (tv.buffering && tv.error == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }
        tv.error?.let {
            ErrorBox("$it\nAppuie sur Retour pour revenir.", Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun ErrorBox(text: String, modifier: Modifier) {
    Box(modifier.background(Color(0xDD1C2330), RoundedCornerShape(12.dp)).padding(24.dp)) {
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}
