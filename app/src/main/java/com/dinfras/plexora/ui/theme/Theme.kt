package com.dinfras.plexora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette Plexora : violet -> cyan (identique au web)
val PlexoraViolet = Color(0xFF7C3AED)
val PlexoraCyan = Color(0xFF22D3EE)
val PlexoraBackground = Color(0xFF030712)
val PlexoraSurface = Color(0xFF111827)
val PlexoraSurfaceVariant = Color(0xFF1F2937)
val PlexoraOrange = Color(0xFFFB923C) // accent "actif" (chaine/onglet selectionne)

private val PlexoraColorScheme = darkColorScheme(
    primary = PlexoraViolet,
    secondary = PlexoraCyan,
    background = PlexoraBackground,
    surface = PlexoraSurface,
    surfaceVariant = PlexoraSurfaceVariant,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun PlexoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlexoraColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
