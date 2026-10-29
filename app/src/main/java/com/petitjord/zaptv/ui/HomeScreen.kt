package com.petitjord.zaptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Section(val label: String, val icon: ImageVector)

private val sections = listOf(
    Section("TV en direct", Icons.Filled.PlayArrow),
    Section("Guide TV", Icons.Filled.DateRange),
    Section("Films", Icons.Filled.Star),
    Section("Séries", Icons.Filled.List),
    Section("Favoris", Icons.Filled.Favorite),
    Section("Paramètres", Icons.Filled.Settings),
)

@Composable
fun HomeScreen(nav: Nav, focusContent: Boolean) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val railFocus = rememberInitialFocus(!focusContent)

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(196.dp).fillMaxHeight().background(Panel).padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "ZapTV", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp, bottom = 18.dp),
            )
            sections.forEachIndexed { i, s ->
                val isSel = i == selected
                FocusBox(
                    onClick = { selected = i },
                    focusRequester = if (isSel) railFocus else null,
                    background = if (isSel) AccentDim else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) { focused ->
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(s.icon, null, tint = if (isSel || focused) Color.White else TextDim, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.label, color = if (isSel || focused) Color.White else TextDim, fontSize = 15.sp)
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (selected) {
                0 -> LiveSection(nav, focusContent)
                1 -> GuideSection(nav, focusContent)
                2 -> MoviesSection(nav, focusContent)
                3 -> SeriesSection(nav, focusContent)
                4 -> FavoritesSection(nav, focusContent)
                else -> SettingsSection(nav)
            }
        }
    }
}
