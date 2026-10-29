package com.petitjord.tivihome.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Channel
import com.petitjord.tivihome.data.Programme
import com.petitjord.tivihome.data.VodItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReplaySection(nav: Nav, focusContent: Boolean) {
    val data = rememberLoad("live-streams") { AppRepo.liveStreams().filter { it.archiveDays > 0 } }
    LoadView(data) { channels ->
        if (channels.isEmpty()) {
            EmptyText("Ton fournisseur ne propose pas de replay sur tes chaînes.\nLes chaînes avec archives apparaîtront ici automatiquement.")
        } else {
            ReplayContent(channels, nav, focusContent)
        }
    }
}

/** Minuit (heure locale) il y a [daysAgo] jours. */
private fun dayStart(daysAgo: Int): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -daysAgo)
}.timeInMillis

private fun dayLabel(daysAgo: Int): String = when (daysAgo) {
    0 -> "Aujourd'hui"
    1 -> "Hier"
    else -> SimpleDateFormat("EEE d MMM", Locale.FRANCE).format(Date(dayStart(daysAgo)))
        .replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ReplayContent(channels: List<Channel>, nav: Nav, focusContent: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var chIdx by rememberSaveable { mutableIntStateOf(0) }
    var day by rememberSaveable { mutableIntStateOf(0) }
    val ch = channels[chIdx.coerceIn(0, channels.lastIndex)]
    val restore = rememberInitialFocus(focusContent)

    Row(Modifier.fillMaxSize()) {
        // Chaînes avec replay
        LazyColumn(
            modifier = Modifier.width(270.dp).fillMaxHeight().background(Color(0xFF10141B)).focusRestorer(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text("Replay", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 10.dp))
            }
            itemsIndexed(channels) { i, c ->
                val sel = i == chIdx
                FocusBox(
                    onClick = { chIdx = i },
                    onFocus = { if (chIdx != i) { chIdx = i; day = 0 } },
                    background = if (sel) AccentDim else Color.Transparent,
                    focusRequester = if (sel) restore else null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(c.icon, c.name, Modifier.size(width = 52.dp, height = 34.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(c.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${c.archiveDays} j", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
            // Jours disponibles
            val days = (0 until ch.archiveDays.coerceIn(1, 14)).toList()
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(46.dp).focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(days) { d ->
                    FocusBox(
                        onClick = { day = d },
                        onFocus = { day = d },
                        background = if (d == day) AccentDim else Panel,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(38.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp).height(38.dp), contentAlignment = Alignment.Center) {
                            Text(dayLabel(d), color = Color.White, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            val progs = rememberLoad("replay-${ch.streamId}") {
                delay(250)
                AppRepo.replayProgrammes(ch)
            }
            LoadView(progs) { all ->
                val from = dayStart(day)
                val to = from + 24 * 3_600_000L
                val list = remember(all, day) { all.filter { it.start in from until to }.sortedByDescending { it.start } }
                if (list.isEmpty()) {
                    EmptyText("Aucune émission en replay ce jour-là sur ${ch.name}.")
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(day, ch.streamId) { listState.scrollToItem(0) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(list) { p ->
                            ReplayRow(p) {
                                scope.launch {
                                    try {
                                        val url = AppRepo.replayUrl(ch, p)
                                        nav.playVod(VodItem(null, "${ch.name} · ${p.title}", url, ch.icon))
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(ctx, "Replay indisponible", android.widget.Toast.LENGTH_SHORT).show()
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

@Composable
private fun ReplayRow(p: Programme, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        background = Panel,
        focusedBackground = Color(0xFF243044),
        scaleOnFocus = 1.01f,
        modifier = Modifier.fillMaxWidth().height(66.dp),
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(92.dp)) {
                Text(hm(p.start), color = if (focused) Accent else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${(p.end - p.start) / 60_000} min", color = TextDim, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(p.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.description.isNotBlank()) {
                    Text(p.description, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("⟲", color = if (focused) Accent else TextDim, fontSize = 20.sp)
        }
    }
}
