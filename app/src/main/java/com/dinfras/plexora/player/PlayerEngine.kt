package com.dinfras.plexora.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Moteur de lecture principal, Hilt-scope (etape 4/6 du portage
// architecture StreamVault-IPTV, voir le plan) — generalise
// LiveFullscreenPlayerSession (qui ne servait qu'au zapping Live) a TOUTE la
// lecture "principale" de l'appli : Live plein ecran, Films, Series, Radio.
// Ces contenus sont mutuellement exclusifs (un seul a la fois occupe le
// lecteur principal), donc un seul ExoPlayer partage entre eux est correct
// et evite toute reconstruction au changement de chaine/contenu — comme
// Media3PlayerEngine chez StreamVault.
//
// L'apercu EPG (petite vignette dans le guide TV, LiveOsd.kt) reste sur
// LiveVideoPlayer (lecteur ephemere independant) : c'est un contenu
// secondaire qui ne doit jamais interrompre le lecteur principal en cours.
@Singleton
class PlayerEngine @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile private var player: ExoPlayer? = null
    @Volatile private var settingsUsed: PlayerSettings? = null

    @UnstableApi
    @Synchronized
    internal fun playerFor(streamUrl: String, settings: PlayerSettings): ExoPlayer {
        val existing = player
        return if (existing != null && settingsUsed == settings) {
            com.dinfras.plexora.data.DebugLog.event("PlayerEngine: reuse existing player")
            existing.apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                playWhenReady = true
            }
        } else {
            com.dinfras.plexora.data.DebugLog.event("PlayerEngine: (re)build player (premiere fois ou reglages changes)")
            existing?.release()
            buildLivePlayer(context, streamUrl, settings).also {
                player = it
                settingsUsed = settings
            }
        }
    }

    // Appele a la fermeture reelle du lecteur principal (LiveFullscreenActivity
    // .onDestroy(), ou la sortie du plein ecran Films/Series) : plus aucune
    // raison de garder ce lecteur vivant.
    @Synchronized
    fun release() {
        player?.release()
        player = null
        settingsUsed = null
    }
}
