package com.dinfras.plexora.di

import com.dinfras.plexora.player.PlayerEngine
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Pont vers PlayerEngine (Hilt-scope) depuis des composables qui ne sont pas
// encore des ViewModels Hilt (etape 4/6 du portage architecture
// StreamVault-IPTV, voir le plan). Le re-cablage complet des ecrans en
// ViewModels (etape 5) rendra ce pont inutile -- il permet de brancher le
// nouveau moteur sans attendre cette etape.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerEngineEntryPoint {
    fun playerEngine(): PlayerEngine
}

fun playerEngineOf(context: android.content.Context): PlayerEngine =
    EntryPoints.get(context.applicationContext, PlayerEngineEntryPoint::class.java).playerEngine()
