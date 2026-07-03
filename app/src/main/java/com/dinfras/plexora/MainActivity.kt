package com.dinfras.plexora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Text("Plexora", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = PlexoraViolet)
    }
}

@UnstableApi
@Composable
private fun AppContent() {
    var creds by remember { mutableStateOf<XtreamCredentials?>(null) }
    var tab by remember { mutableStateOf(Tab.LIVE) }
    val current = creds

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
                    Tab.SETTINGS -> SettingsScreen(onLogout = { creds = null; tab = Tab.LIVE })
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
        Text("Plexora", fontWeight = FontWeight.Bold, color = PlexoraViolet, modifier = Modifier.padding(end = 24.dp))
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
