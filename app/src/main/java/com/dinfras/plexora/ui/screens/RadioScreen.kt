package com.dinfras.plexora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.AppUiState
import com.dinfras.plexora.ui.theme.PlexoraOrange

// Meme logique que TV/Films/Series : categories repliables (blanc = curseur
// D-pad, orange = categorie active), fleche GAUCHE ou Retour les rouvre.
@androidx.media3.common.util.UnstableApi
@Composable
fun RadioScreen(creds: XtreamCredentials, onCategoriesVisibleChange: (Boolean) -> Unit = {}) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var stations by remember { mutableStateOf<List<XtreamChannel>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf<XtreamChannel?>(null) }
    var pausedStation by remember { mutableStateOf<XtreamChannel?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Les stations radio ne sont qu'un sous-ensemble des chaines live (memes
    // categories/flux) : on reutilise le catalogue Live deja recupere (par
    // CatalogDownloadScreen ou l'onglet TV) au lieu de refaire les memes
    // appels reseau, qui declenchaient un blocage cote serveur (HTTP 451).
    LaunchedEffect(creds) {
        var live = CatalogCache.getLive() ?: CatalogCache.loadLiveFromDisk(context)
        val stale = live == null || CatalogCache.isStale(live!!.fetchedAt)
        if (live == null || stale) {
            runCatching {
                val newCategories = service.getLiveCategories(creds.username, creds.password)
                val newChannels = service.getLiveStreams(creds.username, creds.password).filter { it.streamId > 0 }
                CatalogCache.setLive(context, CatalogCache.LiveData(newCategories, newChannels))
                live = CatalogCache.LiveData(newCategories, newChannels)
            }.onFailure { if (live == null) error = friendlyNetworkError(it) }
        }
        live?.let { data ->
            val radioCatIds = data.categories.filter { it.categoryName.contains("radio", ignoreCase = true) }.map { it.categoryId }.toSet()
            categories = data.categories.filter { it.categoryId in radioCatIds }
            stations = data.channels.filter { it.categoryId in radioCatIds }
        }
        loading = false
    }

    val filtered = remember(stations, selectedCat) {
        if (selectedCat == null) stations else stations.filter { it.categoryId == selectedCat }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Erreur de chargement :\n$error", color = Color.Red) }
        return
    }
    if (stations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune station radio disponible sur cet abonnement.", color = Color.Gray)
        }
        return
    }

    // La colonne categories se replie des qu'on en valide une, comme sur
    // Films/Series. Inutile si une seule categorie radio existe.
    var categoriesCollapsed by remember { mutableStateOf(categories.size <= 1) }
    var categoryFocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(categoriesCollapsed) { onCategoriesVisibleChange(!categoriesCollapsed) }

    BackHandler(enabled = categoriesCollapsed && categories.size > 1) { categoriesCollapsed = false }

    val firstCategoryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(categoriesCollapsed) {
        if (!categoriesCollapsed) firstCategoryFocusRequester.requestFocus()
    }

    Row(Modifier.fillMaxSize()) {
        if (categories.size > 1 && !categoriesCollapsed) {
            LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
                item {
                    CategoryEntryRow(
                        label = "Toutes les stations",
                        active = selectedCat == null,
                        focused = categoryFocus == "__all__",
                        onFocus = { categoryFocus = "__all__" },
                        onClick = { selectedCat = null; categoriesCollapsed = true },
                        modifier = Modifier.focusRequester(firstCategoryFocusRequester),
                    )
                }
                items(categories) { cat ->
                    CategoryEntryRow(
                        label = cat.categoryName,
                        active = selectedCat == cat.categoryId,
                        focused = categoryFocus == cat.categoryId,
                        onFocus = { categoryFocus = cat.categoryId },
                        onClick = { selectedCat = cat.categoryId; categoriesCollapsed = true },
                    )
                }
            }
        }

        LazyColumn(Modifier.width(300.dp).fillMaxHeight().background(Color(0xFF111827).copy(alpha = AppUiState.overlayAlpha.floatValue))) {
            items(filtered) { s ->
                // La station reste surlignee meme en pause (active passe alors a
                // null mais pausedStation garde la reference).
                val isActive = (active ?: pausedStation)?.streamId == s.streamId
                var isFocused by remember(s) { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable { active = s; pausedStation = null }
                        .background(if (isFocused) Color.White else if (isActive) PlexoraOrange.copy(alpha = 0.25f) else Color.Transparent)
                        .padding(12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelLogo(s.name, s.streamIcon, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        s.name,
                        color = if (isFocused) Color.Black else if (isActive) PlexoraOrange else Color.White,
                        fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
            val station = active
            val paused = station == null && pausedStation != null
            if (station == null && pausedStation == null) {
                Text("Sélectionne une station", color = Color.Gray)
            } else {
                val displayed = station ?: pausedStation!!
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ChannelLogo(displayed.name, displayed.streamIcon, size = 96.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(displayed.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    IconButton(
                        onClick = {
                            if (paused) {
                                active = pausedStation
                                pausedStation = null
                            } else {
                                pausedStation = station
                                active = null
                            }
                        },
                        modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(PlexoraOrange),
                    ) {
                        Icon(
                            if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) "Lecture" else "Pause",
                            tint = Color.Black,
                        )
                    }
                    if (station != null) {
                        val url = remember(station) { XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, station.streamId) }
                        // Lecture audio : le player video sert de moteur de lecture, la surface reste noire
                        Box(Modifier.size(1.dp)) { LiveVideoPlayer(url, Modifier.size(1.dp), showLoadingIndicator = false) }
                    }
                }
            }
        }
    }
}
