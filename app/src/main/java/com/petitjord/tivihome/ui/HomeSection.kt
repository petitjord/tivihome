package com.petitjord.tivihome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Movie
import com.petitjord.tivihome.data.Programme
import com.petitjord.tivihome.data.Series
import com.petitjord.tivihome.data.VodItem
import kotlinx.coroutines.delay
import java.util.Calendar

private data class HomeData(val channels: List<Channel>, val movies: List<Movie>, val series: List<Series>)

/** Page d'accueil : tout ce qui compte pour toi, en un coup d'œil. */
@Composable
fun HomeSection(nav: Nav, focusContent: Boolean) {
    val data = rememberLoad("home") {
        val ch = AppRepo.liveStreams()
        val mv = runCatching { AppRepo.vodStreams() }.getOrDefault(emptyList())
        val se = runCatching { AppRepo.seriesList() }.getOrDefault(emptyList())
        HomeData(ch, mv, se)
    }
    LoadView(data) { d -> HomeContent(d, nav, focusContent) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeContent(d: HomeData, nav: Nav, focusContent: Boolean) {
    val ctx = LocalContext.current
    val favs = AppRepo.favorites
    val resume = AppRepo.resume
    val reminders = AppRepo.reminders
    val first = rememberInitialFocus(focusContent)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val favChannels = remember(d, favs) { d.channels.filter { it.favKey in favs } }
    val lastChannel = remember(d) { d.channels.firstOrNull { it.streamId == AppRepo.lastChannelId } }
    val liveRow = remember(favChannels, lastChannel) {
        (listOfNotNull(lastChannel) + favChannels).distinct().take(20)
    }
    val recentMovies = remember(d) { d.movies.sortedByDescending { it.added }.take(20) }
    val recentSeries = remember(d) { d.series.sortedByDescending { it.added }.take(20) }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    // Le premier rang affiché reçoit le focus initial
    var firstAssigned = false
    fun takeFirst(): FocusRequester? = if (!firstAssigned) { firstAssigned = true; first } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 20.dp, top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    if (hour in 5..17) "Bonjour" else "Bonsoir",
                    color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(hm(now), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
        }

        val upcoming = reminders.filter { it.start > now - 5 * 60_000 }.sortedBy { it.start }
        if (upcoming.isNotEmpty()) {
            item { SectionHeader("Tes rappels", "${upcoming.size}") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp), modifier = Modifier.focusRestorer()) {
                    items(upcoming) { r ->
                        val ch = d.channels.firstOrNull { it.streamId == r.streamId }
                        FocusBox(
                            onClick = { if (ch != null) nav.playLive(listOf(ch), 0) },
                            onLongClick = {
                                AppRepo.dropReminder(r)
                                android.widget.Toast.makeText(ctx, "Rappel supprimé", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            background = Panel,
                            modifier = Modifier.width(260.dp).height(84.dp),
                        ) {
                            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
                                Text("${hm(r.start)} · ${r.channelName}", color = Accent, fontSize = 13.sp, maxLines = 1)
                                Text(r.title, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (resume.isNotEmpty()) {
            item { SectionHeader("Continuer à regarder", "${resume.size}") }
            item {
                val req = takeFirst()
                LazyRow(Modifier.height(166.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    items(resume) { r ->
                        ResumeCard(
                            entry = r,
                            onClick = { nav.playVod(VodItem(r.key, r.title, r.url, r.image)) },
                            onLongClick = { AppRepo.forgetProgress(r.key) },
                            focusRequester = if (r == resume.first()) req else null,
                        )
                    }
                }
            }
        }

        if (liveRow.isNotEmpty()) {
            item { SectionHeader(if (favChannels.isEmpty()) "Ta dernière chaîne" else "En ce moment sur tes chaînes", null) }
            item {
                val req = takeFirst()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp), modifier = Modifier.focusRestorer()) {
                    itemsIndexed(liveRow) { i, ch ->
                        LiveCard(ch, now, if (i == 0) req else null) { nav.playLive(liveRow, i) }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Astuce : maintiens OK sur une chaîne pour l'ajouter aux favoris, elle apparaîtra ici avec son programme en cours.",
                    color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        if (recentMovies.isNotEmpty() && recentMovies.first().added > 0) {
            item { SectionHeader("Films ajoutés récemment", null) }
            item {
                val req = takeFirst()
                LazyRow(Modifier.height(214.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                    itemsIndexed(recentMovies) { i, m ->
                        Poster(
                            m.name, m.poster, m.favKey in favs,
                            onClick = { nav.openMovie(m) },
                            onLongClick = { toggleFav(ctx, m.favKey, m.name) },
                            focusRequester = if (i == 0) req else null,
                            modifier = Modifier.width(134.dp),
                        )
                    }
                }
            }
        }

        if (recentSeries.isNotEmpty() && recentSeries.first().added > 0) {
            item { SectionHeader("Séries mises à jour", null) }
            item {
                val req = takeFirst()
                LazyRow(Modifier.height(214.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                    itemsIndexed(recentSeries) { i, s ->
                        Poster(
                            s.name, s.cover, s.favKey in favs,
                            onClick = { nav.openSeries(s) },
                            onLongClick = { toggleFav(ctx, s.favKey, s.name) },
                            focusRequester = if (i == 0) req else null,
                            modifier = Modifier.width(134.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Carte de chaîne avec son programme en cours. */
@Composable
private fun LiveCard(ch: Channel, now: Long, requester: FocusRequester?, onClick: () -> Unit) {
    val epg = rememberLoad("row-${ch.streamId}") { AppRepo.shortEpg(ch, 4) }
    val cur: Programme? = (epg.state as? Load.Ok<List<Programme>>)?.data?.firstOrNull { it.isNow(now) }
    FocusBox(
        onClick = onClick,
        focusRequester = requester,
        background = Panel,
        scaleOnFocus = 1.05f,
        modifier = Modifier.width(240.dp).height(128.dp),
    ) { focused ->
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(ch.icon, ch.name, Modifier.size(width = 64.dp, height = 40.dp))
                Spacer(Modifier.width(10.dp))
                Text(ch.name, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Text(cur?.title ?: "Pas de programme", color = if (cur != null) Color(0xFFD7DCE5) else TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (cur != null) {
                LinearProgressIndicator(
                    progress = { cur.progress(now) },
                    color = if (focused) Accent else Color(0xFF5B6B82),
                    trackColor = Color(0x22FFFFFF),
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            }
        }
    }
}
