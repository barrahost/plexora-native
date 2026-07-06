package com.dinfras.plexora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.UnstableApi
import com.dinfras.plexora.data.XtreamChannel
import com.dinfras.plexora.ui.LiveFullscreenLaunchArgs
import com.dinfras.plexora.ui.screens.LiveFullscreenPlayer
import com.dinfras.plexora.ui.theme.PlexoraTheme
import kotlinx.coroutines.delay

// Plein ecran live dans sa propre Activite/fenetre, plutot qu'en calque
// Compose superpose (FullscreenHost) au-dessus de MainActivity : ce calque
// fonctionne pour Films/Series (FullscreenPlayer.kt, plus simple) mais pas
// pour le lecteur live (bien plus riche en etat — OSD, zapping, guide) —
// diagnostique via le journal de bord embarque, sans cause identifiable dans
// le code (la composition s'executait entierement mais ses effets
// n'etaient jamais "committes" a l'ecran). Voir LiveFullscreenLaunchArgs pour
// la transmission des parametres (memoire partagee, pas d'extras d'Intent).
class LiveFullscreenActivity : ComponentActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val creds = LiveFullscreenLaunchArgs.creds
        val service = LiveFullscreenLaunchArgs.service
        val initialChannel = LiveFullscreenLaunchArgs.initialChannel
        if (creds == null || service == null || initialChannel == null) {
            finish()
            return
        }

        setContent {
            PlexoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Attend que la fenetre de cette Activite FRAICHEMENT creee
                    // ait reellement le focus avant de construire l'ExoPlayer :
                    // le construire trop tot (fenetre pas encore posee/edge-to-
                    // edge pas stabilise) laissait le decodeur video attache a
                    // une surface pas encore prete a l'affichage — audio
                    // audible, image noire, et rendu principal bloque (meme
                    // symptome que le cas "reprise au demarrage" deja corrige
                    // par un delai fixe de 600ms, mais qui ne s'appliquait
                    // qu'a ce cas precis, pas au zapping manuel vers le plein
                    // ecran qui cree lui aussi une Activite neuve a chaque
                    // fois).
                    var windowReady by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        var tries = 0
                        while (!windowReady && tries < 80) {
                            windowReady = this@LiveFullscreenActivity.hasWindowFocus()
                            tries++
                            delay(25)
                        }
                    }
                    if (!windowReady) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                        return@Surface
                    }
                    var currentChannel by remember { mutableStateOf(initialChannel) }
                    // Tenu a jour en continu (pas seulement a la sortie) :
                    // l'ecran appelant doit resynchroniser sa chaine active
                    // meme si cette Activite se ferme autrement qu'en passant
                    // par onExit (bouton Accueil, changement d'appli...).
                    LaunchedEffect(currentChannel) { LiveFullscreenLaunchArgs.lastChannel = currentChannel }
                    LiveFullscreenPlayer(
                        creds = creds,
                        service = service,
                        categories = LiveFullscreenLaunchArgs.categories,
                        channels = LiveFullscreenLaunchArgs.channels,
                        channel = currentChannel,
                        onChannelChange = { currentChannel = it },
                        onExit = { finish() },
                    )
                }
            }
        }
    }
}
