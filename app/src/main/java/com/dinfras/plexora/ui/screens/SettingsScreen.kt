package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.data.*
import com.dinfras.plexora.ui.theme.PlexoraOrange
import com.dinfras.plexora.ui.theme.PlexoraViolet
import kotlinx.coroutines.launch

private data class BufferOption(val mode: BufferMode, val label: String, val desc: String)

private class InvalidPlaylistCredentialsException : Exception("Identifiants incorrects.")

// La date d'expiration change rarement : un seul appel serveur par session
// suffit, pas un a chaque ouverture de la section Lecteur. Le cache est
// invalide par compte (cle URL+utilisateur), sinon un changement de playlist
// afficherait la date de l'ancien abonnement.
private var cachedExpDate: Long? = null
private var expDateCacheKey: String? = null

private val BUFFER_OPTIONS = listOf(
    BufferOption(BufferMode.NONE, "Aucun", "Latence minimale, très sensible aux coupures. Pour réseau très stable uniquement."),
    BufferOption(BufferMode.SMALL, "Faible", "Réaction rapide, plus sensible aux coupures sur réseau instable."),
    BufferOption(BufferMode.MEDIUM, "Moyen", "Équilibre recommandé pour la plupart des connexions."),
    BufferOption(BufferMode.HIGH, "Élevé", "Absorbe les ralentissements serveur — moins de coupures."),
)

private enum class SettingsSection(val label: String) {
    PLAYLISTS("Listes de lecture"),
    APPEARANCE("Apparence"),
    PLAYER("Lecteur"),
    GENERAL("Général"),
}

// Sous-menu façon TiviMate : une colonne de sections a gauche, le contenu
// de la section choisie a droite.
@Composable
fun SettingsScreen(
    activeCreds: XtreamCredentials,
    onLogout: () -> Unit,
    onSwitchPlaylist: (XtreamCredentials) -> Unit,
) {
    var section by remember { mutableStateOf(SettingsSection.PLAYLISTS) }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(260.dp).fillMaxHeight().background(Color(0xFF111827)).padding(vertical = 24.dp)) {
            Text("Paramètres", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.headlineSmall.fontSize, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))
            SettingsSection.entries.forEach { s ->
                val active = s == section
                Text(
                    s.label,
                    modifier = Modifier.fillMaxWidth().clickable { section = s }
                        .background(if (active) PlexoraViolet.copy(alpha = 0.25f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    color = if (active) Color.White else Color(0xFF9CA3AF),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight().padding(32.dp)) {
            when (section) {
                SettingsSection.PLAYLISTS -> PlaylistsSection(activeCreds, onSwitchPlaylist)
                SettingsSection.APPEARANCE -> AppearanceSection()
                SettingsSection.PLAYER -> PlayerSection(activeCreds, onLogout)
                SettingsSection.GENERAL -> GeneralSection()
            }
        }
    }
}

@Composable
private fun PlaylistsSection(activeCreds: XtreamCredentials, onSwitchPlaylist: (XtreamCredentials) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<SavedPlaylist>>(emptyList()) }
    var showAddForm by remember { mutableStateOf(false) }

    suspend fun refreshPlaylists() { playlists = PlaylistsStore.getAll(context) }
    LaunchedEffect(Unit) { refreshPlaylists() }

    Column(Modifier.fillMaxSize().widthIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        Text("Listes de lecture", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        Text("Bascule entre plusieurs comptes Xtream ou ajoutes-en un nouveau.", color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
        Spacer(Modifier.height(16.dp))

        playlists.forEach { p ->
            val isActive = p.url == activeCreds.url && p.username == activeCreds.username
            Row(
                Modifier.fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) PlexoraViolet.copy(alpha = 0.2f) else Color(0xFF1F2937))
                    .clickable(enabled = !isActive) { onSwitchPlaylist(p.toCredentials()) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.label, fontWeight = FontWeight.SemiBold, color = if (isActive) PlexoraViolet else Color.White)
                    Text(if (isActive) "Playlist active" else "Toucher pour activer", fontSize = MaterialTheme.typography.bodySmall.fontSize, color = Color.Gray)
                }
                IconButton(onClick = {
                    scope.launch { PlaylistsStore.remove(context, p.id); refreshPlaylists() }
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Supprimer", tint = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (showAddForm) {
            AddPlaylistForm(
                onCancel = { showAddForm = false },
                onSaved = { scope.launch { refreshPlaylists() }; showAddForm = false },
            )
        } else {
            OutlinedButton(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Ajouter une playlist")
            }
        }
    }
}

private val TEXT_SCALE_OPTIONS = listOf(
    0.85f to "Petit",
    1f to "Normal",
    1.15f to "Grand",
    1.3f to "Très grand",
)

@Composable
private fun AppearanceSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var overlayAlpha by remember { mutableFloatStateOf(UiPrefs.DEFAULT_OVERLAY_ALPHA) }
    var textScale by remember { mutableFloatStateOf(UiPrefs.DEFAULT_TEXT_SCALE) }
    LaunchedEffect(Unit) {
        overlayAlpha = UiPrefs.getOverlayAlpha(context)
        textScale = UiPrefs.getTextScale(context)
    }

    Column(Modifier.fillMaxSize().widthIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        Text("Apparence", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        Spacer(Modifier.height(16.dp))
        Text("Transparence de l'affichage", fontWeight = FontWeight.SemiBold)
        Text(
            "Opacité des bandeaux affichés par-dessus la vidéo en plein écran.",
            color = Color.Gray,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
        )
        Spacer(Modifier.height(12.dp))
        // Un curseur (Slider) se manipule mal a la telecommande D-pad — on
        // utilise des paliers selectionnables par OK, comme le reste des
        // reglages de l'appli (tampon, decodeur...).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { pct ->
                val active = kotlin.math.abs(overlayAlpha - pct) < 0.01f
                Text(
                    "${(pct * 100).toInt()}%",
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (active) PlexoraViolet.copy(alpha = 0.25f) else Color(0xFF1F2937))
                        .clickable {
                            overlayAlpha = pct
                            com.dinfras.plexora.ui.AppUiState.overlayAlpha.floatValue = pct
                            scope.launch { UiPrefs.setOverlayAlpha(context, pct) }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (active) Color.White else Color(0xFF9CA3AF),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Taille du texte", fontWeight = FontWeight.SemiBold)
        Text(
            "S'applique immédiatement dans toute l'application.",
            color = Color.Gray,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TEXT_SCALE_OPTIONS.forEach { (scale, label) ->
                val active = kotlin.math.abs(textScale - scale) < 0.01f
                Text(
                    label,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (active) PlexoraViolet.copy(alpha = 0.25f) else Color(0xFF1F2937))
                        .clickable {
                            textScale = scale
                            com.dinfras.plexora.ui.AppUiState.textScale.floatValue = scale
                            scope.launch { UiPrefs.setTextScale(context, scale) }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (active) Color.White else Color(0xFF9CA3AF),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private val DECODER_OPTIONS = listOf(
    DecoderMode.AUTO to "Auto",
    DecoderMode.HARDWARE to "Matériel",
    DecoderMode.SOFTWARE to "Logiciel",
)

@Composable
private fun DecoderPicker(label: String, value: DecoderMode, onChange: (DecoderMode) -> Unit) {
    Text(label, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DECODER_OPTIONS.forEach { (mode, label2) ->
            val active = value == mode
            Text(
                label2,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (active) PlexoraViolet.copy(alpha = 0.25f) else Color(0xFF1F2937))
                    .clickable { onChange(mode) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = if (active) Color.White else Color(0xFF9CA3AF),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun SettingsToggle(label: String, desc: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            desc?.let { Text(it, color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = PlexoraOrange))
    }
}

@Composable
private fun PlayerSection(activeCreds: XtreamCredentials, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceId = remember { getDeviceId(context) }
    var buffer by remember { mutableStateOf(BufferMode.MEDIUM) }
    var audioDecoder by remember { mutableStateOf(DecoderMode.AUTO) }
    var videoDecoder by remember { mutableStateOf(DecoderMode.AUTO) }
    var tunneling by remember { mutableStateOf(false) }
    var expDate by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        buffer = BufferPrefs.get(context)
        audioDecoder = PlayerPrefs.getAudioDecoder(context)
        videoDecoder = PlayerPrefs.getVideoDecoder(context)
        tunneling = PlayerPrefs.getTunneling(context)
        val accountKey = "${activeCreds.url}|${activeCreds.username}"
        if (expDateCacheKey != accountKey) {
            cachedExpDate = null
            runCatching {
                val info = XtreamClient.create(activeCreds.url).getAccountInfo(activeCreds.username, activeCreds.password)
                cachedExpDate = info.userInfo?.expDate?.toLongOrNull()
                expDateCacheKey = accountKey
            }
        }
        expDate = cachedExpDate
    }

    Column(Modifier.fillMaxSize().widthIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        Text("Lecteur", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        Spacer(Modifier.height(16.dp))
        Text("Taille du tampon vidéo", fontWeight = FontWeight.SemiBold)
        Text("Augmente-la si les chaînes coupent souvent.", color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
        Spacer(Modifier.height(12.dp))

        BUFFER_OPTIONS.forEach { opt ->
            val active = buffer == opt.mode
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) PlexoraViolet.copy(alpha = 0.2f) else Color(0xFF1F2937))
                    .clickable {
                        buffer = opt.mode
                        scope.launch { BufferPrefs.set(context, opt.mode) }
                    }
                    .padding(14.dp),
            ) {
                Text(opt.label, fontWeight = FontWeight.SemiBold, color = if (active) PlexoraViolet else Color.White)
                Text(opt.desc, fontSize = MaterialTheme.typography.bodySmall.fontSize, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(28.dp))
        DecoderPicker("Décodeur audio", audioDecoder) {
            audioDecoder = it
            scope.launch { PlayerPrefs.setAudioDecoder(context, it) }
        }
        Spacer(Modifier.height(20.dp))
        DecoderPicker("Décodeur vidéo", videoDecoder) {
            videoDecoder = it
            scope.launch { PlayerPrefs.setVideoDecoder(context, it) }
        }
        Text(
            "Essaie le décodeur logiciel si l'image ou le son est corrompu avec le décodeur matériel.",
            color = Color.Gray,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))
        SettingsToggle(
            "Lecture en tunnel",
            "Peut fluidifier la lecture sur certaines TV Android certifiées. Désactive si l'image se fige.",
            tunneling,
        ) {
            tunneling = it
            scope.launch { PlayerPrefs.setTunneling(context, it) }
        }

        Spacer(Modifier.height(28.dp))
        Text("Abonnement", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        expDate?.let { seconds ->
            val daysLeft = ((seconds * 1000 - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt()
            val dateStr = remember(seconds) {
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE).format(java.util.Date(seconds * 1000))
            }
            val warn = daysLeft in 0..7
            val expired = daysLeft < 0
            Text(
                if (expired) "Abonnement expiré depuis le $dateStr" else "Expire le $dateStr${if (warn) " (dans $daysLeft jour${if (daysLeft > 1) "s" else ""})" else ""}",
                color = if (expired || warn) MaterialTheme.colorScheme.error else Color(0xFF9CA3AF),
                fontWeight = if (expired || warn) FontWeight.Bold else FontWeight.Normal,
            )
        } ?: Text("Information non disponible.", color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)

        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            scope.launch {
                CredentialsStore.clear(context)
                onLogout()
            }
        }) { Text("Déconnexion") }

        Spacer(Modifier.height(24.dp))
        Text(
            "Identifiant de cet appareil :\n$deviceId",
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            color = Color.Gray,
        )
    }
}

@Composable
private fun GeneralSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoStart by remember { mutableStateOf(false) }
    var resumeLast by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        autoStart = PlayerPrefs.getAutoStartOnBoot(context)
        resumeLast = PlayerPrefs.getResumeLastChannel(context)
    }

    Column(Modifier.fillMaxSize().widthIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        Text("Général", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        Spacer(Modifier.height(16.dp))
        SettingsToggle(
            "Démarrer automatiquement au démarrage d'Android",
            "Relance Plexora automatiquement apres un redemarrage de la TV/box.",
            autoStart,
        ) {
            autoStart = it
            scope.launch { PlayerPrefs.setAutoStartOnBoot(context, it) }
        }
        Spacer(Modifier.height(12.dp))
        SettingsToggle(
            "Reprendre la dernière chaîne",
            "Preselectionne la derniere chaine regardee au retour sur l'onglet TV.",
            resumeLast,
        ) {
            resumeLast = it
            scope.launch { PlayerPrefs.setResumeLastChannel(context, it) }
        }
    }
}

@Composable
private fun AddPlaylistForm(onCancel: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F2937)).padding(14.dp),
    ) {
        Text("Nouvelle playlist", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Nom (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("URL du serveur") }, placeholder = { Text("http://monserveur.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Nom d'utilisateur") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Mot de passe") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = MaterialTheme.typography.bodySmall.fontSize)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            TextButton(onClick = onCancel) { Text("Annuler") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !saving,
                onClick = {
                    error = null
                    saving = true
                    scope.launch {
                        runCatching {
                            val creds = XtreamCredentials(url, username, password)
                            val info = XtreamClient.create(creds.url).getAccountInfo(creds.username, creds.password)
                            if (info.userInfo?.auth != 1) throw InvalidPlaylistCredentialsException()
                            val finalLabel = label.ifBlank { "$username — ${url.substringAfter("//")}" }
                            PlaylistsStore.upsert(context, finalLabel, creds)
                            onSaved()
                        }.onFailure {
                            error = if (it is InvalidPlaylistCredentialsException) {
                                "Identifiants incorrects. Vérifie l'URL, le nom d'utilisateur et le mot de passe."
                            } else {
                                "Impossible de joindre le serveur.\n(${it.message})"
                            }
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Vérification..." else "Ajouter") }
        }
    }
}
