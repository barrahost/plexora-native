package com.dinfras.plexora.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.FullscreenHost
import com.dinfras.plexora.ui.theme.PlexoraOrange
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
@Composable
fun SeriesScreen(creds: XtreamCredentials, onCategoriesVisibleChange: (Boolean) -> Unit = {}) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    val screenContext = LocalContext.current
    val memCached = remember { CatalogCache.getSeries() }
    var categories by remember { mutableStateOf(memCached?.categories ?: emptyList()) }
    var seriesList by remember { mutableStateOf(memCached?.series ?: emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<XtreamSeries?>(null) }
    // Deja en cache (memoire ou disque) : affichage instantane, pas d'ecran de chargement.
    var loading by remember { mutableStateOf(memCached == null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(creds) {
        var haveData = memCached != null
        var stale = memCached == null || CatalogCache.isStale(memCached.fetchedAt)
        if (!haveData) {
            CatalogCache.loadSeriesFromDisk(screenContext)?.let {
                categories = it.categories
                seriesList = it.series
                loading = false
                haveData = true
                stale = CatalogCache.isStale(it.fetchedAt)
            }
        }
        // Recupere une fois en entier juste apres la connexion
        // (CatalogDownloadScreen) : on ne relance l'appel reseau ici que
        // s'il n'y a rien en cache, ou que le cache a plus de 24h.
        if (!haveData || stale) {
            if (creds.isM3u()) {
                val catalog = M3uCatalogSync.refreshAll(screenContext, creds)
                if (catalog != null) {
                    categories = catalog.seriesCategories
                    seriesList = catalog.series
                } else if (!haveData) {
                    error = "Impossible de récupérer la playlist M3U."
                }
            } else {
                runCatching {
                    val newCategories = service.getSeriesCategories(creds.username, creds.password)
                    val newSeries = service.getSeriesList(creds.username, creds.password).filter { it.seriesId > 0 }
                    categories = newCategories
                    seriesList = newSeries
                    CatalogCache.setSeries(screenContext, CatalogCache.SeriesData(newCategories, newSeries))
                }.onFailure { if (!haveData) error = friendlyNetworkError(it) }
            }
        }
        M3uSeriesEpisodesStore.loadFromDisk(screenContext)
        loading = false
    }

    // Filtre d'affichage : categories decochees a l'import (assistant).
    val hidden = com.dinfras.plexora.data.CategoryVisibility.hidden.value
    val visibleCategories = remember(categories, hidden) {
        categories.filter { !com.dinfras.plexora.data.CategoryVisibility.isSeriesHidden(it.categoryId) }
    }
    val visibleSeries = remember(seriesList, hidden) {
        seriesList.filter { !com.dinfras.plexora.data.CategoryVisibility.isSeriesHidden(it.categoryId) }
    }

    val filtered = remember(visibleSeries, selectedCat) {
        if (selectedCat == null) visibleSeries else visibleSeries.filter { it.categoryId == selectedCat }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Erreur de chargement :\n$error", color = Color.Red) }
        return
    }

    val series = selected
    if (series != null) {
        SeriesDetail(series = series, creds = creds, service = service, onBack = { selected = null })
        return
    }

    var focused by remember { mutableStateOf(filtered.firstOrNull()) }

    // Meme comportement que Films : la colonne categories se replie des
    // qu'on en valide une, fleche GAUCHE (ou Retour) la refait reapparaitre.
    var categoriesCollapsed by remember { mutableStateOf(false) }
    var categoryFocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(categoriesCollapsed) { onCategoriesVisibleChange(!categoriesCollapsed) }

    BackHandler(enabled = categoriesCollapsed) { categoriesCollapsed = false }

    val firstCategoryFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(categoriesCollapsed) {
        if (!categoriesCollapsed) firstCategoryFocusRequester.requestFocus()
    }

    Row(Modifier.fillMaxSize()) {
        if (!categoriesCollapsed) {
            LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
                item {
                    CategoryEntryRow(
                        label = "Toutes les séries",
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

        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF030712))) {
            val fs = focused
            if (fs != null) {
                MediaPreviewInfo(
                    title = fs.name,
                    rating = fs.rating5based,
                    releaseDate = null,
                    genre = categories.firstOrNull { it.categoryId == fs.categoryId }?.categoryName,
                    plot = null,
                    cast = null,
                    director = null,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered) { s ->
                    var isFocused by remember(s) { mutableStateOf(false) }
                    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "posterScale")
                    Column(
                        Modifier
                            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) focused = s }
                            .focusable()
                            .clickable { selected = s }
                            .scale(scale),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F2937))
                                .then(if (isFocused) Modifier.border(3.dp, PlexoraOrange, RoundedCornerShape(10.dp)) else Modifier),
                        ) {
                            if (!s.cover.isNullOrBlank()) {
                                AsyncImage(model = s.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.name,
                            maxLines = 2,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = if (isFocused) PlexoraOrange else Color.White,
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

// Fiche serie en plein largeur (banniere + synopsis + actions + episodes en
// bande horizontale), comme le repere fourni par l'utilisateur — remplace
// l'ancienne disposition en 3 colonnes.
@androidx.media3.common.util.UnstableApi
@Composable
fun SeriesDetail(series: XtreamSeries, creds: XtreamCredentials, service: XtreamService, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<SeriesFullInfo?>(null) }
    var episodesBySeason by remember { mutableStateOf<Map<String, List<SeriesEpisode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var activeEp by remember { mutableStateOf<SeriesEpisode?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(series) {
        saved = MyListStore.isSavedSeries(context, series.seriesId)
        if (creds.isM3u()) {
            // Pas d'endpoint get_series_info pour une playlist M3U : les
            // episodes sont deja connus depuis le parsing (M3uSeriesEpisodesStore).
            episodesBySeason = M3uSeriesEpisodesStore.episodesFor(series.seriesId).groupBy { it.season.toString() }
            selectedSeason = episodesBySeason.keys.firstOrNull()
        } else {
            runCatching {
                val result = service.getSeriesInfo(creds.username, creds.password, series.seriesId)
                info = result.info
                episodesBySeason = result.episodes ?: emptyMap()
                selectedSeason = episodesBySeason.keys.firstOrNull()
            }
        }
        loading = false
    }

    // Bouton Retour : episode ouvert -> fiche serie. Selectionner un
    // episode demarre directement la lecture en plein ecran, sans etape
    // intermediaire d'apercu — comme TiviMate.
    BackHandler(enabled = activeEp != null) { activeEp = null }
    BackHandler(enabled = activeEp == null) { onBack() }

    val ep = activeEp
    // Publie dans FullscreenHost (rendu hors marge overscan) au lieu
    // d'afficher le lecteur ici, pour une lecture qui couvre reellement
    // tout l'ecran de la TV.
    LaunchedEffect(ep) {
        if (ep != null) {
            // Un episode issu d'une playlist M3U a directement son URL de flux
            // stockee dans "id" (pas de stream_id Xtream a combiner).
            val url = if (ep.id.startsWith("http")) ep.id else XtreamClient.seriesStreamUrl(creds.url, creds.username, creds.password, ep.id, ep.containerExtension ?: "mp4")
            val content: @Composable () -> Unit = { FullscreenPlayer(streamUrl = url, title = "${series.name} — Épisode ${ep.episodeNum}", onClose = { activeEp = null }) }
            FullscreenHost.content.value = content
        } else {
            FullscreenHost.content.value = null
        }
    }
    DisposableEffect(Unit) { onDispose { FullscreenHost.content.value = null } }

    if (ep != null) {
        return
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF030712)).verticalScroll(rememberScrollState())) {
        // Banniere : arriere-plan + degrade + titre/infos superposes
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            val backdrop = info?.backdropPath?.firstOrNull() ?: series.cover
            if (!backdrop.isNullOrBlank()) {
                AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF030712), Color(0xFF030712).copy(alpha = 0.4f), Color.Transparent))))
            Column(Modifier.align(Alignment.BottomStart).padding(24.dp).widthIn(max = 720.dp)) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("< Retour", color = Color.White) }
                Spacer(Modifier.height(4.dp))
                val year = info?.releaseDate?.take(4)
                Text(
                    if (!year.isNullOrBlank()) "${series.name} ($year)" else series.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rating = info?.rating?.toDoubleOrNull() ?: series.rating5based
                    if (rating != null && rating > 0) {
                        Text(
                            String.format("%.1f", rating),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.background(PlexoraOrange, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    val meta = listOfNotNull(
                        year,
                        "${episodesBySeason.size} saison${if (episodesBySeason.size > 1) "s" else ""}".takeIf { episodesBySeason.isNotEmpty() },
                        info?.genre,
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, color = Color.Gray, fontSize = 13.sp)
                }
                info?.cast?.let { if (it.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("Acteurs : $it", color = Color(0xFF9CA3AF), fontSize = 13.sp, maxLines = 1) } }
                info?.director?.let { if (it.isNotBlank()) { Text("Réalisateur : $it", color = Color(0xFF9CA3AF), fontSize = 13.sp, maxLines = 1) } }
                info?.plot?.let { if (it.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(it, color = Color.LightGray, fontSize = 13.sp, maxLines = 3) } }
            }
        }

        // Actions
        val firstSeason = episodesBySeason.keys.firstOrNull()
        val firstEp = episodesBySeason[selectedSeason ?: firstSeason]?.firstOrNull()
        Row(Modifier.fillMaxWidth().padding(24.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(
                icon = Icons.Filled.PlayArrow,
                label = if (firstEp != null) "Regarder S${selectedSeason ?: firstSeason} E${firstEp.episodeNum}" else "Regarder",
                enabled = firstEp != null,
                primary = true,
            ) { activeEp = firstEp }
            ActionButton(icon = Icons.Filled.OpenInNew, label = "Ouvrir dans un lecteur externe", enabled = firstEp != null) {
                val e = firstEp ?: return@ActionButton
                val url = if (e.id.startsWith("http")) e.id else XtreamClient.seriesStreamUrl(creds.url, creds.username, creds.password, e.id, e.containerExtension ?: "mp4")
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(url), "video/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
            val trailer = info?.youtubeTrailer
            if (!trailer.isNullOrBlank()) {
                ActionButton(icon = Icons.Filled.Movie, label = "Bande annonce") {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$trailer"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
            ActionButton(icon = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, label = "Ajouter à ma liste") {
                scope.launch { saved = MyListStore.toggleSeries(context, series.seriesId) }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            return@Column
        }

        // Toutes les saisons empilees, chacune avec sa bande d'episodes —
        // comme le repere fourni (pas besoin de choisir une saison au prealable).
        episodesBySeason.keys.toList().forEach { season ->
            val episodes = episodesBySeason[season] ?: emptyList()
            if (episodes.isEmpty()) return@forEach
            SeasonRow(
                season = season,
                episodes = episodes,
                fallbackCover = series.cover,
                onSelectEpisode = { activeEp = it },
            )
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SeasonRow(season: String, episodes: List<SeriesEpisode>, fallbackCover: String?, onSelectEpisode: (SeriesEpisode) -> Unit) {
    val listState = rememberLazyListState()
    Text("Saison $season", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
    Spacer(Modifier.height(8.dp))
    Box {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(episodes) { e ->
                var isFocused by remember { mutableStateOf(false) }
                Column(
                    Modifier.width(220.dp)
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable { onSelectEpisode(e) },
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1F2937))
                            .then(if (isFocused) Modifier.border(3.dp, PlexoraOrange, RoundedCornerShape(8.dp)) else Modifier),
                    ) {
                        val thumb = e.info?.movieImage ?: fallbackCover
                        if (!thumb.isNullOrBlank()) {
                            AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "E${e.episodeNum} — ${e.title ?: "Épisode ${e.episodeNum}"}",
                        color = if (isFocused) PlexoraOrange else Color.White,
                        maxLines = 1,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        Text(
            "${(listState.firstVisibleItemIndex + 1).coerceAtMost(episodes.size)} / ${episodes.size}",
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp),
        )
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = if (isFocused) PlexoraOrange else if (primary) Color.White else Color(0xFF1F2937)
    val fg = if (isFocused) Color.Black else if (primary) Color.Black else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = enabled)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontWeight = if (primary || isFocused) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

