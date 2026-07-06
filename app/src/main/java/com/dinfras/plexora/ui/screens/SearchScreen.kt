package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.data.*
import com.dinfras.plexora.ui.FullscreenHost

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
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    var openMovie by remember { mutableStateOf<XtreamMovie?>(null) }
    var openSeries by remember { mutableStateOf<XtreamSeries?>(null) }
    var openChannel by remember { mutableStateOf<XtreamChannel?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Recherche : s'appuie sur le catalogue deja telecharge (memoire, sinon
    // disque) au lieu de relancer un appel reseau complet a chaque visite —
    // meme logique que les onglets TV/Films/Series.
    LaunchedEffect(creds) {
        val live = CatalogCache.getLive() ?: CatalogCache.loadLiveFromDisk(context)
        val vod = CatalogCache.getMovies() ?: CatalogCache.loadMoviesFromDisk(context)
        val ser = CatalogCache.getSeries() ?: CatalogCache.loadSeriesFromDisk(context)

        val haveAll = live != null && vod != null && ser != null
        if (live != null) { categories = live.categories; channels = live.channels }
        if (vod != null) movies = vod.movies
        if (ser != null) series = ser.series

        if (!haveAll) {
            if (creds.isM3u()) {
                val catalog = M3uCatalogSync.refreshAll(context, creds)
                if (catalog != null) {
                    categories = catalog.liveCategories
                    channels = catalog.liveChannels
                    movies = catalog.movies
                    series = catalog.series
                } else if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                    error = "Impossible de récupérer la playlist M3U."
                }
            } else {
                runCatching {
                    val newCategories = live?.categories ?: service.getLiveCategories(creds.username, creds.password)
                    val newChannels = live?.channels ?: service.getLiveStreams(creds.username, creds.password).filter { it.streamId > 0 }
                    val newMovies = vod?.movies ?: service.getVodStreams(creds.username, creds.password).filter { it.streamId > 0 }
                    val newSeries = ser?.series ?: service.getSeriesList(creds.username, creds.password).filter { it.seriesId > 0 }
                    categories = newCategories
                    channels = newChannels
                    movies = newMovies
                    series = newSeries
                    if (live == null) CatalogCache.setLive(context, CatalogCache.LiveData(newCategories, newChannels))
                    if (vod == null) CatalogCache.setMovies(context, CatalogCache.MovieData(service.getVodCategories(creds.username, creds.password), newMovies))
                    if (ser == null) CatalogCache.setSeries(context, CatalogCache.SeriesData(service.getSeriesCategories(creds.username, creds.password), newSeries))
                }.onFailure {
                    // N'affiche l'erreur que si on n'a vraiment rien a chercher —
                    // avec un cache partiel, la recherche reste utilisable.
                    if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) error = friendlyNetworkError(it)
                }
            }
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

    // Plein ecran publie dans FullscreenHost (rendu hors marge overscan par
    // MainActivity) au lieu d'etre affiche inline ici. Pas d'apercu prealable
    // ici (ouverture directe depuis la recherche) : on cree donc le lecteur
    // partage nous-memes, pour la meme chaine tout au long du zapping.
    val openChannelUrl = openChannel?.let { ch ->
        ch.directUrl ?: com.dinfras.plexora.data.XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, ch.streamId)
    }
    val openChannelPlayer = openChannelUrl?.let { com.dinfras.plexora.player.rememberLiveExoPlayer(it) }
    LaunchedEffect(openChannel, openChannelPlayer) {
        val ch = openChannel
        val player = openChannelPlayer
        FullscreenHost.content.value = if (ch != null && player != null) {
            {
                LiveFullscreenPlayer(
                    creds = creds,
                    service = service,
                    categories = categories,
                    channels = channels,
                    channel = ch,
                    player = player,
                    onChannelChange = { openChannel = it },
                    onExit = { openChannel = null },
                )
            }
        } else {
            null
        }
    }
    DisposableEffect(Unit) { onDispose { FullscreenHost.content.value = null } }

    // La recherche respecte aussi le filtre d'import (assistant) : une
    // categorie decochee ne ressort pas dans les resultats.
    val hidden = com.dinfras.plexora.data.CategoryVisibility.hidden.value
    val channelResults = remember(query, channels, hidden) {
        if (query.isBlank()) emptyList() else channels.filter { it.name.contains(query.trim(), ignoreCase = true) && !com.dinfras.plexora.data.CategoryVisibility.isLiveHidden(it.categoryId) }.map { SearchResult.Channel(it) }.take(20)
    }
    val movieResults = remember(query, movies, hidden) {
        if (query.isBlank()) emptyList() else movies.filter { it.name.contains(query.trim(), ignoreCase = true) && !com.dinfras.plexora.data.CategoryVisibility.isVodHidden(it.categoryId) }.map { SearchResult.Movie(it) }.take(20)
    }
    val seriesResults = remember(query, series, hidden) {
        if (query.isBlank()) emptyList() else series.filter { it.name.contains(query.trim(), ignoreCase = true) && !com.dinfras.plexora.data.CategoryVisibility.isSeriesHidden(it.categoryId) }.map { SearchResult.Series(it) }.take(20)
    }
    val hasResults = channelResults.isNotEmpty() || movieResults.isNotEmpty() || seriesResults.isNotEmpty()

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
        error?.let {
            Text("Erreur de chargement :\n$it", color = MaterialTheme.colorScheme.error)
            return@Column
        }
        if (query.isBlank()) {
            Text("Commence à taper pour chercher parmi les chaînes, films et séries.", color = Color.Gray)
            return@Column
        }
        if (!hasResults) {
            Text("Aucun résultat pour « $query ».", color = Color.Gray)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (channelResults.isNotEmpty()) {
                item { SearchSectionHeader("Chaînes TV") }
                items(channelResults) { r ->
                    SearchResultRow(r) { openChannel = (r as SearchResult.Channel).channel }
                }
            }
            if (movieResults.isNotEmpty()) {
                item { SearchSectionHeader("Films") }
                items(movieResults) { r ->
                    SearchResultRow(r) { openMovie = (r as SearchResult.Movie).movie }
                }
            }
            if (seriesResults.isNotEmpty()) {
                item { SearchSectionHeader("Séries") }
                items(seriesResults) { r ->
                    SearchResultRow(r) { openSeries = (r as SearchResult.Series).series }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(label: String) {
    Text(
        label,
        color = Color.Gray,
        fontWeight = FontWeight.Bold,
        fontSize = MaterialTheme.typography.labelLarge.fontSize,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SearchResultRow(r: SearchResult, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (isFocused) Color.White else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (r) {
            is SearchResult.Channel -> Icons.Filled.Tv
            is SearchResult.Movie -> Icons.Filled.Movie
            is SearchResult.Series -> Icons.Filled.Theaters
        }
        Icon(icon, contentDescription = null, tint = if (isFocused) Color.Black else Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(r.name, fontWeight = FontWeight.SemiBold, color = if (isFocused) Color.Black else Color.White)
    }
}
