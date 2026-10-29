package com.petitjord.tivihome.ui

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Category
import com.petitjord.tivihome.data.Movie
import com.petitjord.tivihome.data.Series

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
            inProgress = { m -> AppRepo.resume.any { it.key == m.favKey } },
            focusContent = focusContent,
            unit = "films",
            titleOf = { it.name },
            imageOf = { it.poster },
            metaOf = { m -> m.rating?.takeIf { it != "0" && it.isNotBlank() }?.let { "★ $it" } },
            addedOf = { it.added },
            initialCat = CatalogRequest.movies.also { CatalogRequest.movies = null },
            detailsOf = { m ->
                val i = AppRepo.vodInfo(m)
                HeroDetails(i.plot, i.backdrop, i.year, i.durationText, i.genre, i.rating?.takeIf { it != "0" && it.isNotBlank() }, i.cast, i.director)
            },
        ) { m, requester, onFocus ->
            Poster(
                title = m.name, image = m.poster, favorite = m.favKey in favs,
                onClick = { nav.openMovie(m) },
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
            inProgress = { false },
            focusContent = focusContent,
            unit = "séries",
            titleOf = { it.name },
            imageOf = { it.cover },
            metaOf = { se -> se.rating?.takeIf { it != "0" && it.isNotBlank() }?.let { "★ $it" } },
            addedOf = { it.added },
            initialCat = CatalogRequest.series.also { CatalogRequest.series = null },
            detailsOf = { se ->
                val i = AppRepo.seriesInfo(se.seriesId)
                HeroDetails(i.plot ?: se.plot, i.backdrop, null, null, i.genre, se.rating?.takeIf { it != "0" && it.isNotBlank() }, i.cast, null)
            },
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

/** Catégories à gauche, fiche de l'élément sélectionné en haut, rangée d'affiches en bas. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <T> CatalogGrid(
    cats: List<Category>,
    items: List<T>,
    categoryOf: (T) -> String,
    isFav: (T) -> Boolean,
    inProgress: (T) -> Boolean,
    focusContent: Boolean,
    unit: String,
    titleOf: (T) -> String,
    imageOf: (T) -> String?,
    metaOf: (T) -> String?,
    detailsOf: suspend (T) -> HeroDetails?,
    addedOf: (T) -> Long,
    initialCat: String?,
    cell: @Composable (T, androidx.compose.ui.focus.FocusRequester?, () -> Unit) -> Unit,
) {
    val resumeKeys = AppRepo.resume
    val hasResume = remember(items, resumeKeys) { items.any(inProgress) }
    val groups = remember(cats, hasResume) {
        (if (hasResume) listOf(Category(RESUME, "▶ En cours")) else emptyList()) +
            listOf(Category(RECENT, "Ajouts récents"), Category(FAV, "★ Favoris"), Category(ALL, "Tout")) + cats
    }
    var catId by rememberSaveable { mutableStateOf(initialCat ?: cats.firstOrNull()?.id ?: ALL) }
    var focusedIdx by rememberSaveable { mutableIntStateOf(0) }
    val favs = AppRepo.favorites
    val list = remember(catId, items, favs, resumeKeys, AppRepo.hiddenGroups, AppRepo.lockedGroups, AppRepo.unlocked, AppRepo.profileId) {
        when (catId) {
            ALL -> items.filter { visibleCat(categoryOf(it)) }
            FAV -> items.filter(isFav)
            RESUME -> items.filter(inProgress)
            RECENT -> items.filter { visibleCat(categoryOf(it)) }.sortedByDescending(addedOf).take(200)
            else -> items.filter { categoryOf(it) == catId }
        }
    }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    var firstCat by remember { mutableStateOf(true) }
    LaunchedEffect(catId) {
        if (firstCat) firstCat = false else { rowState.scrollToItem(0); focusedIdx = 0 }
    }
    val restore = rememberInitialFocus(focusContent)

    Row(Modifier.fillMaxSize()) {
        CategoryColumn(groups, catId, onSelect = { catId = it })
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Fiche de l'élément survolé : texte à gauche, grande image nette à droite
            val hero = list.getOrNull(focusedIdx.coerceAtMost(list.lastIndex).coerceAtLeast(0))
            val det = rememberLoad(hero?.let(titleOf)) {
                if (hero == null) null else { kotlinx.coroutines.delay(300); runCatching { detailsOf(hero) }.getOrNull() }
            }
            val d = (det.state as? Load.Ok<HeroDetails?>)?.data
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val img = d?.backdrop ?: hero?.let(imageOf)
                if (img != null) {
                    AsyncImage(
                        img, null, contentScale = ContentScale.Crop, alignment = Alignment.TopEnd,
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(0.66f).align(Alignment.TopEnd),
                    )
                }
                // Fondu de l'image dans le fond, vers la gauche et vers le bas
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.30f to Bg, 0.55f to Bg.copy(alpha = 0.75f), 1f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Bg)))
                Column(Modifier.fillMaxWidth(0.52f).padding(start = 16.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        hero?.let(titleOf) ?: "", color = Color.White, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(d?.year, d?.duration, d?.genre, (d?.rating ?: hero?.let(metaOf))?.let { if (it.startsWith("★")) it else "★ $it" })
                        .filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (meta.isNotBlank()) Text(meta, color = TextDim, fontSize = 13.sp, maxLines = 1)
                    d?.cast?.let { Text("Acteurs : $it", color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    d?.director?.let { Text("Réalisation : $it", color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(
                        when {
                            !d?.plot.isNullOrBlank() -> d!!.plot!!
                            det.state is Load.Loading && hero != null -> "…"
                            else -> ""
                        },
                        color = Color(0xFFD0D7E2), fontSize = 13.sp, lineHeight = 18.sp,
                        maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // Une seule rangée d'affiches : l'affiche sélectionnée reste calée à gauche
            Text(
                "${groups.firstOrNull { it.id == catId }?.name ?: ""}  ·  ${list.size} $unit",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            Box(Modifier.fillMaxWidth().height(196.dp).padding(start = 10.dp)) {
                if (AppRepo.groupNeedsPin(catId)) {
                    LockedGroupView()
                } else if (list.isEmpty()) {
                    EmptyText(if (catId == FAV) "Aucun favori. Maintiens OK sur une affiche pour l'ajouter." else "Rien dans cette catégorie.")
                } else {
                    PivotRow {
                        LazyRow(
                            state = rowState,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            contentPadding = PaddingValues(start = 6.dp, end = 800.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(list) { i, item ->
                                Box(Modifier.width(120.dp)) {
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
        }
    }
}

// ===================== Fiche série =====================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailScreen(series: Series, nav: Nav) {
    val data = rememberLoad("info-${series.seriesId}") { AppRepo.seriesInfo(series.seriesId) }
    val ctx = LocalContext.current

    val backdrop = (data.state as? Load.Ok<com.petitjord.tivihome.data.SeriesInfo>)?.data?.backdrop ?: series.cover
    Box(Modifier.fillMaxSize()) {
    Backdrop(backdrop)
    Row(Modifier.fillMaxSize().padding(28.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Panel2)) {
                if (series.cover != null) {
                    AsyncImage(series.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Text(series.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            series.rating?.takeIf { it != "0" }?.let { Text("★ $it", color = Gold, fontSize = 14.sp) }
            (data.state as? Load.Ok<com.petitjord.tivihome.data.SeriesInfo>)?.data?.trailer?.let { t ->
                FocusBox(
                    onClick = { openTrailer(ctx, t) },
                    background = Glass,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("▶ Bande-annonce", color = Color.White) }
                }
            }
            val fav = series.favKey in AppRepo.favorites
            FocusBox(
                onClick = { toggleFav(ctx, series.favKey, series.name) },
                background = Glass,
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
                val meta = listOfNotNull(info.genre, info.cast?.let { "Avec $it" }).joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Text(meta, color = Accent, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                }
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
                                onClick = {
                                    // Toute la série dans l'ordre : l'épisode suivant s'enchaîne tout seul.
                                    val queue = info.episodes.map { episodeItem(series, it) }
                                    nav.playQueue(queue, info.episodes.indexOf(e).coerceAtLeast(0))
                                },
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
}

/** Fiche affichée en haut de la grille pour l'élément survolé. */
data class HeroDetails(
    val plot: String?,
    val backdrop: String?,
    val year: String?,
    val duration: String?,
    val genre: String?,
    val rating: String?,
    val cast: String?,
    val director: String?,
)

/** Catégorie à ouvrir directement en arrivant sur Films / Séries (bouton « Voir tout »). */
object CatalogRequest {
    var movies: String? = null
    var series: String? = null
}
