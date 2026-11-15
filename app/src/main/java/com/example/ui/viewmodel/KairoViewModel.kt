package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiInferenceParams
import com.example.data.ai.AiStreamEvent
import com.example.data.ai.DiscoveredModel
import com.example.data.ai.MediaUtils
import com.example.data.ai.PendingAttachment
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import com.example.data.local.ProviderWithModels
import com.example.data.preferences.UserSettings
import com.example.data.repository.KairoRepository
import com.example.data.repository.ProviderPreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "KairoViewModel"

data class SelectableModel(
    val model: ModelEntity,
    val provider: ProviderEntity
)

sealed interface GenerationStatus {
    data object Idle : GenerationStatus
    data object Thinking : GenerationStatus
    data class Streaming(val messageId: String, val text: String, val reasoning: String) : GenerationStatus
}

data class ProviderDiscoveryState(
    val isLoading: Boolean = false,
    val models: List<DiscoveredModel> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class KairoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KairoRepository(application.applicationContext)
    val ttsController = TtsController(application.applicationContext)

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private val _incognitoMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()

    private val _generationStatus = MutableStateFlow<GenerationStatus>(GenerationStatus.Idle)
    val generationStatus: StateFlow<GenerationStatus> = _generationStatus.asStateFlow()

    private val _drawerSearchQuery = MutableStateFlow("")
    val drawerSearchQuery: StateFlow<String> = _drawerSearchQuery.asStateFlow()

    private val _discoveryState = MutableStateFlow(ProviderDiscoveryState())
    val discoveryState: StateFlow<ProviderDiscoveryState> = _discoveryState.asStateFlow()

    private val _transientNotice = MutableStateFlow<String?>(null)
    val transientNotice: StateFlow<String?> = _transientNotice.asStateFlow()

    private var activeGenerationJob: Job? = null

    val settings: StateFlow<UserSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val allProvidersWithModels: StateFlow<List<ProviderWithModels>> =
        repository.providersWithModelsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectableModels: StateFlow<List<SelectableModel>> =
        repository.enabledProvidersWithModelsFlow
            .combine(settings) { list, _ ->
                list.flatMap { pwm ->
                    pwm.models.filter { it.isEnabled }.map { model ->
                        SelectableModel(model = model, provider = pwm.provider)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeModel: StateFlow<SelectableModel?> =
        combine(selectableModels, settings) { available, prefs ->
            available.firstOrNull { it.model.id == prefs.selectedModelRecordId }
                ?: available.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessions: StateFlow<List<ChatSessionEntity>> =
        repository.sessionsFlow
            .combine(_drawerSearchQuery) { list, query ->
                val q = query.trim()
                if (q.isBlank()) list else list.filter { it.title.contains(q, ignoreCase = true) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSession: StateFlow<ChatSessionEntity?> =
        _currentSessionId.flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeSession(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<ChatMessageEntity>> =
        combine(_isIncognito, _currentSessionId) { incognito, sessionId ->
            incognito to sessionId
        }.flatMapLatest { (incognito, sessionId) ->
            when {
                incognito -> _incognitoMessages
                sessionId != null -> repository.observeMessages(sessionId)
                else -> flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureBuiltInSeeded()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsController.shutdown()
    }

    fun consumeNotice() {
        _transientNotice.value = null
    }

    fun updateComposerText(value: String) {
        _composerText.value = value
    }

    fun updateDrawerSearchQuery(query: String) {
        _drawerSearchQuery.value = query
    }

    fun startNewChat(incognito: Boolean = false) {
        stopGeneration()
        ttsController.stop()
        _currentSessionId.value = null
        _isIncognito.value = incognito
        _incognitoMessages.value = emptyList()
        _composerText.value = ""
        _pendingAttachments.value = emptyList()
    }

    fun toggleIncognito() {
        if (messages.value.isEmpty() && _currentSessionId.value == null) {
            _isIncognito.value = !_isIncognito.value
        } else {
            startNewChat(incognito = !_isIncognito.value)
        }
    }

    fun openSession(sessionId: String) {
        stopGeneration()
        ttsController.stop()
        _isIncognito.value = false
        _incognitoMessages.value = emptyList()
        _currentSessionId.value = sessionId
        _composerText.value = ""
        _pendingAttachments.value = emptyList()

        viewModelScope.launch {
            val session = repository.getSession(sessionId)
            val modelId = session?.modelRecordId
            if (!modelId.isNullOrBlank() && selectableModels.value.any { it.model.id == modelId }) {
                repository.setSelectedModelId(modelId)
            }
        }
    }

    fun selectModel(modelRecordId: String) {
        viewModelScope.launch {
            repository.setSelectedModelId(modelRecordId)
            val sid = _currentSessionId.value
            if (sid != null && !_isIncognito.value) {
                repository.updateSessionModel(sid, modelRecordId)
            }
        }
    }

    fun attachMediaUri(uri: Uri, isImage: Boolean) {
        viewModelScope.launch {
            val attachment = MediaUtils.createAttachmentFromUri(
                context = getApplication(),
                uri = uri,
                forceImage = isImage
            )
            if (attachment != null) {
                _pendingAttachments.value = _pendingAttachments.value + attachment
            } else {
                _transientNotice.value = "Could not read selected attachment."
            }
        }
    }

    fun removePendingAttachment(attachmentId: String) {
        _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == attachmentId }
    }

    fun sendMessage(overridePrompt: String? = null) {
        val rawText = (overridePrompt ?: _composerText.value).trim()
        val attachments = _pendingAttachments.value
        if (rawText.isEmpty() && attachments.isEmpty()) return
        if (_generationStatus.value !is GenerationStatus.Idle) return

        val selected = activeModel.value
        if (selected == null) {
            _transientNotice.value = "No active model enabled. Enable or add a model in Providers."
            return
        }

        if (overridePrompt == null) {
            _composerText.value = ""
            _pendingAttachments.value = emptyList()
        }

        val imageUris = attachments.filter { it.isImage }.joinToString("\n") { it.uriString }
        val fileNames = attachments.joinToString("\n") { it.displayName }
        val fileTextContext = attachments
            .filter { !it.isImage && it.extractedText.isNotBlank() }
            .joinToString("\n\n") { "--- Attached file: ${it.displayName} ---\n${it.extractedText}" }

        val userMsg = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = _currentSessionId.value ?: "",
            role = "user",
            content = rawText,
            attachmentUris = imageUris,
            attachmentFileNames = fileNames,
            attachmentTextContext = fileTextContext,
            modelDisplayName = selected.model.displayName,
            timestamp = System.currentTimeMillis()
        )

        activeGenerationJob = viewModelScope.launch {
            val incognitoMode = _isIncognito.value
            val sessionId = if (incognitoMode) {
                "incognito"
            } else {
                val existingId = _currentSessionId.value
                if (existingId != null) {
                    existingId
                } else {
                    val created = repository.createSession(
                        title = repository.deriveTitleFromPrompt(
                            rawText,
                            attachments.map { it.displayName }
                        ),
                        modelRecordId = selected.model.id,
                        systemPrompt = settings.value.defaultSystemPrompt
                    )
                    _currentSessionId.value = created.id
                    created.id
                }
            }

            val persistedUserMsg = userMsg.copy(sessionId = sessionId)
            val historySnapshot: List<ChatMessageEntity>

            if (incognitoMode) {
                _incognitoMessages.value = _incognitoMessages.value + persistedUserMsg
                historySnapshot = _incognitoMessages.value
            } else {
                repository.saveMessage(persistedUserMsg)
                historySnapshot = repository.getMessages(sessionId)
            }

            runInference(
                sessionId = sessionId,
                incognitoMode = incognitoMode,
                selected = selected,
                history = historySnapshot
            )
        }
    }

    fun regenerateFromMessage(assistantMessage: ChatMessageEntity) {
        if (_generationStatus.value !is GenerationStatus.Idle) return
        val selected = activeModel.value ?: return

        activeGenerationJob = viewModelScope.launch {
            val incognitoMode = _isIncognito.value
            val sessionId = if (incognitoMode) "incognito" else (_currentSessionId.value ?: return@launch)
            val historySnapshot = if (incognitoMode) {
                val trimmed = _incognitoMessages.value.filter { it.timestamp < assistantMessage.timestamp }
                _incognitoMessages.value = trimmed
                trimmed
            } else {
                repository.deleteMessagesFrom(sessionId, assistantMessage.timestamp)
                repository.getMessages(sessionId)
            }

            if (historySnapshot.isEmpty()) return@launch
            runInference(
                sessionId = sessionId,
                incognitoMode = incognitoMode,
                selected = selected,
                history = historySnapshot
            )
        }
    }

    private suspend fun runInference(
        sessionId: String,
        incognitoMode: Boolean,
        selected: SelectableModel,
        history: List<ChatMessageEntity>
    ) {
        val assistantMsgId = UUID.randomUUID().toString()
        _generationStatus.value = GenerationStatus.Thinking

        val currentPrefs = settings.value
        val sessionPrompt = if (!incognitoMode) {
            repository.getSession(sessionId)?.systemPrompt.orEmpty().ifBlank { currentPrefs.defaultSystemPrompt }
        } else {
            currentPrefs.defaultSystemPrompt
        }

        val params = AiInferenceParams(
            systemPrompt = sessionPrompt,
            temperature = currentPrefs.temperature,
            maxTokens = currentPrefs.maxTokens,
            streamingEnabled = currentPrefs.streamingEnabled
        )

        val rawContent = StringBuilder()
        val explicitReasoning = StringBuilder()

        try {
            repository.streamCompletion(
                provider = selected.provider,
                model = selected.model,
                messages = history,
                params = params
            ).collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> {
                        if (event.reasoningDelta.isNotEmpty()) {
                            explicitReasoning.append(event.reasoningDelta)
                        }
                        if (event.textDelta.isNotEmpty()) {
                            rawContent.append(event.textDelta)
                        }
                        val (cleanText, inlineThink) = splitThinkBlocks(rawContent.toString())
                        val combinedReasoning = buildCombinedReasoning(explicitReasoning.toString(), inlineThink)
                        _generationStatus.value = GenerationStatus.Streaming(
                            messageId = assistantMsgId,
                            text = cleanText,
                            reasoning = combinedReasoning
                        )
                    }
                    AiStreamEvent.Completed -> Unit
                }
            }

            val (finalCleanText, finalInlineThink) = splitThinkBlocks(rawContent.toString())
            val finalReasoning = buildCombinedReasoning(explicitReasoning.toString(), finalInlineThink)

            if (finalCleanText.isBlank() && finalReasoning.isBlank()) {
                val errMsg = ChatMessageEntity(
                    id = assistantMsgId,
                    sessionId = sessionId,
                    role = "assistant",
                    content = "The model returned an empty response. Check your model configuration or try again.",
                    modelDisplayName = selected.model.displayName,
                    isError = true,
                    timestamp = System.currentTimeMillis()
                )
                persistOrAppend(incognitoMode, errMsg)
            } else {
                val finalMsg = ChatMessageEntity(
                    id = assistantMsgId,
                    sessionId = sessionId,
                    role = "assistant",
                    content = finalCleanText.trim(),
                    reasoningContent = finalReasoning.trim(),
                    modelDisplayName = selected.model.displayName,
                    isError = false,
                    timestamp = System.currentTimeMillis()
                )
                persistOrAppend(incognitoMode, finalMsg)
            }
        } catch (e: CancellationException) {
            val (partialClean, partialInlineThink) = splitThinkBlocks(rawContent.toString())
            val partialReasoning = buildCombinedReasoning(explicitReasoning.toString(), partialInlineThink)
            if (partialClean.isNotBlank() || partialReasoning.isNotBlank()) {
                val partialMsg = ChatMessageEntity(
                    id = assistantMsgId,
                    sessionId = sessionId,
                    role = "assistant",
                    content = partialClean.trimEnd() + "\n\n*[Generation stopped]*",
                    reasoningContent = partialReasoning.trim(),
                    modelDisplayName = selected.model.displayName,
                    timestamp = System.currentTimeMillis()
                )
                persistOrAppend(incognitoMode, partialMsg)
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed for ${selected.model.modelId}", e)
            val errorText = e.localizedMessage ?: "Failed to generate response."
            val errMsg = ChatMessageEntity(
                id = assistantMsgId,
                sessionId = sessionId,
                role = "assistant",
                content = errorText,
                modelDisplayName = selected.model.displayName,
                isError = true,
                timestamp = System.currentTimeMillis()
            )
            persistOrAppend(incognitoMode, errMsg)
        } finally {
            _generationStatus.value = GenerationStatus.Idle
        }
    }

    private suspend fun persistOrAppend(incognito: Boolean, message: ChatMessageEntity) {
        if (incognito) {
            _incognitoMessages.value = _incognitoMessages.value + message
        } else {
            repository.saveMessage(message)
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _generationStatus.value = GenerationStatus.Idle
    }

    fun setMessageFeedback(messageId: String, feedback: Int) {
        viewModelScope.launch {
            if (_isIncognito.value) {
                _incognitoMessages.value = _incognitoMessages.value.map {
                    if (it.id == messageId) it.copy(feedback = feedback) else it
                }
            } else {
                repository.updateMessageFeedback(messageId, feedback)
            }
        }
    }

    fun renameCurrentSession(newTitle: String) {
        val sid = _currentSessionId.value ?: return
        if (newTitle.isBlank()) return
        viewModelScope.launch { repository.renameSession(sid, newTitle) }
    }

    fun updateCurrentSessionSystemPrompt(prompt: String) {
        val sid = _currentSessionId.value ?: return
        viewModelScope.launch {
            repository.updateSessionSystemPrompt(sid, prompt)
            _transientNotice.value = "Chat system prompt updated"
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_currentSessionId.value == sessionId) {
                startNewChat(incognito = false)
            }
            repository.deleteSession(sessionId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            startNewChat(incognito = false)
            repository.clearAllSessions()
            _transientNotice.value = "All chat history cleared"
        }
    }

    // Settings & Providers Management
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDefaultSystemPrompt(prompt: String) {
        viewModelScope.launch { repository.setDefaultSystemPrompt(prompt) }
    }

    fun setTemperature(value: Float) {
        viewModelScope.launch { repository.setTemperature(value) }
    }

    fun setMaxTokens(value: Int) {
        viewModelScope.launch { repository.setMaxTokens(value) }
    }

    fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setStreamingEnabled(enabled) }
    }

    fun saveProvider(
        existingId: String? = null,
        name: String,
        type: ProviderType,
        baseUrl: String,
        apiKey: String,
        preset: ProviderPreset? = null
    ) {
        viewModelScope.launch {
            val id = repository.addOrUpdateProvider(
                existingId = existingId,
                name = name,
                type = type,
                baseUrl = baseUrl,
                apiKey = apiKey,
                isEnabled = true,
                initialSuggestedModels = if (existingId == null) preset?.suggestedModels.orEmpty() else emptyList()
            )
            // If user added a provider with suggested models, keep current or auto-select if needed
            val addedModels = allProvidersWithModels.value.firstOrNull { it.provider.id == id }?.models
            if (!addedModels.isNullOrEmpty() && activeModel.value == null) {
                repository.setSelectedModelId(addedModels.first().id)
            }
            _transientNotice.value = if (existingId == null) "Provider '$name' added" else "Provider '$name' updated"
        }
    }

    fun toggleProviderEnabled(provider: ProviderEntity, enabled: Boolean) {
        viewModelScope.launch { repository.toggleProviderEnabled(provider, enabled) }
    }

    fun deleteCustomProvider(providerId: String) {
        viewModelScope.launch {
            repository.deleteCustomProvider(providerId)
            _transientNotice.value = "Provider deleted"
        }
    }

    fun saveModel(
        existingId: String? = null,
        providerId: String,
        modelId: String,
        displayName: String,
        supportsVision: Boolean,
        selectImmediately: Boolean = false
    ) {
        viewModelScope.launch {
            val savedId = repository.addOrUpdateModel(
                existingId = existingId,
                providerId = providerId,
                modelId = modelId,
                displayName = displayName,
                supportsVision = supportsVision
            )
            if (selectImmediately) {
                repository.setSelectedModelId(savedId)
            }
            _transientNotice.value = if (existingId == null) "Model '$displayName' added" else "Model updated"
        }
    }

    fun toggleModelEnabled(model: ModelEntity, enabled: Boolean) {
        viewModelScope.launch { repository.toggleModelEnabled(model, enabled) }
    }

    fun deleteCustomModel(modelId: String) {
        viewModelScope.launch {
            repository.deleteCustomModel(modelId)
            _transientNotice.value = "Model removed"
        }
    }

    fun fetchModelsFromProvider(provider: ProviderEntity) {
        viewModelScope.launch {
            _discoveryState.value = ProviderDiscoveryState(isLoading = true)
            val result = repository.fetchModelsForProvider(provider)
            result.fold(
                onSuccess = { models ->
                    _discoveryState.value = ProviderDiscoveryState(
                        isLoading = false,
                        models = models,
                        message = if (models.isEmpty()) "Connected, but no models returned." else "Found ${models.size} models",
                        isError = false
                    )
                },
                onFailure = { err ->
                    _discoveryState.value = ProviderDiscoveryState(
                        isLoading = false,
                        models = emptyList(),
                        message = err.localizedMessage ?: "Failed to connect to provider",
                        isError = true
                    )
                }
            )
        }
    }

    fun importDiscoveredModels(providerId: String, models: List<DiscoveredModel>) {
        viewModelScope.launch {
            repository.addDiscoveredModels(providerId, models)
            _discoveryState.value = ProviderDiscoveryState()
            _transientNotice.value = "Added ${models.size} model(s)"
        }
    }

    fun clearDiscoveryState() {
        _discoveryState.value = ProviderDiscoveryState()
    }

    private fun splitThinkBlocks(raw: String): Pair<String, String> {
        val startIdx = raw.indexOf("<think>")
        if (startIdx == -1) return raw to ""
        val endIdx = raw.indexOf("</think>", startIndex = startIdx + 7)
        return if (endIdx == -1) {
            val cleanBefore = raw.substring(0, startIdx)
            val thinking = raw.substring(startIdx + 7)
            cleanBefore to thinking
        } else {
            val clean = (raw.substring(0, startIdx) + raw.substring(endIdx + 8)).trimStart()
            val thinking = raw.substring(startIdx + 7, endIdx)
            clean to thinking
        }
    }

    private fun buildCombinedReasoning(explicit: String, inlineThink: String): String {
        return listOf(explicit.trim(), inlineThink.trim())
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }
}
