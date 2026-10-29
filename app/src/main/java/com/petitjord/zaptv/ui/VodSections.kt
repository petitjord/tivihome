package com.petitjord.zaptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petitjord.zaptv.data.AppRepo
import com.petitjord.zaptv.data.Category
import com.petitjord.zaptv.data.Movie
import com.petitjord.zaptv.data.Series

// ===================== Films =====================

@Composable
fun MoviesSection(nav: Nav, focusContent: Boolean) {
    val data = rememberLoad("vod") { AppRepo.vodCategories() to AppRepo.vodStreams() }
    LoadView(data) { (cats, movies) ->
        val ctx = LocalContext.current
        val favs = AppRepo.favorites
        CatalogGrid(
            cats = cats,
            items = movies,
            categoryOf = { it.categoryId },
            isFav = { it.favKey in favs },
            focusContent = focusContent,
            unit = "films",
        ) { m, requester, onFocus ->
            Poster(
                title = m.name, image = m.poster, favorite = m.favKey in favs,
                onClick = { nav.play(PlayRequest(m.name, url = AppRepo.movieUrl(m))) },
                onLongClick = { toggleFav(ctx, m.favKey, m.name) },
                onFocus = onFocus, focusRequester = requester,
            )
        }
    }
}

// ===================== Séries =====================

@Composable
fun SeriesSection(nav: Nav, focusContent: Boolean) {
    val data = rememberLoad("series") { AppRepo.seriesCategories() to AppRepo.seriesList() }
    LoadView(data) { (cats, list) ->
        val ctx = LocalContext.current
        val favs = AppRepo.favorites
        CatalogGrid(
            cats = cats,
            items = list,
            categoryOf = { it.categoryId },
            isFav = { it.favKey in favs },
            focusContent = focusContent,
            unit = "séries",
        ) { s, requester, onFocus ->
            Poster(
                title = s.name, image = s.cover, favorite = s.favKey in favs,
                onClick = { nav.openSeries(s) },
                onLongClick = { toggleFav(ctx, s.favKey, s.name) },
                onFocus = onFocus, focusRequester = requester,
            )
        }
    }
}

/** Catégories à gauche, grille d'affiches à droite. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <T> CatalogGrid(
    cats: List<Category>,
    items: List<T>,
    categoryOf: (T) -> String,
    isFav: (T) -> Boolean,
    focusContent: Boolean,
    unit: String,
    cell: @Composable (T, androidx.compose.ui.focus.FocusRequester?, () -> Unit) -> Unit,
) {
    val groups = remember(cats) { listOf(Category(FAV, "★ Favoris"), Category(ALL, "Tout")) + cats }
    var catId by rememberSaveable { mutableStateOf(cats.firstOrNull()?.id ?: ALL) }
    var focusedIdx by rememberSaveable { mutableIntStateOf(0) }
    val favs = AppRepo.favorites
    val list = remember(catId, items, favs) {
        when (catId) {
            ALL -> items
            FAV -> items.filter(isFav)
            else -> items.filter { categoryOf(it) == catId }
        }
    }
    val gridState = rememberLazyGridState()
    var firstCat by remember { mutableStateOf(true) }
    LaunchedEffect(catId) {
        if (firstCat) firstCat = false else { gridState.scrollToItem(0); focusedIdx = 0 }
    }
    val restore = rememberInitialFocus(focusContent)

    Row(Modifier.fillMaxSize()) {
        CategoryColumn(groups, catId, onSelect = { catId = it })
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 16.dp, end = 12.dp)) {
            SectionHeader(groups.firstOrNull { it.id == catId }?.name ?: "", "${list.size} $unit")
            if (list.isEmpty()) {
                EmptyText(if (catId == FAV) "Aucun favori.\nMaintiens OK sur une affiche pour l'ajouter." else "Rien dans cette catégorie.")
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(140.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(list) { i, item ->
                        cell(
                            item,
                            if (i == focusedIdx.coerceAtMost(list.lastIndex)) restore else null,
                        ) { focusedIdx = i }
                    }
                }
            }
        }
    }
}

// ===================== Fiche série =====================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailScreen(series: Series, nav: Nav, focusContent: Boolean) {
    val data = rememberLoad("info-${series.seriesId}") { AppRepo.seriesInfo(series.seriesId) }
    val ctx = LocalContext.current

    Row(Modifier.fillMaxSize().padding(28.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Panel2)) {
                if (series.cover != null) {
                    AsyncImage(series.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Text(series.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            val fav = series.favKey in AppRepo.favorites
            FocusBox(
                onClick = { toggleFav(ctx, series.favKey, series.name) },
                background = Panel,
                modifier = Modifier.fillMaxWidth().height(42.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (fav) "★ Dans les favoris" else "☆ Ajouter aux favoris", color = if (fav) Gold else Color.White)
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            LoadView(data) { info ->
                val plot = info.plot ?: series.plot
                val seasons = remember(info) { info.episodes.map { it.season }.distinct().sorted() }
                var season by rememberSaveable { mutableIntStateOf(seasons.firstOrNull() ?: 0) }
                val episodes = remember(info, season) { info.episodes.filter { it.season == season } }
                val restore = rememberInitialFocus(true, season)

                if (!plot.isNullOrBlank()) {
                    Text(plot, color = Color(0xFFB8C0CC), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                }
                if (seasons.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(46.dp).focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons) { s ->
                            FocusBox(
                                onClick = { season = s },
                                onFocus = { season = s },
                                background = if (s == season) AccentDim else Panel,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.height(38.dp),
                            ) {
                                Box(Modifier.padding(horizontal = 16.dp).height(38.dp), contentAlignment = Alignment.Center) {
                                    Text("Saison $s", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (episodes.isEmpty()) {
                    EmptyText("Aucun épisode disponible.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        itemsIndexed(episodes) { i, e ->
                            FocusBox(
                                onClick = { nav.play(PlayRequest("${series.name} — ${e.title}", url = AppRepo.episodeUrl(e))) },
                                focusRequester = if (i == 0) restore else null,
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                            ) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("E${e.episodeNum}", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(e.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        e.plot?.let {
                                            Text(it, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
