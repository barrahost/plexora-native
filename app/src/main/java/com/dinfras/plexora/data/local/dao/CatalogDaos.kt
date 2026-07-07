package com.dinfras.plexora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dinfras.plexora.data.local.entity.CatalogContentType
import com.dinfras.plexora.data.local.entity.CategoryEntity
import com.dinfras.plexora.data.local.entity.ChannelEntity
import com.dinfras.plexora.data.local.entity.EpgProgramEntity
import com.dinfras.plexora.data.local.entity.MovieEntity
import com.dinfras.plexora.data.local.entity.SeriesEntity

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE contentType = :type")
    suspend fun getByType(type: CatalogContentType): List<CategoryEntity>

    @Query("DELETE FROM categories WHERE contentType = :type")
    suspend fun clearType(type: CatalogContentType)
}

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Query("SELECT * FROM channels")
    suspend fun getAll(): List<ChannelEntity>

    @Query("DELETE FROM channels")
    suspend fun clearAll()
}

@Dao
interface MovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MovieEntity>)

    @Query("SELECT * FROM movies")
    suspend fun getAll(): List<MovieEntity>

    @Query("DELETE FROM movies")
    suspend fun clearAll()
}

@Dao
interface SeriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeriesEntity>)

    @Query("SELECT * FROM series")
    suspend fun getAll(): List<SeriesEntity>

    @Query("DELETE FROM series")
    suspend fun clearAll()
}

@Dao
interface EpgProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpgProgramEntity>)

    @Query("SELECT * FROM epg_programs WHERE epgChannelId = :epgChannelId ORDER BY startTimestamp")
    suspend fun programsFor(epgChannelId: String): List<EpgProgramEntity>

    @Query("DELETE FROM epg_programs")
    suspend fun clearAll()
}
