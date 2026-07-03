package com.dinfras.plexora.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.first

private val KEY_OVERLAY_ALPHA = floatPreferencesKey("overlay_alpha")

// Opacite des elements affiches par-dessus la video (barre du lecteur plein
// ecran, boutons plein ecran) — 1 = opaque, 0 = totalement transparent.
object UiPrefs {
    const val DEFAULT_OVERLAY_ALPHA = 0.85f

    suspend fun getOverlayAlpha(context: Context): Float =
        context.dataStore.data.first()[KEY_OVERLAY_ALPHA] ?: DEFAULT_OVERLAY_ALPHA

    suspend fun setOverlayAlpha(context: Context, alpha: Float) {
        context.dataStore.edit { it[KEY_OVERLAY_ALPHA] = alpha.coerceIn(0f, 1f) }
    }
}
