package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.theme.PlexoraOrange

@androidx.media3.common.util.UnstableApi
@Composable
fun RadioScreen(creds: XtreamCredentials) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var stations by remember { mutableStateOf<List<XtreamChannel>>(emptyList()) }
    var active by remember { mutableStateOf<XtreamChannel?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(creds) {
        runCatching {
            val cats = service.getLiveCategories(creds.username, creds.password)
            val radioCatIds = cats.filter { it.categoryName.contains("radio", ignoreCase = true) }.map { it.categoryId }.toSet()
            val all = service.getLiveStreams(creds.username, creds.password)
            stations = all.filter { it.categoryId in radioCatIds }
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (stations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune station radio disponible sur cet abonnement.", color = Color.Gray)
        }
        return
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.width(300.dp).fillMaxHeight().background(Color(0xFF111827))) {
            items(stations) { s ->
                val isActive = active?.streamId == s.streamId
                Text(
                    s.name,
                    modifier = Modifier.fillMaxWidth().clickable { active = s }
                        .background(if (isActive) PlexoraOrange.copy(alpha = 0.2f) else Color.Transparent)
                        .padding(16.dp, 12.dp),
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
            val station = active
            if (station == null) {
                Text("Sélectionne une station", color = Color.Gray)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(station.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val url = remember(station) { XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, station.streamId) }
                    // Lecture audio : le player video sert de moteur de lecture, la surface reste noire
                    Box(Modifier.size(1.dp)) { LiveVideoPlayer(url, Modifier.size(1.dp)) }
                }
            }
        }
    }
}
