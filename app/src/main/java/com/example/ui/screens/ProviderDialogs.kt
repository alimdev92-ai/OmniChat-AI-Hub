package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ai.DiscoveredModel
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import com.example.data.repository.ProviderPreset
import com.example.data.repository.ProviderPresets
import com.example.ui.viewmodel.ProviderDiscoveryState

@Composable
fun ProviderEditorDialog(
    initialProvider: ProviderEntity? = null,
    initialPreset: ProviderPreset? = null,
    onSave: (
        existingId: String?,
        name: String,
        type: ProviderType,
        baseUrl: String,
        apiKey: String,
        preset: ProviderPreset?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf(initialPreset) }
    var name by remember {
        mutableStateOf(initialProvider?.name ?: initialPreset?.name ?: "")
    }
    var type by remember {
        mutableStateOf(initialProvider?.providerType ?: initialPreset?.type ?: ProviderType.OPENAI_COMPATIBLE)
    }
    var baseUrl by remember {
        mutableStateOf(initialProvider?.baseUrl ?: initialPreset?.defaultBaseUrl ?: "https://api.openai.com/v1")
    }
    var apiKey by remember { mutableStateOf(initialProvider?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }

    val availableTypes = remember {
        listOf(ProviderType.OPENAI_COMPATIBLE, ProviderType.ANTHROPIC, ProviderType.GEMINI_REST)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProvider == null) "Add AI Provider" else "Edit Provider")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialProvider == null) {
                    Text(
                        text = "Quick presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProviderPresets.all.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset?.name == preset.name,
                                onClick = {
                                    selectedPreset = preset
                                    name = preset.name
                                    type = preset.type
                                    baseUrl = preset.defaultBaseUrl
                                },
                                label = { Text(preset.name) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name") },
                    placeholder = { Text("e.g., OpenAI, OpenRouter, Ollama") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_name_input")
                )

                Text(
                    text = "API Format",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableTypes.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = {
                                type = candidate
                                if (baseUrl.isBlank() || availableTypes.any { it.defaultBaseUrl == baseUrl }) {
                                    baseUrl = candidate.defaultBaseUrl
                                }
                            },
                            label = { Text(candidate.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text(type.defaultBaseUrl) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_url_input")
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-… (leave empty for local Ollama)") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showKey) "Hide API key" else "Show API key"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_key_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(initialProvider?.id, name, type, baseUrl, apiKey, selectedPreset)
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                modifier = Modifier.testTag("save_provider_button")
            ) {
                Text(if (initialProvider == null) "Add Provider" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ModelEditorDialog(
    providerName: String,
    initialModel: ModelEntity? = null,
    onSave: (
        existingId: String?,
        modelId: String,
        displayName: String,
        supportsVision: Boolean,
        selectImmediately: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var modelId by remember { mutableStateOf(initialModel?.modelId ?: "") }
    var displayName by remember { mutableStateOf(initialModel?.displayName ?: "") }
    var supportsVision by remember { mutableStateOf(initialModel?.supportsVision ?: true) }
    var selectImmediately by remember { mutableStateOf(initialModel == null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialModel == null) "Add Model to $providerName" else "Edit Model")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID (API name)") },
                    placeholder = { Text("e.g., gpt-4o, claude-sonnet-4-5, llama3.3") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_id_input")
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("Optional friendly label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_display_name_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Supports Image Input", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Enable vision attachments for this model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = supportsVision, onCheckedChange = { supportsVision = it })
                }
                if (initialModel == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectImmediately = !selectImmediately },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectImmediately,
                            onCheckedChange = { selectImmediately = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Use as active model right away", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialModel?.id,
                        modelId,
                        displayName.ifBlank { modelId },
                        supportsVision,
                        selectImmediately
                    )
                },
                enabled = modelId.isNotBlank(),
                modifier = Modifier.testTag("save_model_button")
            ) {
                Text(if (initialModel == null) "Add Model" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FetchModelsDialog(
    provider: ProviderEntity,
    discoveryState: ProviderDiscoveryState,
    onImportSelected: (List<DiscoveredModel>) -> Unit,
    onDismiss: () -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val filtered = remember(discoveryState.models, filterQuery) {
        val q = filterQuery.trim()
        if (q.isBlank()) discoveryState.models
        else discoveryState.models.filter {
            it.modelId.contains(q, ignoreCase = true) ||
                it.displayName.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discover Models · ${provider.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    discoveryState.isLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Connecting to ${provider.name}…")
                        }
                    }

                    discoveryState.isError -> {
                        Text(
                            text = discoveryState.message ?: "Connection failed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    discoveryState.models.isEmpty() -> {
                        Text(
                            text = discoveryState.message ?: "No models returned by endpoint.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            value = filterQuery,
                            onValueChange = { filterQuery = it },
                            placeholder = { Text("Filter ${discoveryState.models.size} models…") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(filtered, key = { it.modelId }) { model ->
                                val isChecked = model.modelId in selectedIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) selectedIds.remove(model.modelId)
                                            else selectedIds.add(model.modelId)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedIds.add(model.modelId)
                                            else selectedIds.remove(model.modelId)
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.displayName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (model.displayName != model.modelId) {
                                            Text(
                                                text = model.modelId,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!discoveryState.isLoading && !discoveryState.isError && discoveryState.models.isNotEmpty()) {
                Button(
                    onClick = {
                        val picked = discoveryState.models.filter { it.modelId in selectedIds }
                        onImportSelected(picked)
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("Import (${selectedIds.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
