package com.dinfras.plexora.data

import android.content.Context
import com.squareup.moshi.Types
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class M3uCatalog(
    val liveCategories: List<XtreamCategory>,
    val liveChannels: List<XtreamChannel>,
    val movieCategories: List<XtreamCategory>,
    val movies: List<XtreamMovie>,
    val seriesCategories: List<XtreamCategory>,
    val series: List<XtreamSeries>,
    // Pas d'endpoint "get_series_info" pour une playlist M3U — les episodes de
    // chaque serie sont deja connus au moment du parsing, stockes ici plutot
    // que recharges a la demande.
    val seriesEpisodes: Map<Int, List<SeriesEpisode>>,
    // Attribut url-tvg/x-tvg-url de l'entete M3U, si present — guide XMLTV
    // associe a la playlist (repris par LocalEpgStore.refreshFromUrl).
    val xmltvUrl: String?,
)

// Groupe les entrees "series" par titre d'emission avant de les eclater en
// XtreamSeries + episodes — un simple regex retire le marqueur de saison/
// episode (S01E02, 1x02) du titre affiche pour retrouver le nom de la serie.
private val EPISODE_MARKER = Pattern.compile(
    """\s*[-–|]?\s*S(\d{1,2})\s*[.\- ]?E(\d{1,3})\b|\bS(\d{1,2})E(\d{1,3})\b|\b(\d{1,2})x(\d{1,3})\b""",
    Pattern.CASE_INSENSITIVE,
)

private data class RawEntry(
    val tvgId: String?,
    val tvgLogo: String?,
    val groupTitle: String?,
    val name: String,
    val url: String,
)

object M3uParser {
    private val http = XtreamClient.http.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAndParse(m3uUrl: String): M3uCatalog {
        val request = Request.Builder().url(m3uUrl).build()
        val text = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw java.io.IOException("Reponse vide")
        }
        return parse(text)
    }

    fun parse(text: String): M3uCatalog {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var xmltvUrl: String? = null
        val entries = mutableListOf<RawEntry>()

        var pendingTvgId: String? = null
        var pendingTvgLogo: String? = null
        var pendingGroup: String? = null
        var pendingName: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXTM3U") -> {
                    xmltvUrl = attr(line, "url-tvg") ?: attr(line, "x-tvg-url")
                }
                line.startsWith("#EXTINF") -> {
                    pendingTvgId = attr(line, "tvg-id")
                    pendingTvgLogo = attr(line, "tvg-logo")
                    pendingGroup = attr(line, "group-title")
                    pendingName = line.substringAfterLast(',').trim().ifBlank { null }
                }
                line.startsWith("#") -> {
                    // Autres directives (#EXTGRP, #EXTVLCOPT...) ignorees.
                }
                else -> {
                    val name = pendingName
                    if (name != null) {
                        entries.add(RawEntry(pendingTvgId, pendingTvgLogo, pendingGroup, name, line))
                    }
                    pendingTvgId = null
                    pendingTvgLogo = null
                    pendingGroup = null
                    pendingName = null
                }
            }
        }

        val liveCats = LinkedHashMap<String, XtreamCategory>()
        val movieCats = LinkedHashMap<String, XtreamCategory>()
        val seriesCats = LinkedHashMap<String, XtreamCategory>()
        val liveChannels = mutableListOf<XtreamChannel>()
        val movies = mutableListOf<XtreamMovie>()
        // showTitle -> episodes bruts, assembles en XtreamSeries a la fin.
        val showEpisodes = LinkedHashMap<String, MutableList<Pair<RawEntry, MatchResult2?>>>()

        var nextId = 1
        for (entry in entries) {
            val group = entry.groupTitle?.ifBlank { null } ?: "Autres"
            val bucket = classify(group, entry.name)
            when (bucket) {
                Bucket.MOVIE -> {
                    val cat = movieCats.getOrPut(group) { XtreamCategory(categoryId = "m3u-movie-$group", categoryName = group) }
                    movies.add(
                        XtreamMovie(
                            name = entry.name,
                            streamId = nextId++,
                            streamIcon = entry.tvgLogo,
                            cover = entry.tvgLogo,
                            categoryId = cat.categoryId,
                            containerExtension = entry.url.substringAfterLast('.', "mp4").take(4),
                            directUrl = entry.url,
                        ),
                    )
                }
                Bucket.SERIES -> {
                    val match = extractEpisode(entry.name)
                    val showTitle = match?.showTitle ?: entry.name
                    showEpisodes.getOrPut("$group||$showTitle") { mutableListOf() }.add(entry to match)
                    seriesCats.getOrPut(group) { XtreamCategory(categoryId = "m3u-series-$group", categoryName = group) }
                }
                Bucket.LIVE -> {
                    val cat = liveCats.getOrPut(group) { XtreamCategory(categoryId = "m3u-live-$group", categoryName = group) }
                    liveChannels.add(
                        XtreamChannel(
                            num = nextId,
                            name = entry.name,
                            streamId = nextId++,
                            streamIcon = entry.tvgLogo,
                            categoryId = cat.categoryId,
                            epgChannelId = entry.tvgId,
                            directUrl = entry.url,
                        ),
                    )
                }
            }
        }

        val seriesList = mutableListOf<XtreamSeries>()
        val seriesEpisodesMap = mutableMapOf<Int, List<SeriesEpisode>>()
        for ((key, eps) in showEpisodes) {
            val group = key.substringBefore("||")
            val showTitle = key.substringAfter("||")
            val cat = seriesCats[group] ?: continue
            val seriesId = nextId++
            val cover = eps.firstOrNull()?.first?.tvgLogo
            seriesList.add(XtreamSeries(name = showTitle, seriesId = seriesId, cover = cover, categoryId = cat.categoryId))
            seriesEpisodesMap[seriesId] = eps
                .sortedWith(compareBy({ it.second?.season ?: 0 }, { it.second?.episode ?: 0 }))
                .mapIndexed { idx, (entry, match) ->
                    SeriesEpisode(
                        id = entry.url,
                        episodeNum = match?.episode ?: (idx + 1),
                        title = entry.name,
                        containerExtension = entry.url.substringAfterLast('.', "mp4").take(4),
                        season = match?.season ?: 1,
                        info = EpisodeInfo(movieImage = entry.tvgLogo),
                    )
                }
        }

        return M3uCatalog(
            liveCategories = liveCats.values.toList(),
            liveChannels = liveChannels,
            movieCategories = movieCats.values.toList(),
            movies = movies,
            seriesCategories = seriesCats.values.toList(),
            series = seriesList,
            seriesEpisodes = seriesEpisodesMap,
            xmltvUrl = xmltvUrl,
        )
    }

    private fun attr(line: String, key: String): String? {
        val m = Pattern.compile("""$key="([^"]*)"""", Pattern.CASE_INSENSITIVE).matcher(line)
        return if (m.find()) m.group(1)?.ifBlank { null } else null
    }

    private enum class Bucket { LIVE, MOVIE, SERIES }

    private fun classify(group: String, name: String): Bucket {
        val g = group.lowercase()
        val n = name.lowercase()
        return when {
            listOf("serie", "séries", "series").any { g.contains(it) } -> Bucket.SERIES
            listOf("film", "movie", "vod").any { g.contains(it) } -> Bucket.MOVIE
            EPISODE_MARKER.matcher(n).find() && listOf("serie", "series").any { g.contains(it) || n.contains(it) } -> Bucket.SERIES
            else -> Bucket.LIVE
        }
    }

    private data class MatchResult2(val showTitle: String, val season: Int, val episode: Int)

    private fun extractEpisode(name: String): MatchResult2? {
        val m = EPISODE_MARKER.matcher(name)
        if (!m.find()) return null
        val season = (m.group(1) ?: m.group(3) ?: m.group(5))?.toIntOrNull() ?: return null
        val episode = (m.group(2) ?: m.group(4) ?: m.group(6))?.toIntOrNull() ?: return null
        val showTitle = name.substring(0, m.start()).trim().trimEnd('-', '–', '|', ' ').ifBlank { name }
        return MatchResult2(showTitle, season, episode)
    }
}

// Une playlist M3U se recupere et se parse en un seul appel (contrairement a
// Xtream qui a un endpoint separe par catalogue) : ce point d'entree unique
// remplit TV/Films/Series/Guide d'un coup, evitant de re-telecharger le
// meme fichier depuis plusieurs ecrans qui se rafraichissent en meme temps.
object M3uCatalogSync {
    private val mutex = Mutex()

    suspend fun refreshAll(context: Context, creds: XtreamCredentials): M3uCatalog? = mutex.withLock {
        runCatching {
            val catalog = M3uParser.fetchAndParse(creds.m3uLink())
            CatalogCache.setLive(context, CatalogCache.LiveData(catalog.liveCategories, catalog.liveChannels))
            CatalogCache.setMovies(context, CatalogCache.MovieData(catalog.movieCategories, catalog.movies))
            CatalogCache.setSeries(context, CatalogCache.SeriesData(catalog.seriesCategories, catalog.series))
            M3uSeriesEpisodesStore.set(context, catalog.seriesEpisodes)
            LocalEpgStore.refreshOnceIfNeededFromUrl(context, catalog.xmltvUrl)
            catalog
        }.getOrNull()
    }
}

// Cache memoire+disque des episodes de series issues d'une playlist M3U —
// il n'existe pas d'equivalent a get_series_info pour une source M3U, les
// episodes sont donc connus entierement des le parsing et conserves ici.
object M3uSeriesEpisodesStore {
    private const val FILE_NAME = "m3u_series_episodes.json"
    private val mapType = Types.newParameterizedType(
        Map::class.java,
        Integer::class.java,
        Types.newParameterizedType(List::class.java, SeriesEpisode::class.java),
    )
    private val adapter = MoshiProvider.instance.adapter<Map<Int, List<SeriesEpisode>>>(mapType)

    @Volatile private var cache: Map<Int, List<SeriesEpisode>> = emptyMap()

    fun episodesFor(seriesId: Int): List<SeriesEpisode> = cache[seriesId] ?: emptyList()

    fun set(context: Context, data: Map<Int, List<SeriesEpisode>>) {
        cache = data
        runCatching { File(context.filesDir, FILE_NAME).writeText(adapter.toJson(data)) }
    }

    fun loadFromDisk(context: Context) {
        if (cache.isNotEmpty()) return
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        runCatching { adapter.fromJson(file.readText()) }.getOrNull()?.let { cache = it }
    }
}
