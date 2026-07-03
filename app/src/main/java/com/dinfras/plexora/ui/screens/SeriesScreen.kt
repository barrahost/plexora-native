package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
            item {
                Text(
                    "Toutes les séries",
                    modifier = Modifier.fillMaxWidth().clickable { selectedCat = null }
                        .background(if (selectedCat == null) PlexoraOrange.copy(alpha = 0.2f) else Color.Transparent)
                        .padding(16.dp, 12.dp),
                    fontWeight = if (selectedCat == null) FontWeight.Bold else FontWeight.Normal,
                )
            }
            items(categories) { cat ->
                val active = selectedCat == cat.categoryId
                Text(
                    cat.categoryName,
                    modifier = Modifier.fillMaxWidth().clickable { selectedCat = cat.categoryId }
                        .background(if (active) PlexoraOrange.copy(alpha = 0.2f) else Color.Transparent)
                        .padding(16.dp, 12.dp),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF030712)),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered) { s ->
                Column(Modifier.clickable { selected = s }) {
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

@androidx.media3.common.util.UnstableApi
@Composable
private fun SeriesDetail(series: XtreamSeries, creds: XtreamCredentials, service: XtreamService, onBack: () -> Unit) {
    var episodesBySeason by remember { mutableStateOf<Map<String, List<SeriesEpisode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var activeEp by remember { mutableStateOf<SeriesEpisode?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(series) {
        runCatching {
            val info = service.getSeriesInfo(creds.username, creds.password, series.seriesId)
            episodesBySeason = info.episodes ?: emptyMap()
            selectedSeason = episodesBySeason.keys.firstOrNull()
        }
        loading = false
    }

    val ep = activeEp
    if (ep != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val url = remember(ep) {
                XtreamClient.seriesStreamUrl(creds.url, creds.username, creds.password, ep.id, ep.containerExtension ?: "mp4")
            }
            LiveVideoPlayer(url, Modifier.fillMaxSize())
            TextButton(onClick = { activeEp = null }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text("< Retour", color = Color.White)
            }
        }
        return
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF030712))) {
        Column(Modifier.width(200.dp).fillMaxHeight().background(Color(0xFF111827)).padding(8.dp)) {
            TextButton(onClick = onBack) { Text("< Retour") }
            Spacer(Modifier.height(8.dp))
            Text(series.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            if (loading) {
                CircularProgressIndicator(Modifier.padding(16.dp))
            } else {
                episodesBySeason.keys.forEach { season ->
                    val active = selectedSeason == season
                    Text(
                        "Saison $season",
                        modifier = Modifier.fillMaxWidth().clickable { selectedSeason = season }
                            .background(if (active) PlexoraOrange.copy(alpha = 0.25f) else Color.Transparent)
                            .padding(10.dp),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
            val episodes = episodesBySeason[selectedSeason] ?: emptyList()
            items(episodes) { e ->
                Text(
                    "E${e.episodeNum} — ${e.title ?: "Épisode ${e.episodeNum}"}",
                    modifier = Modifier.fillMaxWidth().clickable { activeEp = e }.padding(16.dp, 12.dp),
                )
            }
        }
    }
}
