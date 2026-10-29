package com.petitjord.tivihome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Movie
import com.petitjord.tivihome.data.Series
import com.petitjord.tivihome.data.VodItem

private data class FavData(val channels: List<Channel>, val movies: List<Movie>, val series: List<Series>)

@Composable
fun FavoritesSection(nav: Nav, focusContent: Boolean) {
    val favs = AppRepo.favorites
    // Ne charge les films / séries que si des favoris de ce type existent.
    val data = rememberLoad("favs") {
        val f = AppRepo.favorites
        FavData(
            channels = if (f.any { it.startsWith("live:") }) AppRepo.liveStreams() else emptyList(),
            movies = if (f.any { it.startsWith("movie:") }) AppRepo.vodStreams() else emptyList(),
            series = if (f.any { it.startsWith("series:") }) AppRepo.seriesList() else emptyList(),
        )
    }
    LoadView(data) { all -> FavContent(all, favs, nav, focusContent) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FavContent(all: FavData, favs: Set<String>, nav: Nav, focusContent: Boolean) {
    val ctx = LocalContext.current
    val channels = remember(all, favs) { all.channels.filter { it.favKey in favs } }
    val movies = remember(all, favs) { all.movies.filter { it.favKey in favs } }
    val series = remember(all, favs) { all.series.filter { it.favKey in favs } }
    val restore = rememberInitialFocus(focusContent)

    val resume = AppRepo.resume
    if (channels.isEmpty() && movies.isEmpty() && series.isEmpty() && resume.isEmpty()) {
        EmptyText("Pas encore de favoris.\nMaintiens le bouton OK sur une chaîne, un film ou une série pour l'ajouter ici.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).focusRestorer(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (resume.isNotEmpty()) {
            item { SectionHeader("Continuer à regarder", "${resume.size}") }
            item {
                LazyRow(Modifier.height(170.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    items(resume) { r ->
                        ResumeCard(
                            entry = r,
                            onClick = { nav.playVod(VodItem(r.key, r.title, r.url, r.image)) },
                            onLongClick = {
                                AppRepo.forgetProgress(r.key)
                                android.widget.Toast.makeText(ctx, "Retiré de « Continuer à regarder »", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            focusRequester = if (r == resume.first()) restore else null,
                        )
                    }
                }
            }
        }
        if (channels.isNotEmpty()) {
            item { SectionHeader("Chaînes", "${channels.size}") }
            itemsIndexed(channels) { i, ch ->
                ChannelRow(
                    ch = ch, favorite = true,
                    onClick = { nav.playLive(channels, i) },
                    onLongClick = { toggleFav(ctx, ch.favKey, ch.name) },
                    onFocus = {},
                    requester = if (i == 0 && resume.isEmpty()) restore else null,
                )
            }
        }
        if (movies.isNotEmpty()) {
            item { SectionHeader("Films", "${movies.size}") }
            item {
                LazyRow(Modifier.height(230.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    items(movies) { m ->
                        Poster(
                            m.name, m.poster, true,
                            onClick = { nav.playVod(movieItem(m)) },
                            onLongClick = { toggleFav(ctx, m.favKey, m.name) },
                            focusRequester = if (resume.isEmpty() && channels.isEmpty() && m == movies.first()) restore else null,
                            modifier = Modifier.width(144.dp),
                        )
                    }
                }
            }
        }
        if (series.isNotEmpty()) {
            item { SectionHeader("Séries", "${series.size}") }
            item {
                LazyRow(Modifier.height(230.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    items(series) { s ->
                        Poster(
                            s.name, s.cover, true,
                            onClick = { nav.openSeries(s) },
                            onLongClick = { toggleFav(ctx, s.favKey, s.name) },
                            focusRequester = if (resume.isEmpty() && channels.isEmpty() && movies.isEmpty() && s == series.first()) restore else null,
                            modifier = Modifier.width(144.dp),
                        )
                    }
                }
            }
        }
    }
}
