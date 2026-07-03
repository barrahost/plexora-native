package com.dinfras.plexora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

private val GRADIENTS = listOf(
    listOf(Color(0xFF7C3AED), Color(0xFFD946EF)),
    listOf(Color(0xFF2563EB), Color(0xFF22D3EE)),
    listOf(Color(0xFFF97316), Color(0xFFFBBF24)),
    listOf(Color(0xFF059669), Color(0xFF2DD4BF)),
    listOf(Color(0xFFE11D48), Color(0xFFF472B6)),
)

private fun initialsOf(name: String): String {
    val clean = name.replace(Regex("^[^a-zA-Z0-9]*([A-Z]{2,3}\\s*\\|)?\\s*"), "").trim()
    val words = clean.split(Regex("\\s+")).filter { it.any(Char::isLetterOrDigit) }
    return if (words.size >= 2) "${words[0][0]}${words[1][0]}".uppercase()
    else clean.take(2).uppercase().ifBlank { name.take(2).uppercase() }
}

// Logo de chaine avec repli elegant (initiales sur degrade) quand l'icone est
// absente ou casse — comme sur TiviMate, le fond reste transparent des que
// l'image se charge correctement (pas de pastille colorée derriere un logo valide).
@Composable
fun ChannelLogo(name: String, iconUrl: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    if (iconUrl.isNullOrBlank()) {
        FallbackBadge(name, size, modifier)
        return
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = iconUrl,
            contentDescription = null,
            modifier = Modifier.size(size),
        ) {
            val state = painter.state
            if (state is AsyncImagePainter.State.Error) {
                FallbackBadge(name, size)
            } else {
                SubcomposeAsyncImageContent(modifier = Modifier.size(size), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable
private fun FallbackBadge(name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val gradient = GRADIENTS[Math.floorMod(name.hashCode(), GRADIENTS.size)]
    Box(
        modifier.size(size).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initialsOf(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.6).sp)
    }
}
