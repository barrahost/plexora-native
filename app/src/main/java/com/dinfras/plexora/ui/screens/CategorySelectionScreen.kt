package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.data.*
import kotlinx.coroutines.launch
import com.dinfras.plexora.ui.theme.PlexoraViolet

private data class CatEntry(val prefix: String, val id: String, val name: String, val typeLabel: String)

// Assistant d'import facon TiviMate, reduit a son etape utile : apres avoir
// valide une playlist, on liste toutes ses categories (TV / Films / Series),
// toutes cochees par defaut, et l'utilisateur decoche celles qu'il ne veut
// pas charger — inutile de tirer 4000 chaines quand on n'en veut que 300.
@androidx.media3.common.util.UnstableApi
@Composable
fun CategorySelectionScreen(creds: XtreamCredentials, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<CatEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    // Ids coches (= a importer). Tout coche par defaut.
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(creds) {
        runCatching {
            val list = mutableListOf<CatEntry>()
            if (creds.isM3u()) {
                val catalog = M3uParser.fetchAndParse(creds.m3uLink())
                catalog.liveCategories.forEach { list.add(CatEntry(CategoryVisibility.PREFIX_LIVE, it.categoryId, it.categoryName, "Chaînes TV")) }
                catalog.movieCategories.forEach { list.add(CatEntry(CategoryVisibility.PREFIX_VOD, it.categoryId, it.categoryName, "Films")) }
                catalog.seriesCategories.forEach { list.add(CatEntry(CategoryVisibility.PREFIX_SERIES, it.categoryId, it.categoryName, "Séries")) }
            } else {
                val service = XtreamClient.create(creds.url)
                runCatching { service.getLiveCategories(creds.username, creds.password) }.getOrDefault(emptyList())
                    .forEach { list.add(CatEntry(CategoryVisibility.PREFIX_LIVE, it.categoryId, it.categoryName, "Chaînes TV")) }
                runCatching { service.getVodCategories(creds.username, creds.password) }.getOrDefault(emptyList())
                    .forEach { list.add(CatEntry(CategoryVisibility.PREFIX_VOD, it.categoryId, it.categoryName, "Films")) }
                runCatching { service.getSeriesCategories(creds.username, creds.password) }.getOrDefault(emptyList())
                    .forEach { list.add(CatEntry(CategoryVisibility.PREFIX_SERIES, it.categoryId, it.categoryName, "Séries")) }
            }
            entries = list
            list.forEach { checked[it.prefix + it.id] = true }
        }.onFailure { error = true }
        loading = false
    }

    fun finish() {
        saving = true
        scope.launch {
            val hidden = entries.map { it.prefix + it.id }.filter { checked[it] != true }.toSet()
            CategoryPrefs.setHidden(context, CategoryPrefs.playlistIdOf(creds), hidden)
            // Applique tout de suite : dans le flux de connexion, cette playlist
            // devient la playlist active juste apres. Dans l'ajout depuis les
            // Parametres, MainActivity rechargera de toute facon le filtre de
            // la playlist reellement active — pas d'effet de bord la.
            CategoryVisibility.hidden.value = hidden
            onDone()
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Récupération des catégories...", color = Color.Gray)
            }
        }
        return
    }
    // Pas de categories exploitables (M3U sans group-title, ou echec) : on
    // n'impose pas cette etape, on continue directement.
    if (error || entries.isEmpty()) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    val grouped = remember(entries) { entries.groupBy { it.typeLabel } }

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Que veux-tu importer ?", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.headlineSmall.fontSize)
        Text(
            "Décoche les catégories que tu ne veux pas charger. Tu pourras les réactiver plus tard.",
            color = Color.Gray,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f)) {
            grouped.forEach { (type, cats) ->
                item(key = "h_$type") {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(type.uppercase(), color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        Spacer(Modifier.weight(1f))
                        val allOn = cats.all { checked[it.prefix + it.id] == true }
                        Text(
                            if (allOn) "Tout décocher" else "Tout cocher",
                            color = PlexoraViolet,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                cats.forEach { checked[it.prefix + it.id] = !allOn }
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                items(cats, key = { it.prefix + it.id }) { c ->
                    val key = c.prefix + c.id
                    val on = checked[key] == true
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clickable { checked[key] = !on }
                            .background(if (focused) Color.White else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (on) PlexoraViolet else Color(0xFF374151)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, color = if (focused) Color.Black else Color.White, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val total = entries.size
            val kept = entries.count { checked[it.prefix + it.id] == true }
            Text("$kept / $total catégories", color = Color.Gray, modifier = Modifier.align(Alignment.CenterVertically))
            Spacer(Modifier.weight(1f))
            Button(enabled = !saving, onClick = { finish() }) {
                Text(if (saving) "..." else "Importer")
            }
        }
    }
}
