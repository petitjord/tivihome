package com.petitjord.zaptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.petitjord.zaptv.data.AppRepo
import com.petitjord.zaptv.data.Channel
import com.petitjord.zaptv.data.Series

/** Ce que le lecteur doit jouer : une liste de chaînes (zapping) ou une vidéo. */
data class PlayRequest(
    val title: String,
    val url: String? = null,
    val channels: List<Channel> = emptyList(),
    val index: Int = 0,
) {
    val isLive get() = channels.isNotEmpty()
}

sealed interface Screen {
    data object Home : Screen
    data class Player(val request: PlayRequest) : Screen
    data class SeriesDetail(val series: Series) : Screen
}

/** Actions de navigation partagées par tous les écrans. */
class Nav(
    val play: (PlayRequest) -> Unit,
    val openSeries: (Series) -> Unit,
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
    run {
        val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
        // Vrai quand on revient d'un écran secondaire : le focus revient dans le contenu.
        var returning by remember { mutableStateOf(false) }
        val holder = rememberSaveableStateHolder()

        val nav = remember {
            Nav(
                play = { stack.add(Screen.Player(it)) },
                openSeries = { stack.add(Screen.SeriesDetail(it)) },
                logout = {
                    AppRepo.logout()
                    onLogout()
                },
            )
        }

        BackHandler(enabled = stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            returning = true
        }

        when (val top = stack.last()) {
            is Screen.Home -> holder.SaveableStateProvider("home") {
                HomeScreen(nav = nav, focusContent = returning)
            }
            is Screen.SeriesDetail -> holder.SaveableStateProvider("series-${top.series.seriesId}") {
                SeriesDetailScreen(series = top.series, nav = nav, focusContent = returning)
            }
            is Screen.Player -> PlayerScreen(request = top.request)
        }
    }
}
