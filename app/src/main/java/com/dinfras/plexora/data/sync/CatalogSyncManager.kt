package com.dinfras.plexora.data.sync

import androidx.room.withTransaction
import com.dinfras.plexora.data.DebugLog
import com.dinfras.plexora.data.XtreamCategory
import com.dinfras.plexora.data.XtreamChannel
import com.dinfras.plexora.data.XtreamCredentials
import com.dinfras.plexora.data.XtreamMovie
import com.dinfras.plexora.data.XtreamSeries
import com.dinfras.plexora.data.XtreamService
import com.dinfras.plexora.data.local.PlexoraDatabase
import com.dinfras.plexora.data.local.dao.CategoryDao
import com.dinfras.plexora.data.local.dao.ChannelDao
import com.dinfras.plexora.data.local.dao.MovieDao
import com.dinfras.plexora.data.local.dao.SeriesDao
import com.dinfras.plexora.data.local.entity.CatalogContentType
import com.dinfras.plexora.data.local.entity.CategoryEntity
import com.dinfras.plexora.data.local.entity.ChannelEntity
import com.dinfras.plexora.data.local.entity.MovieEntity
import com.dinfras.plexora.data.local.entity.SeriesEntity
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSectionResult(val ok: Boolean, val error: String? = null)

data class CatalogSyncResult(
    val live: SyncSectionResult,
    val vod: SyncSectionResult,
    val series: SyncSectionResult,
)

// Synchronisation avec commit atomique (etape 3/6 du portage architecture
// StreamVault-IPTV, voir le plan) : chaque section (Live/Films/Series) est
// entierement recuperee EN MEMOIRE (runCatching, pas d'ecriture en base)
// avant toute ecriture -- un echec reseau sur une section ne touche JAMAIS
// la base pour cette section (elle garde son dernier etat valide, comme le
// faisait deja CatalogCache.kt pour un compte sans Films par exemple).
// L'ecriture elle-meme (vider + inserer) se fait dans une seule transaction
// Room (withTransaction), donc aucun etat partiel visible meme en cas de
// plantage pendant l'ecriture. Contrairement au systeme a etapes complet de
// StreamVault (STARTING/FETCHING/RECOVERING/STAGED/COMMITTING/COMPLETED,
// pense pour des catalogues de 100 000+ items traites par lots), on garde
// ici une version simplifiee adaptee a l'echelle de Plexora (un seul compte
// Xtream, quelques milliers d'items) : la "zone de staging" est simplement
// la memoire, pas des tables SQL dediees.
@Singleton
class CatalogSyncManager @Inject constructor(
    private val database: PlexoraDatabase,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
) {
    suspend fun syncXtreamCatalog(creds: XtreamCredentials, service: XtreamService): CatalogSyncResult {
        DebugLog.event("CatalogSyncManager: STARTING")

        val live = runCatching {
            val categories = service.getLiveCategories(creds.username, creds.password)
            val channels = service.getLiveStreams(creds.username, creds.password).filter { it.streamId > 0 }
            categories to channels
        }
        val vod = runCatching {
            val categories = service.getVodCategories(creds.username, creds.password)
            val movies = service.getVodStreams(creds.username, creds.password).filter { it.streamId > 0 }
            categories to movies
        }
        val series = runCatching {
            val categories = service.getSeriesCategories(creds.username, creds.password)
            val list = service.getSeriesList(creds.username, creds.password).filter { it.seriesId > 0 }
            categories to list
        }

        DebugLog.event("CatalogSyncManager: COMMITTING")

        live.getOrNull()?.let { (categories, channels) ->
            database.withTransaction {
                categoryDao.clearType(CatalogContentType.LIVE)
                categoryDao.insertAll(categories.map { it.toEntity(CatalogContentType.LIVE) })
                channelDao.clearAll()
                channelDao.insertAll(channels.map { it.toEntity() })
            }
        }
        vod.getOrNull()?.let { (categories, movies) ->
            database.withTransaction {
                categoryDao.clearType(CatalogContentType.VOD)
                categoryDao.insertAll(categories.map { it.toEntity(CatalogContentType.VOD) })
                movieDao.clearAll()
                movieDao.insertAll(movies.map { it.toEntity() })
            }
        }
        series.getOrNull()?.let { (categories, list) ->
            database.withTransaction {
                categoryDao.clearType(CatalogContentType.SERIES)
                categoryDao.insertAll(categories.map { it.toEntity(CatalogContentType.SERIES) })
                seriesDao.clearAll()
                seriesDao.insertAll(list.map { it.toEntity() })
            }
        }

        DebugLog.event("CatalogSyncManager: COMPLETED")
        return CatalogSyncResult(
            live = live.toSectionResult(),
            vod = vod.toSectionResult(),
            series = series.toSectionResult(),
        )
    }
}

private fun <T> Result<T>.toSectionResult(): SyncSectionResult =
    if (isSuccess) SyncSectionResult(true) else SyncSectionResult(false, exceptionOrNull()?.message)

private fun XtreamCategory.toEntity(type: CatalogContentType) =
    CategoryEntity(categoryId = categoryId, categoryName = categoryName, contentType = type)

private fun XtreamChannel.toEntity() =
    ChannelEntity(
        streamId = streamId,
        num = num,
        name = name,
        streamIcon = streamIcon,
        categoryId = categoryId,
        epgChannelId = epgChannelId,
        tvArchive = tvArchive,
        directUrl = directUrl,
    )

private fun XtreamMovie.toEntity() =
    MovieEntity(
        streamId = streamId,
        name = name,
        streamIcon = streamIcon,
        cover = cover,
        categoryId = categoryId,
        containerExtension = containerExtension,
        rating5based = rating5based,
        directUrl = directUrl,
    )

private fun XtreamSeries.toEntity() =
    SeriesEntity(seriesId = seriesId, name = name, cover = cover, categoryId = categoryId, rating5based = rating5based)
