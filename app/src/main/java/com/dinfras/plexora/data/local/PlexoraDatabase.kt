package com.dinfras.plexora.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dinfras.plexora.data.local.dao.CategoryDao
import com.dinfras.plexora.data.local.dao.ChannelDao
import com.dinfras.plexora.data.local.dao.EpgProgramDao
import com.dinfras.plexora.data.local.dao.MovieDao
import com.dinfras.plexora.data.local.dao.SeriesDao
import com.dinfras.plexora.data.local.entity.CategoryEntity
import com.dinfras.plexora.data.local.entity.ChannelEntity
import com.dinfras.plexora.data.local.entity.EpgProgramEntity
import com.dinfras.plexora.data.local.entity.MovieEntity
import com.dinfras.plexora.data.local.entity.SeriesEntity

// Base SQLite locale du catalogue (etape 2/6 du portage architecture
// StreamVault-IPTV, voir le plan) : remplacera CatalogCache.kt/
// LocalEpgStore.kt une fois les ecrans re-cables (etape 5). Schema pas
// encore exporte (exportSchema=false) : pas de migration a gerer tant que
// la base n'est pas utilisee en production.
@Database(
    entities = [
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpgProgramEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PlexoraDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun epgProgramDao(): EpgProgramDao
}
