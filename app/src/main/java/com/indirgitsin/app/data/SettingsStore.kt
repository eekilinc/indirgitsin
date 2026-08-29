package com.indirgitsin.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsStore {
    private val KEY_AUTO_HIGH = booleanPreferencesKey("auto_high_quality")
    private val KEY_AUDIO_FORMAT = stringPreferencesKey("audio_format")

    fun autoHighFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AUTO_HIGH] ?: true }
    suspend fun setAutoHigh(context: Context, value: Boolean) { context.settingsDataStore.edit { it[KEY_AUTO_HIGH] = value } }

    fun audioFormatFlow(context: Context): Flow<String> = context.settingsDataStore.data.map { it[KEY_AUDIO_FORMAT] ?: "M4A" }
    suspend fun setAudioFormat(context: Context, value: String) { context.settingsDataStore.edit { it[KEY_AUDIO_FORMAT] = value } }
}