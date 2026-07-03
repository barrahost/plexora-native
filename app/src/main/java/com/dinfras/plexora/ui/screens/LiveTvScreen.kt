package com.dinfras.plexora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
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

// Sequence identique a la version web : categories -> chaines -> apercu
// (lecteur + EPG en dessous) -> plein ecran sur une seconde action.
@androidx.media3.common.util.UnstableApi
@Composable
fun LiveTvScreen(creds: XtreamCredentials) {
    val service = remember(creds) { XtreamClient.create(creds.url) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var channels by remember { mutableStateOf<List<XtreamChannel>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var activeChannel by remember { mutableStateOf<XtreamChannel?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(creds) {
        runCatching {
            categories = service.getLiveCategories(creds.username, creds.password)
            channels = service.getLiveStreams(creds.username, creds.password).filter { it.streamId > 0 }
        }.onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    val filteredChannels = remember(channels, selectedCat) {
        if (selectedCat == null) channels else channels.filter { it.categoryId == selectedCat }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Erreur de chargement :\n$error", color = Color.Red)
        }
        return
    }

    // Bouton Retour : ferme le plein ecran, puis deselectionne la chaine
    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = !fullscreen && activeChannel != null) { activeChannel = null }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827))) {
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

            LazyColumn(Modifier.width(280.dp).fillMaxHeight().background(Color(0xFF0B0F19))) {
                items(filteredChannels) { ch ->
                    val active = activeChannel?.streamId == ch.streamId
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { activeChannel = ch }
                            .background(if (active) PlexoraOrange.copy(alpha = 0.2f) else Color.Transparent)
                            .padding(12.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelLogo(ch.name, ch.streamIcon, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            ch.name,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            // Colonne 3 : apercu (haut) + EPG (bas), jamais plein ecran ici
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val channel = activeChannel
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                    if (channel == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sélectionne une chaîne", color = Color.Gray)
                        }
                    } else {
                        val url = remember(channel) {
                            XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, channel.streamId)
                        }
                        LiveVideoPlayer(url, Modifier.fillMaxSize().clickable { fullscreen = true })
                        IconButton(onClick = { fullscreen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Plein écran", tint = Color.White)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF030712))) {
                    if (channel != null) EpgPanel(creds = creds, service = service, channel = channel)
                }
            }
        }

        val fsChannel = activeChannel
        if (fullscreen && fsChannel != null) {
            val url = remember(fsChannel) {
                XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, fsChannel.streamId)
            }
            FullscreenPlayer(streamUrl = url, title = fsChannel.name, onClose = { fullscreen = false })
        }
    }
}

@Composable
private fun EpgPanel(creds: XtreamCredentials, service: XtreamService, channel: XtreamChannel) {
    var epg by remember(channel) { mutableStateOf<List<EpgItem>>(emptyList()) }
    var loading by remember(channel) { mutableStateOf(true) }

    LaunchedEffect(channel) {
        loading = true
        epg = runCatching { service.getShortEpg(creds.username, creds.password, channel.streamId).epgListings ?: emptyList() }
            .getOrDefault(emptyList())
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        return
    }
    if (epg.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Guide des programmes non fourni pour cette chaîne.", color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        items(epg) { item ->
            val isNow = item.nowPlaying == 1
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    decodeEpgText(item.title),
                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                    color = if (isNow) PlexoraOrange else Color.White,
                )
                item.description?.let {
                    val desc = decodeEpgText(it)
                    if (desc.isNotBlank()) {
                        Text(desc, color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize, maxLines = 2)
                    }
                }
            }
        }
    }
}
