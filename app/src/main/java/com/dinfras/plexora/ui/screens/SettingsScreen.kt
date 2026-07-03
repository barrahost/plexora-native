package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.data.BufferMode
import com.dinfras.plexora.data.BufferPrefs
import com.dinfras.plexora.data.CredentialsStore
import com.dinfras.plexora.data.getDeviceId
import com.dinfras.plexora.ui.theme.PlexoraViolet
import kotlinx.coroutines.launch

private data class BufferOption(val mode: BufferMode, val label: String, val desc: String)

private val BUFFER_OPTIONS = listOf(
    BufferOption(BufferMode.NONE, "Aucun", "Latence minimale, très sensible aux coupures. Pour réseau très stable uniquement."),
    BufferOption(BufferMode.SMALL, "Faible", "Réaction rapide, plus sensible aux coupures sur réseau instable."),
    BufferOption(BufferMode.MEDIUM, "Moyen", "Équilibre recommandé pour la plupart des connexions."),
    BufferOption(BufferMode.HIGH, "Élevé", "Absorbe les ralentissements serveur — moins de coupures."),
)

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceId = remember { getDeviceId(context) }
    var buffer by remember { mutableStateOf(BufferMode.MEDIUM) }

    LaunchedEffect(Unit) { buffer = BufferPrefs.get(context) }

    Column(Modifier.fillMaxSize().padding(32.dp).widthIn(max = 480.dp)) {
        Text("Paramètres", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.headlineSmall.fontSize)
        Spacer(Modifier.height(24.dp))

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

        Spacer(Modifier.height(32.dp))
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
