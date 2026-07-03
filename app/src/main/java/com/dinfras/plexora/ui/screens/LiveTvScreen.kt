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
fun LiveTvScreen(creds: XtreamCredentials) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var channels by remember { mutableStateOf<List<XtreamChannel>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var activeChannel by remember { mutableStateOf<XtreamChannel?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(creds) {
        runCatching {
            categories = service.getLiveCategories(creds.username, creds.password)
            channels = service.getLiveStreams(creds.username, creds.password)
        }
        loading = false
    }

    val filteredChannels = remember(channels, selectedCat) {
        if (selectedCat == null) channels else channels.filter { it.categoryId == selectedCat }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Row(Modifier.fillMaxSize()) {
        // Colonne 1 : catégories
        LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
            items(categories) { cat ->
                val active = selectedCat == cat.categoryId
                Text(
                    cat.categoryName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedCat = cat.categoryId }
                        .background(if (active) PlexoraOrange.copy(alpha = 0.2f) else Color.Transparent)
                        .padding(16.dp, 12.dp),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        // Colonne 2 : chaînes
        LazyColumn(Modifier.width(280.dp).fillMaxHeight().background(Color(0xFF0B0F19))) {
            items(filteredChannels) { ch ->
                val active = activeChannel?.streamId == ch.streamId
                Text(
                    ch.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeChannel = ch }
                        .background(if (active) PlexoraOrange.copy(alpha = 0.2f) else Color.Transparent)
                        .padding(16.dp, 12.dp),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        // Colonne 3 : lecteur
        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
            val channel = activeChannel
            if (channel == null) {
                Text("Sélectionne une chaîne", color = Color.Gray)
            } else {
                val url = remember(channel) {
                    XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, channel.streamId)
                }
                LiveVideoPlayer(url, Modifier.fillMaxSize())
            }
        }
    }
}
