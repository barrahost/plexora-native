package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.data.*

private sealed interface SearchResult {
    val name: String
    data class Channel(val channel: XtreamChannel) : SearchResult { override val name get() = channel.name }
    data class Movie(val movie: XtreamMovie) : SearchResult { override val name get() = movie.name }
    data class Series(val series: XtreamSeries) : SearchResult { override val name get() = series.name }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun SearchScreen(creds: XtreamCredentials) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var channels by remember { mutableStateOf<List<XtreamChannel>>(emptyList()) }
    var movies by remember { mutableStateOf<List<XtreamMovie>>(emptyList()) }
    var series by remember { mutableStateOf<List<XtreamSeries>>(emptyList()) }
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    var openMovie by remember { mutableStateOf<XtreamMovie?>(null) }
    var openSeries by remember { mutableStateOf<XtreamSeries?>(null) }
    var openChannel by remember { mutableStateOf<XtreamChannel?>(null) }

    LaunchedEffect(creds) {
        runCatching {
            categories = service.getLiveCategories(creds.username, creds.password)
            channels = service.getLiveStreams(creds.username, creds.password).filter { it.streamId > 0 }
            movies = service.getVodStreams(creds.username, creds.password).filter { it.streamId > 0 }
            series = service.getSeriesList(creds.username, creds.password).filter { it.seriesId > 0 }
        }
        loading = false
    }

    openMovie?.let { m ->
        MovieDetail(creds = creds, service = service, movie = m, onBack = { openMovie = null })
        return
    }
    openSeries?.let { s ->
        SeriesDetail(series = s, creds = creds, service = service, onBack = { openSeries = null })
        return
    }
    openChannel?.let { ch ->
        LiveFullscreenPlayer(
            creds = creds,
            service = service,
            categories = categories,
            channels = channels,
            channel = ch,
            onChannelChange = { openChannel = it },
            onExit = { openChannel = null },
        )
        return
    }

    val results = remember(query, channels, movies, series) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim()
            (channels.filter { it.name.contains(q, ignoreCase = true) }.map { SearchResult.Channel(it) } +
                movies.filter { it.name.contains(q, ignoreCase = true) }.map { SearchResult.Movie(it) } +
                series.filter { it.name.contains(q, ignoreCase = true) }.map { SearchResult.Series(it) })
                .take(60)
        }
    }

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Rechercher", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.headlineSmall.fontSize)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Nom de la chaîne, du film ou de la série...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            return@Column
        }
        if (query.isBlank()) {
            Text("Commence à taper pour chercher parmi les chaînes, films et séries.", color = Color.Gray)
            return@Column
        }
        if (results.isEmpty()) {
            Text("Aucun résultat pour « $query ».", color = Color.Gray)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(results) { r ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            when (r) {
                                is SearchResult.Channel -> openChannel = r.channel
                                is SearchResult.Movie -> openMovie = r.movie
                                is SearchResult.Series -> openSeries = r.series
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (icon, label) = when (r) {
                        is SearchResult.Channel -> Icons.Filled.Tv to "TV"
                        is SearchResult.Movie -> Icons.Filled.Movie to "Film"
                        is SearchResult.Series -> Icons.Filled.Theaters to "Série"
                    }
                    Icon(icon, contentDescription = label, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontWeight = FontWeight.SemiBold)
                        Text(label, color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
            }
        }
    }
}
