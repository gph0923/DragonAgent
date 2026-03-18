package com.dragon.agent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// 扩展 Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dragon_agent_settings")

/**
 * 设置数据类
 */
data class UserSettings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-3.5-turbo",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048
)

private const val DEFAULT_SYSTEM_PROMPT = """你是一个有帮助的 AI 助手。请用简洁清晰的方式回答用户的问题。"""

/**
 * Settings DataStore 管理器
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val BASE_URL = stringPreferencesKey("base_url")
        private val MODEL = stringPreferencesKey("model")
        private val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val TEMPERATURE = stringPreferencesKey("temperature")
        private val MAX_TOKENS = stringPreferencesKey("max_tokens")
    }

    /**
     * 获取设置 Flow
     */
    val settings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            apiKey = preferences[API_KEY] ?: "",
            baseUrl = preferences[BASE_URL] ?: "https://api.openai.com/v1",
            model = preferences[MODEL] ?: "gpt-3.5-turbo",
            systemPrompt = preferences[SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT,
            temperature = preferences[TEMPERATURE]?.toFloatOrNull() ?: 0.7f,
            maxTokens = preferences[MAX_TOKENS]?.toIntOrNull() ?: 2048
        )
    }

    /**
     * 保存 API Key
     */
    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }

    /**
     * 保存 Base URL
     */
    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[BASE_URL] = baseUrl
        }
    }

    /**
     * 保存 Model
     */
    suspend fun saveModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL] = model
        }
    }

    /**
     * 保存 System Prompt
     */
    suspend fun saveSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[SYSTEM_PROMPT] = prompt
        }
    }

    /**
     * 保存 Temperature
     */
    suspend fun saveTemperature(temperature: Float) {
        context.dataStore.edit { preferences ->
            preferences[TEMPERATURE] = temperature.toString()
        }
    }

    /**
     * 保存 Max Tokens
     */
    suspend fun saveMaxTokens(maxTokens: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_TOKENS] = maxTokens.toString()
        }
    }

    /**
     * 保存所有设置
     */
    suspend fun saveSettings(settings: UserSettings) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = settings.apiKey
            preferences[BASE_URL] = settings.baseUrl
            preferences[MODEL] = settings.model
            preferences[SYSTEM_PROMPT] = settings.systemPrompt
            preferences[TEMPERATURE] = settings.temperature.toString()
            preferences[MAX_TOKENS] = settings.maxTokens.toString()
        }
    }

    /**
     * 清除所有设置
     */
    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
