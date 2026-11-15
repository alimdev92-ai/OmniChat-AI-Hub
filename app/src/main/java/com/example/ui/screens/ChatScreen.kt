package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ai.PendingAttachment
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.ui.components.FloatingComposerBar
import com.example.ui.components.MarkdownText
import com.example.ui.components.SparkEmblem
import com.example.ui.components.ThinkingDots
import com.example.ui.theme.EditorialSerifFamily
import com.example.ui.theme.LocalKairoColors
import com.example.ui.viewmodel.GenerationStatus
import com.example.ui.viewmodel.SelectableModel
import kotlinx.coroutines.delay
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentSession: ChatSessionEntity?,
    messages: List<ChatMessageEntity>,
    isIncognito: Boolean,
    composerText: String,
    pendingAttachments: List<PendingAttachment>,
    activeModel: SelectableModel?,
    providerCount: Int,
    modelCount: Int,
    generationStatus: GenerationStatus,
    speakingMessageId: String?,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onToggleIncognito: () -> Unit,
    onComposerTextChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenProvidersSettings: () -> Unit,
    onVoiceInput: () -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerate: (ChatMessageEntity) -> Unit,
    onToggleFeedback: (String, Int) -> Unit,
    onToggleReadAloud: (String, String) -> Unit,
    onRenameSession: (String) -> Unit,
    onUpdateSessionSystemPrompt: (String) -> Unit,
    onDeleteCurrentSession: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }

    val isGenerating = generationStatus !is GenerationStatus.Idle
    val hasMessages = messages.isNotEmpty() || isGenerating

    LaunchedEffect(messages.size, generationStatus) {
        if (messages.isNotEmpty() || isGenerating) {
            val targetIndex = messages.size + if (isGenerating) 1 else 0
            if (targetIndex > 0) {
                listState.animateScrollToItem((targetIndex - 1).coerceAtLeast(0))
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                title = when {
                    isIncognito && hasMessages -> "Temporary chat"
                    hasMessages -> currentSession?.title ?: "Conversation"
                    else -> ""
                },
                hasMessages = hasMessages,
                isIncognito = isIncognito,
                onOpenDrawer = onOpenDrawer,
                onToggleIncognito = onToggleIncognito,
                onNewChat = onNewChat,
                showOverflowMenu = showOverflowMenu,
                onOverflowMenuChange = { showOverflowMenu = it },
                onRenameClick = {
                    showOverflowMenu = false
                    showRenameDialog = true
                },
                onSystemPromptClick = {
                    showOverflowMenu = false
                    showSystemPromptDialog = true
                },
                onShareTranscript = {
                    showOverflowMenu = false
                    val transcript = messages.joinToString("\n\n---\n\n") { m ->
                        val who = if (m.role == "user") "**You**" else "**${m.modelDisplayName.ifBlank { "Kairo" }}**"
                        "$who\n${m.content}"
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, transcript)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export conversation"))
                },
                onDeleteChat = {
                    showOverflowMenu = false
                    onDeleteCurrentSession()
                }
            )
        },
        bottomBar = {
            FloatingComposerBar(
                text = composerText,
                onTextChange = onComposerTextChange,
                pendingAttachments = pendingAttachments,
                onRemoveAttachment = onRemoveAttachment,
                activeModel = activeModel,
                isGenerating = isGenerating,
                hasMessages = hasMessages,
                onOpenAttachments = { showAttachmentSheet = true },
                onOpenModelPicker = onOpenModelPicker,
                onVoiceInput = onVoiceInput,
                onSend = onSend,
                onStop = onStopGeneration
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isIncognito) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.temporary_chat_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!hasMessages) {
                EmptyChatGreeting(
                    activeModel = activeModel,
                    providerCount = providerCount,
                    modelCount = modelCount,
                    onOpenProvidersSettings = onOpenProvidersSettings,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            if (msg.role == "user") {
                                UserMessageBubble(message = msg)
                            } else {
                                AssistantMessageBlock(
                                    message = msg,
                                    isSpeaking = speakingMessageId == msg.id,
                                    onRegenerate = { onRegenerate(msg) },
                                    onToggleFeedback = { value -> onToggleFeedback(msg.id, value) },
                                    onToggleReadAloud = { onToggleReadAloud(msg.id, msg.content) }
                                )
                            }
                        }

                        when (generationStatus) {
                            GenerationStatus.Thinking -> {
                                item(key = "thinking_dots") {
                                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                        ThinkingDots()
                                    }
                                }
                            }
                            is GenerationStatus.Streaming -> {
                                item(key = "streaming_message") {
                                    StreamingAssistantBlock(
                                        text = generationStatus.text,
                                        reasoning = generationStatus.reasoning
                                    )
                                }
                            }
                            GenerationStatus.Idle -> Unit
                        }

                        if (!isGenerating && messages.any { it.role == "assistant" }) {
                            item(key = "disclaimer_footer") {
                                Text(
                                    text = stringResource(R.string.ai_disclaimer),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Add to conversation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                SheetOptionRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Photo or Image",
                    subtitle = "Zero-permission gallery picker for vision analysis",
                    onClick = {
                        showAttachmentSheet = false
                        onPickPhoto()
                    }
                )
                SheetOptionRow(
                    icon = Icons.Rounded.Description,
                    title = "Code or Text Document",
                    subtitle = "Attach .txt, .md, .json, .csv, or source code files",
                    onClick = {
                        showAttachmentSheet = false
                        onPickDocument()
                    }
                )
                if (currentSession != null && !isIncognito) {
                    SheetOptionRow(
                        icon = Icons.Outlined.Tune,
                        title = "Chat System Prompt",
                        subtitle = "Customize AI persona or rules for this conversation",
                        onClick = {
                            showAttachmentSheet = false
                            showSystemPromptDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showRenameDialog && currentSession != null) {
        var titleInput by remember(currentSession.id) { mutableStateOf(currentSession.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameSession(titleInput)
                        showRenameDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSystemPromptDialog && currentSession != null) {
        var promptInput by remember(currentSession.id) { mutableStateOf(currentSession.systemPrompt) }
        AlertDialog(
            onDismissRequest = { showSystemPromptDialog = false },
            title = { Text("Chat System Prompt") },
            text = {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("e.g., Act as a senior Kotlin engineer…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateSessionSystemPrompt(promptInput)
                        showSystemPromptDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showSystemPromptDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    hasMessages: Boolean,
    isIncognito: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleIncognito: () -> Unit,
    onNewChat: () -> Unit,
    showOverflowMenu: Boolean,
    onOverflowMenuChange: (Boolean) -> Unit,
    onRenameClick: () -> Unit,
    onSystemPromptClick: () -> Unit,
    onShareTranscript: () -> Unit,
    onDeleteChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Staggered 3-line drawer trigger matching screenshot 1 & 3
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.testTag("menu_drawer_button")
        ) {
            val iconTint = MaterialTheme.colorScheme.onBackground
            Canvas(modifier = Modifier.size(22.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(iconTint, Offset(2.dp.toPx(), 6.dp.toPx()), Offset(20.dp.toPx(), 6.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(iconTint, Offset(2.dp.toPx(), 11.dp.toPx()), Offset(16.dp.toPx(), 11.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(iconTint, Offset(2.dp.toPx(), 16.dp.toPx()), Offset(11.dp.toPx(), 16.dp.toPx()), stroke, StrokeCap.Round)
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        if (!hasMessages) {
            IconButton(
                onClick = onToggleIncognito,
                modifier = Modifier.testTag("incognito_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = if (isIncognito) "Disable temporary chat" else "Enable temporary chat",
                    tint = if (isIncognito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            IconButton(
                onClick = onNewChat,
                modifier = Modifier.testTag("top_new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = "New chat",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Box {
                IconButton(
                    onClick = { onOverflowMenuChange(true) },
                    modifier = Modifier.testTag("chat_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Chat options",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { onOverflowMenuChange(false) }
                ) {
                    if (!isIncognito) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = onRenameClick
                        )
                        DropdownMenuItem(
                            text = { Text("System prompt") },
                            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                            onClick = onSystemPromptClick
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Export transcript") },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = onShareTranscript
                    )
                    if (!isIncognito) {
                        DropdownMenuItem(
                            text = { Text("Delete chat") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                            onClick = onDeleteChat
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatGreeting(
    activeModel: SelectableModel?,
    providerCount: Int,
    modelCount: Int,
    onOpenProvidersSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning.\nWhat shall we build?"
            in 12..16 -> "Ready when you are."
            in 17..20 -> "What's on your mind\nthis evening?"
            else -> "It's a late-night\njam session."
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SparkEmblem(size = 50.dp, isAnimating = false)
        Spacer(Modifier.height(18.dp))
        Text(
            text = greeting,
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = EditorialSerifFamily
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))

        val pillLabel = if (activeModel != null) {
            "${activeModel.provider.name} · $modelCount model${if (modelCount == 1) "" else "s"}"
        } else {
            "$providerCount provider${if (providerCount == 1) "" else "s"} · Add models"
        }

        Surface(
            modifier = Modifier
                .testTag("empty_state_provider_pill")
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenProvidersSettings),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pillLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Manage",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessageEntity) {
    val kairoColors = LocalKairoColors.current
    val imageUris = remember(message.attachmentUris) {
        message.attachmentUris.lineSequence().filter { it.isNotBlank() }.toList()
    }
    val fileNames = remember(message.attachmentFileNames, imageUris) {
        message.attachmentFileNames.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(20.dp),
            color = kairoColors.userBubble
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (imageUris.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        imageUris.forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Attached image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
                if (message.attachmentTextContext.isNotBlank() && fileNames.isNotEmpty()) {
                    fileNames.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageBlock(
    message: ChatMessageEntity,
    isSpeaking: Boolean,
    onRegenerate: () -> Unit,
    onToggleFeedback: (Int) -> Unit,
    onToggleReadAloud: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (message.reasoningContent.isNotBlank()) {
            ReasoningTraceCollapsible(reasoning = message.reasoningContent)
        }

        if (message.isError) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            MarkdownText(markdown = message.content)
        }

        // Action bar matching Screenshot 3 (Copy, Share, Read Aloud, Thumbs Up, Thumbs Down, Regenerate)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(message.content))
                    copied = true
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                    contentDescription = "Copy response",
                    tint = if (copied) LocalKairoColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message.content)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share response"))
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share response",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (!message.isError) {
                IconButton(
                    onClick = onToggleReadAloud,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = if (isSpeaking) "Stop reading" else "Read aloud",
                        tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { onToggleFeedback(if (message.feedback == 1) 0 else 1) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (message.feedback == 1) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Helpful",
                        tint = if (message.feedback == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { onToggleFeedback(if (message.feedback == -1) 0 else -1) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (message.feedback == -1) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Not helpful",
                        tint = if (message.feedback == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = onRegenerate,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Regenerate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            if (message.modelDisplayName.isNotBlank()) {
                Text(
                    text = message.modelDisplayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StreamingAssistantBlock(
    text: String,
    reasoning: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (reasoning.isNotBlank()) {
            ReasoningTraceCollapsible(reasoning = reasoning, defaultExpanded = text.isBlank())
        }
        if (text.isNotBlank()) {
            MarkdownText(markdown = text)
        } else {
            ThinkingDots(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun ReasoningTraceCollapsible(
    reasoning: String,
    defaultExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Thinking process",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SheetOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
