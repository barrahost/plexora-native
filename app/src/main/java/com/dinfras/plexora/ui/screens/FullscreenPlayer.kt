package com.dinfras.plexora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.AppUiState
import com.dinfras.plexora.ui.theme.PlexoraOrange
import kotlinx.coroutines.delay

private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 5_000L

// Lecteur plein ecran partage entre Films et Series (Live TV a son propre
// OSD dans LiveOsd.kt, avec guide et changement de chaine). Contrairement
// a la version precedente, expose de vrais controles de lecture — lecture/
// pause et avance/recul de 10s — au lieu d'un simple bandeau de titre.
@UnstableApi
@Composable
fun FullscreenPlayer(streamUrl: String, title: String, onClose: () -> Unit) {
    val overlayAlpha = AppUiState.overlayAlpha.floatValue
    val focusRequester = remember { FocusRequester() }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    // requestFocus() echoue silencieusement si le conteneur n'est pas encore
    // pose a l'ecran — on reessaie jusqu'a ce qu'il capte reellement le focus,
    // sinon les touches (OK, avance/recul) ne lui parviennent pas.
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var tries = 0
        while (!hasFocus && tries < 40) {
            runCatching { focusRequester.requestFocus() }
            tries++
            delay(25)
        }
    }

    // Se referme toute seule apres quelques secondes sans interaction, comme
    // n'importe quel lecteur video (YouTube, Netflix...).
    LaunchedEffect(showControls, positionMs) {
        if (showControls) {
            delay(CONTROLS_TIMEOUT_MS)
            showControls = false
        }
    }

    // Position/duree interrogees periodiquement — ExoPlayer n'expose pas de
    // flow pret a l'emploi pour ca, seulement une lecture ponctuelle.
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            positionMs = p.currentPosition.coerceAtLeast(0)
            durationMs = p.duration.coerceAtLeast(0)
            isPlaying = p.playWhenReady
            delay(500)
        }
    }

    BackHandler(enabled = showControls) { showControls = false }
    BackHandler(enabled = !showControls) { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (showControls) {
                            player?.let { it.playWhenReady = !it.playWhenReady }
                        } else {
                            showControls = true
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (showControls) {
                            player?.let { it.seekTo((it.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)) }
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (showControls) {
                            player?.let { it.seekTo((it.currentPosition + SEEK_STEP_MS).coerceAtMost(it.duration.coerceAtLeast(0))) }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        LiveVideoPlayer(streamUrl, Modifier.fillMaxSize(), onPlayerReady = { player = it })

        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = overlayAlpha), Color.Transparent)))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        if (showControls) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "Reculer de 10s", tint = Color.White.copy(alpha = 0.6f))
                    Box(
                        Modifier.size(56.dp).background(PlexoraOrange, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = Color.Black,
                        )
                    }
                    Icon(Icons.Filled.FastForward, contentDescription = "Avancer de 10s", tint = Color.White.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(16.dp))
                val total = durationMs.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { (positionMs.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = PlexoraOrange,
                    trackColor = Color(0xFF374151),
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(positionMs), color = Color.Gray)
                    Text(formatDuration(durationMs), color = Color.Gray)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
