package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinfras.plexora.player.LiveVideoPlayer
import com.dinfras.plexora.ui.AppUiState

// Lecteur plein ecran partage entre Live TV, Films et Series — reproduit le
// composant Player.tsx unique de la version web.
@androidx.media3.common.util.UnstableApi
@Composable
fun FullscreenPlayer(streamUrl: String, title: String, onClose: () -> Unit) {
    val overlayAlpha = AppUiState.overlayAlpha.floatValue
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LiveVideoPlayer(streamUrl, Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = overlayAlpha), Color.Transparent)))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
