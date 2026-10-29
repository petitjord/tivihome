package com.petitjord.tivihome.ui

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.USER_AGENT

/**
 * Lecteur unique de l'appli : la même lecture passe de l'aperçu au plein écran
 * sans être rechargée, comme sur un vrai décodeur.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class TvPlayer(ctx: Context) {

    val exo: ExoPlayer = run {
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

    /** Chaîne en cours (null pour un film / épisode ou à l'arrêt). */
    var channel by mutableStateOf<Channel?>(null)
        private set

    /** Liste dans laquelle on zappe. */
    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set

    var buffering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Vrai quand l'écran plein écran est affiché (la lecture ne doit pas s'arrêter). */
    var fullscreen = false

    private var format = "ts"
    private var fallbackTried = false

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }

            override fun onPlayerError(e: PlaybackException) {
                val ch = channel
                if (ch != null && !fallbackTried) {
                    // Repli automatique TS <-> HLS
                    fallbackTried = true
                    format = if (format == "ts") "m3u8" else "ts"
                    start(AppRepo.liveUrl(ch, format))
                } else {
                    error = "Lecture impossible (${e.errorCodeName})"
                    buffering = false
                }
            }
        })
    }

    fun playChannel(ch: Channel, list: List<Channel>) {
        channels = list
        if (ch == channel && error == null && exo.playbackState != Player.STATE_IDLE) return
        channel = ch
        format = AppRepo.liveFormat
        fallbackTried = false
        start(AppRepo.liveUrl(ch, format))
    }

    fun zap(delta: Int) {
        val list = channels
        if (list.isEmpty()) return
        val i = list.indexOf(channel).coerceAtLeast(0)
        playChannel(list[(i + delta + list.size) % list.size], list)
    }

    fun playUrl(url: String, startMs: Long = 0L) {
        channel = null
        channels = emptyList()
        fallbackTried = true
        start(url, startMs)
    }

    fun stop() {
        exo.stop()
        exo.clearMediaItems()
        channel = null
        error = null
        buffering = false
    }

    fun release() = exo.release()

    private fun start(url: String, startMs: Long = 0L) {
        error = null
        buffering = true
        exo.setMediaItem(MediaItem.fromUri(url), startMs.coerceAtLeast(0L))
        exo.prepare()
        exo.play()
    }
}

val LocalTvPlayer = staticCompositionLocalOf<TvPlayer> { error("TvPlayer absent") }

/** Surface vidéo branchée sur le lecteur partagé. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoSurface(modifier: Modifier = Modifier, withControls: Boolean = false) {
    val tv = LocalTvPlayer.current
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = withControls
                isFocusable = withControls
                isFocusableInTouchMode = withControls
                keepScreenOn = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                player = tv.exo
                if (withControls) {
                    controllerShowTimeoutMs = 4_000
                    setShowSubtitleButton(true)
                    post { requestFocus(); showController() }
                }
            }
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
