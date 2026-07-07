package com.dinfras.plexora

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.dinfras.plexora.data.XtreamClient
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Configure Coil une seule fois pour toute l'appli : cache disque/memoire
// explicite + fondu a l'affichage (au lieu d'un pop-in brutal, comme
// TiviMate) + le meme client OkHttp que l'API pour ne pas multiplier les
// pools de connexions vers le serveur IPTV.
//
// @HiltAndroidApp : fondation du portage architecture StreamVault-IPTV
// (etape 1/6, voir le plan) — active la generation du graphe de dependances
// Hilt pour toute l'appli.
//
// Configuration.Provider (etape 3/6) : WorkManager utilise HiltWorkerFactory
// pour injecter les dependances des Workers (CatalogSyncWorker) — necessite
// de desactiver son initialisation par defaut dans AndroidManifest.xml.
@HiltAndroidApp
class PlexoraApp : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()


    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { XtreamClient.http }
        .crossfade(200)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            // 2% du disque est bien trop petit pour un catalogue de plusieurs
            // milliers d'affiches : le cache se vidait en permanence et
            // rechargeait les memes images a chaque ouverture, d'ou la
            // lenteur constante. Taille fixe et genereuse a la place.
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(300L * 1024 * 1024)
                .build()
        }
        .build()
}
