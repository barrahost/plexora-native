package com.dinfras.plexora

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.dinfras.plexora.data.XtreamCredentials
import com.dinfras.plexora.ui.screens.*
import com.dinfras.plexora.ui.theme.PlexoraBackground
import com.dinfras.plexora.ui.theme.PlexoraTheme
import com.dinfras.plexora.ui.theme.PlexoraViolet
import kotlinx.coroutines.delay

private enum class Tab(val label: String) {
    LIVE("Live TV"), MOVIES("Films"), SERIES("Séries"), RADIO("Radio"), SETTINGS("Paramètres")
}

// Marge de securite TV (overscan) : de nombreux televiseurs (ex. TCL) rognent
// les bords exterieurs de l'image. Sans cette marge, la barre de navigation
// en haut de l'ecran etait invisible car coupee par la TV.
private val TvSafeArea = PaddingValues(horizontal = 32.dp, vertical = 20.dp)

class MainActivity : ComponentActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlexoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSplash by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(1000)
                        showSplash = false
                    }

                    if (showSplash) {
                        SplashScreen()
                    } else {
                        Box(Modifier.fillMaxSize().padding(TvSafeArea)) {
                            AppContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(PlexoraBackground), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.logo_plexora_login),
            contentDescription = "Plexora",
            modifier = Modifier.width(280.dp),
        )
    }
}

@UnstableApi
@Composable
private fun AppContent() {
    val context = LocalContext.current
    var creds by remember { mutableStateOf<XtreamCredentials?>(null) }
    var tab by remember { mutableStateOf(Tab.LIVE) }
    val current = creds

    // Bouton Retour, du plus profond (ecrans) au plus superficiel (ici) :
    // Compose empile les BackHandler dans l'ordre de composition — celui du
    // composant le plus imbrique (ex. lecteur plein ecran ouvert dans
    // LiveTvScreen) est toujours prioritaire sur ceux-ci, sans coordination
    // manuelle necessaire (contrairement au bricolage fait cote web).

    // Le plus superficiel : retour a l'onglet racine avant de quitter
    BackHandler(enabled = tab != Tab.LIVE) { tab = Tab.LIVE }

    // Le fallback ultime : double-appui sous 2s pour quitter l'app
    var lastBack by remember { mutableStateOf(0L) }
    BackHandler(enabled = true) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) {
            (context as? Activity)?.finish()
        } else {
            lastBack = now
            Toast.makeText(context, "Appuie de nouveau sur Retour pour quitter", Toast.LENGTH_SHORT).show()
        }
    }

    if (current == null) {
        LoginScreen(onLoggedIn = { creds = it })
    } else {
        Column(Modifier.fillMaxSize()) {
            TopNav(active = tab, onSelect = { tab = it })
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.LIVE -> LiveTvScreen(current)
                    Tab.MOVIES -> MoviesScreen(current)
                    Tab.SERIES -> SeriesScreen(current)
                    Tab.RADIO -> RadioScreen(current)
                    Tab.SETTINGS -> SettingsScreen(
                        activeCreds = current,
                        onLogout = { creds = null; tab = Tab.LIVE },
                        onSwitchPlaylist = { creds = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopNav(active: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF111827)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_plexora_nav),
            contentDescription = "Plexora",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(36.dp).padding(end = 20.dp),
        )
        Tab.entries.forEach { t ->
            val isActive = t == active
            Text(
                t.label,
                modifier = Modifier
                    .clickable { onSelect(t) }
                    .background(if (isActive) PlexoraViolet else Color.Transparent, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}
