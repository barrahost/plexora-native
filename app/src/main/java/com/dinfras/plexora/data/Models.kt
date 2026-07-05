package com.dinfras.plexora.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class XtreamCredentials(
    val url: String,
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val auth: Int = 0,
    val status: String = "",
    @Json(name = "exp_date") val expDate: String? = null,
)

@JsonClass(generateAdapter = true)
data class AccountInfo(
    @Json(name = "user_info") val userInfo: UserInfo?,
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
)

@JsonClass(generateAdapter = true)
data class XtreamChannel(
    val num: Int = 0,
    val name: String = "",
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "category_id") val categoryId: String = "",
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    @Json(name = "tv_archive") val tvArchive: Int = 0,
    // Non nul uniquement pour les chaines issues d'une playlist M3U : l'URL du
    // flux est alors directement celle du fichier M3U, pas reconstruite via
    // Xtream (url/user/pass/stream_id).
    val directUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamMovie(
    val name: String = "",
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    val cover: String? = null,
    @Json(name = "category_id") val categoryId: String = "",
    @Json(name = "container_extension") val containerExtension: String? = "mp4",
    @Json(name = "rating_5based") val rating5based: Double? = null,
    val directUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamSeries(
    val name: String = "",
    @Json(name = "series_id") val seriesId: Int,
    val cover: String? = null,
    @Json(name = "category_id") val categoryId: String = "",
    @Json(name = "rating_5based") val rating5based: Double? = null,
)

@JsonClass(generateAdapter = true)
data class SeriesInfoWrapper(
    val info: SeriesFullInfo? = null,
    val episodes: Map<String, List<SeriesEpisode>>? = null,
)

@JsonClass(generateAdapter = true)
data class SeriesFullInfo(
    val name: String = "",
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    val rating: String? = null,
    @Json(name = "backdrop_path") val backdropPath: List<String>? = null,
    @Json(name = "youtube_trailer") val youtubeTrailer: String? = null,
)

@JsonClass(generateAdapter = true)
data class SeriesEpisode(
    val id: String = "",
    @Json(name = "episode_num") val episodeNum: Int = 0,
    val title: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = "mp4",
    val season: Int = 0,
    val info: EpisodeInfo? = null,
)

@JsonClass(generateAdapter = true)
data class EpisodeInfo(
    @Json(name = "movie_image") val movieImage: String? = null,
    val plot: String? = null,
)

@JsonClass(generateAdapter = true)
data class EpgListingsWrapper(
    @Json(name = "epg_listings") val epgListings: List<EpgItem>? = null,
)

@JsonClass(generateAdapter = true)
data class EpgItem(
    val title: String = "",
    val description: String? = null,
    val start: String = "",
    val end: String = "",
    @Json(name = "start_timestamp") val startTimestamp: Long = 0,
    @Json(name = "stop_timestamp") val stopTimestamp: Long = 0,
    @Json(name = "now_playing") val nowPlaying: Int = 0,
)

@JsonClass(generateAdapter = true)
data class VodInfoWrapper(
    val info: VodInfo? = null,
)

@JsonClass(generateAdapter = true)
data class VodInfo(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "backdrop_path") val backdropPath: List<String>? = null,
    @Json(name = "youtube_trailer") val youtubeTrailer: String? = null,
)
