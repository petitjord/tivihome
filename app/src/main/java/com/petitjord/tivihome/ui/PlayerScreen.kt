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
import com.petitjord.tivihome.data.VodItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var tracksOpen by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf("") }
    var okLong by remember { mutableStateOf(false) }
    var okDown by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val allChannels = rememberLoad("all-channels") { AppRepo.liveStreams() }
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
    LaunchedEffect(listOpen, tracksOpen) {
        if (!listOpen && !tracksOpen) { delay(30); runCatching { rootFocus.requestFocus() } }
    }
    // Saisie d'un numéro : on valide 1,5 s après le dernier chiffre.
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1_500)
        val all = (allChannels.state as? Load.Ok<List<Channel>>)?.data ?: emptyList()
        if (!tv.jumpToNumber(digits.toInt(), all)) {
            android.widget.Toast.makeText(ctx, "Pas de chaîne n° $digits", android.widget.Toast.LENGTH_SHORT).show()
        }
        digits = ""
    }
    // Nouveau programme / nouvelle chaîne : on montre le bandeau
    LaunchedEffect(tv.channel) { infoVisible = true; activity++ }

    BackHandler {
        when {
            tracksOpen -> tracksOpen = false
            listOpen -> listOpen = false
            digits.isNotEmpty() -> digits = ""
            else -> nav.back()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { activity++; false }
            .focusRequester(rootFocus)
            .onKeyEvent { ev ->
                if (listOpen || tracksOpen) return@onKeyEvent false
                val isOk = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                if (isOk) {
                    // OK court : liste des chaînes. OK maintenu : pistes audio / sous-titres.
                    if (ev.type == KeyEventType.KeyDown) {
                        if (ev.nativeKeyEvent.repeatCount == 0) { okDown = true; okLong = false }
                        if (ev.nativeKeyEvent.repeatCount == 1) { okLong = true; tracksOpen = true; infoVisible = false }
                    } else if (ev.type == KeyEventType.KeyUp) {
                        if (okDown && !okLong) { listOpen = true; infoVisible = false }
                        okDown = false
                    }
                    return@onKeyEvent true
                }
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = digitOf(ev.key)
                if (digit != null) {
                    if (digit == 0 && digits.isEmpty()) {
                        // 0 seul : retour à la chaîne précédente
                        val prev = AppRepo.previousChannelId
                        val all = (allChannels.state as? Load.Ok<List<Channel>>)?.data ?: emptyList()
                        val target = tv.channels.firstOrNull { it.streamId == prev } ?: all.firstOrNull { it.streamId == prev }
                        if (target != null) tv.playChannel(target, if (target in tv.channels) tv.channels else all)
                    } else if (digits.length < 4) {
                        digits += digit
                    }
                    return@onKeyEvent true
                }
                when (ev.key) {
                    Key.MediaPlayPause, Key.MediaPause, Key.MediaPlay, Key.Spacebar -> {
                        if (tv.pausedAt != null) scope.launch { tv.resumeLive() } else tv.pauseLive()
                        true
                    }
                    Key.MediaFastForward, Key.MediaStop -> { if (tv.delayMs > 0 || tv.pausedAt != null) tv.goLive(); true }
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { tv.zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { tv.zap(1); true }
                    Key.DirectionLeft, Key.Menu -> { listOpen = true; infoVisible = false; true }
                    Key.DirectionRight, Key.Info -> { infoVisible = !infoVisible; true }
                    Key.MediaAudioTrack, Key.Captions -> { tracksOpen = true; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        VideoSurface(Modifier.fillMaxSize())
        WatchClock(Modifier.align(Alignment.TopStart))

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

        // Pause / différé
        tv.pausedAt?.let { since ->
            var tick by remember { mutableIntStateOf(0) }
            LaunchedEffect(since) { while (true) { delay(1_000); tick++ } }
            val secs = ((System.currentTimeMillis() - since) / 1000).toInt() + tick * 0
            Box(Modifier.align(Alignment.Center).background(Color(0xCC0B0E13), RoundedCornerShape(16.dp)).padding(horizontal = 28.dp, vertical = 18.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏸  En pause", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("depuis ${secs / 60} min ${"%02d".format(secs % 60)}", color = TextDim, fontSize = 14.sp)
                    Text(
                        if ((tv.channel?.archiveDays ?: 0) > 0) "▶ reprend là où tu t'es arrêté" else "▶ reprend (cette chaîne n'a pas de replay)",
                        color = TextDim, fontSize = 12.sp,
                    )
                }
            }
        }
        if (tv.delayMs > 0 && tv.pausedAt == null) {
            Box(Modifier.align(Alignment.TopCenter).padding(28.dp).background(Color(0xDD0B0E13), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Différé −${tv.delayMs / 60_000} min  ·  ⏩ ou ■ : retour au direct", color = Gold, fontSize = 14.sp)
            }
        }

        // Numéro en cours de saisie
        if (digits.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(32.dp)
                    .background(Color(0xDD0B0E13), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(digits, color = Accent, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = tracksOpen,
            enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            TracksPanel(onClose = { tracksOpen = false })
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
    val epg = rememberLoad("row-${ch.streamId}") { AppRepo.shortEpg(ch, 4) }
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
        ChannelLogo(ch.icon, ch.name, Modifier.size(width = 170.dp, height = 108.dp))
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
                Spacer(Modifier.width(14.dp))
                Row(
                    Modifier.background(Color(0x33FF4D5E), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveDot(7.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("EN DIRECT", color = Color(0xFFFF8A95), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
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
            Text(hm(System.currentTimeMillis()), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            if (index >= 0) Text("${index + 1} / $total", color = TextDim, fontSize = 13.sp)
            Text("▲▼ zapper · OK liste · OK maintenu pistes · 0 précédente", color = TextDim, fontSize = 11.sp)
        }
    }
}

/** Film, épisode ou replay en plein écran : reprise, épisode suivant automatique, passer l'intro. */
@Composable
fun VodScreen(queue: List<VodItem>, startIndex: Int) {
    val tv = LocalTvPlayer.current
    val ctx = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, queue.lastIndex)) }
    val item = queue[index]
    val next = queue.getOrNull(index + 1)
    var countdown by remember { mutableIntStateOf(-1) }
    var position by remember { mutableStateOf(0L) }
    val isEpisode = item.key?.startsWith("episode:") == true

    LaunchedEffect(item.url) {
        countdown = -1
        val start = item.key?.let { AppRepo.resumePosition(it) } ?: 0L
        tv.playUrl(item.url, start)
        if (start > 0) {
            android.widget.Toast.makeText(ctx, "Reprise à ${formatDuration(start)}", android.widget.Toast.LENGTH_SHORT).show()
        }
        var ticks = 0
        while (true) {
            delay(1_000)
            position = tv.exo.currentPosition
            if (++ticks % 10 == 0 && tv.exo.isPlaying) AppRepo.saveProgress(item, tv.exo.currentPosition, tv.exo.duration)
        }
    }
    // Fin de l'épisode : compte à rebours puis épisode suivant
    LaunchedEffect(tv.ended, index) {
        if (!tv.ended) return@LaunchedEffect
        AppRepo.saveProgress(item, tv.exo.duration, tv.exo.duration)
        if (next == null) return@LaunchedEffect
        for (c in 8 downTo 1) { countdown = c; delay(1_000) }
        countdown = -1
        index++
    }
    DisposableEffect(Unit) {
        onDispose {
            AppRepo.saveProgress(queue[index], tv.exo.currentPosition, tv.exo.duration)
            tv.stop()
        }
    }
    val showSkip = isEpisode && position in 10_000L..240_000L

    Box(
        Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                // Pendant le compte à rebours : OK lance tout de suite l'épisode suivant
                countdown > 0 && (ev.key == Key.DirectionCenter || ev.key == Key.Enter) -> { countdown = -1; index++; true }
                showSkip && (ev.key == Key.MediaFastForward || ev.key == Key.Menu) -> {
                    tv.exo.seekTo(tv.exo.currentPosition + 85_000); true
                }
                next != null && ev.key == Key.MediaNext -> { index++; true }
                else -> false
            }
        },
    ) {
        VideoSurface(Modifier.fillMaxSize(), withControls = true)
        WatchClock(Modifier.align(Alignment.TopStart))
        if (tv.buffering && tv.error == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }
        tv.error?.let {
            ErrorBox("$it\nAppuie sur Retour pour revenir.", Modifier.align(Alignment.Center))
        }
        if (showSkip) {
            Pill("⏩  Passer l'intro : touche Menu ou ⏩", Modifier.align(Alignment.TopEnd).padding(28.dp))
        }
        if (countdown > 0 && next != null) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(36.dp)
                    .background(Color(0xEE0B0E13), RoundedCornerShape(14.dp)).padding(22.dp),
            ) {
                Text("Épisode suivant dans $countdown s", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(next.title, color = Color.White, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(360.dp))
                Text("OK pour lancer maintenant · Retour pour quitter", color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier) {
    Box(modifier.background(Color(0xDD0B0E13), RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d h %02d".format(h, m) else "%d min %02d".format(m, s)
}

@Composable
private fun ErrorBox(text: String, modifier: Modifier) {
    Box(modifier.background(Color(0xDD1C2330), RoundedCornerShape(12.dp)).padding(24.dp)) {
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

private fun digitOf(k: Key): Int? = when (k) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

/** Choix des pistes audio et sous-titres, par-dessus la vidéo. */
@Composable
fun TracksPanel(onClose: () -> Unit) {
    val tv = LocalTvPlayer.current
    var tick by remember { mutableIntStateOf(0) }
    val audio = remember(tick) { tv.tracks(androidx.media3.common.C.TRACK_TYPE_AUDIO) }
    val subs = remember(tick) { tv.tracks(androidx.media3.common.C.TRACK_TYPE_TEXT) }
    val first = rememberInitialFocus(true)

    Column(
        Modifier.width(380.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0x000B0E13), Color(0xE60B0E13), Color(0xF20B0E13))))
            .padding(start = 40.dp, top = 28.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Audio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (audio.isEmpty()) Text("Une seule piste", color = TextDim, fontSize = 14.sp)
        audio.forEachIndexed { i, t ->
            TrackRow(t.label, t.selected, if (i == 0) first else null) {
                tv.selectTrack(androidx.media3.common.C.TRACK_TYPE_AUDIO, t); tick++
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Sous-titres", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        TrackRow("Désactivés", subs.none { it.selected }, if (audio.isEmpty()) first else null) {
            tv.selectTrack(androidx.media3.common.C.TRACK_TYPE_TEXT, null); tick++
        }
        subs.forEach { t ->
            TrackRow(t.label, t.selected, null) {
                tv.selectTrack(androidx.media3.common.C.TRACK_TYPE_TEXT, t); tick++
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Retour pour fermer", color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, requester: FocusRequester?, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        focusRequester = requester,
        background = if (selected) AccentDim else Color.Transparent,
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected) "●" else "○", color = if (selected) Accent else TextDim, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Heure affichée en permanence en haut à gauche pendant la lecture. */
@Composable
fun WatchClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            // Se réveille pile au changement de minute
            delay(60_000 - now % 60_000)
        }
    }
    Box(
        modifier.padding(start = 32.dp, top = 22.dp)
            .background(Color(0x990B0E13), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(hm(now), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
