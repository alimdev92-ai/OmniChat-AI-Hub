package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ai.DiscoveredModel
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import com.example.data.local.ProviderWithModels
import com.example.data.preferences.UserSettings
import com.example.data.repository.ProviderPreset
import com.example.data.repository.ProviderPresets
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.ProviderDiscoveryState
import com.example.ui.viewmodel.SelectableModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndProvidersScreen(
    initialTab: Int,
    providersWithModels: List<ProviderWithModels>,
    activeModel: SelectableModel?,
    settings: UserSettings,
    discoveryState: ProviderDiscoveryState,
    onBack: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSaveProvider: (String?, String, ProviderType, String, String, ProviderPreset?) -> Unit,
    onToggleProviderEnabled: (ProviderEntity, Boolean) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSaveModel: (String?, String, String, String, Boolean, Boolean) -> Unit,
    onToggleModelEnabled: (ModelEntity, Boolean) -> Unit,
    onDeleteModel: (String) -> Unit,
    onFetchModels: (ProviderEntity) -> Unit,
    onImportDiscoveredModels: (String, List<DiscoveredModel>) -> Unit,
    onClearDiscoveryState: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDefaultSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onStreamingEnabledChange: (Boolean) -> Unit,
    onClearAllHistory: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var editingProvider by remember { mutableStateOf<ProviderEntity?>(null) }
    var presetForNewProvider by remember { mutableStateOf<ProviderPreset?>(null) }
    var showProviderDialog by remember { mutableStateOf(false) }

    var modelDialogProvider by remember { mutableStateOf<ProviderEntity?>(null) }
    var editingModel by remember { mutableStateOf<ModelEntity?>(null) }

    var fetchDialogProvider by remember { mutableStateOf<ProviderEntity?>(null) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedTab == 0) "Providers & Models" else "App Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to chat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Providers & Models") },
                    modifier = Modifier.testTag("tab_providers")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("App Settings") },
                    modifier = Modifier.testTag("tab_app_settings")
                )
            }

            if (selectedTab == 0) {
                ProvidersTabContent(
                    providersWithModels = providersWithModels,
                    activeModelId = activeModel?.model?.id,
                    onAddCustomProvider = { preset ->
                        editingProvider = null
                        presetForNewProvider = preset
                        showProviderDialog = true
                    },
                    onEditProvider = { provider ->
                        editingProvider = provider
                        presetForNewProvider = null
                        showProviderDialog = true
                    },
                    onToggleProviderEnabled = onToggleProviderEnabled,
                    onDeleteProvider = onDeleteProvider,
                    onAddModel = { provider ->
                        modelDialogProvider = provider
                        editingModel = null
                    },
                    onEditModel = { provider, model ->
                        modelDialogProvider = provider
                        editingModel = model
                    },
                    onToggleModelEnabled = onToggleModelEnabled,
                    onDeleteModel = onDeleteModel,
                    onSelectModel = onSelectModel,
                    onFetchModels = { provider ->
                        fetchDialogProvider = provider
                        onFetchModels(provider)
                    }
                )
            } else {
                AppSettingsTabContent(
                    settings = settings,
                    activeModel = activeModel,
                    onOpenModelPicker = onOpenModelPicker,
                    onThemeModeChange = onThemeModeChange,
                    onDefaultSystemPromptChange = onDefaultSystemPromptChange,
                    onTemperatureChange = onTemperatureChange,
                    onMaxTokensChange = onMaxTokensChange,
                    onStreamingEnabledChange = onStreamingEnabledChange,
                    onRequestClearHistory = { showClearHistoryConfirm = true }
                )
            }
        }
    }

    if (showProviderDialog) {
        ProviderEditorDialog(
            initialProvider = editingProvider,
            initialPreset = presetForNewProvider,
            onSave = { existingId, name, type, baseUrl, apiKey, preset ->
                onSaveProvider(existingId, name, type, baseUrl, apiKey, preset)
                showProviderDialog = false
            },
            onDismiss = { showProviderDialog = false }
        )
    }

    val targetProviderForModel = modelDialogProvider
    if (targetProviderForModel != null) {
        ModelEditorDialog(
            providerName = targetProviderForModel.name,
            initialModel = editingModel,
            onSave = { existingId, modelId, displayName, supportsVision, selectNow ->
                onSaveModel(
                    existingId,
                    targetProviderForModel.id,
                    modelId,
                    displayName,
                    supportsVision,
                    selectNow
                )
                modelDialogProvider = null
                editingModel = null
            },
            onDismiss = {
                modelDialogProvider = null
                editingModel = null
            }
        )
    }

    val targetFetchProvider = fetchDialogProvider
    if (targetFetchProvider != null) {
        FetchModelsDialog(
            provider = targetFetchProvider,
            discoveryState = discoveryState,
            onImportSelected = { models ->
                onImportDiscoveredModels(targetFetchProvider.id, models)
                fetchDialogProvider = null
                onClearDiscoveryState()
            },
            onDismiss = {
                fetchDialogProvider = null
                onClearDiscoveryState()
            }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear all conversations?") },
            text = { Text("This permanently deletes all saved chat threads on this device. Your providers and models will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllHistory()
                        showClearHistoryConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProvidersTabContent(
    providersWithModels: List<ProviderWithModels>,
    activeModelId: String?,
    onAddCustomProvider: (ProviderPreset?) -> Unit,
    onEditProvider: (ProviderEntity) -> Unit,
    onToggleProviderEnabled: (ProviderEntity, Boolean) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onAddModel: (ProviderEntity) -> Unit,
    onEditModel: (ProviderEntity, ModelEntity) -> Unit,
    onToggleModelEnabled: (ModelEntity, Boolean) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onFetchModels: (ProviderEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "add_provider_header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onAddCustomProvider(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_provider_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add New AI Provider")
                }

                Text(
                    text = "Quick setup presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProviderPresets.all.forEach { preset ->
                        OutlinedButton(
                            onClick = { onAddCustomProvider(preset) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("+ ${preset.name}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        items(providersWithModels, key = { it.provider.id }) { pwm ->
            ProviderCard(
                provider = pwm.provider,
                models = pwm.models,
                activeModelId = activeModelId,
                onEditProvider = { onEditProvider(pwm.provider) },
                onToggleProviderEnabled = { enabled -> onToggleProviderEnabled(pwm.provider, enabled) },
                onDeleteProvider = { onDeleteProvider(pwm.provider.id) },
                onAddModel = { onAddModel(pwm.provider) },
                onEditModel = { model -> onEditModel(pwm.provider, model) },
                onToggleModelEnabled = onToggleModelEnabled,
                onDeleteModel = onDeleteModel,
                onSelectModel = onSelectModel,
                onFetchModels = { onFetchModels(pwm.provider) }
            )
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderEntity,
    models: List<ModelEntity>,
    activeModelId: String?,
    onEditProvider: () -> Unit,
    onToggleProviderEnabled: (Boolean) -> Unit,
    onDeleteProvider: () -> Unit,
    onAddModel: () -> Unit,
    onEditModel: (ModelEntity) -> Unit,
    onToggleModelEnabled: (ModelEntity, Boolean) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onFetchModels: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (provider.providerType == ProviderType.FIREBASE_AI_LOGIC) {
                        Icons.Rounded.AutoAwesome
                    } else {
                        Icons.Rounded.CloudQueue
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (provider.isBuiltIn) {
                            "Built-in Firebase AI Logic · No API key needed"
                        } else {
                            "${provider.providerType.label} · ${provider.baseUrl}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggleProviderEnabled
                )
            }

            if (!provider.isBuiltIn) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddModel,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("add_model_to_${provider.name}")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Model", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onFetchModels,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Fetch Models", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onEditProvider) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit provider")
                    }
                    IconButton(onClick = onDeleteProvider) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete provider",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAddModel,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Gemini Model", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (models.isEmpty()) {
                Text(
                    text = "No models added yet. Tap 'Add Model' or 'Fetch Models'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    models.forEach { model ->
                        val isActive = model.id == activeModelId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectModel(model.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Active model",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = model.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!model.isBuiltIn) {
                                    IconButton(
                                        onClick = { onEditModel(model) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "Edit model",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteModel(model.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove model",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = model.isEnabled,
                                    onCheckedChange = { onToggleModelEnabled(model, it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSettingsTabContent(
    settings: UserSettings,
    activeModel: SelectableModel?,
    onOpenModelPicker: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDefaultSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onStreamingEnabledChange: (Boolean) -> Unit,
    onRequestClearHistory: () -> Unit
) {
    var systemPromptDraft by remember(settings.defaultSystemPrompt) {
        mutableStateOf(settings.defaultSystemPrompt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Appearance
        SettingsSectionCard(title = "Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    )
                }
            }
        }

        // Active Default Model
        SettingsSectionCard(title = "Active Model") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenModelPicker)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeModel?.model?.displayName ?: "No model selected",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = activeModel?.provider?.name ?: "Add a provider to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // System Prompt
        SettingsSectionCard(title = "Default System Prompt") {
            OutlinedTextField(
                value = systemPromptDraft,
                onValueChange = { systemPromptDraft = it },
                minLines = 3,
                maxLines = 5,
                placeholder = { Text("Optional global instructions applied to new conversations…") },
                modifier = Modifier.fillMaxWidth()
            )
            if (systemPromptDraft != settings.defaultSystemPrompt) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onDefaultSystemPromptChange(systemPromptDraft) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save System Prompt")
                }
            }
        }

        // Inference Parameters
        SettingsSectionCard(title = "Generation Controls") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stream Responses", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Show tokens live as they are generated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.streamingEnabled,
                    onCheckedChange = onStreamingEnabledChange
                )
            }

            Spacer(Modifier.height(10.dp))

            val tempRounded = (settings.temperature * 10f).roundToInt() / 10f
            Text("Temperature: $tempRounded", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.temperature,
                onValueChange = { onTemperatureChange((it * 10f).roundToInt() / 10f) },
                valueRange = 0f..1.5f
            )

            Spacer(Modifier.height(6.dp))

            Text("Max Output Tokens: ${settings.maxTokens}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.maxTokens.toFloat(),
                onValueChange = { onMaxTokensChange(((it / 256f).roundToInt() * 256).coerceIn(512, 16384)) },
                valueRange = 512f..16384f
            )
        }

        // Data & Privacy
        SettingsSectionCard(title = "Data & Storage") {
            OutlinedButton(
                onClick = onRequestClearHistory,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear All Conversations")
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
