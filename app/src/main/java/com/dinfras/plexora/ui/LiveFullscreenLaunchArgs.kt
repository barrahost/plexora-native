package com.dinfras.plexora.ui

import com.dinfras.plexora.data.XtreamCategory
import com.dinfras.plexora.data.XtreamChannel
import com.dinfras.plexora.data.XtreamCredentials
import com.dinfras.plexora.data.XtreamService

// Le plein ecran live tourne dans sa propre Activite (LiveFullscreenActivity)
// plutot qu'en calque Compose superpose (FullscreenHost) : ce calque, pourtant
// utilise avec succes par Films/Series, ne "committait" jamais ses effets
// (DisposableEffect/focus) pour le lecteur live plus complexe — diagnostique
// via le journal de bord (checkpoints atteints, mais gestionnaire jamais
// enregistre), sans cause identifiable au niveau du code. Une Activite dediee
// a son propre cycle de vie/fenetre, plus robuste.
//
// Les listes (categories/chaines, potentiellement des milliers d'entrees) et
// les objets non-serialisables (XtreamService) passent par ce holder statique
// en memoire (meme processus) plutot que par les extras d'Intent.
object LiveFullscreenLaunchArgs {
    @Volatile var creds: XtreamCredentials? = null
    @Volatile var service: XtreamService? = null
    @Volatile var categories: List<XtreamCategory> = emptyList()
    @Volatile var channels: List<XtreamChannel> = emptyList()
    @Volatile var initialChannel: XtreamChannel? = null

    // Renseigne par l'Activite juste avant sa fermeture : permet a l'ecran
    // appelant de resynchroniser sa chaine active (zapping fait pendant le
    // plein ecran) une fois revenu au premier plan.
    @Volatile var lastChannel: XtreamChannel? = null
}
