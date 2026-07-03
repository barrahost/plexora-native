package com.dinfras.plexora.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.first

data class SavedPlaylist(
    val id: String,
    val label: String,
    val url: String,
    val username: String,
    val password: String,
)

private val KEY_PLAYLISTS = stringPreferencesKey("playlists_json")

// Gere la liste des playlists enregistrees (comme la version web) ; l'entree
// "active" reste dupliquee dans CredentialsStore pour la connexion automatique.
object PlaylistsStore {
    private val listType = Types.newParameterizedType(List::class.java, SavedPlaylist::class.java)
    private val adapter = MoshiProvider.instance.adapter<List<SavedPlaylist>>(listType)

    suspend fun getAll(context: Context): List<SavedPlaylist> {
        val json = context.dataStore.data.first()[KEY_PLAYLISTS] ?: return emptyList()
        return runCatching { adapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    private suspend fun saveAll(context: Context, playlists: List<SavedPlaylist>) {
        context.dataStore.edit { it[KEY_PLAYLISTS] = adapter.toJson(playlists) }
    }

    // Ajoute la playlist si elle n'existe pas deja (meme URL + utilisateur), sinon la met a jour.
    suspend fun upsert(context: Context, label: String, creds: XtreamCredentials): SavedPlaylist {
        val all = getAll(context).toMutableList()
        val existing = all.indexOfFirst { it.url == creds.url && it.username == creds.username }
        val entry = if (existing >= 0) {
            all[existing].copy(label = label, password = creds.password)
        } else {
            SavedPlaylist(id = "${creds.url}|${creds.username}", label = label, url = creds.url, username = creds.username, password = creds.password)
        }
        if (existing >= 0) all[existing] = entry else all.add(entry)
        saveAll(context, all)
        return entry
    }

    suspend fun remove(context: Context, id: String) {
        saveAll(context, getAll(context).filterNot { it.id == id })
    }
}

fun SavedPlaylist.toCredentials() = XtreamCredentials(url, username, password)
