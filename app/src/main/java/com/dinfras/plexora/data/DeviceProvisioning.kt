package com.dinfras.plexora.data

import android.content.Context
import android.provider.Settings
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
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
// jamais la clé service_role côté client). Voir supabase/schema.sql pour la
// table "devices" attendue.
object SupabaseConfig {
    const val URL = "" // ex: https://xxxxx.supabase.co
    const val ANON_KEY = ""
}

object DeviceProvisioningClient {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, DeviceProvision::class.java)
    private val adapter = moshi.adapter<List<DeviceProvision>>(listType)

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Retourne les identifiants associés à cet appareil, ou null si aucune
    // association n'existe encore (première utilisation, config manuelle).
    suspend fun lookup(deviceId: String): XtreamCredentials? {
        if (SupabaseConfig.URL.isBlank()) return null
        val url = "${SupabaseConfig.URL}/rest/v1/devices?device_id=eq.$deviceId&select=*"
        val request = Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .build()
        return runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val list = adapter.fromJson(body) ?: return@runCatching null
                list.firstOrNull()?.let { XtreamCredentials(it.serverUrl, it.username, it.password) }
            }
        }.getOrNull()
    }
}
