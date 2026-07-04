package com.dinfras.plexora.data

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface XtreamService {
    @GET("player_api.php")
    suspend fun getAccountInfo(
        @Query("username") username: String,
        @Query("password") password: String,
    ): AccountInfo

    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories",
    ): List<XtreamCategory>

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
    ): List<XtreamChannel>

    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories",
    ): List<XtreamCategory>

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
    ): List<XtreamMovie>

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories",
    ): List<XtreamCategory>

    @GET("player_api.php")
    suspend fun getSeriesList(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
    ): List<XtreamSeries>

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("series_id") seriesId: Int,
        @Query("action") action: String = "get_series_info",
    ): SeriesInfoWrapper

    @GET("player_api.php")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: Int,
        @Query("limit") limit: Int = 6,
        @Query("action") action: String = "get_short_epg",
    ): EpgListingsWrapper

    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("vod_id") vodId: Int,
        @Query("action") action: String = "get_vod_info",
    ): VodInfoWrapper
}

// La grille EPG multi-chaines peut composer une dizaine de lignes a la fois,
// chacune declenchant son propre appel get_short_epg. Sans limite, ces
// appels concurrents saturent les connexions au serveur IPTV (souvent
// mono-thread) et retardent/bloquent la requete du flux video en cours de
// lecture. On borne donc les appels EPG concurrents pour ne jamais affamer
// la lecture du direct.
private val epgFetchLimiter = Semaphore(2)

suspend fun XtreamService.getShortEpgThrottled(username: String, password: String, streamId: Int): EpgListingsWrapper =
    epgFetchLimiter.withPermit { getShortEpg(username, password, streamId) }

// Comme TiviMate : consulte d'abord le XMLTV telecharge en local (instantane,
// aucun appel reseau) — l'API par chaine ne sert plus que de repli si la
// chaine est absente du guide local (pas de correspondance epg_channel_id,
// ou telechargement XMLTV pas encore termine/disponible sur ce serveur).
suspend fun XtreamService.getEpgForChannel(username: String, password: String, channel: XtreamChannel): List<EpgItem> {
    LocalEpgStore.programsFor(channel.epgChannelId)?.let { if (it.isNotEmpty()) return it }
    return runCatching { getShortEpgThrottled(username, password, channel.streamId).epgListings ?: emptyList() }.getOrDefault(emptyList())
}

// Client HTTP natif : contrairement au WebView, aucune contrainte CORS ici —
// c'est exactement ce qui nous manquait dans la version Capacitor.
object XtreamClient {
    // Client HTTP partage avec Coil (affiches) pour n'utiliser qu'un seul pool
    // de connexions vers ce serveur — sinon Retrofit, Coil et ExoPlayer
    // ouvrent chacun leur propre pool et se disputent le nombre limite de
    // connexions simultanees que le serveur accepte par hote, ralentissant
    // tout le monde (affiches lentes, flux qui peine a demarrer).
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 12; maxRequests = 24 })
        .build()

    fun create(baseUrl: String): XtreamService {
        val normalized = baseUrl.trim().let {
            val withScheme = if (!it.startsWith("http://") && !it.startsWith("https://")) "http://$it" else it
            if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(http)
            .addConverterFactory(MoshiConverterFactory.create(MoshiProvider.instance))
            .build()
            .create(XtreamService::class.java)
    }

    private fun schemed(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (!normalized.startsWith("http")) "http://$normalized" else normalized
    }

    fun liveStreamUrl(baseUrl: String, username: String, password: String, streamId: Int): String =
        "${schemed(baseUrl)}/live/$username/$password/$streamId.m3u8"

    fun vodStreamUrl(baseUrl: String, username: String, password: String, streamId: Int, ext: String): String =
        "${schemed(baseUrl)}/movie/$username/$password/$streamId.$ext"

    fun seriesStreamUrl(baseUrl: String, username: String, password: String, episodeId: String, ext: String): String =
        "${schemed(baseUrl)}/series/$username/$password/$episodeId.$ext"
}
