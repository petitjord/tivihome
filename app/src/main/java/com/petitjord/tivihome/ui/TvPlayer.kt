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
        // Même client réseau que l'API : tolère les certificats des serveurs IPTV enregistrés
        val http = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(com.petitjord.tivihome.data.Net.client)
            .setUserAgent(USER_AGENT)
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
    /** Film / épisode arrivé à la fin. */
    var ended by mutableStateOf(false)
        private set

    /** Vrai quand l'écran plein écran est affiché (la lecture ne doit pas s'arrêter). */
    var fullscreen = false

    private var format = "ts"
    private var fallbackTried = false
    private var retries = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentUrl: String? = null

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED && channel == null
                if (state == Player.STATE_READY) { error = null; retries = 0 }
                // Un direct qui « se termine » est en fait une coupure : on relance.
                if (state == Player.STATE_ENDED && channel != null) reconnect()
            }

            override fun onPlayerError(e: PlaybackException) {
                val ch = channel
                if (ch != null && !fallbackTried) {
                    // Repli automatique TS <-> HLS
                    fallbackTried = true
                    format = if (format == "ts") "m3u8" else "ts"
                    start(AppRepo.liveUrl(ch, format))
                } else if (ch != null && retries < 3) {
                    reconnect()
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
        retries = 0
        AppRepo.rememberChannel(ch.streamId)
        start(AppRepo.liveUrl(ch, format))
    }

    /** Reconnexion automatique après une coupure (3 essais espacés). */
    private fun reconnect() {
        val ch = channel ?: return
        retries++
        buffering = true
        handler.postDelayed({
            if (channel == ch) start(AppRepo.liveUrl(ch, format))
        }, 1_500L * retries)
    }

    /** Saute à la chaîne portant ce numéro dans la liste en cours (ou toutes les chaînes). */
    fun jumpToNumber(num: Int, all: List<Channel>): Boolean {
        val inList = channels.firstOrNull { it.num == num }
        if (inList != null) { playChannel(inList, channels); return true }
        val any = all.firstOrNull { it.num == num } ?: return false
        playChannel(any, all)
        return true
    }

    // ---------- Pistes audio / sous-titres ----------

    data class Track(val label: String, val group: androidx.media3.common.TrackGroup, val index: Int, val selected: Boolean)

    fun tracks(type: Int): List<Track> {
        val out = mutableListOf<Track>()
        exo.currentTracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val lang = f.language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.FRANCE).replaceFirstChar { c -> c.uppercase() } }
                val label = listOfNotNull(f.label, lang, f.channelCount.takeIf { it > 0 && type == androidx.media3.common.C.TRACK_TYPE_AUDIO }?.let { "$it can." })
                    .distinct().joinToString(" · ").ifBlank { "Piste ${out.size + 1}" }
                out += Track(label, g.mediaTrackGroup, i, g.isTrackSelected(i))
            }
        }
        return out
    }

    fun selectTrack(type: Int, t: Track?) {
        val b = exo.trackSelectionParameters.buildUpon()
        if (t == null) {
            b.setTrackTypeDisabled(type, true)
        } else {
            b.setTrackTypeDisabled(type, false)
            b.setOverrideForType(androidx.media3.common.TrackSelectionOverride(t.group, t.index))
        }
        exo.trackSelectionParameters = b.build()
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
        handler.removeCallbacksAndMessages(null)
        exo.stop()
        exo.clearMediaItems()
        channel = null
        error = null
        buffering = false
    }

    fun release() = exo.release()

    private fun start(url: String, startMs: Long = 0L) {
        currentUrl = url
        ended = false
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
                subtitleView?.setFractionalTextSize(
                    when (AppRepo.subtitleSize) { 0 -> 0.045f; 2 -> 0.075f; else -> 0.0533f },
                )
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
