package com.dinfras.plexora.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

// La lecture plein ecran doit couvrir TOUT l'ecran de la TV, pas seulement
// la zone de contenu reduite par la marge de securite overscan (appliquee
// autour de la sidebar/navigation pour ne pas etre coupee sur certaines
// TV). En rendant ce contenu ici, au niveau racine de l'activite (en dehors
// de cette marge), le lecteur atteint reellement les bords de l'ecran.
object FullscreenHost {
    val content = mutableStateOf<(@Composable () -> Unit)?>(null)
}
