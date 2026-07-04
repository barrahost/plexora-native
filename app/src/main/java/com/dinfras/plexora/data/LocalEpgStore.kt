package com.dinfras.plexora.data

import android.content.Context
import android.util.Xml
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// Comme TiviMate : au lieu d'interroger get_short_epg chaine par chaine a
// chaque affichage, on telecharge une seule fois le flux XMLTV complet
// (xmltv.php, fourni par la quasi-totalite des panels Xtream) et on le
// garde en local. Les ecrans consultent d'abord ce cache ; l'appel API par
// chaine ne sert plus que de repli si une chaine n'y figure pas.
object LocalEpgStore {
    private const val FILE_NAME = "epg_cache.json"
    private const val MAX_PAST_HOURS = 2L
    private const val MAX_FUTURE_HOURS = 72L

    @Volatile private var cache: Map<String, List<EpgItem>>? = null
    @Volatile private var refreshedThisSession = false

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Types.newParameterizedType(List::class.java, EpgItem::class.java),
    )
    private val adapter = MoshiProvider.instance.adapter<Map<String, List<EpgItem>>>(mapType)

    fun getCached(): Map<String, List<EpgItem>>? = cache

    fun programsFor(epgChannelId: String?): List<EpgItem>? {
        if (epgChannelId.isNullOrBlank()) return null
        return cache?.get(epgChannelId)
    }

    suspend fun loadFromDisk(context: Context): Map<String, List<EpgItem>>? {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return@withContext null
            val loaded = runCatching { adapter.fromJson(file.readText()) }.getOrNull()
            if (loaded != null) cache = loaded
            loaded
        }
    }

    // Telechargement + analyse du XMLTV complet — potentiellement plusieurs
    // Mo pour un gros catalogue, fait en arriere-plan sans bloquer l'affichage.
    suspend fun refresh(context: Context, creds: XtreamCredentials): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val base = creds.url.trim().trimEnd('/').let { if (!it.startsWith("http")) "http://$it" else it }
            val url = "$base/xmltv.php?username=${creds.username}&password=${creds.password}"
            val client = XtreamClient.http.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body?.string() ?: return@withContext false
                val parsed = parseXmltv(body)
                if (parsed.isEmpty()) return@withContext false
                cache = parsed
                runCatching { File(context.filesDir, FILE_NAME).writeText(adapter.toJson(parsed)) }
                true
            }
        }.getOrDefault(false)
    }

    private fun parseXmltv(xml: String): Map<String, List<EpgItem>> {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val nowSec = System.currentTimeMillis() / 1000
        val minStart = nowSec - MAX_PAST_HOURS * 3600
        val maxStart = nowSec + MAX_FUTURE_HOURS * 3600

        val result = HashMap<String, MutableList<EpgItem>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())

        var channelId: String? = null
        var start = 0L
        var stop = 0L
        var title = StringBuilder()
        var desc = StringBuilder()
        var currentTag: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "programme") {
                        channelId = parser.getAttributeValue(null, "channel")
                        start = runCatching { sdf.parse(parser.getAttributeValue(null, "start"))?.time?.div(1000) }.getOrNull() ?: 0L
                        stop = runCatching { sdf.parse(parser.getAttributeValue(null, "stop"))?.time?.div(1000) }.getOrNull() ?: 0L
                        title = StringBuilder()
                        desc = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    when (currentTag) {
                        "title" -> title.append(parser.text)
                        "desc" -> desc.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme" && !channelId.isNullOrBlank() && stop > 0 && start in minStart..maxStart) {
                        val nowPlaying = if (nowSec in start until stop) 1 else 0
                        result.getOrPut(channelId!!) { mutableListOf() }.add(
                            EpgItem(
                                title = title.toString(),
                                description = desc.toString().ifBlank { null },
                                start = "",
                                end = "",
                                startTimestamp = start,
                                stopTimestamp = stop,
                                nowPlaying = nowPlaying,
                            ),
                        )
                    }
                    currentTag = null
                }
            }
            eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
        }
        return result.mapValues { it.value.sortedBy { p -> p.startTimestamp } }
    }

    // Un seul telechargement complet par session (pas a chaque changement
    // d'onglet) — appele en arriere-plan, sans bloquer l'affichage.
    suspend fun refreshOnceIfNeeded(context: Context, creds: XtreamCredentials) {
        if (refreshedThisSession) return
        refreshedThisSession = true
        refresh(context, creds)
    }

    fun clear(context: Context) {
        cache = null
        refreshedThisSession = false
        File(context.filesDir, FILE_NAME).delete()
    }
}
