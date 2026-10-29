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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Category
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Programme

private val DP_PER_MIN = 5.dp
private const val WINDOW_MIN = 12 * 60          // 12 heures de grille
private val CHANNEL_CELL = 190.dp
private val ROW_HEIGHT = 54.dp

@Composable
fun GuideSection(nav: Nav, focusContent: Boolean) {
    val data = rememberLoad("live") { AppRepo.liveCategories() to AppRepo.liveStreams() }
    LoadView(data) { (cats, channels) -> GuideContent(cats, channels, nav, focusContent) }
}

private data class Focused(val channel: Channel, val prog: Programme?)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GuideContent(cats: List<Category>, channels: List<Channel>, nav: Nav, focusContent: Boolean) {
    val groups = remember(cats) { listOf(Category(FAV, "★ Favoris"), Category(ALL, "Toutes")) + cats }
    val favs = AppRepo.favorites
    var catId by rememberSaveable { mutableStateOf(cats.firstOrNull()?.id ?: ALL) }
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
    LaunchedEffect(catId) { vState.scrollToItem(0) }
    var focused by remember { mutableStateOf<Focused?>(null) }
    val restore = rememberInitialFocus(focusContent)

    Column(Modifier.fillMaxSize()) {
        GuideHeader(focused, Modifier.fillMaxWidth().height(118.dp))

        // Groupes de chaînes
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(48.dp).focusRestorer(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groups.filter { !AppRepo.groupInvisible(it.id) }) { g ->
                val sel = g.id == catId
                FocusBox(
                    onClick = { catId = g.id },
                    onFocus = { catId = g.id },
                    background = if (sel) AccentDim else Panel,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(36.dp),
                ) { _ ->
                    Box(Modifier.padding(horizontal = 14.dp).height(36.dp), contentAlignment = Alignment.Center) {
                        Text(g.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }

        TimeRuler(windowStart, hScroll)

        if (AppRepo.groupNeedsPin(catId)) {

            LockedGroupView()

        } else if (list.isEmpty()) {
            EmptyText("Aucune chaîne dans ce groupe.")
        } else {
            LazyColumn(
                state = vState,
                modifier = Modifier.fillMaxSize().focusRestorer(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(list) { i, ch ->
                    GuideRow(
                        ch = ch,
                        windowStart = windowStart,
                        hScroll = hScroll,
                        firstCellRequester = if (i == 0) restore else null,
                        onFocus = { p -> focused = Focused(ch, p) },
                        onPlay = { nav.playLive(list, i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideHeader(f: Focused?, modifier: Modifier) {
    Row(modifier.background(Panel).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (f == null) {
            Text("Guide TV — OK : regarder · OK maintenu sur une émission à venir : rappel", color = TextDim)
        } else {
            ChannelLogo(f.channel.icon, f.channel.name, Modifier.size(width = 120.dp, height = 80.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(f.prog?.title ?: f.channel.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(f.channel.name)
                        f.prog?.let { append("  ·  ${hm(it.start)} – ${hm(it.end)}") }
                    },
                    color = Accent, fontSize = 13.sp,
                )
                val d = f.prog?.description.orEmpty()
                if (d.isNotBlank()) Text(d, color = Color(0xFFB8C0CC), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TimeRuler(windowStart: Long, hScroll: ScrollState) {
    Row(Modifier.fillMaxWidth().height(28.dp).background(Color(0xFF10141B))) {
        Box(Modifier.width(CHANNEL_CELL).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text("Aujourd'hui", color = TextDim, fontSize = 12.sp)
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
    windowStart: Long,
    hScroll: ScrollState,
    firstCellRequester: androidx.compose.ui.focus.FocusRequester?,
    onFocus: (Programme?) -> Unit,
    onPlay: () -> Unit,
) {
    val windowEnd = windowStart + WINDOW_MIN * 60_000L
    val epg = rememberLoad("epg-${ch.streamId}") { AppRepo.shortEpg(ch, 24) }

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 1.dp)) {
        Row(
            Modifier.width(CHANNEL_CELL).fillMaxHeight().background(Panel).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(ch.icon, ch.name, Modifier.size(width = 52.dp, height = 34.dp))
            Spacer(Modifier.width(8.dp))
            Text(ch.name, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
            Row(Modifier.width(DP_PER_MIN * WINDOW_MIN).fillMaxHeight()) {
                val segs: List<Seg> = when (val s = epg.state) {
                    is Load.Ok -> buildSegments(s.data, windowStart, windowEnd)
                    else -> emptyList()
                }
                if (epg.state is Load.Loading) {
                    Box(Modifier.width(DP_PER_MIN * 120).fillMaxHeight().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text("…", color = TextDim)
                    }
                }
                var first = true
                for (seg in segs) {
                    when (seg) {
                        is Seg.Gap -> Spacer(Modifier.width(seg.width))
                        is Seg.Prog -> {
                            ProgCell(seg, ch, if (first) firstCellRequester else null, onFocus, onPlay)
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
    requester: androidx.compose.ui.focus.FocusRequester?,
    onFocus: (Programme?) -> Unit,
    onPlay: () -> Unit,
) {
    val p = seg.p
    val live = p?.isNow() ?: true
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val reminded = p != null && AppRepo.reminders.any { it.streamId == ch.streamId && it.start == p.start }
    FocusBox(
        onClick = onPlay,
        onLongClick = if (p == null || live) null else {
            {
                val on = AppRepo.toggleReminder(ch, p)
                android.widget.Toast.makeText(
                    ctx,
                    if (on) "Rappel : « ${p.title} » à ${hm(p.start)}" else "Rappel supprimé",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onFocus = { onFocus(p) },
        focusRequester = requester,
        shape = RoundedCornerShape(4.dp),
        background = if (live) Color(0xFF1E2A3A) else Panel,
        focusedBackground = AccentDim,
        modifier = Modifier.width(seg.width).fillMaxHeight().padding(horizontal = 1.dp),
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.Center) {
            Text(
                (if (reminded) "🔔 " else "") + (p?.title ?: "Pas d'information"), color = Color.White, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (p != null) {
                Text("${hm(p.start)} – ${hm(p.end)}", color = TextDim, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
