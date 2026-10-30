package com.petitjord.tivihome.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Élément navigable à la télécommande.
 * OK = onClick, OK maintenu = onLongClick (ajout aux favoris).
 */
@Composable
fun FocusBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    background: Color = Color.Transparent,
    focusedBackground: Color = Panel2,
    scaleOnFocus: Float = 1f,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val focus by rememberUpdatedState(onFocus)

    // Focus : léger ressort, halo coloré et bordure en dégradé
    val scale by animateFloatAsState(
        if (focused) scaleOnFocus else 1f,
        tween(140),
        label = "scale",
    )
    val bg by animateColorAsState(if (focused) focusedBackground else background, tween(180), label = "bg")
    val glowAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "glow")

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale; scaleY = scale
            }
            .then(
                if (AppTheme.richEffects && glowAlpha > 0.01f) {
                    Modifier.shadow(
                        elevation = (22 * glowAlpha).dp, shape = shape,
                        ambientColor = Accent.copy(alpha = 0.55f * glowAlpha),
                        spotColor = Accent.copy(alpha = 0.75f * glowAlpha),
                    )
                } else Modifier,
            )
            .background(bg, shape)
            .border(2.dp, Color.White.copy(alpha = glowAlpha), shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused) focus?.invoke()
                focused = it.isFocused
            }
            .onKeyEvent { ev ->
                val ok = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                if (!ok) return@onKeyEvent false
                when (ev.type) {
                    KeyEventType.KeyDown -> {
                        if (ev.nativeKeyEvent.repeatCount == 0) pressed = true
                        if (ev.nativeKeyEvent.repeatCount == 1 && longClick != null) {
                            longFired = true
                            longClick?.invoke()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        // N'agit que si l'appui a commencé sur cet élément.
                        if (pressed && !longFired) click()
                        pressed = false
                        longFired = false
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { click() },
                    onLongPress = { longClick?.invoke() },
                )
            },
    ) {
        content(focused)
    }
}

/** Donne le focus à l'élément dès qu'il apparaît (utile au retour du lecteur). */
@Composable
fun rememberInitialFocus(enabled: Boolean, key: Any? = Unit): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(enabled, key) {
        if (!enabled) return@LaunchedEffect
        repeat(20) {
            delay(40)
            if (runCatching { fr.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return fr
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

class Loaded<T>(val state: Load<T>, val retry: () -> Unit)

/** Charge des données en tâche de fond, avec bouton « Réessayer » en cas d'erreur. */
@Composable
fun <T> rememberLoad(key: Any?, loader: suspend () -> T): Loaded<T> {
    var attempt by remember(key) { mutableIntStateOf(0) }
    var state by remember(key) { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(key, attempt) {
        state = Load.Loading
        state = try {
            Load.Ok(loader())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Err(e.message ?: e.javaClass.simpleName)
        }
    }
    return Loaded(state) { attempt++ }
}

@Composable
fun <T> LoadView(loaded: Loaded<T>, content: @Composable (T) -> Unit) {
    when (val s = loaded.state) {
        is Load.Loading -> SkeletonScreen()
        is Load.Err -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Erreur : ${s.message}", color = Color.White, textAlign = TextAlign.Center)
                val fr = rememberInitialFocus(true)
                Button(onClick = loaded.retry, modifier = Modifier.focusRequester(fr)) { Text("Réessayer") }
            }
        }
        is Load.Ok -> content(s.data)
    }
}

@Composable
fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextDim, textAlign = TextAlign.Center)
    }
}
