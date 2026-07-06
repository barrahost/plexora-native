package com.dinfras.plexora.ui

// Repli pour le plein ecran live : le journal de diagnostic a confirme
// qu'Android recoit bien chaque touche (Activity.dispatchKeyEvent) mais que
// le focus Compose du lecteur plein ecran se perd parfois silencieusement
// (onKeyEvent jamais declenche) — l'ecran reste alors noir/fige sans que rien
// ne reponde. Plutot que de continuer a chercher pourquoi le focus Compose
// se perd, on route les touches directement depuis l'Activite (fiable) vers
// le gestionnaire du plein ecran actif, sans dependre du systeme de focus.
object FullscreenKeyHandler {
    @Volatile var handler: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null
}
