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
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Series

sealed interface Screen {
    data object Home : Screen
    data object LiveFull : Screen
    data class Vod(val title: String, val url: String) : Screen
    data class SeriesDetail(val series: Series) : Screen
}

/** Actions de navigation partagées par tous les écrans. */
class Nav(
    val playLive: (list: List<Channel>, index: Int) -> Unit,
    val playVod: (title: String, url: String) -> Unit,
    val openSeries: (Series) -> Unit,
    val back: () -> Unit,
    val logout: () -> Unit,
)

@Composable
fun App() {
    var loggedIn by remember { mutableStateOf(AppRepo.client != null) }
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (!loggedIn) {
            LoginScreen(onLoggedIn = { loggedIn = true })
        } else {
            MainNavigation(onLogout = { loggedIn = false })
        }
    }
}

@Composable
private fun MainNavigation(onLogout: () -> Unit) {
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
            playVod = { title, url ->
                tv.fullscreen = true
                stack.add(Screen.Vod(title, url))
            },
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
        )
        self
    }

    BackHandler(enabled = stack.size > 1) { nav.back() }

    CompositionLocalProvider(LocalTvPlayer provides tv) {
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
                is Screen.Vod -> VodScreen(top.title, top.url)
            }
        }
    }
}
