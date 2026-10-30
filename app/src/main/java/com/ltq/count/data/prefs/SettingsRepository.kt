package com.ltq.count.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Home-card density. This only changes presentation, never stored counter data. */
enum class CardSize { COMPACT, STANDARD, COMFORTABLE }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "count_settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val cardSizeKey = stringPreferencesKey("card_size")

    private val preferences: Flow<Preferences> = context.settingsDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    val themeMode: Flow<ThemeMode> = preferences.map { prefs ->
        prefs[themeKey]?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: ThemeMode.SYSTEM
    }

    val cardSize: Flow<CardSize> = preferences.map { prefs ->
        prefs[cardSizeKey]?.let { raw -> CardSize.entries.firstOrNull { it.name == raw } } ?: CardSize.STANDARD
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[themeKey] = mode.name }
    }

    suspend fun setCardSize(size: CardSize) {
        context.settingsDataStore.edit { prefs -> prefs[cardSizeKey] = size.name }
    }
}
