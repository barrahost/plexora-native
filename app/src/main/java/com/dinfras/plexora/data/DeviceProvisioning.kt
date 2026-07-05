package com.dinfras.plexora.data

import android.content.Context
import android.provider.Settings
import com.squareup.moshi.Types
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// ── Équivalent "MAC Address" HotPlayer ───────────────────────────────────────
// Android bloque la lecture de la vraie adresse MAC matérielle depuis une
// app classique depuis Android 6 (retourne une valeur bidon). On utilise à la
// place l'ANDROID_ID : un identifiant stable par appareil+app, généré au
// premier démarrage, qui survit aux mises à jour de l'app (mais pas à un
// reset usine ou une réinstallation). Fonctionnellement équivalent pour
// associer un appareil à une playlist.

fun getDeviceId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
}

// À renseigner une fois le projet Supabase créé (URL du projet + clé anon,
// jamais la clé service_role côté client). Voir supabase/schema.sql +
// migration_admin.sql + migration_multi_playlists.sql (repo plexora-web).
object SupabaseConfig {
    const val URL = "https://cnsgyoirnhkjmklmzklh.supabase.co"
    const val ANON_KEY = "sb_publishable_SUTpOhDg71DlUWfgvGErDQ_dBKUJLJy"
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ProvisionedPlaylist(
    @com.squareup.moshi.Json(name = "server_url") val serverUrl: String,
    val username: String,
    val password: String,
    val label: String?,
)

object DeviceProvisioningClient {
    private val listType = Types.newParameterizedType(List::class.java, ProvisionedPlaylist::class.java)
    private val adapter = MoshiProvider.instance.adapter<List<ProvisionedPlaylist>>(listType)

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Comme HotPlayer, un appareil peut avoir plusieurs playlists associees —
    // la fonction Postgres get_device_playlist renvoie desormais toutes les
    // playlists actives du device_id demande (pas une seule ligne).
    suspend fun lookup(deviceId: String): List<ProvisionedPlaylist> {
        if (SupabaseConfig.URL.isBlank()) return emptyList()
        val url = "${SupabaseConfig.URL}/rest/v1/rpc/get_device_playlist"
        val json = """{"p_device_id":"${deviceId.replace("\"", "")}"}"""
        val request = Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .header("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val body = resp.body?.string() ?: return@runCatching emptyList()
                adapter.fromJson(body) ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }
}
