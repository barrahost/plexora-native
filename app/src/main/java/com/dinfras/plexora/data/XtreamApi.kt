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

    fun liveStreamUrl(baseUrl: String, username: String, password: String, streamId: Int): String {
        val normalized = baseUrl.trim().trimEnd('/')
        val withScheme = if (!normalized.startsWith("http")) "http://$normalized" else normalized
        return "$withScheme/live/$username/$password/$streamId.m3u8"
    }
}
