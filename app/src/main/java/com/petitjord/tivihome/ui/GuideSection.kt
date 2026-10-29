package com.petitjord.tivihome.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Category
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Programme
import kotlinx.coroutines.delay

private val DP_PER_MIN = 5.dp
private const val WINDOW_MIN = 12 * 60          // 12 heures de grille
private val CHANNEL_CELL = 210.dp
private val ROW_HEIGHT = 44.dp

/**
 * Écran TV : en haut l'aperçu vidéo et la fiche du programme sélectionné,
 * en dessous le guide des chaînes. À gauche, les groupes.
 */
@Composable
fun GuideSection(nav: Nav, focusContent: Boolean) {
    val data = rememberLoad("live") { AppRepo.liveCategories() to AppRepo.liveStreams() }
    LoadView(data) { (cats, channels) -> GuideContent(cats, channels, nav, focusContent) }
}

private data class Focused(val channel: Channel, val prog: Programme?)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GuideContent(cats: List<Category>, channels: List<Channel>, nav: Nav, focusContent: Boolean) {
    val tv = LocalTvPlayer.current
    val groups = remember(cats) { listOf(Category(FAV, "★ Favoris"), Category(ALL, "Toutes les chaînes")) + cats }
    val favs = AppRepo.favorites
    var catId by rememberSaveable { mutableStateOf(ALL) }
    val list = remember(catId, channels, favs, AppRepo.hiddenGroups, AppRepo.lockedGroups, AppRepo.unlocked, AppRepo.profileId) {
        when (catId) {
            ALL -> channels.filter { visibleCat(it.categoryId) }
            FAV -> channels.filter { it.favKey in favs }
            else -> channels.filter { it.categoryId == catId }
        }
    }
    // Début de la grille : l'heure actuelle arrondie à la demi-heure.
    val windowStart = remember { System.currentTimeMillis().let { it - it % (30 * 60_000) } }
    val hScroll = rememberScrollState()          // partagé : toutes les lignes défilent ensemble
    val vState = rememberLazyListState()
    var rowIdx by rememberSaveable { mutableIntStateOf(0) }
    var firstCat by remember { mutableStateOf(true) }
    LaunchedEffect(catId) { if (firstCat) firstCat = false else { vState.scrollToItem(0); rowIdx = 0 } }
    var focused by remember { mutableStateOf<Focused?>(tv.channel?.let { Focused(it, null) }) }
    // Au retour du plein écran, on se replace sur la chaîne regardée
    LaunchedEffect(Unit) {
        val playing = tv.channel
        if (focusContent && playing != null) {
            val i = list.indexOf(playing)
            if (i >= 0) { rowIdx = i; vState.scrollToItem((i - 3).coerceAtLeast(0)) }
        }
    }
    // L'aperçu suit la chaîne sélectionnée, après un court arrêt dessus
    LaunchedEffect(focused?.channel) {
        val ch = focused?.channel ?: return@LaunchedEffect
        delay(650)
        tv.playChannel(ch, list)
    }
    DisposableEffect(Unit) { onDispose { if (!tv.fullscreen) tv.stop() } }
    val restore = rememberInitialFocus(focusContent)

    Row(Modifier.fillMaxSize()) {
        CategoryColumn(groups, catId, onSelect = { catId = it })

        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp)) {
            TvHeader(focused, nav, list.ifEmpty { channels }, Modifier.fillMaxWidth().height(196.dp))
            Spacer(Modifier.height(8.dp))
            TimeRuler(windowStart, hScroll)

            if (AppRepo.groupNeedsPin(catId)) {
                LockedGroupView()
            } else if (list.isEmpty()) {
                EmptyText(if (catId == FAV) "Aucun favori.\nMaintiens OK sur une chaîne pour l'ajouter." else "Aucune chaîne dans ce groupe.")
            } else {
                LazyColumn(
                    state = vState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(list) { i, ch ->
                        GuideRow(
                            ch = ch,
                            playing = tv.channel == ch,
                            windowStart = windowStart,
                            hScroll = hScroll,
                            firstCellRequester = if (i == rowIdx.coerceAtMost(list.lastIndex)) restore else null,
                            onFocus = { p -> rowIdx = i; focused = Focused(ch, p) },
                            onPlay = { nav.playLive(list, i) },
                        )
                    }
                }
            }
        }
    }
}

/** En-tête : petite vidéo à gauche, fiche du programme à droite. */
@Composable
private fun TvHeader(f: Focused?, nav: Nav, channels: List<Channel>, modifier: Modifier) {
    val tv = LocalTvPlayer.current
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (tv.channel != null) VideoSurface(Modifier.fillMaxSize())
            when {
                tv.error != null -> Text("Flux indisponible", color = TextDim, fontSize = 12.sp)
                tv.channel == null -> Text("Aperçu", color = TextDim, fontSize = 12.sp)
                tv.buffering -> CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (f == null) {
                Text("Choisis une chaîne", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("OK : plein écran · OK maintenu : favori ou rappel", color = TextDim, fontSize = 13.sp)
            } else {
                val p = f.prog ?: rememberLoad("row-${f.channel.streamId}") { AppRepo.shortEpg(f.channel, 4) }
                    .state.let { (it as? Load.Ok<List<Programme>>)?.data?.firstOrNull { pr -> pr.isNow() } }
                Text(
                    p?.title ?: f.channel.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p != null) {
                        Text("${hm(p.start)} – ${hm(p.end)}", color = TextDim, fontSize = 13.sp)
                        Spacer(Modifier.width(10.dp))
                        if (p.isNow()) {
                            LinearProgressIndicator(
                                progress = { p.progress() }, color = Accent, trackColor = Color(0x33FFFFFF),
                                modifier = Modifier.width(90.dp).height(3.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("${((p.end - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)} min restantes", color = TextDim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(f.channel.name, color = Accent, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val d = p?.description.orEmpty()
                Text(
                    d.ifBlank { "Pas de description." }, color = Color(0xFFB8C0CC), fontSize = 13.sp,
                    lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallAction("▦ Multi-écran") { nav.openMulti(channels) }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    FocusBox(onClick = onClick, background = Panel2, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) {
        Box(Modifier.padding(horizontal = 12.dp).height(32.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TimeRuler(windowStart: Long, hScroll: ScrollState) {
    Row(Modifier.fillMaxWidth().height(24.dp)) {
        Box(Modifier.width(CHANNEL_CELL).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Text(
                java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.FRANCE).format(java.util.Date()).replaceFirstChar { it.uppercase() },
                color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(Modifier.weight(1f).horizontalScroll(hScroll, enabled = false)) {
            for (i in 0 until WINDOW_MIN / 30) {
                Box(Modifier.width(DP_PER_MIN * 30).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    Text(hm(windowStart + i * 30 * 60_000L), color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private sealed interface Seg {
    data class Gap(val width: Dp) : Seg
    data class Prog(val p: Programme?, val width: Dp) : Seg
}

@Composable
private fun GuideRow(
    ch: Channel,
    playing: Boolean,
    windowStart: Long,
    hScroll: ScrollState,
    firstCellRequester: FocusRequester?,
    onFocus: (Programme?) -> Unit,
    onPlay: () -> Unit,
) {
    val ctx = LocalContext.current
    val windowEnd = windowStart + WINDOW_MIN * 60_000L
    val epg = rememberLoad("epg-${ch.streamId}") {
        delay(120) // pas de requête pendant un défilement rapide
        AppRepo.shortEpg(ch, 24)
    }
    val fav = ch.favKey in AppRepo.favorites

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 1.dp)) {
        // Colonne chaîne : numéro, logo, nom
        Row(
            Modifier.width(CHANNEL_CELL).fillMaxHeight().background(Panel, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (ch.num > 0) "${ch.num}" else "", fontSize = 12.sp, modifier = Modifier.width(30.dp),
                color = if (playing) Accent else TextDim, fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
            )
            ChannelLogo(ch.icon, ch.name, Modifier.size(width = 44.dp, height = 28.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                ch.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                color = if (playing) Accent else Color.White,
            )
            if (fav) Text("★", color = Gold, fontSize = 11.sp)
        }

        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
            Row(Modifier.width(DP_PER_MIN * WINDOW_MIN).fillMaxHeight()) {
                val segs: List<Seg> = when (val s = epg.state) {
                    is Load.Ok -> buildSegments(s.data, windowStart, windowEnd)
                    is Load.Err -> listOf(Seg.Prog(null, DP_PER_MIN * 120))
                    else -> emptyList()
                }
                if (epg.state is Load.Loading) {
                    // Cellule vide focusable pendant le chargement : la navigation ne « saute » pas
                    ProgCell(Seg.Prog(null, DP_PER_MIN * 120), ch, firstCellRequester, onFocus, onPlay, onFav = { toggleFav(ctx, ch.favKey, ch.name) })
                }
                var first = true
                for (seg in segs) {
                    when (seg) {
                        is Seg.Gap -> Spacer(Modifier.width(seg.width))
                        is Seg.Prog -> {
                            ProgCell(seg, ch, if (first) firstCellRequester else null, onFocus, onPlay, onFav = { toggleFav(ctx, ch.favKey, ch.name) })
                            first = false
                        }
                    }
                }
            }
        }
    }
}

private fun buildSegments(progs: List<Programme>, start: Long, end: Long): List<Seg> {
    val out = mutableListOf<Seg>()
    var t = start
    for (p in progs) {
        if (p.end <= start || p.start >= end) continue
        val s = maxOf(p.start, t)
        val e = minOf(p.end, end)
        if (e <= s) continue
        if (s > t) out += Seg.Gap(DP_PER_MIN * ((s - t) / 60_000f))
        out += Seg.Prog(p, DP_PER_MIN * ((e - s) / 60_000f))
        t = e
    }
    if (out.isEmpty()) out += Seg.Prog(null, DP_PER_MIN * 120)
    return out
}

@Composable
private fun ProgCell(
    seg: Seg.Prog,
    ch: Channel,
    requester: FocusRequester?,
    onFocus: (Programme?) -> Unit,
    onPlay: () -> Unit,
    onFav: () -> Unit,
) {
    val p = seg.p
    val live = p?.isNow() ?: true
    val ctx = LocalContext.current
    val reminded = p != null && AppRepo.reminders.any { it.streamId == ch.streamId && it.start == p.start }
    FocusBox(
        onClick = onPlay,
        // OK maintenu : rappel sur une émission à venir, favori sinon
        onLongClick = {
            if (p != null && !live) {
                val on = AppRepo.toggleReminder(ch, p)
                android.widget.Toast.makeText(
                    ctx, if (on) "Rappel : « ${p.title} » à ${hm(p.start)}" else "Rappel supprimé",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else onFav()
        },
        onFocus = { onFocus(p) },
        focusRequester = requester,
        shape = RoundedCornerShape(4.dp),
        background = if (live) Panel2 else Panel,
        focusedBackground = Color.White,
        modifier = Modifier.width(seg.width).fillMaxHeight().padding(horizontal = 1.dp),
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (reminded) "🔔 " else "") + (p?.title ?: "Pas d'information"),
                color = if (focused) Bg else if (p == null) TextDim else Color.White,
                fontSize = 13.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
