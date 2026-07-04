package com.dinfras.plexora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.AppUiState
import com.dinfras.plexora.ui.theme.PlexoraOrange

// Sequence identique a la version web : categories -> chaines -> apercu
// (lecteur + EPG en dessous) -> plein ecran sur une seconde action.
@androidx.media3.common.util.UnstableApi
@Composable
fun LiveTvScreen(creds: XtreamCredentials, onCategoriesVisibleChange: (Boolean) -> Unit = {}) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    val memCached = remember { CatalogCache.getLive() }
    var categories by remember { mutableStateOf(memCached?.categories ?: emptyList()) }
    var channels by remember { mutableStateOf(memCached?.channels ?: emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var activeChannel by remember { mutableStateOf<XtreamChannel?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    // Deja en cache (memoire ou disque) : on affiche instantanement, pas
    // d'ecran de chargement — comme TiviMate. Sinon, chargement classique.
    var loading by remember { mutableStateOf(memCached == null) }
    var error by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(creds) {
        var haveData = memCached != null
        if (!haveData) {
            CatalogCache.loadLiveFromDisk(context)?.let {
                categories = it.categories
                channels = it.channels
                loading = false
                haveData = true
            }
        }
        runCatching {
            val newCategories = service.getLiveCategories(creds.username, creds.password)
            val newChannels = service.getLiveStreams(creds.username, creds.password).filter { it.streamId > 0 }
            categories = newCategories
            channels = newChannels
            CatalogCache.setLive(context, CatalogCache.LiveData(newCategories, newChannels))
            if (PlayerPrefs.getResumeLastChannel(context)) {
                val lastId = PlayerPrefs.getLastChannelId(context)
                if (lastId != null) activeChannel = newChannels.firstOrNull { it.streamId == lastId }
            }
        }.onFailure { if (!haveData) error = it.message ?: it.toString() }
        loading = false
    }

    // Guide TV complet (XMLTV) telecharge une seule fois par session en
    // arriere-plan, comme TiviMate — les panneaux EPG consultent ensuite ce
    // cache local au lieu d'interroger le serveur chaine par chaine.
    LaunchedEffect(creds) {
        LocalEpgStore.loadFromDisk(context)
        LocalEpgStore.refreshOnceIfNeeded(context, creds)
    }

    // Retient la derniere chaine regardee pour la reprendre au prochain
    // lancement si le reglage correspondant est active.
    LaunchedEffect(activeChannel) {
        activeChannel?.let { PlayerPrefs.setLastChannelId(context, it.streamId) }
    }

    val filteredChannels = remember(channels, selectedCat) {
        if (selectedCat == null) channels else channels.filter { it.categoryId == selectedCat }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Erreur de chargement :\n$error", color = Color.Red)
        }
        return
    }

    // La colonne categories se replie des qu'on en valide une (OK), comme
    // sur Films/Series, pour laisser plus de place a la liste des chaines
    // et au programme. Fleche GAUCHE depuis la liste des chaines, ou
    // Retour, la refait reapparaitre.
    var categoriesCollapsed by remember { mutableStateOf(false) }
    var categoryFocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(categoriesCollapsed) { onCategoriesVisibleChange(!categoriesCollapsed) }

    // Bouton Retour : rouvre les categories, puis deselectionne la chaine
    // (le plein ecran gere son propre Retour — guide TV puis sortie — dans LiveFullscreenPlayer)
    BackHandler(enabled = categoriesCollapsed) { categoriesCollapsed = false }
    BackHandler(enabled = !categoriesCollapsed && !fullscreen && activeChannel != null) { activeChannel = null }

    val firstCategoryFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(categoriesCollapsed) {
        if (!categoriesCollapsed) firstCategoryFocusRequester.requestFocus()
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (!categoriesCollapsed) {
                LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
                    item {
                        CategoryEntryRow(
                            label = "Toutes les chaînes",
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

            LazyColumn(Modifier.width(280.dp).fillMaxHeight().background(Color(0xFF0B0F19).copy(alpha = AppUiState.overlayAlpha.floatValue))) {
                itemsIndexed(filteredChannels) { idx, ch ->
                    // Selectionner une chaine demarre directement la lecture en
                    // plein ecran, sans etape intermediaire d'apercu — comme TiviMate.
                    ChannelRow(idx, ch, activeChannel?.streamId == ch.streamId, creds, service) {
                        activeChannel = ch
                        fullscreen = true
                    }
                }
            }

            // Colonne 3 : petit apercu (haut, avec fiche programme flottante) + programme complet de la chaine (bas)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val channel = activeChannel
                var epg by remember(channel) { mutableStateOf<List<EpgItem>>(emptyList()) }
                LaunchedEffect(channel) {
                    epg = if (channel == null) emptyList() else service.getEpgForChannel(creds.username, creds.password, channel)
                }

                Box(Modifier.height(200.dp).fillMaxWidth().background(Color.Black)) {
                    if (channel == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sélectionne une chaîne", color = Color.Gray)
                        }
                    } else {
                        val url = remember(channel) {
                            XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, channel.streamId)
                        }
                        LiveVideoPlayer(url, Modifier.fillMaxSize().clickable { fullscreen = true })
                        IconButton(onClick = { fullscreen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Plein écran", tint = Color.White)
                        }

                        val now = epg.firstOrNull { it.nowPlaying == 1 } ?: epg.firstOrNull()
                        if (now != null) {
                            ProgramInfoCard(now, Modifier.align(Alignment.TopEnd).padding(12.dp))
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF030712))) {
                    if (channel != null) {
                        ChannelEpgList(channel = channel, epg = epg, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        val fsChannel = activeChannel
        if (fullscreen && fsChannel != null) {
            LiveFullscreenPlayer(
                creds = creds,
                service = service,
                categories = categories,
                channels = channels,
                channel = fsChannel,
                onChannelChange = { activeChannel = it },
                onExit = { fullscreen = false },
            )
        }
    }
}
