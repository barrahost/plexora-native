package com.dinfras.plexora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinfras.plexora.data.*
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.AppUiState
import com.dinfras.plexora.ui.theme.PlexoraOrange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LONG_PRESS_MS = 500L

private fun formatTime(epochSeconds: Long): String =
    if (epochSeconds <= 0) "" else SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(epochSeconds * 1000))

private fun durationMinutes(item: EpgItem): Long =
    ((item.stopTimestamp - item.startTimestamp) / 60).coerceAtLeast(0)

// Incrustations plein ecran en direct, inspirees de TiviMate : la fleche
// GAUCHE de la telecommande fait defiler 3 niveaux d'incrustation (liste des
// chaines + info programme -> categories + liste -> categories seules), et
// RETOUR ouvre le guide TV multi-chaines plutot que de quitter directement.
@androidx.media3.common.util.UnstableApi
@Composable
fun LiveFullscreenPlayer(
    creds: XtreamCredentials,
    service: XtreamService,
    categories: List<XtreamCategory>,
    channels: List<XtreamChannel>,
    channel: XtreamChannel,
    onChannelChange: (XtreamChannel) -> Unit,
    onExit: () -> Unit,
) {
    var osdStage by remember { mutableStateOf(0) }
    var showGrid by remember { mutableStateOf(false) }
    var showQuickBar by remember { mutableStateOf(false) }
    var selectedCat by remember { mutableStateOf<String?>(channel.categoryId) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<XtreamChannel>() }

    LaunchedEffect(channel) {
        history.removeAll { it.streamId == channel.streamId }
        history.add(0, channel)
        if (history.size > 20) history.removeRange(20, history.size)
    }

    // requestFocus() echoue silencieusement si le conteneur n'est pas encore
    // pose a l'ecran au moment de l'appel — au premier montage c'est
    // frequent, et le focus reste alors sur la liste de chaines en dessous :
    // aucune touche (OK, fleches) n'atteint le lecteur plein ecran. On
    // reessaie donc jusqu'a ce qu'il capte reellement le focus.
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var tries = 0
        while (!hasFocus && tries < 40) {
            runCatching { focusRequester.requestFocus() }
            tries++
            delay(25)
        }
    }

    // Quand une incrustation se ferme (barre rapide, panneaux, guide), le focus
    // etait sur un de ses elements desormais disparus — on le ramene sur le
    // conteneur video pour que la telecommande reste operationnelle.
    LaunchedEffect(showQuickBar, osdStage, showGrid) {
        if (!showQuickBar && osdStage == 0 && !showGrid) {
            var tries = 0
            while (tries < 20) {
                runCatching { focusRequester.requestFocus() }
                if (hasFocus) break
                tries++
                delay(25)
            }
        }
    }

    LaunchedEffect(osdStage, channel) {
        if (osdStage > 0) {
            delay(8000)
            osdStage = 0
        }
    }
    LaunchedEffect(showQuickBar, channel) {
        if (showQuickBar) {
            delay(8000)
            showQuickBar = false
        }
    }

    val url = remember(channel) {
        channel.directUrl ?: XtreamClient.liveStreamUrl(creds.url, creds.username, creds.password, channel.streamId)
    }

    // Retour : ferme le guide (et quitte le plein ecran), sinon masque
    // l'incrustation ou la barre rapide, sinon ouvre le guide TV.
    BackHandler(enabled = showGrid) { showGrid = false; onExit() }
    BackHandler(enabled = !showGrid && osdStage > 0) { osdStage = 0 }
    BackHandler(enabled = !showGrid && osdStage == 0 && showQuickBar) { showQuickBar = false }
    BackHandler(enabled = !showGrid && osdStage == 0 && !showQuickBar) { showGrid = true }

    var okDownJob by remember { mutableStateOf<Job?>(null) }
    var okWasLongPress by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (okDownJob == null && !showGrid && osdStage == 0) {
                                okWasLongPress = false
                                okDownJob = scope.launch {
                                    delay(LONG_PRESS_MS)
                                    okWasLongPress = true
                                    showQuickBar = false
                                    osdStage = 1
                                }
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            okDownJob?.cancel()
                            okDownJob = null
                            if (!okWasLongPress && !showGrid && osdStage == 0) {
                                showQuickBar = !showQuickBar
                            }
                            true
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyDown && !showGrid) {
                    when (event.key) {
                        // Gauche/droite restent reserves au defilement des tuiles
                        // quand la barre rapide est ouverte.
                        Key.DirectionLeft -> {
                            if (showQuickBar) return@onKeyEvent false
                            osdStage = (osdStage + 1).coerceAtMost(3)
                            true
                        }
                        Key.DirectionRight -> {
                            if (showQuickBar) return@onKeyEvent false
                            // Symetrique de GAUCHE : on referme les incrustations un niveau a la fois
                            // (categories seules -> categories+chaines -> chaines+programme -> video nue).
                            if (osdStage > 0) osdStage -= 1
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            // Zapping direct sans ouvrir d'incrustation, comme TiviMate — la barre
                            // rapide s'affiche brievement pour confirmer la nouvelle chaine, et
                            // les zaps suivants restent possibles pendant qu'elle est visible.
                            // Le zap reste dans la categorie selectionnee (repli sur la liste
                            // complete si la chaine courante n'en fait pas partie).
                            //
                            // Ne consomme l'evenement (true) QUE pour le zap (osdStage == 0) —
                            // sinon (panneau chaines/categories ouvert), il faut le laisser
                            // remonter au systeme de focus de Compose pour naviguer dans la
                            // LazyColumn, sinon impossible de monter/descendre dans la liste.
                            if (osdStage != 0) return@onKeyEvent false
                            val catList = if (selectedCat == null) channels else channels.filter { it.categoryId == selectedCat }
                            val zapList = if (catList.any { it.streamId == channel.streamId }) catList else channels
                            val idx = zapList.indexOfFirst { it.streamId == channel.streamId }
                            if (idx >= 0 && zapList.size > 1) {
                                val nextIdx = if (event.key == Key.DirectionUp) {
                                    (idx - 1 + zapList.size) % zapList.size
                                } else {
                                    (idx + 1) % zapList.size
                                }
                                onChannelChange(zapList[nextIdx])
                                showQuickBar = true
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        LiveVideoPlayer(url, Modifier.fillMaxSize())

        when {
            showGrid -> EpgGridOverlay(
                creds = creds,
                service = service,
                channels = channels,
                activeChannel = channel,
                previewUrl = url,
                onSelectChannel = { onChannelChange(it) },
            )
            osdStage == 1 -> ChannelSwitcherPanel(
                creds = creds,
                service = service,
                channels = if (selectedCat == null) channels else channels.filter { it.categoryId == selectedCat },
                activeChannel = channel,
                onSelectChannel = { onChannelChange(it); osdStage = 0 },
            )
            osdStage == 2 -> CategoryChannelPanel(
                creds = creds,
                service = service,
                categories = categories,
                channels = channels,
                selectedCat = selectedCat,
                onSelectCat = { selectedCat = it },
                activeChannel = channel,
                onSelectChannel = { onChannelChange(it); osdStage = 0 },
            )
            osdStage == 3 -> CategoryOnlyPanel(
                categories = categories,
                selectedCat = selectedCat,
                onSelectCat = { selectedCat = it; osdStage = 2 },
            )
            showQuickBar -> QuickBar(
                creds = creds,
                service = service,
                channels = channels,
                history = history,
                activeChannel = channel,
                onSelectChannel = { onChannelChange(it) },
                onOpenGuide = { showQuickBar = false; showGrid = true },
            )
        }
    }
}

// Appui simple sur OK en plein ecran : barre d'infos (chaine, programme en
// cours/suivant, progression) + acces rapide au Guide TV, a l'historique des
// chaines visitees, et aux chaines elles-memes — comme le OSD par defaut de TiviMate.
@Composable
private fun QuickBar(
    creds: XtreamCredentials,
    service: XtreamService,
    channels: List<XtreamChannel>,
    history: List<XtreamChannel>,
    activeChannel: XtreamChannel,
    onSelectChannel: (XtreamChannel) -> Unit,
    onOpenGuide: () -> Unit,
) {
    var epg by remember(activeChannel) { mutableStateOf<List<EpgItem>>(emptyList()) }
    LaunchedEffect(activeChannel) {
        epg = service.getEpgForChannel(creds.username, creds.password, activeChannel, channels)
    }
    val nowIndex = epg.indexOfFirst { it.nowPlaying == 1 }
    val now = epg.getOrNull(nowIndex) ?: epg.firstOrNull()
    val next = if (nowIndex >= 0) epg.getOrNull(nowIndex + 1) else null

    var showingHistory by remember { mutableStateOf(false) }
    val strip = if (showingHistory) history else channels

    Box(Modifier.fillMaxSize()) {
        if (now != null) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF111827).copy(alpha = AppUiState.overlayAlpha.floatValue))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(activeChannel.name, activeChannel.streamIcon, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(decodeEpgText(now.title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text(activeChannel.name, color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${formatTime(now.startTimestamp)} – ${formatTime(now.stopTimestamp)}   ·   ${durationMinutes(now)} min",
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
                if (next != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Ensuite : ${decodeEpgText(next.title)}", color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = AppUiState.overlayAlpha.floatValue)))),
        ) {
            if (now != null) {
                val total = (now.stopTimestamp - now.startTimestamp).coerceAtLeast(1)
                val elapsed = ((System.currentTimeMillis() / 1000) - now.startTimestamp).coerceIn(0, total)
                LinearProgressIndicator(
                    progress = { elapsed.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = PlexoraOrange,
                    trackColor = Color(0xFF374151),
                )
            }
            // Focus place d'office sur la premiere tuile : sans ca, le conteneur
            // plein ecran garde le focus et la touche OK referme la barre au
            // lieu de valider une tuile.
            val firstTileFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { firstTileFocusRequester.requestFocus() }
            Row(
                Modifier.fillMaxWidth().padding(16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickBarTile(Icons.Filled.Grid3x3, "Guide TV", onClick = onOpenGuide, modifier = Modifier.focusRequester(firstTileFocusRequester))
                QuickBarTile(Icons.Filled.History, "Historique", active = showingHistory, onClick = { showingHistory = !showingHistory })
                strip.forEach { ch ->
                    QuickBarChannelTile(ch, active = ch.streamId == activeChannel.streamId) { onSelectChannel(ch) }
                }
            }
        }
    }
}

@Composable
private fun QuickBarTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val fg = if (isFocused) Color.Black else Color.White
    Column(
        modifier
            .width(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (isFocused) Color.White else if (active) PlexoraOrange.copy(alpha = 0.3f) else Color(0xFF1F2937))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = fg, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun QuickBarChannelTile(channel: XtreamChannel, active: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val fg = if (isFocused) Color.Black else Color.White
    Column(
        Modifier
            .width(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (isFocused) Color.White else if (active) PlexoraOrange.copy(alpha = 0.3f) else Color(0xFF1F2937))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelLogo(channel.name, channel.streamIcon, size = 28.dp)
        Spacer(Modifier.height(4.dp))
        Text(channel.name, color = fg, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun CategoryRow(label: String, active: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        label,
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (isFocused) Color.White else if (active) PlexoraOrange.copy(alpha = 0.25f) else Color.Transparent)
            .padding(16.dp, 12.dp),
        color = if (isFocused) Color.Black else Color.White,
        fontWeight = if (active || isFocused) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun ChannelRow(
    index: Int,
    channel: XtreamChannel,
    active: Boolean,
    creds: XtreamCredentials,
    service: XtreamService,
    onClick: () -> Unit,
) {
    var nowTitle by remember(channel) { mutableStateOf<String?>(null) }
    LaunchedEffect(channel) {
        val epg = service.getEpgForChannel(creds.username, creds.password, channel)
        val now = epg.firstOrNull { it.nowPlaying == 1 } ?: epg.firstOrNull()
        nowTitle = now?.let { decodeEpgText(it.title) }
    }
    // Le curseur D-pad (blanc) est un etat distinct de "active" (chaine en
    // cours de lecture, orange) — sinon impossible de savoir ou se trouve
    // le curseur tant qu'on n'a pas encore valide une chaine.
    var isFocused by remember { mutableStateOf(false) }
    val bg = when {
        isFocused -> Color.White
        active -> PlexoraOrange.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    val fg = if (isFocused) Color.Black else Color.White
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(bg)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel.name, channel.streamIcon, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${index + 1}  ${channel.name}",
                color = if (isFocused) fg else if (active) PlexoraOrange else Color.White,
                fontWeight = if (active || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                fontSize = 14.sp,
            )
            if (!nowTitle.isNullOrBlank()) {
                Text(
                    nowTitle!!,
                    color = if (isFocused) Color(0xFF374151) else Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

// Niveau 1 (flèche gauche x1, ou flèche droite depuis le niveau 2) : liste
// des chaines + programme complet de la chaine survolee, video visible a droite.
@Composable
private fun ChannelSwitcherPanel(
    creds: XtreamCredentials,
    service: XtreamService,
    channels: List<XtreamChannel>,
    activeChannel: XtreamChannel,
    onSelectChannel: (XtreamChannel) -> Unit,
) {
    var epg by remember(activeChannel) { mutableStateOf<List<EpgItem>>(emptyList()) }
    LaunchedEffect(activeChannel) {
        epg = service.getEpgForChannel(creds.username, creds.password, activeChannel, channels)
    }
    val now = epg.firstOrNull { it.nowPlaying == 1 } ?: epg.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxHeight()) {
            LazyColumn(Modifier.width(300.dp).fillMaxHeight().background(Color(0xFF0B0F19).copy(alpha = AppUiState.overlayAlpha.floatValue))) {
                itemsIndexed(channels) { idx, ch ->
                    ChannelRow(idx, ch, ch.streamId == activeChannel.streamId, creds, service) { onSelectChannel(ch) }
                }
            }
            ChannelEpgList(
                channel = activeChannel,
                epg = epg,
                modifier = Modifier.width(280.dp).fillMaxHeight().background(Color(0xFF0B0F19).copy(alpha = AppUiState.overlayAlpha.floatValue)),
            )
        }
        if (now != null) {
            ProgramInfoCard(now, Modifier.align(Alignment.TopEnd).padding(24.dp))
        }
    }
}

@Composable
fun ChannelEpgList(channel: XtreamChannel, epg: List<EpgItem>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            channel.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.padding(16.dp, 12.dp),
        )
        if (epg.isEmpty()) {
            Text(
                "Guide non fourni pour cette chaîne.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return
        }
        LazyColumn {
            items(epg) { item ->
                val isNow = item.nowPlaying == 1
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .background(if (isFocused) Color.White else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        formatTime(item.startTimestamp),
                        color = if (isFocused) Color.Black else if (isNow) PlexoraOrange else Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        decodeEpgText(item.title),
                        color = if (isFocused) Color.Black else if (isNow) PlexoraOrange else Color.White,
                        fontWeight = if (isNow || isFocused) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun ProgramInfoCard(item: EpgItem, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111827).copy(alpha = AppUiState.overlayAlpha.floatValue))
            .padding(16.dp),
    ) {
        Text(decodeEpgText(item.title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatTime(item.startTimestamp)} – ${formatTime(item.stopTimestamp)}   ·   ${durationMinutes(item)} min",
            color = Color.Gray,
            fontSize = 12.sp,
        )
        item.description?.let { desc ->
            val d = decodeEpgText(desc)
            if (d.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(d, color = Color.LightGray, fontSize = 13.sp, maxLines = 6)
            }
        }
    }
}

// Niveau 2 (flèche gauche x2) : categories + liste des chaines, video visible a droite.
@Composable
private fun CategoryChannelPanel(
    creds: XtreamCredentials,
    service: XtreamService,
    categories: List<XtreamCategory>,
    channels: List<XtreamChannel>,
    selectedCat: String?,
    onSelectCat: (String?) -> Unit,
    activeChannel: XtreamChannel,
    onSelectChannel: (XtreamChannel) -> Unit,
) {
    val filtered = remember(channels, selectedCat) {
        if (selectedCat == null) channels else channels.filter { it.categoryId == selectedCat }
    }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF111827).copy(alpha = AppUiState.overlayAlpha.floatValue))) {
            item { CategoryRow("Toutes les chaînes", selectedCat == null) { onSelectCat(null) } }
            items(categories) { cat -> CategoryRow(cat.categoryName, selectedCat == cat.categoryId) { onSelectCat(cat.categoryId) } }
        }
        LazyColumn(Modifier.width(300.dp).fillMaxHeight().background(Color(0xFF0B0F19).copy(alpha = AppUiState.overlayAlpha.floatValue))) {
            itemsIndexed(filtered) { idx, ch ->
                ChannelRow(idx, ch, ch.streamId == activeChannel.streamId, creds, service) { onSelectChannel(ch) }
            }
        }
    }
}

// Niveau 3 (flèche gauche x3) : uniquement les categories, plein écran centre sur le choix du bouquet.
@Composable
private fun CategoryOnlyPanel(
    categories: List<XtreamCategory>,
    selectedCat: String?,
    onSelectCat: (String?) -> Unit,
) {
    LazyColumn(Modifier.width(320.dp).fillMaxHeight().background(Color(0xFF111827).copy(alpha = AppUiState.overlayAlpha.floatValue))) {
        item { CategoryRow("Toutes les chaînes", selectedCat == null) { onSelectCat(null) } }
        items(categories) { cat -> CategoryRow(cat.categoryName, selectedCat == cat.categoryId) { onSelectCat(cat.categoryId) } }
    }
}

// Guide TV (bouton Retour) : apercu + fiche du programme en cours, puis grille
// multi-chaines avec les creneaux positionnes selon l'heure reelle.
private const val PX_PER_MIN = 2.8f

@androidx.media3.common.util.UnstableApi
@Composable
private fun EpgGridOverlay(
    creds: XtreamCredentials,
    service: XtreamService,
    channels: List<XtreamChannel>,
    activeChannel: XtreamChannel,
    previewUrl: String,
    onSelectChannel: (XtreamChannel) -> Unit,
) {
    var focused by remember { mutableStateOf(activeChannel) }
    var focusedEpg by remember { mutableStateOf<List<EpgItem>>(emptyList()) }
    LaunchedEffect(focused) {
        focusedEpg = service.getEpgForChannel(creds.username, creds.password, focused, channels)
    }
    val now = focusedEpg.firstOrNull { it.nowPlaying == 1 } ?: focusedEpg.firstOrNull()
    val windowStart = remember { (now?.startTimestamp ?: (System.currentTimeMillis() / 1000)) }

    Column(Modifier.fillMaxSize().background(Color(0xFF030712).copy(alpha = AppUiState.overlayAlpha.floatValue))) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                Modifier.width(280.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black),
            ) {
                LiveVideoPlayer(previewUrl, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (now != null) {
                    Text(decodeEpgText(now.title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatTime(now.startTimestamp)} – ${formatTime(now.stopTimestamp)}   ·   ${durationMinutes(now)} min",
                        color = Color.Gray,
                        fontSize = 13.sp,
                    )
                    now.description?.let { desc ->
                        val d = decodeEpgText(desc)
                        if (d.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(d, color = Color.LightGray, fontSize = 13.sp, maxLines = 4)
                        }
                    }
                } else {
                    Text(focused.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        Divider(color = Color(0xFF1F2937))
        EpgGridList(
            channels = channels,
            activeChannel = activeChannel,
            windowStart = windowStart,
            creds = creds,
            service = service,
            onFocus = { focused = it },
            onSelect = onSelectChannel,
        )
    }
}

// Grille multi-chaines reutilisee par le guide TV plein ecran ET par la
// 3e colonne de l'ecran Live TV (navigation classique).
@Composable
fun EpgGridList(
    channels: List<XtreamChannel>,
    activeChannel: XtreamChannel?,
    windowStart: Long,
    creds: XtreamCredentials,
    service: XtreamService,
    onFocus: (XtreamChannel) -> Unit,
    onSelect: (XtreamChannel) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(channels) { idx, ch ->
            EpgGridRow(
                index = idx,
                channel = ch,
                active = ch.streamId == activeChannel?.streamId,
                windowStart = windowStart,
                creds = creds,
                service = service,
                onFocus = { onFocus(ch) },
                onSelect = { onSelect(ch) },
            )
        }
    }
}

@Composable
private fun EpgGridRow(
    index: Int,
    channel: XtreamChannel,
    active: Boolean,
    windowStart: Long,
    creds: XtreamCredentials,
    service: XtreamService,
    onFocus: () -> Unit,
    onSelect: () -> Unit,
) {
    var epg by remember(channel) { mutableStateOf<List<EpgItem>>(emptyList()) }
    LaunchedEffect(channel) {
        epg = service.getEpgForChannel(creds.username, creds.password, channel)
    }

    Row(
        Modifier.fillMaxWidth()
            .background(if (active) Color(0xFF1F2937) else Color.Transparent)
            .clickable { onFocus(); onSelect() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(190.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel.name, channel.streamIcon, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text("${index + 1}  ${channel.name}", color = Color.White, fontSize = 13.sp, maxLines = 1)
        }
        if (epg.isEmpty()) {
            Text("Pas d'information", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                // Decalage initial pour aligner chaque chaine sur la meme fenetre temporelle.
                val leadMinutes = ((epg.first().startTimestamp - windowStart) / 60).coerceIn(0, 6 * 60)
                if (leadMinutes > 0) Spacer(Modifier.width((leadMinutes * PX_PER_MIN).dp))
                epg.take(8).forEach { item ->
                    val minutes = durationMinutes(item).coerceAtLeast(5)
                    Column(
                        Modifier.width((minutes * PX_PER_MIN).dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (item.nowPlaying == 1) PlexoraOrange.copy(alpha = 0.35f) else Color(0xFF1F2937))
                            .padding(6.dp),
                    ) {
                        Text(decodeEpgText(item.title), color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
