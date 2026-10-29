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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Category
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Programme
import kotlinx.coroutines.delay

@Composable
fun LiveSection(nav: Nav, focusContent: Boolean) {
    val data = rememberLoad("live") { AppRepo.liveCategories() to AppRepo.liveStreams() }
    LoadView(data) { (cats, channels) -> LiveContent(cats, channels, nav, focusContent) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LiveContent(cats: List<Category>, channels: List<Channel>, nav: Nav, focusContent: Boolean) {
    val ctx = LocalContext.current
    val tv = LocalTvPlayer.current
    var catId by rememberSaveable { mutableStateOf(ALL) }
    var focusedIdx by rememberSaveable { mutableIntStateOf(0) }
    var hovered by remember { mutableStateOf<Channel?>(tv.channel) }
    val favs = AppRepo.favorites

    val groups = remember(cats) {
        listOf(Category(FAV, "★ Favoris"), Category(ALL, "Toutes les chaînes")) + cats
    }
    val list = remember(catId, channels, favs, AppRepo.hiddenGroups) {
        when (catId) {
            ALL -> channels.filter { it.categoryId !in AppRepo.hiddenGroups }
            FAV -> channels.filter { it.favKey in favs }
            else -> channels.filter { it.categoryId == catId }
        }
    }
    // Au retour du plein écran, on se replace sur la chaîne qu'on regardait.
    LaunchedEffect(Unit) {
        val playing = tv.channel
        if (focusContent && playing != null) {
            val i = list.indexOf(playing)
            if (i >= 0) focusedIdx = i
        }
    }
    val listState = rememberLazyListState()
    var firstCat by remember { mutableStateOf(true) }
    LaunchedEffect(catId) {
        if (firstCat) firstCat = false else { listState.scrollToItem(0); focusedIdx = 0 }
    }
    // Aperçu : la chaîne survolée se lance après un court instant.
    LaunchedEffect(hovered) {
        val ch = hovered ?: return@LaunchedEffect
        delay(700)
        tv.playChannel(ch, list)
    }
    // On quitte la section sans passer en plein écran : on coupe le son.
    DisposableEffect(Unit) { onDispose { if (!tv.fullscreen) tv.stop() } }

    val restore = rememberInitialFocus(focusContent)
    LaunchedEffect(Unit) {
        if (focusContent && listState.layoutInfo.visibleItemsInfo.none { it.index == focusedIdx }) {
            listState.scrollToItem((focusedIdx - 3).coerceAtLeast(0))
        }
    }

    Row(Modifier.fillMaxSize()) {
        CategoryColumn(groups, catId, onSelect = { catId = it })

        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, top = 16.dp, end = 12.dp)) {
            SectionHeader(groups.firstOrNull { it.id == catId }?.name ?: "", "${list.size}")
            if (list.isEmpty()) {
                EmptyText(if (catId == FAV) "Aucun favori.\nMaintiens OK sur une chaîne pour l'ajouter." else "Aucune chaîne dans ce groupe.")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    itemsIndexed(list) { i, ch ->
                        ChannelRow(
                            ch = ch,
                            favorite = ch.favKey in favs,
                            playing = tv.channel == ch,
                            onClick = { nav.playLive(list, i) },
                            onLongClick = { toggleFav(ctx, ch.favKey, ch.name) },
                            onFocus = { focusedIdx = i; hovered = ch },
                            requester = if (i == focusedIdx.coerceAtMost(list.lastIndex)) restore else null,
                        )
                    }
                }
            }
        }

        PreviewPanel(hovered, Modifier.width(320.dp).fillMaxHeight())
    }
}

/** Ligne de chaîne : numéro, logo, nom, programme en cours et sa progression. */
@Composable
fun ChannelRow(
    ch: Channel,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    playing: Boolean = false,
    requester: FocusRequester? = null,
    compact: Boolean = false,
) {
    val epg = rememberLoad("row-${ch.streamId}") {
        delay(180) // pas de requête pendant un défilement rapide
        AppRepo.shortEpg(ch, 4)
    }
    val now = System.currentTimeMillis()
    val current: Programme? = when (val s = epg.state) {
        is Load.Ok -> s.data.firstOrNull { it.isNow(now) }
        else -> null
    }

    FocusBox(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocus = onFocus,
        focusRequester = requester,
        focusedBackground = Color(0xFF243044),
        scaleOnFocus = 1.02f,
        modifier = Modifier.fillMaxWidth().height(if (compact) 60.dp else 64.dp),
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ch.num > 0) "${ch.num}" else "", fontSize = 13.sp,
                color = if (playing) Accent else TextDim,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(38.dp),
            )
            ChannelLogo(ch.icon, ch.name, Modifier.size(width = 58.dp, height = 38.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ch.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (focused || playing) Color.White else Color(0xFFD7DCE5),
                        fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favorite) Text("  ★", color = Gold, fontSize = 13.sp)
                }
                if (current != null) {
                    Text(current.title, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(
                        progress = { current.progress(now) },
                        color = if (focused) Accent else Color(0xFF5B6B82),
                        trackColor = Color(0x22FFFFFF),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }
        }
    }
}

/** Panneau de droite : vidéo en aperçu + programme. */
@Composable
private fun PreviewPanel(ch: Channel?, modifier: Modifier) {
    val tv = LocalTvPlayer.current
    Column(modifier.background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (tv.channel != null) VideoSurface(Modifier.fillMaxSize())
            when {
                tv.error != null -> Text("Flux indisponible", color = TextDim, fontSize = 13.sp)
                tv.channel == null -> Text("Aperçu", color = TextDim, fontSize = 13.sp)
                tv.buffering -> CircularProgressIndicator(color = Accent, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
        }
        if (ch != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(ch.icon, ch.name, Modifier.size(width = 48.dp, height = 32.dp))
                Spacer(Modifier.width(10.dp))
                Text(ch.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ProgrammeInfo(ch)
            Spacer(Modifier.weight(1f))
            Text("OK : plein écran   ·   OK maintenu : favori", color = TextDim, fontSize = 12.sp)
        } else {
            Text("Choisis une chaîne", color = TextDim)
        }
    }
}

@Composable
private fun ProgrammeInfo(ch: Channel) {
    val epg = rememberLoad("row-${ch.streamId}") { AppRepo.shortEpg(ch, 4) }
    when (val s = epg.state) {
        is Load.Loading -> Text("Chargement du programme…", color = TextDim, fontSize = 13.sp)
        is Load.Err -> Text("Programme indisponible", color = TextDim, fontSize = 13.sp)
        is Load.Ok -> {
            val now = System.currentTimeMillis()
            val current = s.data.firstOrNull { it.isNow(now) }
            if (current == null) {
                Text("Pas d'information de programme", color = TextDim, fontSize = 13.sp)
            } else {
                Text("EN CE MOMENT", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(current.title, color = Color.White, fontSize = 15.sp, maxLines = 2)
                Text("${hm(current.start)} – ${hm(current.end)}", color = TextDim, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { current.progress(now) }, color = Accent, trackColor = Panel2,
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
                if (current.description.isNotBlank()) {
                    Text(current.description, color = Color(0xFFB8C0CC), fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                s.data.firstOrNull { it.start >= current.end }?.let { next ->
                    Text("ENSUITE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text("${hm(next.start)}  ${next.title}", color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
