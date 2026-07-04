package com.dinfras.plexora.ui

import androidx.compose.runtime.mutableFloatStateOf
import com.dinfras.plexora.data.UiPrefs

// Etat en memoire partage entre MainActivity (qui applique le fontScale a
// toute l'appli) et l'ecran Parametres (qui le modifie) — pour que le
// changement de taille de texte s'applique immediatement, sans redemarrage.
object AppUiState {
    val textScale = mutableFloatStateOf(UiPrefs.DEFAULT_TEXT_SCALE)
}
