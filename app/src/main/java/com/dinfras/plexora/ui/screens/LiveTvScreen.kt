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
import com.dinfras.plexora.player.rememberLiveExoPlayer
import com.dinfras.plexora.ui.AppUiState
import com.dinfras.plexora.ui.FullscreenHost
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
    // Toujours vrai au depart, meme si le catalogue est deja en cache : tant
    // qu'on n'a pas verifie s'il faut reprendre la derniere chaine, afficher
    // le menu (meme brievement) puis basculer en plein ecran donnait un
    // flash visible du menu avant la lecture. Le spinner reste donc affiche
    // jusqu'a ce que cette decision soit prise (voir plus bas).
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(creds) {
        var haveData = memCached != null
        var stale = memCached == null || CatalogCache.isStale(memCached.fetchedAt)
        if (!haveData) {
            CatalogCache.loadLiveFromDisk(context)?.let {
                categories = it.categories
                channels = it.channels
                haveData = true
                stale = CatalogCache.isStale(it.fetchedAt)
            }
        }
        // Le catalogue est deja recupere une fois en entier juste apres la
        // connexion (CatalogDownloadScreen) : on ne relance l'appel reseau
        // ici que s'il n'y a rien en cache, ou que le cache a plus de 24h —
        // sinon on declenchait un double appel qui provoquait un blocage
        // cote serveur (HTTP 451), et le catalogue ne se rafraichissait
        // plus jamais une fois telecharge.
        if (!haveData || stale) {
            if (creds.isM3u()) {
                val catalog = M3uCatalogSync.refreshAll(context, creds)
                if (catalog != null) {
                    categories = catalog.liveCategories
                    channels = catalog.liveChannels
                } else if (!haveData) {
                    error = "Impossible de récupérer la playlist M3U."
                }
            } else {
                runCatching {
                    val newCategories = service.getLiveCategories(creds.username, creds.password)
                    val newChannels = service.getLiveStreams(creds.username, creds.password).filter { it.streamId > 0 }
                    categories = newCategories
                    channels = newChannels
                    CatalogCache.setLive(context, CatalogCache.LiveData(newCategories, newChannels))
                }.onFailure { if (!haveData) error = friendlyNetworkError(it) }
            }
        }
        if (PlayerPrefs.getResumeLastChannel(context)) {
            val lastId = PlayerPrefs.getLastChannelId(context)
            val resumed = lastId?.let { id -> channels.firstOrNull { it.streamId == id } }
            if (resumed != null) {
                activeChannel = resumed
                // Reprend directement en plein ecran, pas juste en apercu —
                // comme TiviMate au demarrage. Court delai avant d'entrer en
                // plein ecran : au tout premier lancement (a froid), la fenetre
                // de l'Activity n'est pas encore totalement prete a l'affichage
                // (edge-to-edge, insets...) — initialiser ExoPlayer/la surface
                // video trop tot dans cette fenetre produisait un ecran noir
                // (audio seul) et un blocage complet de l'appli. Le zapping
                // manuel (fenetre deja stable) n'est pas concerne, donc pas de
                // delai ajoute la-bas.
                kotlinx.coroutines.delay(600)
                fullscreen = true
            }
        }
        loading = false
    }

    // Guide TV complet (XMLTV) telecharge une seule fois par session en
    // arriere-plan, comme TiviMate — les panneaux EPG consultent ensuite ce
    // cache local au lieu d'interroger le serveur chaine par chaine.
    LaunchedEffect(creds) {
        LocalEpgStore.loadFromDisk(context)
        // Pour une M3U, le guide est deja declenche via M3uCatalogSync (url-tvg
        // de l'en-tete) — il n'y a pas de xmltv.php a interroger ici.
        if (!creds.isM3u()) LocalEpgStore.refreshOnceIfNeeded(context, creds)
    }

    // Retient la derniere chaine regardee pour la reprendre au prochain
    // lancement si le reglage correspondant est active.
    LaunchedEffect(activeChannel) {
        activeChannel?.let { PlayerPrefs.setLastChannelId(context, it.streamId) }
    }

    // Filtre d'affichage : masque les categories decochees a l'import
    // (assistant). On travaille ensuite sur ces listes filtrees partout —
    // liste, colonne categories ET zapping plein ecran.
    val hidden = com.dinfras.plexora.data.CategoryVisibility.hidden.value
    val visibleCategories = remember(categories, hidden) {
        categories.filter { !com.dinfras.plexora.data.CategoryVisibility.isLiveHidden(it.categoryId) }
    }
    val visibleChannels = remember(channels, hidden) {
        channels.filter { !com.dinfras.plexora.data.CategoryVisibility.isLiveHidden(it.categoryId) }
    }

    val filteredChannels = remember(visibleChannels, selectedCat) {
        if (selectedCat == null) visibleChannels else visibleChannels.filter { it.categoryId == selectedCat }
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
    LaunchedEffect(categoriesCollapsed, fullscreen) {
        // Ne pas reclamer le focus des categories quand on est en plein ecran :
        // sinon ce focus entre en conflit avec celui du lecteur plein ecran
        // (rendu par-dessus), et la telecommande ne pilote plus rien a l'ecran.
        if (!categoriesCollapsed && !fullscreen) firstCategoryFocusRequester.requestFocus()
    }

    // Un seul ExoPlayer/decodeur pour la chaine active, partage entre l'apercu
    // et le plein ecran : sur certaines TV, le decodeur materiel n'a qu'une
    // seule instance disponible — en creer un second pour la MEME chaine juste
    // apres avoir libere le premier (apercu -> plein ecran) laissait le
    // nouveau decodeur incapable de demarrer (image noire, son seul).
    val liveUrl = activeChannel?.let { ch ->
        ch.directUrl ?: XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, ch.streamId)
    }
    val sharedPlayer = liveUrl?.let { com.dinfras.plexora.player.rememberLiveExoPlayer(it) }

    Box(Modifier.fillMaxSize()) {
        // Colonnes + apercu masques en plein ecran : sinon le petit lecteur
        // d'apercu (colonne 3) reste affiche en parallele du plein ecran.
        if (!fullscreen) Row(Modifier.fillMaxSize()) {
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
                    items(visibleCategories) { cat ->
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
                    // 1er clic : selectionne la chaine, affiche l'apercu + EPG
                    // (colonne 3) sans passer en plein ecran. 2e clic sur la
                    // meme chaine (deja active) : lance le plein ecran.
                    ChannelRow(idx, ch, activeChannel?.streamId == ch.streamId, creds, service) {
                        if (activeChannel?.streamId == ch.streamId) {
                            fullscreen = true
                        } else {
                            activeChannel = ch
                        }
                    }
                }
            }

            // Colonne 3 : petit apercu (haut, avec fiche programme flottante) + programme complet de la chaine (bas)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val channel = activeChannel
                var epg by remember(channel) { mutableStateOf<List<EpgItem>>(emptyList()) }
                LaunchedEffect(channel) {
                    epg = if (channel == null) emptyList() else service.getEpgForChannel(creds.username, creds.password, channel, channels)
                }

                Box(Modifier.height(200.dp).fillMaxWidth().background(Color.Black)) {
                    if (channel == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sélectionne une chaîne", color = Color.Gray)
                        }
                    } else {
                        LiveVideoPlayer(
                            liveUrl!!,
                            Modifier.fillMaxSize().clickable { fullscreen = true },
                            externalPlayer = sharedPlayer,
                        )
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

        // Publie dans FullscreenHost (rendu au niveau racine de l'activite,
        // hors marge overscan) au lieu d'afficher le lecteur ici — sinon la
        // lecture plein ecran restait confinee dans la zone de contenu
        // reduite par cette marge, comme une fenetre au lieu de tout l'ecran.
        val fsChannel = activeChannel
        val fsPlayer = sharedPlayer
        LaunchedEffect(fullscreen, fsChannel, fsPlayer) {
            FullscreenHost.content.value = if (fullscreen && fsChannel != null && fsPlayer != null) {
                {
                    LiveFullscreenPlayer(
                        creds = creds,
                        service = service,
                        categories = visibleCategories,
                        channels = visibleChannels,
                        channel = fsChannel,
                        player = fsPlayer,
                        onChannelChange = { activeChannel = it },
                        onExit = { fullscreen = false },
                    )
                }
            } else {
                null
            }
        }
        DisposableEffect(Unit) { onDispose { FullscreenHost.content.value = null } }
    }
}
