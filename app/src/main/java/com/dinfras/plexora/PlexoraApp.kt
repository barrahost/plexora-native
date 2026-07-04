package com.dinfras.plexora

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.dinfras.plexora.data.XtreamClient

// Configure Coil une seule fois pour toute l'appli : cache disque/memoire
// explicite + fondu a l'affichage (au lieu d'un pop-in brutal, comme
// TiviMate) + le meme client OkHttp que l'API pour ne pas multiplier les
// pools de connexions vers le serveur IPTV.
class PlexoraApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { XtreamClient.http }
        .crossfade(200)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizePercent(0.02)
                .build()
        }
        .build()
}
