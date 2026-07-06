package com.dinfras.plexora.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.first

// Categories masquees a l'import d'une playlist (assistant facon TiviMate) :
// l'utilisateur decoche ce qu'il ne veut pas charger. On stocke les ids
// masques, prefixes par type pour eviter toute collision entre un
// category_id "1" cote Live et un "1" cote Films/Series (namespaces Xtream
// distincts) : "L:" live, "V:" films, "S:" series.
object CategoryVisibility {
    const val PREFIX_LIVE = "L:"
    const val PREFIX_VOD = "V:"
    const val PREFIX_SERIES = "S:"

    // Etat observable de la playlist active — les ecrans filtrent leur
    // affichage dessus, recharge a chaque changement de playlist.
    val hidden = mutableStateOf<Set<String>>(emptySet())

    fun isLiveHidden(categoryId: String) = hidden.value.contains(PREFIX_LIVE + categoryId)
    fun isVodHidden(categoryId: String) = hidden.value.contains(PREFIX_VOD + categoryId)
    fun isSeriesHidden(categoryId: String) = hidden.value.contains(PREFIX_SERIES + categoryId)
}

object CategoryPrefs {
    private val setType = Types.newParameterizedType(Set::class.java, String::class.java)
    private val adapter = MoshiProvider.instance.adapter<Set<String>>(setType)

    private fun keyFor(playlistId: String) = stringPreferencesKey("hidden_cats::$playlistId")

    suspend fun getHidden(context: Context, playlistId: String): Set<String> {
        val json = context.dataStore.data.first()[keyFor(playlistId)] ?: return emptySet()
        return runCatching { adapter.fromJson(json) }.getOrNull() ?: emptySet()
    }

    suspend fun setHidden(context: Context, playlistId: String, hidden: Set<String>) {
        context.dataStore.edit { it[keyFor(playlistId)] = adapter.toJson(hidden) }
    }

    // Charge les categories masquees de la playlist active dans l'etat
    // observable partage — appele quand une playlist devient active.
    suspend fun loadActive(context: Context, creds: XtreamCredentials) {
        CategoryVisibility.hidden.value = getHidden(context, playlistIdOf(creds))
    }

    // Meme convention d'id que SavedPlaylist ("url|username").
    fun playlistIdOf(creds: XtreamCredentials) = "${creds.url}|${creds.username}"
}
