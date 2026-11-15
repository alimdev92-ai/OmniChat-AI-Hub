package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.firebase.configureAppCheck
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.ModelPickerBottomSheet
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SettingsAndProvidersScreen
import com.example.ui.theme.KairoTheme
import com.example.ui.viewmodel.KairoViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val viewModel: KairoViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            KairoTheme(themeMode = settings.themeMode) {
                KairoAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun KairoAppRoot(viewModel: KairoViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providersWithModels by viewModel.allProvidersWithModels.collectAsStateWithLifecycle()
    val selectableModels by viewModel.selectableModels.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isIncognito by viewModel.isIncognito.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val generationStatus by viewModel.generationStatus.collectAsStateWithLifecycle()
    val drawerSearchQuery by viewModel.drawerSearchQuery.collectAsStateWithLifecycle()
    val discoveryState by viewModel.discoveryState.collectAsStateWithLifecycle()
    val transientNotice by viewModel.transientNotice.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.ttsController.speakingMessageId.collectAsStateWithLifecycle()

    var showModelPicker by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(transientNotice) {
        val note = transientNotice
        if (!note.isNullOrBlank()) {
            snackbarHostState.showSnackbar(note)
            viewModel.consumeNotice()
        }
    }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachMediaUri(uri, isImage = true)
        }
    }

    // SAF Document Picker for text, markdown, code, json, csv
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachMediaUri(uri, isImage = false)
        }
    }

    // System Speech-to-Text Launcher (no dangerous RECORD_AUDIO permission required)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                val existing = composerText.trimEnd()
                val updated = if (existing.isEmpty()) spoken else "$existing $spoken"
                viewModel.updateComposerText(updated)
            }
        }
    }

    val launchSpeechRecognition: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message…")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Voice input is unavailable on this device.")
            }
        }
    }

    val openSettingsTab: (Int) -> Unit = { tabIndex ->
        settingsInitialTab = tabIndex
        coroutineScope.launch { drawerState.close() }
        navController.navigate(ROUTE_SETTINGS) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_CHAT,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(ROUTE_CHAT) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        sessions = sessions,
                        activeSessionId = currentSession?.id,
                        searchQuery = drawerSearchQuery,
                        providerCount = providersWithModels.count { it.provider.isEnabled },
                        modelCount = selectableModels.size,
                        onSearchQueryChange = viewModel::updateDrawerSearchQuery,
                        onNewChat = {
                            viewModel.startNewChat(incognito = false)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onSelectSession = { sessionId ->
                            viewModel.openSession(sessionId)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onDeleteSession = viewModel::deleteSession,
                        onOpenProviders = { openSettingsTab(0) },
                        onOpenAppSettings = { openSettingsTab(1) }
                    )
                }
            ) {
                ChatScreen(
                    currentSession = currentSession,
                    messages = messages,
                    isIncognito = isIncognito,
                    composerText = composerText,
                    pendingAttachments = pendingAttachments,
                    activeModel = activeModel,
                    providerCount = providersWithModels.count { it.provider.isEnabled },
                    modelCount = selectableModels.size,
                    generationStatus = generationStatus,
                    speakingMessageId = speakingMessageId,
                    snackbarHostState = snackbarHostState,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onNewChat = { viewModel.startNewChat(incognito = false) },
                    onToggleIncognito = viewModel::toggleIncognito,
                    onComposerTextChange = viewModel::updateComposerText,
                    onRemoveAttachment = viewModel::removePendingAttachment,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickDocument = {
                        documentPickerLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/json",
                                "application/xml",
                                "application/javascript"
                            )
                        )
                    },
                    onOpenModelPicker = { showModelPicker = true },
                    onOpenProvidersSettings = { openSettingsTab(0) },
                    onVoiceInput = launchSpeechRecognition,
                    onSend = { viewModel.sendMessage() },
                    onStopGeneration = viewModel::stopGeneration,
                    onRegenerate = viewModel::regenerateFromMessage,
                    onToggleFeedback = viewModel::setMessageFeedback,
                    onToggleReadAloud = viewModel.ttsController::toggleSpeak,
                    onRenameSession = viewModel::renameCurrentSession,
                    onUpdateSessionSystemPrompt = viewModel::updateCurrentSessionSystemPrompt,
                    onDeleteCurrentSession = {
                        currentSession?.id?.let { viewModel.deleteSession(it) }
                    }
                )
            }
        }

        composable(ROUTE_SETTINGS) {
            SettingsAndProvidersScreen(
                initialTab = settingsInitialTab,
                providersWithModels = providersWithModels,
                activeModel = activeModel,
                settings = settings,
                discoveryState = discoveryState,
                onBack = { navController.popBackStack() },
                onOpenModelPicker = { showModelPicker = true },
                onSelectModel = viewModel::selectModel,
                onSaveProvider = viewModel::saveProvider,
                onToggleProviderEnabled = viewModel::toggleProviderEnabled,
                onDeleteProvider = viewModel::deleteCustomProvider,
                onSaveModel = viewModel::saveModel,
                onToggleModelEnabled = viewModel::toggleModelEnabled,
                onDeleteModel = viewModel::deleteCustomModel,
                onFetchModels = viewModel::fetchModelsFromProvider,
                onImportDiscoveredModels = viewModel::importDiscoveredModels,
                onClearDiscoveryState = viewModel::clearDiscoveryState,
                onThemeModeChange = viewModel::setThemeMode,
                onDefaultSystemPromptChange = viewModel::setDefaultSystemPrompt,
                onTemperatureChange = viewModel::setTemperature,
                onMaxTokensChange = viewModel::setMaxTokens,
                onStreamingEnabledChange = viewModel::setStreamingEnabled,
                onClearAllHistory = viewModel::clearAllHistory
            )
        }
    }

    if (showModelPicker) {
        ModelPickerBottomSheet(
            models = selectableModels,
            activeModelId = activeModel?.model?.id,
            onSelectModel = viewModel::selectModel,
            onOpenProvidersSettings = { openSettingsTab(0) },
            onDismiss = { showModelPicker = false }
        )
    }
}
