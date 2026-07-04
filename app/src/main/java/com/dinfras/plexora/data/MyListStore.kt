package com.dinfras.plexora.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

private val KEY_MY_LIST = stringSetPreferencesKey("my_list_items")

// Liste perso locale (par appareil) : simple ensemble de cles "movie:<id>" /
// "series:<id>", pas de synchronisation multi-appareil pour l'instant.
object MyListStore {
    suspend fun isSavedMovie(context: Context, streamId: Int) = isSaved(context, "movie:$streamId")
    suspend fun toggleMovie(context: Context, streamId: Int) = toggle(context, "movie:$streamId")

    suspend fun isSavedSeries(context: Context, seriesId: Int) = isSaved(context, "series:$seriesId")
    suspend fun toggleSeries(context: Context, seriesId: Int) = toggle(context, "series:$seriesId")

    private suspend fun isSaved(context: Context, key: String): Boolean =
        context.dataStore.data.first()[KEY_MY_LIST]?.contains(key) == true

    private suspend fun toggle(context: Context, key: String): Boolean {
        var nowSaved = false
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_MY_LIST] ?: emptySet()).toMutableSet()
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
