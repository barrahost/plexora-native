package com.dinfras.plexora.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

enum class DecoderMode { AUTO, HARDWARE, SOFTWARE }

private val KEY_AUDIO_DECODER = stringPreferencesKey("decoder_audio")
private val KEY_VIDEO_DECODER = stringPreferencesKey("decoder_video")
private val KEY_TUNNELING = booleanPreferencesKey("tunneled_playback")
private val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_boot")
private val KEY_RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
private val KEY_LAST_CHANNEL_ID = intPreferencesKey("last_channel_id")

// Reglages lecteur/demarrage, calques sur les options habituelles des
// lecteurs IPTV (TiviMate...) — decodeur materiel/logiciel, lecture en
// tunnel, demarrage automatique et reprise de la derniere chaine.
object PlayerPrefs {
    suspend fun getAudioDecoder(context: Context): DecoderMode =
        parseMode(context.dataStore.data.first()[KEY_AUDIO_DECODER])

    suspend fun setAudioDecoder(context: Context, mode: DecoderMode) {
        context.dataStore.edit { it[KEY_AUDIO_DECODER] = mode.name }
    }

    suspend fun getVideoDecoder(context: Context): DecoderMode =
        parseMode(context.dataStore.data.first()[KEY_VIDEO_DECODER])

    suspend fun setVideoDecoder(context: Context, mode: DecoderMode) {
        context.dataStore.edit { it[KEY_VIDEO_DECODER] = mode.name }
    }

    suspend fun getTunneling(context: Context): Boolean =
        context.dataStore.data.first()[KEY_TUNNELING] ?: false

    suspend fun setTunneling(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_TUNNELING] = enabled }
    }

    suspend fun getAutoStartOnBoot(context: Context): Boolean =
        context.dataStore.data.first()[KEY_AUTO_START] ?: false

    suspend fun setAutoStartOnBoot(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START] = enabled }
    }

    // Active par defaut : a l'ouverture de l'appli, on reprend directement la
    // derniere chaine regardee en plein ecran (comportement TiviMate).
    suspend fun getResumeLastChannel(context: Context): Boolean =
        context.dataStore.data.first()[KEY_RESUME_LAST_CHANNEL] ?: true

    suspend fun setResumeLastChannel(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_RESUME_LAST_CHANNEL] = enabled }
    }

    suspend fun getLastChannelId(context: Context): Int? =
        context.dataStore.data.first()[KEY_LAST_CHANNEL_ID]

    suspend fun setLastChannelId(context: Context, streamId: Int) {
        context.dataStore.edit { it[KEY_LAST_CHANNEL_ID] = streamId }
    }

    private fun parseMode(v: String?): DecoderMode =
        runCatching { DecoderMode.valueOf(v ?: "AUTO") }.getOrDefault(DecoderMode.AUTO)
}
