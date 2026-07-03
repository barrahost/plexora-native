package com.dinfras.plexora.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

enum class BufferMode { SMALL, MEDIUM, HIGH }

private val KEY_BUFFER = stringPreferencesKey("buffer_mode")

object BufferPrefs {
    suspend fun get(context: Context): BufferMode {
        val v = context.dataStore.data.first()[KEY_BUFFER]
        return runCatching { BufferMode.valueOf(v ?: "MEDIUM") }.getOrDefault(BufferMode.MEDIUM)
    }

    suspend fun set(context: Context, mode: BufferMode) {
        context.dataStore.edit { it[KEY_BUFFER] = mode.name }
    }
}
