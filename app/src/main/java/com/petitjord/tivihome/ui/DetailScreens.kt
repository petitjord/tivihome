package com.petitjord.tivihome.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Movie
import com.petitjord.tivihome.data.MovieInfo

/** Image de fond floutée et assombrie, derrière une fiche. */
@Composable
fun Backdrop(url: String?) {
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                url, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(30.dp).graphicsLayer { alpha = 0.35f },
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Bg, Bg.copy(alpha = 0.85f), Bg.copy(alpha = 0.55f)))))
    }
}

/** Ouvre la bande-annonce dans l'appli YouTube (ou le navigateur). */
fun openTrailer(ctx: Context, trailer: String) {
    val url = if (trailer.startsWith("http")) trailer else "https://www.youtube.com/watch?v=$trailer"
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Aucune appli pour lire la bande-annonce", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MovieDetailScreen(movie: Movie, nav: Nav) {
    val ctx = LocalContext.current
    val data = rememberLoad("vodinfo-${movie.streamId}") { AppRepo.vodInfo(movie) }
    val info: MovieInfo? = (data.state as? Load.Ok<MovieInfo>)?.data
    val resumeAt = AppRepo.resume.firstOrNull { it.key == movie.favKey }
    val fav = movie.favKey in AppRepo.favorites
    val first = rememberInitialFocus(true)

    Box(Modifier.fillMaxSize()) {
        Backdrop(info?.backdrop ?: movie.poster)
        Row(Modifier.fillMaxSize().padding(40.dp)) {
            Box(Modifier.width(270.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Panel2)) {
                if (movie.poster != null) {
                    AsyncImage(movie.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(movie.name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    info?.year,
                    info?.genre,
                    info?.durationText,
                    (info?.rating ?: movie.rating)?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" },
                ).joinToString("   ·   ")
                if (meta.isNotBlank()) Text(meta, color = Accent, fontSize = 15.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    if (resumeAt != null) {
                        ActionButton("▶  Reprendre à ${formatDuration(resumeAt.positionMs)}", primary = true, requester = first) {
                            nav.playVod(movieItem(movie))
                        }
                        ActionButton("Depuis le début") {
                            AppRepo.forgetProgress(movie.favKey)
                            nav.playVod(movieItem(movie))
                        }
                    } else {
                        ActionButton("▶  Lecture", primary = true, requester = first) { nav.playVod(movieItem(movie)) }
                    }
                    info?.trailer?.let { t -> ActionButton("Bande-annonce") { openTrailer(ctx, t) } }
                    ActionButton(if (fav) "★ Favori" else "☆ Favori") { toggleFav(ctx, movie.favKey, movie.name) }
                }

                when (val s = data.state) {
                    is Load.Loading -> Text("Chargement de la fiche…", color = TextDim)
                    is Load.Err -> Text("Fiche indisponible", color = TextDim)
                    is Load.Ok -> {
                        s.data.plot?.let { Text(it, color = Color(0xFFD7DCE5), fontSize = 16.sp, lineHeight = 23.sp) }
                        s.data.director?.let { Detail("Réalisation", it) }
                        s.data.cast?.let { Detail("Avec", it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text(label, color = TextDim, fontSize = 14.sp, modifier = Modifier.width(110.dp))
        Text(value, color = Color.White, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ActionButton(label: String, primary: Boolean = false, requester: FocusRequester? = null, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        focusRequester = requester,
        background = if (primary) Accent else Color(0x33FFFFFF),
        focusedBackground = if (primary) Accent else Color(0x55FFFFFF),
        scaleOnFocus = 1.06f,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.height(48.dp),
    ) {
        Box(Modifier.padding(horizontal = 22.dp).height(48.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (primary) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
