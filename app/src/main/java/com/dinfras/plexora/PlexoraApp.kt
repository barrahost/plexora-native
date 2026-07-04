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
