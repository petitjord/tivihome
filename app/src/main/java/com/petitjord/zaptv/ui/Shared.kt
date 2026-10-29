package com.petitjord.zaptv.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petitjord.zaptv.data.AppRepo
import com.petitjord.zaptv.data.Category
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ALL = "__all__"
const val FAV = "__fav__"

private val hmFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
fun hm(ms: Long): String = synchronized(hmFormat) { hmFormat.format(Date(ms)) }

fun toggleFav(ctx: Context, key: String, name: String) {
    val added = AppRepo.toggleFavorite(key)
    val msg = if (added) "« $name » ajouté aux favoris" else "« $name » retiré des favoris"
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

/** Colonne des catégories : la catégorie change dès qu'on passe dessus. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CategoryColumn(
    groups: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LazyColumn(
        state = state,
        modifier = modifier.width(200.dp).fillMaxHeight().background(Color(0xFF10141B)).focusRestorer(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(groups) { _, g ->
            val sel = g.id == selectedId
            FocusBox(
                onClick = { onSelect(g.id) },
                onFocus = { onSelect(g.id) },
                background = if (sel) AccentDim else Color.Transparent,
                modifier = Modifier.fillMaxWidth().height(42.dp),
            ) { focused ->
                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        g.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                        color = if (sel || focused) Color.White else TextDim,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** Logo de chaîne (ou initiales si absent). */
@Composable
fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF232B38)), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            Text(name.take(3).uppercase(), color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Affiche de film / série (format 2:3). */
@Composable
fun Poster(
    title: String,
    image: String?,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    FocusBox(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocus = onFocus,
        focusRequester = focusRequester,
        background = Panel2,
        scaleOnFocus = 1.06f,
        modifier = modifier.aspectRatio(2f / 3f),
    ) { focused ->
        if (image != null) {
            AsyncImage(
                model = image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                Text(title, color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp)
            }
        }
        if (focused || image == null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
            ) {
                if (image != null) {
                    Text(title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (favorite) {
            Text("★", color = Gold, fontSize = 18.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

@Composable
fun SectionHeader(text: String, sub: String? = null) {
    Row(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) {
            Spacer(Modifier.width(10.dp))
            Text(sub, color = TextDim, fontSize = 14.sp)
        }
    }
}
