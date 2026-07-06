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
import androidx.compose.ui.unit.sp
import com.dinfras.plexora.data.*
import com.dinfras.plexora.ui.AppUiState
import com.dinfras.plexora.ui.LiveFullscreenLaunchArgs
import com.dinfras.plexora.ui.theme.PlexoraOrange

// Sequence identique a la version web : categories -> chaines -> apercu
// (lecteur + EPG en dessous) -> plein ecran sur une seconde action.
//
// La reprise automatique de la derniere chaine (resumeLastChannel) ne doit
// se produire qu'une seule fois par lancement d'appli, a la toute premiere
// entree dans l'onglet TV — pas a chaque retour depuis un autre onglet.
// Sans ce garde-fou, le spinner devait rester affiche a CHAQUE visite de cet
// onglet (meme catalogue deja en cache) pour eviter un flash du menu avant
// le saut en plein ecran, alors que TiviMate affiche la liste instantanement
// des le deuxieme passage.
private object LiveTabSessionState {
    @Volatile var resumeHandled = false
}

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
    // Instantane des le catalogue en cache ET la reprise deja geree une fois
    // cette session — sinon (premiere entree) on garde le spinner le temps
    // de decider s'il faut reprendre la derniere chaine en plein ecran.
    var loading by remember { mutableStateOf(!(memCached != null && LiveTabSessionState.resumeHandled)) }
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
        if (!LiveTabSessionState.resumeHandled) {
            LiveTabSessionState.resumeHandled = true
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
                            com.dinfras.plexora.data.DebugLog.event("clic chaine active -> fullscreen=true (${ch.name})")
                            fullscreen = true
                        } else {
                            com.dinfras.plexora.data.DebugLog.event("clic chaine -> apercu (${ch.name})")
                            activeChannel = ch
                        }
                    }
                }
            }

            // Colonne 3 : fiche chaine (nom + programme en cours, sans lecteur
            // video) + programme complet en dessous — comme Films/Series, dont
            // la fiche de detail n'affiche jamais de video avant le plein
            // ecran. Le 1er lecteur ExoPlayer de la session n'est donc plus
            // construit qu'au moment ou l'utilisateur demande vraiment la
            // lecture (2e clic -> plein ecran), au lieu d'etre cree des le
            // premier clic rien que pour un apercu.
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val channel = activeChannel
                var epg by remember(channel) { mutableStateOf<List<EpgItem>>(emptyList()) }
                LaunchedEffect(channel) {
                    epg = if (channel == null) emptyList() else service.getEpgForChannel(creds.username, creds.password, channel, channels)
                }

                Box(
                    Modifier.height(120.dp).fillMaxWidth().background(Color.Black)
                        .clickable(enabled = channel != null) { fullscreen = true },
                ) {
                    if (channel == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sélectionne une chaîne", color = Color.Gray)
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                            Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Appuie pour lancer en plein écran", color = Color.Gray, fontSize = 13.sp)
                        }
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

        // Plein ecran live dans sa propre Activite (voir LiveFullscreenActivity) :
        // le calque Compose superpose (FullscreenHost) ne "committait" jamais
        // ses effets pour ce lecteur riche en etat (OSD, zapping, guide), alors
        // que Films/Series (plus simples) fonctionnent avec ce meme calque.
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        ) {
            // A la fermeture de l'Activite plein ecran, resynchronise la chaine
            // active (zapping effectue pendant le plein ecran).
            LiveFullscreenLaunchArgs.lastChannel?.let { activeChannel = it }
        }
        LaunchedEffect(fullscreen) {
            val fsChannel = activeChannel
            if (fullscreen && fsChannel != null) {
                LiveFullscreenLaunchArgs.creds = creds
                LiveFullscreenLaunchArgs.service = service
                LiveFullscreenLaunchArgs.categories = visibleCategories
                LiveFullscreenLaunchArgs.channels = visibleChannels
                LiveFullscreenLaunchArgs.initialChannel = fsChannel
                launcher.launch(android.content.Intent(context, com.dinfras.plexora.LiveFullscreenActivity::class.java))
                fullscreen = false
            }
        }
    }
}
