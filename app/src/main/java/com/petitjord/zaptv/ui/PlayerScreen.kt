package com.petitjord.zaptv.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.petitjord.zaptv.data.AppRepo
import com.petitjord.zaptv.data.Channel
import com.petitjord.zaptv.data.USER_AGENT
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(request: PlayRequest) {
    val ctx = LocalContext.current
    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    if (request.isLive) {
        LivePlayer(request, player, buffering, error, setError = { error = it })
    } else {
        VodPlayer(request, player, error)
    }

    // Écoute des états du lecteur
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }
            override fun onPlayerError(e: PlaybackException) {
                error = "Lecture impossible (${e.errorCodeName})"
                buffering = false
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LivePlayer(
    request: PlayRequest,
    player: ExoPlayer,
    buffering: Boolean,
    error: String?,
    setError: (String?) -> Unit,
) {
    val channels = request.channels
    var index by remember { mutableIntStateOf(request.index.coerceIn(0, channels.lastIndex)) }
    var format by remember { mutableStateOf(AppRepo.liveFormat) }
    var showInfo by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    val channel = channels[index]
    val focus = rememberInitialFocus(true)

    // Lance la chaîne (et la relance si on change de format)
    LaunchedEffect(index, format) {
        setError(null)
        player.setMediaItem(MediaItem.fromUri(AppRepo.liveUrl(channel, format)))
        player.prepare()
        player.play()
    }
    // Repli automatique TS -> HLS (ou l'inverse) si la chaîne ne démarre pas
    LaunchedEffect(error) {
        if (error != null && format == AppRepo.liveFormat) {
            format = if (format == "ts") "m3u8" else "ts"
        }
    }
    // Le bandeau d'information disparaît après 5 secondes
    LaunchedEffect(showInfo, infoTick) {
        if (showInfo) { delay(5_000); showInfo = false }
    }

    fun zap(delta: Int) {
        index = (index + delta + channels.size) % channels.size
        format = AppRepo.liveFormat
        showInfo = true; infoTick++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { zap(1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Info -> {
                        showInfo = !showInfo; infoTick++; true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> { showInfo = true; infoTick++; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
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
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }
        if (error != null && format != AppRepo.liveFormat) {
            // Le repli a lui aussi échoué
            ErrorBox("$error\nEssaie une autre chaîne avec ▲ / ▼", Modifier.align(Alignment.Center))
        }
        if (showInfo) {
            LiveInfoBar(channel, index, channels.size, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LiveInfoBar(ch: Channel, index: Int, total: Int, modifier: Modifier) {
    val epg = rememberLoad("bar-${ch.streamId}") { AppRepo.shortEpg(ch.streamId, 4) }
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE05070A))))
            .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(ch.icon, ch.name, Modifier.size(width = 140.dp, height = 90.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ch.num > 0) {
                    Text("${ch.num}", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                }
                Text(ch.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ch.favKey in AppRepo.favorites) {
                    Spacer(Modifier.width(10.dp)); Text("★", color = Gold, fontSize = 20.sp)
                }
            }
            val progs = when (val s = epg.state) {
                is Load.Ok -> s.data
                else -> emptyList()
            }
            val now = System.currentTimeMillis()
            val cur = progs.firstOrNull { it.isNow(now) }
            if (cur != null) {
                Text("${hm(cur.start)} – ${hm(cur.end)}   ${cur.title}", color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { ((now - cur.start).toFloat() / (cur.end - cur.start)).coerceIn(0f, 1f) },
                    color = Accent, trackColor = Color(0x33FFFFFF),
                    modifier = Modifier.fillMaxWidth(0.6f).height(4.dp),
                )
                progs.firstOrNull { it.start >= cur.end }?.let {
                    Text("Ensuite  ${hm(it.start)}   ${it.title}", color = TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else if (epg.state is Load.Ok) {
                Text("Pas d'information de programme", color = TextDim, fontSize = 14.sp)
            }
        }
        Text("${index + 1} / $total\n▲▼ zapper · OK infos", color = TextDim, fontSize = 13.sp)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VodPlayer(request: PlayRequest, player: ExoPlayer, error: String?) {
    LaunchedEffect(request.url) {
        player.setMediaItem(MediaItem.fromUri(request.url!!))
        player.prepare()
        player.play()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    setShowSubtitleButton(true)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    this.player = player
                    post { requestFocus(); showController() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (error != null) {
            ErrorBox("$error\nAppuie sur Retour pour revenir.", Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun ErrorBox(text: String, modifier: Modifier) {
    Box(modifier.background(Color(0xDD1C2330), RoundedCornerShape(10.dp)).padding(24.dp)) {
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}
