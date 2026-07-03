package com.dinfras.plexora.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dinfras.plexora.data.BufferMode
import com.dinfras.plexora.data.BufferPrefs

// ExoPlayer lit nativement HEVC/H.265 et l'audio Dolby (AC3/EAC3) sans les
// limitations du <video> WebView — c'était le principal blocage de la
// version Capacitor pour les flux 4K/Dolby de ce fournisseur.
@UnstableApi
@Composable
fun LiveVideoPlayer(streamUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bufferMode by produceState(initialValue = BufferMode.MEDIUM) {
        value = BufferPrefs.get(context)
    }

    val exoPlayer = remember(streamUrl, bufferMode) {
        val (minMs, maxMs) = when (bufferMode) {
            BufferMode.SMALL -> 5_000 to 15_000
            BufferMode.MEDIUM -> 15_000 to 30_000
            BufferMode.HIGH -> 30_000 to 60_000
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minMs, maxMs, 2_000, 5_000)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = false
            }
        },
    )
}
