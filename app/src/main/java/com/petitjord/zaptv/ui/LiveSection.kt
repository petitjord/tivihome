package com.petitjord.zaptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitjord.zaptv.data.AppRepo
import com.petitjord.zaptv.data.Category
import com.petitjord.zaptv.data.Channel
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
    var catId by rememberSaveable { mutableStateOf(ALL) }
    var focusedIdx by rememberSaveable { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Channel?>(null) }
    val favs = AppRepo.favorites

    val groups = remember(cats) {
        listOf(Category(FAV, "★ Favoris"), Category(ALL, "Toutes les chaînes")) + cats
    }
    val list = remember(catId, channels, favs) {
        when (catId) {
            ALL -> channels
            FAV -> channels.filter { it.favKey in favs }
            else -> channels.filter { it.categoryId == catId }
        }
    }
    val listState = rememberLazyListState()
    var firstCat by remember { mutableStateOf(true) }
    LaunchedEffect(catId) {
        if (firstCat) firstCat = false else { listState.scrollToItem(0); focusedIdx = 0 }
    }
    val restore = rememberInitialFocus(focusContent)

    Row(Modifier.fillMaxSize()) {
        CategoryColumn(groups, catId, onSelect = { catId = it })

        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, top = 16.dp, end = 8.dp)) {
            SectionHeader(groups.firstOrNull { it.id == catId }?.name ?: "", "${list.size} chaînes")
            if (list.isEmpty()) {
                EmptyText(if (catId == FAV) "Aucun favori.\nMaintiens OK sur une chaîne pour l'ajouter." else "Aucune chaîne dans ce groupe.")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(list) { i, ch ->
                        ChannelRow(
                            ch = ch,
                            favorite = ch.favKey in favs,
                            onClick = { nav.play(PlayRequest(ch.name, channels = list, index = i)) },
                            onLongClick = { toggleFav(ctx, ch.favKey, ch.name) },
                            onFocus = { focusedIdx = i; preview = ch },
                            requester = if (i == focusedIdx.coerceAtMost(list.lastIndex)) restore else null,
                        )
                    }
                }
            }
        }

        ChannelPreview(preview, Modifier.width(300.dp).fillMaxHeight())
    }
}

@Composable
fun ChannelRow(
    ch: Channel,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    requester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    FocusBox(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocus = onFocus,
        focusRequester = requester,
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ch.num > 0) "${ch.num}" else "", color = TextDim, fontSize = 14.sp,
                modifier = Modifier.width(48.dp),
            )
            ChannelLogo(ch.icon, ch.name, Modifier.size(width = 64.dp, height = 40.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                ch.name, color = if (focused) Color.White else Color(0xFFD7DCE5), fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (favorite) Text("★", color = Gold, fontSize = 16.sp)
        }
    }
}

/** Panneau de droite : programme en cours et suivant de la chaîne survolée. */
@Composable
fun ChannelPreview(ch: Channel?, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ch == null) {
            Text("Choisis une chaîne", color = TextDim)
        } else {
            ChannelPreviewBody(ch)
        }
    }
}

@Composable
private fun ChannelPreviewBody(ch: Channel) {
    run {
        ChannelLogo(ch.icon, ch.name, Modifier.fillMaxWidth().height(110.dp))
        Text(ch.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)

        val epg = rememberLoad(ch.streamId) {
            delay(300) // évite de charger pendant un défilement rapide
            AppRepo.shortEpg(ch.streamId, 4)
        }
        when (val s = epg.state) {
            is Load.Loading -> Text("Chargement du programme…", color = TextDim, fontSize = 13.sp)
            is Load.Err -> Text("Programme indisponible", color = TextDim, fontSize = 13.sp)
            is Load.Ok -> {
                val progs = s.data
                val now = System.currentTimeMillis()
                val current = progs.firstOrNull { it.isNow(now) }
                if (current == null) {
                    Text("Pas d'information de programme", color = TextDim, fontSize = 13.sp)
                } else {
                    Text("EN CE MOMENT", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(current.title, color = Color.White, fontSize = 16.sp, maxLines = 2)
                    Text("${hm(current.start)} – ${hm(current.end)}", color = TextDim, fontSize = 13.sp)
                    val frac = ((now - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { frac }, color = Accent, trackColor = Panel2,
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                    if (current.description.isNotBlank()) {
                        Text(current.description, color = Color(0xFFB8C0CC), fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                    progs.firstOrNull { it.start >= current.end }?.let { next ->
                        Spacer(Modifier.height(4.dp))
                        Text("ENSUITE", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${hm(next.start)}  ${next.title}", color = Color.White, fontSize = 14.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}
