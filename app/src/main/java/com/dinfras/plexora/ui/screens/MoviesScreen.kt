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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.theme.PlexoraOrange
import kotlinx.coroutines.delay

@androidx.media3.common.util.UnstableApi
@Composable
fun MoviesScreen(creds: XtreamCredentials) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var movies by remember { mutableStateOf<List<XtreamMovie>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<XtreamMovie?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(creds) {
        runCatching {
            categories = service.getVodCategories(creds.username, creds.password)
            movies = service.getVodStreams(creds.username, creds.password).filter { it.streamId > 0 }
        }.onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    val filtered = remember(movies, selectedCat) {
        if (selectedCat == null) movies else movies.filter { it.categoryId == selectedCat }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Erreur de chargement :\n$error", color = Color.Red) }
        return
    }

    val movie = selected
    if (movie != null) {
        MovieDetail(creds = creds, service = service, movie = movie, onBack = { selected = null })
        return
    }

    var focused by remember { mutableStateOf(filtered.firstOrNull()) }
    var focusedInfo by remember { mutableStateOf<VodInfo?>(null) }
    LaunchedEffect(focused) {
        focusedInfo = null
        val f = focused ?: return@LaunchedEffect
        delay(250)
        focusedInfo = runCatching { service.getVodInfo(creds.username, creds.password, f.streamId).info }.getOrNull()
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
            item {
                Text(
                    "Tous les films",
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

        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF030712))) {
            val fm = focused
            if (fm != null) {
                MediaPreviewInfo(
                    title = fm.name,
                    rating = fm.rating5based,
                    releaseDate = focusedInfo?.releaseDate,
                    genre = focusedInfo?.genre,
                    plot = focusedInfo?.plot,
                    cast = focusedInfo?.cast,
                    director = focusedInfo?.director,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered) { m ->
                    Column(
                        Modifier
                            .clickable { selected = m }
                            .focusable()
                            .onFocusChanged { if (it.isFocused) focused = m },
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F2937))) {
                            val poster = m.streamIcon ?: m.cover
                            if (!poster.isNullOrBlank()) {
                                AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(m.name, maxLines = 2, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun MovieDetail(creds: XtreamCredentials, service: XtreamService, movie: XtreamMovie, onBack: () -> Unit) {
    var playing by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<VodInfo?>(null) }

    // Bouton Retour : plein ecran -> lecture -> fiche film -> grille
    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = !fullscreen && playing) { playing = false }
    BackHandler(enabled = !fullscreen && !playing) { onBack() }

    LaunchedEffect(movie) {
        info = runCatching { service.getVodInfo(creds.username, creds.password, movie.streamId).info }.getOrNull()
    }

    val streamUrl = remember(movie) {
        XtreamClient.vodStreamUrl(creds.url, creds.username, creds.password, movie.streamId, movie.containerExtension ?: "mp4")
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(Color(0xFF030712))) {
            // Affiche fixe a gauche
            Box(Modifier.width(260.dp).fillMaxHeight().background(Color(0xFF111827))) {
                val poster = movie.streamIcon ?: movie.cover
                if (!poster.isNullOrBlank()) {
                    AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text("< Retour", color = Color.White)
                }
            }

            // Apercu (haut) + infos (bas) a droite
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                    if (playing) {
                        LiveVideoPlayer(streamUrl, Modifier.fillMaxSize().clickable { fullscreen = true })
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
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF030712)).padding(16.dp)) {
                    LazyColumn {
                        item { Text(movie.name, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        val i = info
                        if (i != null) {
                            i.releaseDate?.let { d -> item { Spacer(Modifier.height(8.dp)); LabelValue("Date de sortie", d) } }
                            i.genre?.let { g -> item { Spacer(Modifier.height(4.dp)); LabelValue("Genre", g) } }
                            i.plot?.let { p -> item { Spacer(Modifier.height(8.dp)); LabelValue("Description", p) } }
                            i.director?.let { d -> item { Spacer(Modifier.height(4.dp)); LabelValue("Réalisateur", d) } }
                            i.cast?.let { c -> item { Spacer(Modifier.height(4.dp)); LabelValue("Casting", c) } }
                        }
                    }
                }
            }
        }

        if (fullscreen) {
            FullscreenPlayer(streamUrl = streamUrl, title = movie.name, onClose = { fullscreen = false })
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, color = PlexoraOrange, fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
        Text(value, color = Color.LightGray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
    }
}
