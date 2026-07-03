package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinfras.plexora.ui.theme.PlexoraOrange

// Fiche d'aperçu affichée au-dessus des grilles Films/Séries, mise a jour au
// fil de la navigation D-pad (comme le panneau central de TiviMate).
@Composable
fun MediaPreviewInfo(
    title: String,
    rating: Double?,
    releaseDate: String?,
    genre: String?,
    plot: String?,
    cast: String?,
    director: String?,
) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row {
            if (rating != null && rating > 0) {
                Text(
                    String.format("%.1f", rating),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.background(PlexoraOrange, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        val meta = listOfNotNull(releaseDate?.take(4)?.takeIf { it.isNotBlank() }, genre).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, color = Color.Gray, fontSize = 13.sp)
        }
        director?.let {
            if (it.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Réalisateur : $it", color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1)
            }
        }
        cast?.let {
            if (it.isNotBlank()) {
                Text("Acteurs : $it", color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1)
            }
        }
        plot?.let {
            if (it.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Color.LightGray, fontSize = 13.sp, maxLines = 3)
            }
        }
    }
}
