package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class ProviderType(val label: String, val defaultBaseUrl: String) {
    FIREBASE_AI_LOGIC("Built-in Gemini", ""),
    OPENAI_COMPATIBLE("OpenAI Compatible", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic (Claude)", "https://api.anthropic.com/v1"),
    GEMINI_REST("Gemini API (Custom Key)", "https://generativelanguage.googleapis.com/v1beta");

    companion object {
        fun fromString(value: String): ProviderType =
            entries.firstOrNull { it.name == value } ?: OPENAI_COMPATIBLE
    }
}

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val baseUrl: String,
    val apiKey: String,
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val providerType: ProviderType
        get() = ProviderType.fromString(type)
}

@Entity(
    tableName = "models",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId")]
)
data class ModelEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val supportsVision: Boolean = true,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ProviderWithModels(
    @Embedded val provider: ProviderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "providerId"
    )
    val models: List<ModelEntity>
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val modelRecordId: String? = null,
    val systemPrompt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val reasoningContent: String = "",
    val attachmentUris: String = "", // newline-delimited URIs
    val attachmentFileNames: String = "", // newline-delimited file names
    val attachmentTextContext: String = "", // extracted text from attached documents
    val modelDisplayName: String = "",
    val feedback: Int = 0, // -1 = down, 0 = none, 1 = up
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
