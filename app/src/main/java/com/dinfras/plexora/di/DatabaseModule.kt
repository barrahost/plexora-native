package com.dinfras.plexora.di

import android.content.Context
import androidx.room.Room
import com.dinfras.plexora.data.local.PlexoraDatabase
import com.dinfras.plexora.data.local.dao.CategoryDao
import com.dinfras.plexora.data.local.dao.ChannelDao
import com.dinfras.plexora.data.local.dao.EpgProgramDao
import com.dinfras.plexora.data.local.dao.MovieDao
import com.dinfras.plexora.data.local.dao.SeriesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Fournit la base Room et ses DAO via Hilt (etape 2/6 du portage
// architecture StreamVault-IPTV, voir le plan) — une seule instance pour
// toute la duree de vie de l'appli, comme XtreamClient.http aujourd'hui.
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlexoraDatabase =
        Room.databaseBuilder(context, PlexoraDatabase::class.java, "plexora.db").build()

    @Provides
    fun provideCategoryDao(db: PlexoraDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideChannelDao(db: PlexoraDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideMovieDao(db: PlexoraDatabase): MovieDao = db.movieDao()

    @Provides
    fun provideSeriesDao(db: PlexoraDatabase): SeriesDao = db.seriesDao()

    @Provides
    fun provideEpgProgramDao(db: PlexoraDatabase): EpgProgramDao = db.epgProgramDao()
}
