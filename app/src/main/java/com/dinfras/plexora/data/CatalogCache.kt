package com.dinfras.plexora.data

import android.content.Context
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// TiviMate ne recharge plus jamais les catalogues une fois recuperes : ils
// sont ecrits sur le disque de l'appareil apres la saisie des identifiants,
// et relus au demarrage au lieu de refaire les appels reseau. Ce cache
// combine memoire (instantane pendant la session) + disque (survit a un
// redemarrage de l'appli), avec rafraichissement discret en arriere-plan
// uniquement lorsque les donnees sont perimees (au-dela de STALE_AFTER_MS).
object CatalogCache {
    const val STALE_AFTER_MS = 24L * 60 * 60 * 1000 // 24h

    @JsonClass(generateAdapter = true)
    data class LiveData(val categories: List<XtreamCategory>, val channels: List<XtreamChannel>, val fetchedAt: Long = System.currentTimeMillis())

    @JsonClass(generateAdapter = true)
    data class MovieData(val categories: List<XtreamCategory>, val movies: List<XtreamMovie>, val fetchedAt: Long = System.currentTimeMillis())

    @JsonClass(generateAdapter = true)
    data class SeriesData(val categories: List<XtreamCategory>, val series: List<XtreamSeries>, val fetchedAt: Long = System.currentTimeMillis())

    @Volatile private var live: LiveData? = null
    @Volatile private var movies: MovieData? = null
    @Volatile private var series: SeriesData? = null

    private const val FILE_LIVE = "catalog_live.json"
    private const val FILE_MOVIES = "catalog_movies.json"
    private const val FILE_SERIES = "catalog_series.json"

    fun getLive() = live
    fun getMovies() = movies
    fun getSeries() = series

    fun isStale(fetchedAt: Long): Boolean = System.currentTimeMillis() - fetchedAt > STALE_AFTER_MS

    suspend fun setLive(context: Context, data: LiveData) {
        live = data
        writeToDisk(context, FILE_LIVE, MoshiProvider.instance.adapter(LiveData::class.java).toJson(data))
    }

    suspend fun setMovies(context: Context, data: MovieData) {
        movies = data
        writeToDisk(context, FILE_MOVIES, MoshiProvider.instance.adapter(MovieData::class.java).toJson(data))
    }

    suspend fun setSeries(context: Context, data: SeriesData) {
        series = data
        writeToDisk(context, FILE_SERIES, MoshiProvider.instance.adapter(SeriesData::class.java).toJson(data))
    }

    // Relit le disque si rien n'est encore en memoire (ex. relance de
    // l'appli) — n'a d'effet que la toute premiere fois par session.
    suspend fun loadLiveFromDisk(context: Context): LiveData? {
        live?.let { return it }
        val loaded = readFromDisk(context, FILE_LIVE) { MoshiProvider.instance.adapter(LiveData::class.java).fromJson(it) }
        if (loaded != null) live = loaded
        return loaded
    }

    suspend fun loadMoviesFromDisk(context: Context): MovieData? {
        movies?.let { return it }
        val loaded = readFromDisk(context, FILE_MOVIES) { MoshiProvider.instance.adapter(MovieData::class.java).fromJson(it) }
        if (loaded != null) movies = loaded
        return loaded
    }

    suspend fun loadSeriesFromDisk(context: Context): SeriesData? {
        series?.let { return it }
        val loaded = readFromDisk(context, FILE_SERIES) { MoshiProvider.instance.adapter(SeriesData::class.java).fromJson(it) }
        if (loaded != null) series = loaded
        return loaded
    }

    fun clear(context: Context) {
        live = null
        movies = null
        series = null
        listOf(FILE_LIVE, FILE_MOVIES, FILE_SERIES).forEach { File(context.filesDir, it).delete() }
    }

    private suspend fun writeToDisk(context: Context, fileName: String, json: String) = withContext(Dispatchers.IO) {
        runCatching { File(context.filesDir, fileName).writeText(json) }
    }

    private suspend fun <T> readFromDisk(context: Context, fileName: String, parse: (String) -> T?): T? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext null
        runCatching { parse(file.readText()) }.getOrNull()
    }
}
