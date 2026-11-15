package com.example.data.repository

import android.content.Context
import com.example.data.ai.AiInferenceParams
import com.example.data.ai.AiStreamEvent
import com.example.data.ai.ConversationTurn
import com.example.data.ai.CustomProviderClient
import com.example.data.ai.DiscoveredModel
import com.example.data.ai.FirebaseAiService
import com.example.data.ai.MediaUtils
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.KairoDatabase
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderDao
import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import com.example.data.local.ProviderWithModels
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.BUILT_IN_DEFAULT_MODEL_ID
import com.example.data.preferences.BUILT_IN_PROVIDER_ID
import com.example.data.preferences.UserSettings
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ProviderPreset(
    val name: String,
    val type: ProviderType,
    val defaultBaseUrl: String,
    val suggestedModels: List<Pair<String, String>> // modelId to displayName
)

object ProviderPresets {
    val all = listOf(
        ProviderPreset(
            name = "OpenAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.openai.com/v1",
            suggestedModels = listOf(
                "gpt-4o" to "GPT-4o",
                "gpt-4o-mini" to "GPT-4o Mini",
                "o3-mini" to "o3 Mini"
            )
        ),
        ProviderPreset(
            name = "Anthropic",
            type = ProviderType.ANTHROPIC,
            defaultBaseUrl = "https://api.anthropic.com/v1",
            suggestedModels = listOf(
                "claude-sonnet-4-5" to "Claude Sonnet 4.5",
                "claude-haiku-4-5" to "Claude Haiku 4.5"
            )
        ),
        ProviderPreset(
            name = "OpenRouter",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            suggestedModels = listOf(
                "openai/gpt-4o" to "OR · GPT-4o",
                "anthropic/claude-sonnet-4.5" to "OR · Sonnet 4.5",
                "deepseek/deepseek-r1" to "OR · DeepSeek R1"
            )
        ),
        ProviderPreset(
            name = "DeepSeek",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.deepseek.com/v1",
            suggestedModels = listOf(
                "deepseek-chat" to "DeepSeek V3",
                "deepseek-reasoner" to "DeepSeek R1"
            )
        ),
        ProviderPreset(
            name = "Groq",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.groq.com/openai/v1",
            suggestedModels = listOf(
                "llama-3.3-70b-versatile" to "Llama 3.3 70B",
                "qwen-qwq-32b" to "Qwen QwQ 32B"
            )
        ),
        ProviderPreset(
            name = "Gemini (Custom Key)",
            type = ProviderType.GEMINI_REST,
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            suggestedModels = listOf(
                "gemini-2.5-pro" to "Gemini 2.5 Pro",
                "gemini-2.5-flash" to "Gemini 2.5 Flash"
            )
        ),
        ProviderPreset(
            name = "Custom Endpoint",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "http://localhost:11434/v1",
            suggestedModels = emptyList()
        )
    )
}

class KairoRepository(private val appContext: Context) {

    private val database = KairoDatabase.getInstance(appContext)
    private val providerDao: ProviderDao = database.providerDao()
    private val chatDao: ChatDao = database.chatDao()
    private val preferences = AppPreferences(appContext)
    private val firebaseAiService = FirebaseAiService()
    private val customProviderClient = CustomProviderClient()
    private val seedMutex = Mutex()

    val settingsFlow: Flow<UserSettings> = preferences.settingsFlow
    val providersWithModelsFlow: Flow<List<ProviderWithModels>> = providerDao.observeProvidersWithModels()
    val enabledProvidersWithModelsFlow: Flow<List<ProviderWithModels>> = providerDao.observeEnabledProvidersWithModels()
    val sessionsFlow: Flow<List<ChatSessionEntity>> = chatDao.observeSessions()

    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessages(sessionId)

    fun observeSession(sessionId: String): Flow<ChatSessionEntity?> =
        chatDao.observeSession(sessionId)

    suspend fun ensureBuiltInSeeded() = seedMutex.withLock {
        val existingBuiltIn = providerDao.getProviderById(BUILT_IN_PROVIDER_ID)
        if (existingBuiltIn == null) {
            val now = System.currentTimeMillis()
            providerDao.upsertProvider(
                ProviderEntity(
                    id = BUILT_IN_PROVIDER_ID,
                    name = "Built-in Gemini",
                    type = ProviderType.FIREBASE_AI_LOGIC.name,
                    baseUrl = "",
                    apiKey = "",
                    isBuiltIn = true,
                    isEnabled = true,
                    createdAt = now
                )
            )
            providerDao.insertModelsIgnore(
                listOf(
                    ModelEntity(
                        id = BUILT_IN_DEFAULT_MODEL_ID,
                        providerId = BUILT_IN_PROVIDER_ID,
                        modelId = "gemini-3.8-flash",
                        displayName = "Gemini 3.8 Flash",
                        supportsVision = true,
                        isEnabled = true,
                        isBuiltIn = true,
                        createdAt = now
                    ),
                    ModelEntity(
                        id = "builtin-gemini-4-argon",
                        providerId = BUILT_IN_PROVIDER_ID,
                        modelId = "gemini-4-argon",
                        displayName = "Gemini 4 Argon",
                        supportsVision = true,
                        isEnabled = true,
                        isBuiltIn = true,
                        createdAt = now + 1
                    ),
                    ModelEntity(
                        id = "builtin-gemini-3.5-flash-lite",
                        providerId = BUILT_IN_PROVIDER_ID,
                        modelId = "gemini-3.5-flash-lite",
                        displayName = "Gemini 3.5 Flash-Lite",
                        supportsVision = true,
                        isEnabled = true,
                        isBuiltIn = true,
                        createdAt = now + 2
                    )
                )
            )
        }
    }

    // Preferences
    suspend fun setSelectedModelId(modelRecordId: String) = preferences.setSelectedModelId(modelRecordId)
    suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
    suspend fun setDefaultSystemPrompt(prompt: String) = preferences.setDefaultSystemPrompt(prompt)
    suspend fun setTemperature(value: Float) = preferences.setTemperature(value)
    suspend fun setMaxTokens(value: Int) = preferences.setMaxTokens(value)
    suspend fun setStreamingEnabled(enabled: Boolean) = preferences.setStreamingEnabled(enabled)

    // Providers & Models
    suspend fun addOrUpdateProvider(
        existingId: String? = null,
        name: String,
        type: ProviderType,
        baseUrl: String,
        apiKey: String,
        isEnabled: Boolean = true,
        initialSuggestedModels: List<Pair<String, String>> = emptyList()
    ): String {
        val id = existingId ?: UUID.randomUUID().toString()
        val existing = existingId?.let { providerDao.getProviderById(it) }
        val entity = ProviderEntity(
            id = id,
            name = name.trim(),
            type = if (existing?.isBuiltIn == true) existing.type else type.name,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            isBuiltIn = existing?.isBuiltIn ?: false,
            isEnabled = isEnabled,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        if (existing != null) {
            providerDao.updateProvider(entity)
        } else {
            providerDao.upsertProvider(entity)
        }

        if (existingId == null && initialSuggestedModels.isNotEmpty()) {
            val baseTime = System.currentTimeMillis()
            val models = initialSuggestedModels.mapIndexed { index, (modelId, display) ->
                ModelEntity(
                    id = UUID.randomUUID().toString(),
                    providerId = id,
                    modelId = modelId,
                    displayName = display,
                    supportsVision = true,
                    isEnabled = true,
                    isBuiltIn = false,
                    createdAt = baseTime + index
                )
            }
            providerDao.insertModelsIgnore(models)
        }
        return id
    }

    suspend fun toggleProviderEnabled(provider: ProviderEntity, enabled: Boolean) {
        providerDao.updateProvider(provider.copy(isEnabled = enabled))
    }

    suspend fun deleteCustomProvider(providerId: String) {
        providerDao.deleteCustomProvider(providerId)
    }

    suspend fun addOrUpdateModel(
        existingId: String? = null,
        providerId: String,
        modelId: String,
        displayName: String,
        supportsVision: Boolean,
        isEnabled: Boolean = true
    ): String {
        val id = existingId ?: UUID.randomUUID().toString()
        val existing = existingId?.let { providerDao.getModelById(it) }
        providerDao.upsertModel(
            ModelEntity(
                id = id,
                providerId = providerId,
                modelId = modelId.trim(),
                displayName = displayName.trim().ifBlank { modelId.trim() },
                supportsVision = supportsVision,
                isEnabled = isEnabled,
                isBuiltIn = existing?.isBuiltIn ?: false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun addDiscoveredModels(providerId: String, discovered: List<DiscoveredModel>) {
        val existingIds = providerDao.getModelsForProvider(providerId).map { it.modelId }.toSet()
        val now = System.currentTimeMillis()
        val newModels = discovered
            .filter { it.modelId !in existingIds }
            .mapIndexed { idx, item ->
                ModelEntity(
                    id = UUID.randomUUID().toString(),
                    providerId = providerId,
                    modelId = item.modelId,
                    displayName = item.displayName,
                    supportsVision = item.supportsVision,
                    isEnabled = true,
                    isBuiltIn = false,
                    createdAt = now + idx
                )
            }
        if (newModels.isNotEmpty()) {
            providerDao.insertModelsIgnore(newModels)
        }
    }

    suspend fun toggleModelEnabled(model: ModelEntity, enabled: Boolean) {
        providerDao.upsertModel(model.copy(isEnabled = enabled))
    }

    suspend fun deleteCustomModel(modelId: String) {
        providerDao.deleteCustomModel(modelId)
    }

    suspend fun fetchModelsForProvider(provider: ProviderEntity): Result<List<DiscoveredModel>> {
        return customProviderClient.fetchAvailableModels(provider)
    }

    // Chat Sessions & Messages
    suspend fun createSession(
        title: String,
        modelRecordId: String?,
        systemPrompt: String = ""
    ): ChatSessionEntity {
        val now = System.currentTimeMillis()
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "New conversation" },
            modelRecordId = modelRecordId,
            systemPrompt = systemPrompt,
            createdAt = now,
            updatedAt = now
        )
        chatDao.upsertSession(session)
        return session
    }

    suspend fun getSession(sessionId: String): ChatSessionEntity? = chatDao.getSessionById(sessionId)

    suspend fun renameSession(sessionId: String, newTitle: String) {
        chatDao.updateSessionTitle(sessionId, newTitle.trim(), System.currentTimeMillis())
    }

    suspend fun updateSessionSystemPrompt(sessionId: String, systemPrompt: String) {
        chatDao.updateSessionSystemPrompt(sessionId, systemPrompt.trim(), System.currentTimeMillis())
    }

    suspend fun updateSessionModel(sessionId: String, modelRecordId: String) {
        chatDao.updateSessionModel(sessionId, modelRecordId, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: String) = chatDao.deleteSession(sessionId)

    suspend fun clearAllSessions() = chatDao.deleteAllSessions()

    suspend fun saveMessage(message: ChatMessageEntity) {
        chatDao.upsertMessage(message)
        chatDao.touchSession(message.sessionId, System.currentTimeMillis())
    }

    suspend fun getMessages(sessionId: String): List<ChatMessageEntity> = chatDao.getMessages(sessionId)

    suspend fun updateMessageFeedback(messageId: String, feedback: Int) {
        chatDao.updateMessageFeedback(messageId, feedback)
    }

    suspend fun deleteMessagesFrom(sessionId: String, fromTimestamp: Long) {
        chatDao.deleteMessagesFrom(sessionId, fromTimestamp)
    }

    // AI Completion Router
    suspend fun streamCompletion(
        provider: ProviderEntity,
        model: ModelEntity,
        messages: List<ChatMessageEntity>,
        params: AiInferenceParams
    ): Flow<AiStreamEvent> {
        val turns = messages
            .filter { !it.isError }
            .map { msg ->
                val bitmaps = if (model.supportsVision && msg.attachmentUris.isNotBlank()) {
                    msg.attachmentUris.split("\n")
                        .filter { it.isNotBlank() }
                        .mapNotNull { uri -> MediaUtils.loadScaledBitmap(appContext, uri) }
                } else {
                    emptyList()
                }
                ConversationTurn(
                    role = msg.role,
                    text = msg.content,
                    imageBitmaps = bitmaps,
                    attachmentTextContext = msg.attachmentTextContext
                )
            }

        return if (provider.providerType == ProviderType.FIREBASE_AI_LOGIC) {
            firebaseAiService.streamChat(
                modelId = model.modelId,
                turns = turns,
                params = params
            )
        } else {
            customProviderClient.streamChat(
                provider = provider,
                modelId = model.modelId,
                turns = turns,
                params = params
            )
        }
    }

    fun deriveTitleFromPrompt(prompt: String, attachmentNames: List<String>): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isBlank()) {
            return attachmentNames.firstOrNull()?.take(32) ?: "New conversation"
        }
        return if (cleaned.length <= 34) cleaned else cleaned.take(32).trimEnd() + "…"
    }
}
