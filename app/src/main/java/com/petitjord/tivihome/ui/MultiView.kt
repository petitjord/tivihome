package com.petitjord.tivihome.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Net

/**
 * Mosaïque : jusqu'à 4 chaînes en même temps.
 * D'abord on coche les chaînes, puis la grille s'affiche ;
 * le son suit la tuile sélectionnée, OK l'ouvre en plein écran.
 */
@Composable
fun MultiScreen(candidates: List<Channel>, nav: Nav) {
    val chosen = remember { mutableStateListOf<Channel>() }
    var started by remember { mutableStateOf(false) }
    if (!started) {
        MultiPicker(candidates, chosen) { if (chosen.isNotEmpty()) started = true }
    } else {
        MultiGrid(chosen.toList(), nav)
    }
}

@Composable
private fun MultiPicker(candidates: List<Channel>, chosen: MutableList<Channel>, onStart: () -> Unit) {
    val ctx = LocalContext.current
    val first = rememberInitialFocus(true)
    // Favoris en premier : ce sont souvent eux qu'on veut voir ensemble
    val ordered = remember(candidates) {
        candidates.sortedByDescending { it.favKey in AppRepo.favorites }
    }
    Row(Modifier.fillMaxSize().background(Bg).padding(32.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Multi-écran", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Coche jusqu'à 4 chaînes, puis lance la mosaïque.", color = TextDim, fontSize = 14.sp)
            Text("${chosen.size} / 4 sélectionnée(s)", color = Accent, fontSize = 16.sp)
            chosen.forEach { Text("• ${it.name}", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(8.dp))
            ActionButton("▦  Lancer la mosaïque", primary = chosen.isNotEmpty()) {
                if (chosen.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "Choisis au moins une chaîne", android.widget.Toast.LENGTH_SHORT).show()
                } else onStart()
            }
            Text("Astuce : la lecture de 4 flux en même temps demande une bonne connexion et un boîtier récent.", color = TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(24.dp))
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(ordered) { ch ->
                val on = ch in chosen
                FocusBox(
                    onClick = {
                        if (on) chosen.remove(ch)
                        else if (chosen.size < 4) chosen.add(ch)
                        else android.widget.Toast.makeText(ctx, "4 chaînes maximum", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    focusRequester = if (ch == ordered.firstOrNull()) first else null,
                    background = if (on) AccentDim else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (on) "☑" else "☐", color = if (on) Accent else TextDim, fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        ChannelLogo(ch.icon, ch.name, Modifier.size(width = 54.dp, height = 34.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(ch.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ch.favKey in AppRepo.favorites) Text("  ★", color = Gold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiGrid(channels: List<Channel>, nav: Nav) {
    var active by remember { mutableIntStateOf(0) }
    val rows = if (channels.size <= 2) listOf(channels.indices.toList()) else listOf(listOf(0, 1), (2 until channels.size).toList())
    Column(Modifier.fillMaxSize().background(Color.Black).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { r ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                r.forEach { i ->
                    MultiTile(
                        ch = channels[i],
                        audio = i == active,
                        first = i == 0,
                        onFocus = { active = i },
                        onOpen = { nav.playLive(channels, i) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // Ligne incomplète (3 chaînes) : case vide pour garder la grille
                if (r.size == 1 && channels.size == 3) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MultiTile(ch: Channel, audio: Boolean, first: Boolean, onFocus: () -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // Chaque tuile a son propre lecteur, plus léger que le lecteur principal
    val player = remember {
        val http = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(Net.client).setUserAgent(Net.userAgent)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(ctx, http)))
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        failed = true
                    }
                })
                setMediaItem(MediaItem.fromUri(AppRepo.liveUrl(ch)))
                prepare()
            }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(audio) { player.volume = if (audio) 1f else 0f }
    val focus = rememberInitialFocus(first)

    FocusBox(
        onClick = onOpen,
        onFocus = onFocus,
        focusRequester = if (first) focus else null,
        shape = RoundedCornerShape(6.dp),
        background = Color.Black,
        focusedBackground = Color.Black,
        modifier = modifier,
    ) { focused ->
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    isFocusable = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize().padding(3.dp),
        )
        if (buffering && !failed) CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center).size(36.dp))
        if (failed) Text("Flux indisponible", color = TextDim, modifier = Modifier.align(Alignment.Center))
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp)
                .background(Color(0xAA0B0E13), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text((if (audio) "🔊 " else "") + ch.name, color = if (focused) Color.White else TextDim, fontSize = 13.sp, maxLines = 1)
        }
    }
}
