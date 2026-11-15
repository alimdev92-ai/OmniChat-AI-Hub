package com.example.data.ai

import android.graphics.Bitmap

sealed interface AiStreamEvent {
    data class Delta(
        val textDelta: String = "",
        val reasoningDelta: String = ""
    ) : AiStreamEvent

    data object Completed : AiStreamEvent
}

data class PendingAttachment(
    val id: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val isImage: Boolean,
    val extractedText: String = ""
)

data class AiInferenceParams(
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val streamingEnabled: Boolean = true
)

data class DiscoveredModel(
    val modelId: String,
    val displayName: String,
    val supportsVision: Boolean = true
)

data class ConversationTurn(
    val role: String, // "user" or "assistant"
    val text: String,
    val imageBitmaps: List<Bitmap> = emptyList(),
    val attachmentTextContext: String = ""
)
