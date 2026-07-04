package com.dinfras.plexora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.theme.PlexoraOrange

@androidx.media3.common.util.UnstableApi
@Composable
fun SeriesScreen(creds: XtreamCredentials) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var seriesList by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<XtreamSeries?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(creds) {
        runCatching {
            categories = service.getSeriesCategories(creds.username, creds.password)
            seriesList = service.getSeriesList(creds.username, creds.password).filter { it.seriesId > 0 }
        }.onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    val filtered = remember(seriesList, selectedCat) {
        if (selectedCat == null) seriesList else seriesList.filter { it.categoryId == selectedCat }
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

    BackHandler(enabled = categoriesCollapsed) { categoriesCollapsed = false }

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

        Column(
            Modifier.weight(1f).fillMaxHeight().background(Color(0xFF030712))
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && categoriesCollapsed) {
                        categoriesCollapsed = false
                        true
                    } else {
                        false
                    }
                },
        ) {
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
                    Column(
                        Modifier
                            .clickable { selected = s }
                            .focusable()
                            .onFocusChanged { if (it.isFocused) focused = s },
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F2937))) {
                            if (!s.cover.isNullOrBlank()) {
                                AsyncImage(model = s.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(s.name, maxLines = 2, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun SeriesDetail(series: XtreamSeries, creds: XtreamCredentials, service: XtreamService, onBack: () -> Unit) {
    var episodesBySeason by remember { mutableStateOf<Map<String, List<SeriesEpisode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var activeEp by remember { mutableStateOf<SeriesEpisode?>(null) }
    var playing by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(series) {
        runCatching {
            val info = service.getSeriesInfo(creds.username, creds.password, series.seriesId)
            episodesBySeason = info.episodes ?: emptyMap()
            selectedSeason = episodesBySeason.keys.firstOrNull()
        }
        loading = false
    }

    // Reinitialise l'apercu quand on change d'episode
    LaunchedEffect(activeEp) { playing = false }
    val ep = activeEp

    // Bouton Retour : plein ecran -> lecture -> episode -> fiche serie -> grille
    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = !fullscreen && playing) { playing = false }
    BackHandler(enabled = !fullscreen && !playing && activeEp != null) { activeEp = null }
    BackHandler(enabled = !fullscreen && !playing && activeEp == null) { onBack() }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(Color(0xFF030712))) {
            // Affiche + saisons a gauche
            Column(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                    if (!series.cover.isNullOrBlank()) {
                        AsyncImage(model = series.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                TextButton(onClick = onBack) { Text("< Retour") }
                Text(series.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    CircularProgressIndicator(Modifier.padding(16.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    LazyColumn {
                        items(episodesBySeason.keys.toList()) { season ->
                            val active = selectedSeason == season
                            Text(
                                "Saison $season",
                                modifier = Modifier.fillMaxWidth().clickable { selectedSeason = season }
                                    .background(if (active) PlexoraOrange.copy(alpha = 0.25f) else Color.Transparent)
                                    .padding(12.dp, 10.dp),
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Episodes au milieu
            LazyColumn(Modifier.width(260.dp).fillMaxHeight().background(Color(0xFF0B0F19))) {
                val episodes = episodesBySeason[selectedSeason] ?: emptyList()
                items(episodes) { e ->
                    val active = activeEp?.id == e.id
                    Text(
                        "E${e.episodeNum} — ${e.title ?: "Épisode ${e.episodeNum}"}",
                        modifier = Modifier.fillMaxWidth().clickable { activeEp = e }
                            .background(if (active) PlexoraOrange.copy(alpha = 0.2f) else Color.Transparent)
                            .padding(16.dp, 12.dp),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            // Apercu (haut) + infos episode (bas) a droite
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                    if (ep == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sélectionne un épisode", color = Color.Gray)
                        }
                    } else {
                        val url = remember(ep) {
                            XtreamClient.seriesStreamUrl(creds.url, creds.username, creds.password, ep.id, ep.containerExtension ?: "mp4")
                        }
                        if (playing) {
                            LiveVideoPlayer(url, Modifier.fillMaxSize().clickable { fullscreen = true })
                            IconButton(onClick = { fullscreen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = "Plein écran", tint = Color.White)
                            }
                        } else {
                            Box(Modifier.fillMaxSize().clickable { playing = true }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text("Lecture", color = Color.White)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF030712)).padding(16.dp)) {
                    if (ep != null) {
                        Column {
                            Text("S$selectedSeason · E${ep.episodeNum} — ${ep.title ?: ""}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (fullscreen && ep != null) {
            val url = remember(ep) {
                XtreamClient.seriesStreamUrl(creds.url, creds.username, creds.password, ep.id, ep.containerExtension ?: "mp4")
            }
            FullscreenPlayer(streamUrl = url, title = "${series.name} — ${ep.title ?: "Épisode ${ep.episodeNum}"}", onClose = { fullscreen = false })
        }
    }
}
