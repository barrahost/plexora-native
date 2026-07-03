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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.theme.PlexoraOrange

@androidx.media3.common.util.UnstableApi
@Composable
fun MoviesScreen(creds: XtreamCredentials) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var movies by remember { mutableStateOf<List<XtreamMovie>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<XtreamMovie?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(creds) {
        runCatching {
            categories = service.getVodCategories(creds.username, creds.password)
            movies = service.getVodStreams(creds.username, creds.password)
        }
        loading = false
    }

    val filtered = remember(movies, selectedCat) {
        if (selectedCat == null) movies else movies.filter { it.categoryId == selectedCat }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val movie = selected
    if (movie != null) {
        MovieDetail(
            movie = movie,
            playing = playing,
            streamUrl = { XtreamClient.vodStreamUrl(creds.url, creds.username, creds.password, movie.streamId, movie.containerExtension ?: "mp4") },
            onPlay = { playing = true },
            onBack = { selected = null; playing = false },
        )
        return
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

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF030712)),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered) { m ->
                Column(
                    Modifier.clickable { selected = m },
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F2937)),
                    ) {
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

@androidx.media3.common.util.UnstableApi
@Composable
private fun MovieDetail(
    movie: XtreamMovie,
    playing: Boolean,
    streamUrl: () -> String,
    onPlay: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (playing) {
            LiveVideoPlayer(streamUrl(), Modifier.fillMaxSize())
        } else {
            val poster = movie.streamIcon ?: movie.cover
            if (!poster.isNullOrBlank()) {
                AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Color(0xFF111827)))
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(movie.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Lecture")
                    }
                }
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Text("< Retour", color = Color.White)
        }
    }
}
