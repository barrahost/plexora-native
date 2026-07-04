package com.dinfras.plexora.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

private val KEY_MY_LIST = stringSetPreferencesKey("my_list_series")

// Liste perso locale (par appareil) : simple ensemble d'identifiants de
// series, pas de synchronisation multi-appareil pour l'instant.
object MyListStore {
    suspend fun isSaved(context: Context, seriesId: Int): Boolean =
        context.dataStore.data.first()[KEY_MY_LIST]?.contains(seriesId.toString()) == true

    suspend fun toggle(context: Context, seriesId: Int): Boolean {
        var nowSaved = false
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_MY_LIST] ?: emptySet()).toMutableSet()
            val key = seriesId.toString()
            nowSaved = if (current.contains(key)) {
                current.remove(key)
                false
            } else {
                current.add(key)
                true
            }
            prefs[KEY_MY_LIST] = current
        }
        return nowSaved
    }
}
