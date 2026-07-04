package com.dinfras.plexora.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.dinfras.plexora.data.BufferMode
import com.dinfras.plexora.data.BufferPrefs

// ExoPlayer lit nativement HEVC/H.265 et l'audio Dolby (AC3/EAC3) sans les
// limitations du <video> WebView — c'était le principal blocage de la
// version Capacitor pour les flux 4K/Dolby de ce fournisseur.
//
// User-Agent explicite : le UA par defaut d'ExoPlayer (AndroidXMedia3/...)
// se faisait rejeter par ce serveur IPTV (flux qui ne demarre jamais dans
// notre appli alors qu'il fonctionne dans VLC/TiviMate, qui envoient un
// User-Agent different). On imite un navigateur pour passer ce filtrage.
private const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@UnstableApi
@Composable
fun LiveVideoPlayer(streamUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bufferMode by produceState(initialValue = BufferMode.MEDIUM) {
        value = BufferPrefs.get(context)
    }

    val exoPlayer = remember(streamUrl, bufferMode) {
        val (minMs, maxMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs) = when (bufferMode) {
            BufferMode.NONE -> intArrayOf(1_000, 3_000, 500, 500)
            BufferMode.SMALL -> intArrayOf(5_000, 15_000, 2_000, 5_000)
            BufferMode.MEDIUM -> intArrayOf(15_000, 30_000, 2_000, 5_000)
            BufferMode.HIGH -> intArrayOf(30_000, 60_000, 2_000, 5_000)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minMs, maxMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
            .build()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
