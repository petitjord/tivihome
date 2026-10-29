package com.petitjord.tivihome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Series
import com.petitjord.tivihome.data.VodItem

sealed interface Screen {
    data object Home : Screen
    data object LiveFull : Screen
    data class Vod(val queue: List<VodItem>, val index: Int) : Screen
    data class MovieDetail(val movie: com.petitjord.tivihome.data.Movie) : Screen
    data class SeriesDetail(val series: Series) : Screen
}

/** Actions de navigation partagées par tous les écrans. */
class Nav(
    val playLive: (list: List<Channel>, index: Int) -> Unit,
    val playVod: (VodItem) -> Unit,
    val playQueue: (List<VodItem>, Int) -> Unit,
    val openMovie: (com.petitjord.tivihome.data.Movie) -> Unit,
    val openSeries: (Series) -> Unit,
    val back: () -> Unit,
    val logout: () -> Unit,
    val switchAccount: (com.petitjord.tivihome.data.Account) -> Unit,
)

@Composable
fun App() {
    var loggedIn by remember { mutableStateOf(AppRepo.client != null) }
    var session by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (!loggedIn) {
            LoginScreen(onLoggedIn = { loggedIn = true })
        } else {
            // Changer de compte recrée toute la navigation
            androidx.compose.runtime.key(session) {
                MainNavigation(onLogout = { loggedIn = false }, onReload = { session++ })
            }
        }
    }
}

@Composable
private fun MainNavigation(onLogout: () -> Unit, onReload: () -> Unit) {
    val ctx = LocalContext.current
    val tv = remember { TvPlayer(ctx.applicationContext) }
    DisposableEffect(Unit) { onDispose { tv.release() } }

    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    // Vrai quand on revient d'un écran secondaire : le focus revient dans le contenu.
    var returning by remember { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()

    val nav = remember {
        lateinit var self: Nav
        self = Nav(
            playLive = { list, i ->
                tv.playChannel(list[i], list)
                tv.fullscreen = true
                stack.add(Screen.LiveFull)
            },
            playVod = { item ->
                tv.fullscreen = true
                stack.add(Screen.Vod(listOf(item), 0))
            },
            playQueue = { queue, i ->
                tv.fullscreen = true
                stack.add(Screen.Vod(queue, i))
            },
            openMovie = { stack.add(Screen.MovieDetail(it)) },
            openSeries = { stack.add(Screen.SeriesDetail(it)) },
            back = {
                if (stack.size > 1) {
                    val top = stack.removeAt(stack.lastIndex)
                    if (top is Screen.LiveFull || top is Screen.Vod) tv.fullscreen = false
                    returning = true
                }
            },
            logout = {
                tv.stop()
                AppRepo.logout()
                onLogout()
            },
            switchAccount = { acc ->
                tv.stop()
                AppRepo.switchAccount(acc)
                onReload()
            },
        )
        self
    }

    // Rappel à l'écran : prioritaire sur le reste pour la touche Retour
    var reminderShown by remember { mutableStateOf<com.petitjord.tivihome.data.Reminder?>(null) }
    var idle by remember { mutableStateOf(false) }
    var lastInput by remember { mutableStateOf(System.currentTimeMillis()) }
    var pendingReminderChannel by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = stack.size > 1) { nav.back() }
    BackHandler(enabled = reminderShown != null) { reminderShown = null }

    LaunchedEffect(Unit) {
        // Guide XMLTV externe : chargé en arrière-plan au démarrage
        launch { AppRepo.loadXmltv() }
        // Démarrage direct sur la dernière chaîne
        if (AppRepo.startOnLastChannel && AppRepo.lastChannelId >= 0) {
            runCatching {
                val all = AppRepo.liveStreams()
                val ch = all.firstOrNull { it.streamId == AppRepo.lastChannelId }
                if (ch != null) {
                    val group = all.filter { it.categoryId == ch.categoryId }
                    nav.playLive(group, group.indexOf(ch))
                }
            }
        }
    }
    // Surveille les rappels et l'inactivité
    LaunchedEffect(Unit) {
        val notified = mutableSetOf<Pair<Int, Long>>()
        while (true) {
            val now = System.currentTimeMillis()
            AppRepo.reminders.firstOrNull { r ->
                r.start - now in -60_000L..120_000L && (r.streamId to r.start) !in notified
            }?.let { r ->
                notified += r.streamId to r.start
                reminderShown = r
            }
            val watching = stack.last() is Screen.LiveFull || stack.last() is Screen.Vod
            idle = !watching && now - lastInput > 5 * 60_000L
            delay(10_000)
        }
    }

    CompositionLocalProvider(LocalTvPlayer provides tv) {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent {
                lastInput = System.currentTimeMillis()
                if (idle) { idle = false; true } else false
            },
        ) {
            AnimatedContent(
                targetState = stack.last(),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "screen",
            ) { top ->
                when (top) {
                    is Screen.Home -> holder.SaveableStateProvider("home") {
                        HomeScreen(nav = nav, focusContent = returning)
                    }
                    is Screen.SeriesDetail -> holder.SaveableStateProvider("series-${top.series.seriesId}") {
                        SeriesDetailScreen(series = top.series, nav = nav)
                    }
                    is Screen.LiveFull -> LiveFullScreen(nav)
                    is Screen.Vod -> VodScreen(top.queue, top.index)
                    is Screen.MovieDetail -> holder.SaveableStateProvider("movie-${top.movie.streamId}") {
                        MovieDetailScreen(top.movie, nav)
                    }
                }
            }

            reminderShown?.let { r ->
                ReminderBanner(
                    r,
                    onWatch = {
                        reminderShown = null
                        AppRepo.dropReminder(r)
                        val ch = (tv.channels.firstOrNull { it.streamId == r.streamId })
                        if (ch != null) {
                            tv.playChannel(ch, tv.channels)
                            if (stack.last() !is Screen.LiveFull) { tv.fullscreen = true; stack.add(Screen.LiveFull) }
                        } else {
                            pendingReminderChannel = r.streamId
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }

            if (idle) ScreenSaver()
        }
    }

    // Chaîne d'un rappel pas encore chargée : on la cherche puis on l'ouvre
    LaunchedEffect(pendingReminderChannel) {
        val id = pendingReminderChannel ?: return@LaunchedEffect
        pendingReminderChannel = null
        runCatching {
            val all = AppRepo.liveStreams()
            val ch = all.firstOrNull { it.streamId == id } ?: return@runCatching
            val group = all.filter { it.categoryId == ch.categoryId }
            if (stack.last() is Screen.LiveFull) tv.playChannel(ch, group) else nav.playLive(group, group.indexOf(ch))
        }
    }
}

@Composable
private fun ReminderBanner(r: com.petitjord.tivihome.data.Reminder, onWatch: () -> Unit, modifier: Modifier) {
    val focus = rememberInitialFocus(true, r)
    Box(modifier.padding(28.dp)) {
        FocusBox(
            onClick = onWatch,
            focusRequester = focus,
            background = Color(0xF0141922),
            focusedBackground = Color(0xF01C2330),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.width(380.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🔔  Ça commence à ${hm(r.start)}", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(r.title, color = Color.White, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${r.channelName} · OK pour regarder · Retour pour ignorer", color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

/** Économiseur d'écran : l'heure qui se déplace doucement sur fond noir. */
@Composable
private fun ScreenSaver() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var pos by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(20_000); now = System.currentTimeMillis(); pos = (pos + 1) % 4 }
    }
    val align = when (pos) { 0 -> Alignment.TopStart; 1 -> Alignment.TopEnd; 2 -> Alignment.BottomEnd; else -> Alignment.BottomStart }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.align(align).padding(60.dp)) {
            Text(hm(now), color = Color(0xCCFFFFFF), fontSize = 80.sp, fontWeight = FontWeight.Thin)
            Text(
                java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.FRANCE).format(java.util.Date(now)).replaceFirstChar { it.uppercase() },
                color = TextDim, fontSize = 20.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp)) {
                LogoMark(20.dp)
                Spacer(Modifier.width(8.dp))
                Text("Tivihome", color = TextDim, fontSize = 14.sp)
            }
        }
    }
}
