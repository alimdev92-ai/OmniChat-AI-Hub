package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kairo_settings")

data class UserSettings(
    val selectedModelRecordId: String = BUILT_IN_DEFAULT_MODEL_ID,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSystemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val streamingEnabled: Boolean = true
)

const val BUILT_IN_PROVIDER_ID = "builtin-firebase-gemini"
const val BUILT_IN_DEFAULT_MODEL_ID = "builtin-gemini-3.8-flash"

class AppPreferences(private val context: Context) {

    private object Keys {
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_SYSTEM_PROMPT = stringPreferencesKey("default_system_prompt")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            selectedModelRecordId = prefs[Keys.SELECTED_MODEL_ID] ?: BUILT_IN_DEFAULT_MODEL_ID,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.SYSTEM,
            defaultSystemPrompt = prefs[Keys.DEFAULT_SYSTEM_PROMPT] ?: "",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 4096,
            streamingEnabled = prefs[Keys.STREAMING_ENABLED] ?: true
        )
    }

    suspend fun setSelectedModelId(modelRecordId: String) {
        context.dataStore.edit { it[Keys.SELECTED_MODEL_ID] = modelRecordId }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultSystemPrompt(prompt: String) {
        context.dataStore.edit { it[Keys.DEFAULT_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setTemperature(temperature: Float) {
        context.dataStore.edit { it[Keys.TEMPERATURE] = temperature.coerceIn(0f, 2f) }
    }

    suspend fun setMaxTokens(maxTokens: Int) {
        context.dataStore.edit { it[Keys.MAX_TOKENS] = maxTokens.coerceIn(256, 32768) }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STREAMING_ENABLED] = enabled }
    }
}
