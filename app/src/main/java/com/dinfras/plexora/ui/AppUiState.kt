package com.dinfras.plexora.ui

import androidx.compose.runtime.mutableFloatStateOf
import com.dinfras.plexora.data.UiPrefs

// Etat en memoire partage entre MainActivity (qui applique le fontScale a
// toute l'appli) et l'ecran Parametres (qui le modifie) — pour que le
// changement de taille de texte s'applique immediatement, sans redemarrage.
object AppUiState {
    val textScale = mutableFloatStateOf(UiPrefs.DEFAULT_TEXT_SCALE)

    // Meme principe pour l'opacite des panneaux qui survolent la video (liste
    // des chaines, categories, guide, barre rapide) — sans etat partage, le
    // reglage n'aurait pris effet qu'apres avoir quitte/rouvert chaque ecran.
    val overlayAlpha = mutableFloatStateOf(UiPrefs.DEFAULT_OVERLAY_ALPHA)
}
