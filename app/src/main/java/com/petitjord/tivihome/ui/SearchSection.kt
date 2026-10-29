package com.petitjord.tivihome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Movie
import com.petitjord.tivihome.data.Series
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.Locale

private data class SearchData(val channels: List<Channel>, val movies: List<Movie>, val series: List<Series>)

/** Minuscules sans accents, pour une recherche tolérante. */
fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@Composable
fun SearchSection(nav: Nav) {
    val data = rememberLoad("search") {
        SearchData(AppRepo.liveStreams(), AppRepo.vodStreams(), AppRepo.seriesList())
    }
    LoadView(data) { d -> SearchContent(d, nav) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchContent(d: SearchData, nav: Nav) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var q by remember { mutableStateOf(query) }
    LaunchedEffect(query) { delay(250); q = query }
    val field = rememberInitialFocus(true)
    val favs = AppRepo.favorites

    val words = remember(q) { fold(q).split(' ').filter { it.isNotBlank() } }
    fun match(name: String): Boolean {
        if (words.isEmpty()) return false
        val n = fold(name)
        return words.all { it in n }
    }
    val channels = remember(words, d) { d.channels.filter { match(it.name) }.take(60) }
    val movies = remember(words, d) { d.movies.filter { match(it.name) }.take(60) }
    val series = remember(words, d) { d.series.filter { match(it.name) }.take(60) }

    Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 22.dp, end = 24.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Chaîne, film, série…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { q = query; focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().focusRequester(field),
        )
        Spacer(Modifier.height(12.dp))

        if (words.isEmpty()) {
            EmptyText("Tape quelques lettres : la recherche couvre les chaînes, les films et les séries.\nLes accents et majuscules sont ignorés.")
        } else if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
            EmptyText("Aucun résultat pour « $q ».")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (channels.isNotEmpty()) {
                    item { SectionHeader("Chaînes", "${channels.size}") }
                    item {
                        LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                            itemsIndexed(channels) { i, ch ->
                                FocusBox(
                                    onClick = { nav.playLive(channels, i) },
                                    onLongClick = { toggleFav(ctx, ch.favKey, ch.name) },
                                    background = Panel,
                                    scaleOnFocus = 1.06f,
                                    modifier = Modifier.width(150.dp).height(120.dp),
                                ) {
                                    Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        ChannelLogo(ch.icon, ch.name, Modifier.size(width = 96.dp, height = 56.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text(ch.name, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
                if (movies.isNotEmpty()) {
                    item { SectionHeader("Films", "${movies.size}") }
                    item {
                        LazyRow(Modifier.height(220.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                            itemsIndexed(movies) { _, m ->
                                Poster(
                                    m.name, m.poster, m.favKey in favs,
                                    onClick = { nav.playVod(movieItem(m)) },
                                    onLongClick = { toggleFav(ctx, m.favKey, m.name) },
                                    modifier = Modifier.width(138.dp),
                                )
                            }
                        }
                    }
                }
                if (series.isNotEmpty()) {
                    item { SectionHeader("Séries", "${series.size}") }
                    item {
                        LazyRow(Modifier.height(220.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                            itemsIndexed(series) { _, s ->
                                Poster(
                                    s.name, s.cover, s.favKey in favs,
                                    onClick = { nav.openSeries(s) },
                                    onLongClick = { toggleFav(ctx, s.favKey, s.name) },
                                    modifier = Modifier.width(138.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
