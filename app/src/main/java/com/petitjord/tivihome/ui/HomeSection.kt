package com.petitjord.tivihome.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import kotlinx.coroutines.launch
import java.util.Calendar

private data class HomeData(val channels: List<Channel>, val movies: List<Movie>, val series: List<Series>)

/** Page d'accueil : tout ce qui compte pour toi, en un coup d'œil. */
@Composable
fun HomeSection(nav: Nav, focusContent: Boolean, onSeeAll: (Int) -> Unit) {
    val data = rememberLoad("home") {
        val ch = AppRepo.liveStreams()
        val mv = runCatching { AppRepo.vodStreams() }.getOrDefault(emptyList())
        val se = runCatching { AppRepo.seriesList() }.getOrDefault(emptyList())
        HomeData(ch, mv, se)
    }
    LoadView(data) { d -> HomeContent(d, nav, focusContent, onSeeAll) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeContent(d: HomeData, nav: Nav, focusContent: Boolean, onSeeAll: (Int) -> Unit) {
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

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var heroImg by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val onHero by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Box(Modifier.fillMaxSize()) {
    // Grande image du film à la une, en fond, jusqu'aux bords de l'écran
    HeroBackdrop(heroImg, if (onHero) 1f else 0.25f)
    Column(Modifier.fillMaxSize()) {
    // En-tête fixe : toujours visible, même quand la liste défile
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (hour in 5..17) "Bonjour" else "Bonsoir",
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                com.petitjord.tivihome.data.Updater.available?.let { v ->
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    ActionButton(
                        if (com.petitjord.tivihome.data.Updater.busy) com.petitjord.tivihome.data.Updater.status else "⬇  Mettre à jour (1.0.$v)",
                        primary = true,
                    ) {
                        if (!com.petitjord.tivihome.data.Updater.busy) scope.launch { com.petitjord.tivihome.data.Updater.downloadAndInstall(ctx) }
                    }
                    Spacer(Modifier.width(20.dp))
                }
                Text(hm(now), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
            }
    }
    // Chaque rangée qui reçoit le focus vient se caler en haut, sous l'en-tête :
    // le carrousel est toujours affiché en entier et rien ne « saute ».
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var itemIndex = 0
    fun Modifier.snapOnFocus(index: Int) = this.onFocusChanged {
        if (it.hasFocus) scope.launch { listState.animateScrollToItem(index) }
    }
    val featured = recentMovies.filter { it.added > 0 }.take(6)
    val upcoming = reminders.filter { it.start > now - 5 * 60_000 }.sortedBy { it.start }

    ScrollSpec(NoAutoScroll) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 400.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // À la une : carrousel des derniers films ajoutés
        if (featured.isNotEmpty()) {
            val idx = itemIndex++
            item { Box(Modifier.snapOnFocus(idx)) { FeaturedCarousel(featured, nav, takeFirst(), onBackdrop = { heroImg = it }) } }
        }

        if (upcoming.isNotEmpty()) {
            val idx = itemIndex++
            item {
                Column(Modifier.snapOnFocus(idx)) {
                    SectionHeader("Tes rappels", "${upcoming.size}")
                    PivotRow {
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
            }
        }

        if (resume.isNotEmpty()) {
            val idx = itemIndex++
            item {
                val req = takeFirst()
                Column(Modifier.snapOnFocus(idx)) {
                    SectionHeader("Continuer à regarder", "${resume.size}")
                    PivotRow {
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
            }
        }

        if (liveRow.isNotEmpty()) {
            val idx = itemIndex++
            item {
                val req = takeFirst()
                Column(Modifier.snapOnFocus(idx)) {
                    SectionHeader(if (favChannels.isEmpty()) "Ta dernière chaîne" else "En ce moment sur tes chaînes", null)
                    PivotRow {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp), modifier = Modifier.focusRestorer()) {
                            itemsIndexed(liveRow) { i, ch ->
                                LiveCard(ch, now, if (i == 0) req else null) { nav.playLive(liveRow, i) }
                            }
                        }
                    }
                }
            }
        }

        if (recentMovies.isNotEmpty() && recentMovies.first().added > 0) {
            val idx = itemIndex++
            item {
                val req = takeFirst()
                Column(Modifier.snapOnFocus(idx)) {
                    SectionHeader("Films ajoutés récemment", null)
                    PosterRow(recentMovies, req, title = { it.name }, image = { it.poster }, fav = { it.favKey in favs },
                        onClick = { nav.openMovie(it) }, onLong = { toggleFav(ctx, it.favKey, it.name) },
                        onSeeAll = { CatalogRequest.movies = RECENT; onSeeAll(4) })
                }
            }
        }

        if (recentSeries.isNotEmpty() && recentSeries.first().added > 0) {
            val idx = itemIndex++
            item {
                val req = takeFirst()
                Column(Modifier.snapOnFocus(idx)) {
                    SectionHeader("Séries mises à jour", null)
                    PosterRow(recentSeries, req, title = { it.name }, image = { it.cover }, fav = { it.favKey in favs },
                        onClick = { nav.openSeries(it) }, onLong = { toggleFav(ctx, it.favKey, it.name) },
                        onSeeAll = { CatalogRequest.series = RECENT; onSeeAll(5) })
                }
            }
        }
    }
    }
}
}
}

/** Rangée d'affiches : l'affiche sélectionnée reste calée à gauche, la rangée défile dessous. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <T> PosterRow(
    items: List<T>,
    requester: FocusRequester?,
    title: (T) -> String,
    image: (T) -> String?,
    fav: (T) -> Boolean,
    onClick: (T) -> Unit,
    onLong: (T) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    PivotRow {
        LazyRow(Modifier.height(200.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(start = 6.dp, end = 600.dp, top = 6.dp, bottom = 6.dp)) {
            itemsIndexed(items) { i, it ->
                Poster(
                    title(it), image(it), fav(it),
                    onClick = { onClick(it) },
                    onLongClick = { onLong(it) },
                    focusRequester = if (i == 0) requester else null,
                    modifier = Modifier.width(124.dp),
                )
            }
            // Carte « Voir tout » en fin de rangée
            if (onSeeAll != null) {
                item {
                    FocusBox(
                        onClick = onSeeAll,
                        background = Panel2,
                        focusedBackground = Color.White,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        modifier = Modifier.width(124.dp).fillMaxHeight(),
                    ) { focused ->
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("→", color = if (focused) Bg else Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text("Voir tout", color = if (focused) Bg else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
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
        background = Glass,
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

/** Grand bandeau « À la une » qui défile tout seul toutes les 8 secondes (sauf quand on y est). */
@Composable
private fun HeroBackdrop(img: String?, target: Float) {
    val a by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(450), label = "hero")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd)
                .offset(x = 24.dp, y = (-18).dp)
                .requiredWidth(maxWidth * 0.76f + 24.dp)
                .requiredHeight(430.dp)
                .graphicsLayer { alpha = a },
        ) {
            if (img != null) {
                coil.compose.AsyncImage(
                    img, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize(),
                )
            }
            // Fondus vers le fond : à gauche pour le texte, en bas pour les rangées
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Bg, 0.38f to Bg.copy(alpha = 0.55f), 0.75f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.45f to Color.Transparent, 1f to Bg)))
        }
    }
}

/** À la une : titre, infos et boutons ; l'image du film est dessinée en fond par HeroBackdrop. */
@Composable
private fun FeaturedCarousel(movies: List<Movie>, nav: Nav, requester: FocusRequester?, onBackdrop: (String?) -> Unit) {
    var index by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var hasFocus by remember { androidx.compose.runtime.mutableStateOf(false) }
    var focusedBtn by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(hasFocus, movies) {
        while (!hasFocus) {
            delay(8_000)
            index = (index + 1) % movies.size
        }
    }
    val m = movies[index.coerceIn(0, movies.lastIndex)]
    val info = rememberLoad("vodinfo-${m.streamId}") { AppRepo.vodInfo(m) }
    val details = (info.state as? Load.Ok<com.petitjord.tivihome.data.MovieInfo>)?.data
    LaunchedEffect(m.streamId, details) { onBackdrop(details?.backdrop ?: m.poster) }

    Box(
        Modifier.fillMaxWidth().height(286.dp)
            .onFocusChanged { hasFocus = it.hasFocus }
            // Défilement manuel : ▶ depuis « Détails » = film suivant, ◀ depuis « Lecture » = film précédent
            .onPreviewKeyEvent { ev ->
                if (ev.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    ev.key == androidx.compose.ui.input.key.Key.DirectionRight && focusedBtn == 1 -> {
                        index = (index + 1) % movies.size; true
                    }
                    ev.key == androidx.compose.ui.input.key.Key.DirectionLeft && focusedBtn == 0 && index > 0 -> {
                        index -= 1; true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(bottom = 18.dp).fillMaxWidth(0.52f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("À LA UNE", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(
                m.name, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp, lineHeight = 42.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(details?.year, details?.durationText, details?.genre?.substringBefore(","), (details?.rating ?: m.rating)?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" })
                .filter { it.isNotBlank() }.joinToString("   ·   ").takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFFD7DCE5), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            details?.plot?.let { Text(it, color = Color(0xFFC3CBD8), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                ActionButton("▶  Lecture", primary = true, requester = requester, onFocus = { focusedBtn = 0 }) { nav.playVod(movieItem(m)) }
                ActionButton("Détails", onFocus = { focusedBtn = 1 }) { nav.openMovie(m) }
            }
        }
        // Pagination + aide
        Row(Modifier.align(Alignment.BottomEnd).padding(bottom = 30.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hasFocus) Text("◀ ▶  ", color = TextDim, fontSize = 12.sp)
            movies.indices.forEach { i ->
                val w by androidx.compose.animation.core.animateDpAsState(if (i == index) 24.dp else 6.dp, label = "dot")
                Box(Modifier.width(w).height(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(if (i == index) Color.White else Color(0x55FFFFFF)))
            }
        }
    }
}
