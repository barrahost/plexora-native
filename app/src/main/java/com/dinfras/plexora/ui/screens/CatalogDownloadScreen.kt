package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.R
import com.dinfras.plexora.data.*
import com.dinfras.plexora.ui.theme.PlexoraBackground
import com.dinfras.plexora.ui.theme.PlexoraOrange
import kotlinx.coroutines.launch

// Ecran affiche une seule fois, juste apres la connexion (ou au premier
// lancement sans catalogue en cache) : recupere chaines/films/series en une
// fois avec un compteur visible, comme TiviMate — au lieu de charger
// silencieusement chaque catalogue au premier passage sur son onglet.
@Composable
fun CatalogDownloadScreen(creds: XtreamCredentials, onComplete: () -> Unit) {
    val context = LocalContext.current
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var liveCount by remember { mutableStateOf<Int?>(null) }
    var radioCount by remember { mutableStateOf<Int?>(null) }
    var movieCount by remember { mutableStateOf<Int?>(null) }
    var seriesCount by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Si rien du tout n'a pu etre recupere, on reste sur cet ecran avec un
    // bouton Reessayer au lieu d'entrer dans une appli vide.
    var blocked by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(creds, attempt) {
        runCatching {
            val liveCats = service.getLiveCategories(creds.username, creds.password)
            val liveChannels = service.getLiveStreams(creds.username, creds.password).filter { it.streamId > 0 }
            val radioCatIds = liveCats.filter { it.categoryName.contains("radio", ignoreCase = true) }.map { it.categoryId }.toSet()
            liveCount = liveChannels.count { it.categoryId !in radioCatIds }
            radioCount = liveChannels.count { it.categoryId in radioCatIds }
            CatalogCache.setLive(context, CatalogCache.LiveData(liveCats, liveChannels))
        }.onFailure { error = friendlyNetworkError(it) }

        runCatching {
            val movieCats = service.getVodCategories(creds.username, creds.password)
            val movies = service.getVodStreams(creds.username, creds.password).filter { it.streamId > 0 }
            movieCount = movies.size
            CatalogCache.setMovies(context, CatalogCache.MovieData(movieCats, movies))
        }.onFailure { if (error == null) error = friendlyNetworkError(it) }

        runCatching {
            val seriesCats = service.getSeriesCategories(creds.username, creds.password)
            val seriesList = service.getSeriesList(creds.username, creds.password).filter { it.seriesId > 0 }
            seriesCount = seriesList.size
            CatalogCache.setSeries(context, CatalogCache.SeriesData(seriesCats, seriesList))
        }.onFailure { if (error == null) error = friendlyNetworkError(it) }

        val nothingFetched = CatalogCache.getLive() == null && CatalogCache.getMovies() == null && CatalogCache.getSeries() == null
        if (nothingFetched && error != null) {
            blocked = true
            return@LaunchedEffect
        }

        // Le guide TV complet (XMLTV) peut etre volumineux — se telecharge
        // sur un scope independant de cet ecran (voir LocalEpgStore), pour
        // ne pas etre annule quand on entre dans l'appli juste apres.
        LocalEpgStore.loadFromDisk(context)
        LocalEpgStore.refreshOnceIfNeeded(context, creds)

        onComplete()
    }

    Box(Modifier.fillMaxSize().background(PlexoraBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 360.dp)) {
            Image(
                painter = painterResource(R.drawable.logo_plexora_login),
                contentDescription = "Plexora",
                modifier = Modifier.width(200.dp),
            )
            Spacer(Modifier.height(32.dp))
            Text("Récupération de ton abonnement...", fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(24.dp))

            CatalogCountRow("Chaînes TV", liveCount)
            Spacer(Modifier.height(12.dp))
            CatalogCountRow("Radios", radioCount)
            Spacer(Modifier.height(12.dp))
            CatalogCountRow("Films", movieCount)
            Spacer(Modifier.height(12.dp))
            CatalogCountRow("Séries", seriesCount)

            error?.let {
                Spacer(Modifier.height(24.dp))
                Text(
                    if (blocked) "Impossible de récupérer ton abonnement :\n$it" else "Certaines listes n'ont pas pu être récupérées :\n$it",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                )
            }
            if (blocked) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = {
                    error = null
                    blocked = false
                    liveCount = null
                    radioCount = null
                    movieCount = null
                    seriesCount = null
                    attempt++
                }) { Text("Réessayer") }
            }
        }
    }
}

@Composable
private fun CatalogCountRow(label: String, count: Int?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (count == null) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PlexoraOrange)
            } else {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Text(count?.toString() ?: "...", color = Color(0xFF9CA3AF), fontWeight = FontWeight.SemiBold)
    }
}
