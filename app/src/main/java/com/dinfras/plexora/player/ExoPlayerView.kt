package com.dinfras.plexora.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.dinfras.plexora.data.BufferMode
import com.dinfras.plexora.data.BufferPrefs
import com.dinfras.plexora.data.DecoderMode
import com.dinfras.plexora.data.PlayerPrefs

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

// Reordonne les decodeurs disponibles selon la preference materiel/logiciel,
// sans jamais en exclure completement (retombe sur les autres si le type
// demande n'existe pas sur l'appareil).
private fun codecSelector(audioMode: DecoderMode, videoMode: DecoderMode): MediaCodecSelector =
    MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        val mode = if (mimeType.startsWith("audio/")) audioMode else videoMode
        when (mode) {
            DecoderMode.AUTO -> infos
            DecoderMode.HARDWARE -> infos.sortedByDescending { it.hardwareAccelerated }
            DecoderMode.SOFTWARE -> infos.sortedBy { it.hardwareAccelerated }
        }
    }

private data class PlayerSettings(
    val bufferMode: BufferMode,
    val audioDecoder: DecoderMode,
    val videoDecoder: DecoderMode,
    val tunneling: Boolean,
)

@UnstableApi
private fun buildLivePlayer(context: android.content.Context, streamUrl: String, settings: PlayerSettings): ExoPlayer {
    com.dinfras.plexora.data.DebugLog.event("buildLivePlayer start")
    val (minMs, maxMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs) = when (settings.bufferMode) {
        BufferMode.NONE -> intArrayOf(1_000, 3_000, 500, 500)
        BufferMode.SMALL -> intArrayOf(5_000, 15_000, 2_000, 5_000)
        BufferMode.MEDIUM -> intArrayOf(15_000, 30_000, 2_000, 5_000)
        BufferMode.HIGH -> intArrayOf(30_000, 60_000, 2_000, 5_000)
    }
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(minMs, maxMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
        // Plafond en octets : cause averee (logcat) du gel plein ecran live sur
        // la TCL — un flux TS live a un debit bien superieur a un film VOD, et
        // 30s de tampon depassaient le heap Java de la TV (192 Mo) :
        // OutOfMemoryError, GC bloquant en boucle de 3-4s (ecran noir fige,
        // son seul), puis kill systeme (FinalizerWatchdog timeout).
        // v2.36 avait mis prioritizeTimeOverSizeThresholds=false avec un
        // plafond de 16 Mo : trop strict pour ce flux, le tampon butait sur la
        // limite d'octets avant d'atteindre bufferForPlaybackMs, et la lecture
        // ne demarrait jamais (spinner bloque indefiniment). On remonte le
        // plafond a 48 Mo (marge large sous le heap 192 Mo) et on repasse
        // prioritizeTimeOverSizeThresholds a true (defaut) pour que le seuil
        // de temps puisse quand meme declencher la lecture si le plafond
        // d'octets est atteint avant.
        .setTargetBufferBytes(48 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(true)
        // Pas de retention du flux deja lu (inutile en live, ca ne fait que
        // consommer de la memoire en plus).
        .setBackBuffer(0, false)
        .build()
    val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(PLAYER_USER_AGENT)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(true)
    val renderersFactory = DefaultRenderersFactory(context)
        .setMediaCodecSelector(codecSelector(settings.audioDecoder, settings.videoDecoder))
        .setEnableDecoderFallback(true)
    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters().setTunnelingEnabled(settings.tunneling).build()
    }
    com.dinfras.plexora.data.DebugLog.event("buildLivePlayer: ExoPlayer.Builder().build() start")
    val built = ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
    com.dinfras.plexora.data.DebugLog.event("buildLivePlayer: build() done, prepare() start")
    return built
        .apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
        .also { com.dinfras.plexora.data.DebugLog.event("buildLivePlayer: prepare() done, return") }
}

// Cache global (meme principe que CatalogCache) : les preferences lecteur
// sont lues une seule fois par processus au lieu d'a chaque composition.
// Cause averee (journal de diagnostic) du gel plein ecran persistant : sans
// ce cache, CHAQUE nouvelle chaine reconstruisait le lecteur avec des
// reglages par defaut, puis un DEUXIEME ExoPlayer ~200-500ms plus tard des
// que les vrais reglages arrivaient de facon asynchrone (DataStore) — les
// deux instances se disputaient alors le decodeur materiel unique de la TV,
// ce qui pouvait geler l'appli durablement (buildLivePlayer visible deux
// fois de suite dans le journal pour une seule selection de chaine).
private object PlayerSettingsCache {
    @Volatile var cached: PlayerSettings? = null
}

@Composable
private fun rememberPlayerSettings(): PlayerSettings {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(PlayerSettingsCache.cached) }
    LaunchedEffect(Unit) {
        if (loaded == null) {
            loaded = PlayerSettings(
                bufferMode = BufferPrefs.get(context),
                audioDecoder = PlayerPrefs.getAudioDecoder(context),
                videoDecoder = PlayerPrefs.getVideoDecoder(context),
                tunneling = PlayerPrefs.getTunneling(context),
            ).also { PlayerSettingsCache.cached = it }
        }
    }
    return loaded ?: PlayerSettings(BufferMode.MEDIUM, DecoderMode.AUTO, DecoderMode.AUTO, false)
}

@UnstableApi
@Composable
fun LiveVideoPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    onPlayerReady: (ExoPlayer) -> Unit = {},
) {
    val context = LocalContext.current
    val settings = rememberPlayerSettings()
    // Aucun retour visuel pendant la mise en tampon jusqu'ici : l'ecran restait
    // noir/fige sans rien indiquer, contrairement a TiviMate qui affiche un
    // indicateur des le lancement de la lecture.
    var isBuffering by remember { mutableStateOf(true) }
    // Sans ca, une erreur ExoPlayer (codec/format non supporte, flux invalide)
    // se traduisait par un ecran juste noir sans aucune indication — impossible
    // de savoir si ca bufferise encore ou si la lecture a echoue pour de bon.
    var playerError by remember { mutableStateOf<String?>(null) }

    // Cle sur streamUrl SEUL (pas sur settings) : la toute premiere chaine
    // d'une session fraiche (cache de reglages encore vide) voyait "settings"
    // changer de valeur ~200-500ms apres le montage, des que le chargement
    // asynchrone (DataStore) aboutissait — remember(streamUrl, settings)
    // reconstruisait alors un DEUXIEME ExoPlayer pendant que le premier
    // negociait encore le decodeur materiel, gelant l'appli durablement
    // (confirme par le journal : deux buildLivePlayer, puis plus aucun
    // evenement jusqu'au redemarrage force). Le lecteur est maintenant
    // construit une seule fois par chaine avec les reglages disponibles a cet
    // instant precis (cache si deja chaud, sinon valeurs par defaut) ; un
    // reglage modifie dans Parametres ne s'appliquera qu'au changement de
    // chaine suivant, ce qui est un compromis largement acceptable face au
    // gel total.
    val exoPlayer = remember(streamUrl) {
        isBuffering = true
        playerError = null
        buildLivePlayer(context, streamUrl, settings)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val name = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "?$playbackState"
                }
                com.dinfras.plexora.data.DebugLog.event("onPlaybackStateChanged $name")
                isBuffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            }
            override fun onRenderedFirstFrame() {
                com.dinfras.plexora.data.DebugLog.event("onRenderedFirstFrame")
            }
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                com.dinfras.plexora.data.DebugLog.event("onIsPlayingChanged $isPlayingNow")
            }
            override fun onPlayerError(error: PlaybackException) {
                com.dinfras.plexora.data.DebugLog.event("onPlayerError ${error.errorCodeName} ${error.cause?.message ?: error.message}")
                isBuffering = false
                playerError = "${error.errorCodeName}\n${error.cause?.message ?: error.message}"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    LaunchedEffect(exoPlayer) { onPlayerReady(exoPlayer) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                com.dinfras.plexora.data.DebugLog.event("AndroidView factory start")
                // Inflate via res/layout/player_view.xml (surface_type="texture_view") :
                // un PlayerView construit directement en code utilise un
                // SurfaceView, dont la surface de composition separee gere mal
                // les transitions/redimensionnements dans un AndroidView Compose
                // (image noire, telecommande figee un moment).
                (android.view.LayoutInflater.from(ctx)
                    .inflate(com.dinfras.plexora.R.layout.player_view, null) as PlayerView)
                    .apply {
                        // Sans ca, cette vue (focusable par defaut) vole le focus
                        // Android au conteneur Compose qui ecoute les touches D-pad
                        // (OK, fleches) : aucune incrustation/controle ne pouvait
                        // alors jamais s'ouvrir, meme en plein ecran normal.
                        isFocusable = false
                        isFocusableInTouchMode = false
                    }
                    .also { com.dinfras.plexora.data.DebugLog.event("AndroidView factory done") }
            },
            // Sans ce bloc, la vue garde le tout premier lecteur ExoPlayer
            // cree (factory ne s'execute qu'une fois) — des qu'un nouveau
            // lecteur est cree (ex. reglages charges en asynchrone juste
            // apres le montage), l'ancien est libere mais la vue continue
            // de le referencer : plus d'image, parfois du son residuel.
            update = {
                com.dinfras.plexora.data.DebugLog.event("AndroidView update: set player start")
                it.player = exoPlayer
                com.dinfras.plexora.data.DebugLog.event("AndroidView update: set player done")
            },
        )
        if (showLoadingIndicator && isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }
        playerError?.let { msg ->
            Text(
                "Erreur de lecture\n$msg",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }
    }
}
