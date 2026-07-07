package com.dinfras.plexora.data.local.entity

import androidx.room.Entity

// Entites Room du catalogue (etape 2/6 du portage architecture StreamVault-
// IPTV, voir le plan) : remplaceront a terme les fichiers JSON de
// CatalogCache.kt/LocalEpgStore.kt. Purement additif pour l'instant --
// aucun ecran ne les consulte encore.

enum class CatalogContentType { LIVE, VOD, SERIES }

@Entity(tableName = "categories", primaryKeys = ["categoryId", "contentType"])
data class CategoryEntity(
    val categoryId: String,
    val categoryName: String,
    val contentType: CatalogContentType,
)

@Entity(tableName = "channels", primaryKeys = ["streamId"])
data class ChannelEntity(
    val streamId: Int,
    val num: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String,
    val epgChannelId: String?,
    val tvArchive: Int,
    val directUrl: String?,
)

@Entity(tableName = "movies", primaryKeys = ["streamId"])
data class MovieEntity(
    val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val cover: String?,
    val categoryId: String,
    val containerExtension: String?,
    val rating5based: Double?,
    val directUrl: String?,
)

@Entity(tableName = "series", primaryKeys = ["seriesId"])
data class SeriesEntity(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val rating5based: Double?,
)

// epgChannelId + startTimestamp : une chaine peut avoir plusieurs programmes,
// chacun identifie par son heure de debut.
@Entity(tableName = "epg_programs", primaryKeys = ["epgChannelId", "startTimestamp"])
data class EpgProgramEntity(
    val epgChannelId: String,
    val title: String,
    val description: String?,
    val start: String,
    val end: String,
    val startTimestamp: Long,
    val stopTimestamp: Long,
    val nowPlaying: Int,
)
