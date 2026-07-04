package com.dinfras.plexora.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.first

private val KEY_OVERLAY_ALPHA = floatPreferencesKey("overlay_alpha")
private val KEY_TEXT_SCALE = floatPreferencesKey("text_scale")

// Opacite des elements affiches par-dessus la video (barre du lecteur plein
// ecran, boutons plein ecran) — 1 = opaque, 0 = totalement transparent.
object UiPrefs {
    const val DEFAULT_OVERLAY_ALPHA = 0.85f
    const val DEFAULT_TEXT_SCALE = 1f

    suspend fun getOverlayAlpha(context: Context): Float =
        context.dataStore.data.first()[KEY_OVERLAY_ALPHA] ?: DEFAULT_OVERLAY_ALPHA

    suspend fun setOverlayAlpha(context: Context, alpha: Float) {
        context.dataStore.edit { it[KEY_OVERLAY_ALPHA] = alpha.coerceIn(0f, 1f) }
    }

    // Applique un facteur global a toutes les tailles de texte de l'appli
    // (via la densite/fontScale de Compose), pas seulement celles qui
    // utilisent MaterialTheme.typography — la plupart de nos ecrans fixent
    // des tailles en .sp en dur.
    suspend fun getTextScale(context: Context): Float =
        context.dataStore.data.first()[KEY_TEXT_SCALE] ?: DEFAULT_TEXT_SCALE

    suspend fun setTextScale(context: Context, scale: Float) {
        context.dataStore.edit { it[KEY_TEXT_SCALE] = scale.coerceIn(0.7f, 1.4f) }
    }
}
