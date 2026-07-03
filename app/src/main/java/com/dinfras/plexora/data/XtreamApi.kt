package com.dinfras.plexora.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
}

// Client HTTP natif : contrairement au WebView, aucune contrainte CORS ici —
// c'est exactement ce qui nous manquait dans la version Capacitor.
object XtreamClient {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun create(baseUrl: String): XtreamService {
        val normalized = baseUrl.trim().let {
            val withScheme = if (!it.startsWith("http://") && !it.startsWith("https://")) "http://$it" else it
            if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(http)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
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
