package com.dinfras.plexora.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

internal val Context.dataStore by preferencesDataStore(name = "plexora_prefs")

private val KEY_URL = stringPreferencesKey("server_url")
private val KEY_USER = stringPreferencesKey("username")
private val KEY_PASS = stringPreferencesKey("password")

object CredentialsStore {
    suspend fun save(context: Context, creds: XtreamCredentials) {
        context.dataStore.edit {
            it[KEY_URL] = creds.url
            it[KEY_USER] = creds.username
            it[KEY_PASS] = creds.password
        }
        val defaultLabel = runCatching { creds.username + " — " + creds.url.substringAfter("//") }.getOrDefault(creds.username)
        PlaylistsStore.upsert(context, defaultLabel, creds)
    }

    suspend fun load(context: Context): XtreamCredentials? {
        val prefs = context.dataStore.data.first()
        val url = prefs[KEY_URL] ?: return null
        val user = prefs[KEY_USER] ?: return null
        val pass = prefs[KEY_PASS] ?: return null
        return XtreamCredentials(url, user, pass)
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
