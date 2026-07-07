package com.dinfras.plexora.data

import com.dinfras.plexora.data.local.dao.CategoryDao
import com.dinfras.plexora.data.local.dao.ChannelDao
import com.dinfras.plexora.data.local.dao.MovieDao
import com.dinfras.plexora.data.local.dao.SeriesDao
import com.dinfras.plexora.data.local.entity.CatalogContentType
import com.dinfras.plexora.data.sync.CatalogSyncManager
import com.dinfras.plexora.data.sync.CatalogSyncResult
import javax.inject.Inject
import javax.inject.Singleton

// Facade Room pour les ecrans (etape 5/6 du portage architecture
// StreamVault-IPTV, voir le plan) : les ViewModels lisent/ecrivent le
// catalogue via ici plutot que via CatalogCache.kt (fichiers JSON,
// progressivement remplace). Mappe les entites Room vers les types
// Xtream* existants pour que l'UI (grilles, listes, fiches detail) n'ait
// rien a changer.
@Singleton
class CatalogRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val syncManager: CatalogSyncManager,
) {
    suspend fun liveCategories(): List<XtreamCategory> =
        categoryDao.getByType(CatalogContentType.LIVE).map { it.toXtream() }

    suspend fun channels(): List<XtreamChannel> = channelDao.getAll().map { it.toXtream() }

    suspend fun movieCategories(): List<XtreamCategory> =
        categoryDao.getByType(CatalogContentType.VOD).map { it.toXtream() }

    suspend fun movies(): List<XtreamMovie> = movieDao.getAll().map { it.toXtream() }

    suspend fun seriesCategories(): List<XtreamCategory> =
        categoryDao.getByType(CatalogContentType.SERIES).map { it.toXtream() }

    suspend fun series(): List<XtreamSeries> = seriesDao.getAll().map { it.toXtream() }

    suspend fun syncXtream(creds: XtreamCredentials, service: XtreamService): CatalogSyncResult =
        syncManager.syncXtreamCatalog(creds, service)
}

private fun com.dinfras.plexora.data.local.entity.CategoryEntity.toXtream() =
    XtreamCategory(categoryId = categoryId, categoryName = categoryName)

private fun com.dinfras.plexora.data.local.entity.ChannelEntity.toXtream() =
    XtreamChannel(
        num = num,
        name = name,
        streamId = streamId,
        streamIcon = streamIcon,
        categoryId = categoryId,
        epgChannelId = epgChannelId,
        tvArchive = tvArchive,
        directUrl = directUrl,
    )

private fun com.dinfras.plexora.data.local.entity.MovieEntity.toXtream() =
    XtreamMovie(
        name = name,
        streamId = streamId,
        streamIcon = streamIcon,
        cover = cover,
        categoryId = categoryId,
        containerExtension = containerExtension,
        rating5based = rating5based,
        directUrl = directUrl,
    )

private fun com.dinfras.plexora.data.local.entity.SeriesEntity.toXtream() =
    XtreamSeries(name = name, seriesId = seriesId, cover = cover, categoryId = categoryId, rating5based = rating5based)
