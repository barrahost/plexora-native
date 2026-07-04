package com.dinfras.plexora

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.dinfras.plexora.data.XtreamCredentials
import com.dinfras.plexora.ui.screens.*
import com.dinfras.plexora.ui.theme.PlexoraBackground
import com.dinfras.plexora.ui.theme.PlexoraTheme
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    SEARCH("Rechercher", Icons.Filled.Search),
    LIVE("TV", Icons.Filled.Tv),
    MOVIES("Films", Icons.Filled.Movie),
    SERIES("Séries", Icons.Filled.Theaters),
    SETTINGS("Paramètres", Icons.Filled.Settings),
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
        // La barre laterale se replie en icones des qu'on navigue dans une
        // section a plusieurs colonnes (TV/Films/Series), pour laisser la
        // place au lecteur et aux informations EPG — comme sur TiviMate.
        val sidebarExpanded = tab == Tab.SEARCH || tab == Tab.SETTINGS
        Row(Modifier.fillMaxSize()) {
            Sidebar(active = tab, expanded = sidebarExpanded, onSelect = { tab = it })
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    Tab.SEARCH -> SearchScreen(current)
                    Tab.LIVE -> LiveTvScreen(current)
                    Tab.MOVIES -> MoviesScreen(current)
                    Tab.SERIES -> SeriesScreen(current)
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

// Barre laterale persistante inspiree de TiviMate : logo en haut, puis les
// sections principales de l'appli, toujours visibles pour une navigation
// rapide au D-pad.
@Composable
private fun Sidebar(active: Tab, expanded: Boolean, onSelect: (Tab) -> Unit) {
    val width by animateDpAsState(if (expanded) 200.dp else 72.dp, animationSpec = tween(200), label = "sidebarWidth")
    Column(
        Modifier.width(width).fillMaxHeight().background(Color(0xFF0B0F19)).padding(vertical = 16.dp),
    ) {
        if (expanded) {
            Image(
                painter = painterResource(R.drawable.logo_plexora_nav),
                contentDescription = "Plexora",
                modifier = Modifier.height(32.dp).padding(horizontal = 16.dp),
            )
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.logo_plexora_login),
                    contentDescription = "Plexora",
                    modifier = Modifier.height(28.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        // Deux etats de selection distincts, comme sur TiviMate : le gris
        // indique la section actuellement ouverte, le blanc indique la
        // position du curseur D-pad — il faut appuyer sur OK pour que le
        // curseur devienne la section active.
        var focusedTab by remember { mutableStateOf<Tab?>(null) }
        Tab.entries.forEach { t ->
            val isActive = t == active
            val isFocused = t == focusedTab
            val bg = when {
                isFocused -> Color.White
                isActive -> Color(0xFF374151)
                else -> Color.Transparent
            }
            val fg = when {
                isFocused -> Color.Black
                isActive -> Color.White
                else -> Color(0xFF9CA3AF)
            }
            Row(
                Modifier.fillMaxWidth()
                    .onFocusChanged { focusedTab = if (it.isFocused) t else if (focusedTab == t) null else focusedTab }
                    .focusable()
                    .clickable { onSelect(t) }
                    .background(bg, RoundedCornerShape(8.dp))
                    .padding(horizontal = if (expanded) 16.dp else 0.dp, vertical = 12.dp),
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!expanded) Spacer(Modifier.weight(1f))
                Icon(t.icon, contentDescription = t.label, tint = fg)
                if (expanded) {
                    Spacer(Modifier.width(12.dp))
                    Text(t.label, color = fg, fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Normal)
                }
                if (!expanded) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
