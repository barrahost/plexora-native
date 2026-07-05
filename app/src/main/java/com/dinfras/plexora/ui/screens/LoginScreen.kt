package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinfras.plexora.R
import com.dinfras.plexora.data.*
import kotlinx.coroutines.launch

private sealed interface LoginState {
    data object CheckingProvisioning : LoginState
    data object Manual : LoginState
    data object LoggingIn : LoginState
}

private class InvalidCredentialsException : Exception("Identifiants incorrects.")

@Composable
fun LoginScreen(onLoggedIn: (XtreamCredentials) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceId = remember { getDeviceId(context) }

    var state by remember { mutableStateOf<LoginState>(LoginState.CheckingProvisioning) }
    var isM3uMode by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uLink by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // 1) Identifiants deja enregistres localement -> connexion directe
    // 2) Sinon, appareil associe a une playlist via Supabase (device_id) -> auto-config
    // 3) Sinon, formulaire manuel (comme la version web)
    LaunchedEffect(Unit) {
        val saved = CredentialsStore.load(context)
        if (saved != null) {
            onLoggedIn(saved)
            return@LaunchedEffect
        }
        val provisioned = DeviceProvisioningClient.lookup(deviceId)
        if (provisioned.isNotEmpty()) {
            // Comme HotPlayer, un appareil peut avoir plusieurs playlists
            // associees, Xtream ou M3U : on les importe toutes dans
            // PlaylistsStore (deja utilise par Parametres > Listes de lecture
            // pour switcher entre comptes), la premiere devenant active.
            val importedCreds = provisioned.mapIndexedNotNull { index, p ->
                val creds = if (p.type == "m3u") {
                    // Detecte au passage si ce lien M3U est en realite un compte
                    // Xtream complet (get.php?username=...&password=...).
                    p.m3uLink?.let { resolveM3uOrXtream(it) }
                } else {
                    if (p.serverUrl != null && p.username != null && p.password != null) {
                        XtreamCredentials(p.serverUrl, p.username, p.password)
                    } else null
                }
                creds?.let {
                    val label = p.label?.takeIf { l -> l.isNotBlank() } ?: "Playlist ${index + 1}"
                    PlaylistsStore.upsert(context, label, it)
                    it
                }
            }
            val active = importedCreds.firstOrNull()
            if (active != null) {
                CredentialsStore.save(context, active)
                onLoggedIn(active)
            } else {
                state = LoginState.Manual
            }
        } else {
            state = LoginState.Manual
        }
    }

    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        when (state) {
            LoginState.CheckingProvisioning -> CircularProgressIndicator()
            else -> Column(
                // Defile verticalement : le formulaire (logo + selecteur + 3
                // champs + bouton + id appareil) depasse la hauteur visible sur
                // une TV avec la marge overscan, le bouton "Se connecter" se
                // retrouvait sous le bord bas de l'ecran, injoignable.
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_plexora_login),
                    contentDescription = "Plexora",
                    modifier = Modifier.width(220.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("Connecte-toi avec ton compte Xtream ou un lien M3U", fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))

                // Segmented control : deux sources possibles, comme le
                // selecteur equivalent ajoute dans l'admin panel.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                ) {
                    SegmentButton("Xtream", selected = !isM3uMode, modifier = Modifier.weight(1f)) { isM3uMode = false }
                    SegmentButton("M3U", selected = isM3uMode, modifier = Modifier.weight(1f)) { isM3uMode = true }
                }
                Spacer(Modifier.height(16.dp))

                if (isM3uMode) {
                    OutlinedTextField(
                        value = m3uLink, onValueChange = { m3uLink = it },
                        label = { Text("Lien M3U") },
                        placeholder = { Text("http://monserveur.com/get.php?...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("URL du serveur") },
                        placeholder = { Text("http://monserveur.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text("Nom d'utilisateur") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Mot de passe") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        error = null
                        state = LoginState.LoggingIn
                        scope.launch {
                            runCatching {
                                if (isM3uMode) {
                                    // Beaucoup de liens M3U de revendeurs Xtream sont en fait
                                    // un compte complet deguise (get.php?username=...&password=...) :
                                    // on en profite pour avoir l'API complete si c'est le cas.
                                    val creds = resolveM3uOrXtream(m3uLink)
                                    if (creds.isM3u()) {
                                        val catalog = M3uParser.fetchAndParse(m3uLink)
                                        if (catalog.liveChannels.isEmpty() && catalog.movies.isEmpty() && catalog.series.isEmpty()) {
                                            throw InvalidCredentialsException()
                                        }
                                    }
                                    CredentialsStore.save(context, creds)
                                    onLoggedIn(creds)
                                } else {
                                    val creds = XtreamCredentials(url, username, password)
                                    val info = XtreamClient.create(creds.url).getAccountInfo(creds.username, creds.password)
                                    if (info.userInfo?.auth != 1) throw InvalidCredentialsException()
                                    CredentialsStore.save(context, creds)
                                    onLoggedIn(creds)
                                }
                            }.onFailure {
                                error = if (it is InvalidCredentialsException) {
                                    if (isM3uMode) "Lien M3U invalide ou vide." else "Identifiants incorrects. Vérifie l'URL, le nom d'utilisateur et le mot de passe."
                                } else {
                                    "Impossible de joindre le serveur.\n(${it.message})"
                                }
                                state = LoginState.Manual
                            }
                        }
                    },
                    enabled = state != LoginState.LoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state == LoginState.LoggingIn) "Connexion..." else "Se connecter")
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "Identifiant de cet appareil (pour association playlist) :\n$deviceId",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}
