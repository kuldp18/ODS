package org.ods.scanner

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException

private val Context.settings by preferencesDataStore("settings")
class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    val theme = context.settings.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it[themeKey] ?: "System" }
    suspend fun theme(value: String) { require(value in listOf("System", "Light", "Dark")); context.settings.edit { it[themeKey] = value } }
}
