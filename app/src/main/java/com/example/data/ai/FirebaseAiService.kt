package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "FirebaseAiService"
private const val REQUEST_TIMEOUT_MS = 60_000L

class FirebaseAiService {

    private val defaultFlashModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private fun resolveModel(
        modelId: String,
        params: AiInferenceParams
    ): GenerativeModel {
        val isDefaultSimple = modelId == "gemini-3.8-flash" &&
            params.systemPrompt.isBlank() &&
            params.temperature == 0.7f &&
            params.maxTokens == 4096

        if (isDefaultSimple) return defaultFlashModel

        val config = generationConfig {
            temperature = params.temperature
            maxOutputTokens = params.maxTokens
        }
        val sysInstruction: Content? = if (params.systemPrompt.isNotBlank()) {
            content { text(params.systemPrompt) }
        } else {
            null
        }
        return Firebase.ai.generativeModel(
            modelName = modelId,
            generationConfig = config,
            systemInstruction = sysInstruction
        )
    }

    fun streamChat(
        modelId: String,
        turns: List<ConversationTurn>,
        params: AiInferenceParams
    ): Flow<AiStreamEvent> = flow {
        if (turns.isEmpty()) {
            throw IllegalArgumentException("Cannot send an empty conversation.")
        }

        val model = resolveModel(modelId, params)
        val historyTurns = turns.dropLast(1).map { turn -> toFirebaseContent(turn) }
        val latestTurn = toFirebaseContent(turns.last())

        try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                val chat = model.startChat(history = historyTurns)
                if (params.streamingEnabled) {
                    chat.sendMessageStream(latestTurn).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            emit(AiStreamEvent.Delta(textDelta = chunkText))
                        }
                    }
                } else {
                    val response = chat.sendMessage(latestTurn)
                    val fullText = response.text.orEmpty()
                    if (fullText.isNotEmpty()) {
                        emit(AiStreamEvent.Delta(textDelta = fullText))
                    }
                }
            }
            emit(AiStreamEvent.Completed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI request timed out", e)
            throw IllegalStateException("Request timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            throw e
        }
    }

    private fun toFirebaseContent(turn: ConversationTurn): Content {
        val roleName = if (turn.role == "assistant") "model" else "user"
        val combinedText = buildString {
            if (turn.attachmentTextContext.isNotBlank()) {
                append(turn.attachmentTextContext.trim())
                if (turn.text.isNotBlank()) append("\n\n")
            }
            append(turn.text)
        }
        return content(role = roleName) {
            turn.imageBitmaps.forEach { bitmap ->
                image(bitmap)
            }
            if (combinedText.isNotBlank()) {
                text(combinedText)
            } else if (turn.imageBitmaps.isNotEmpty()) {
                text("Please analyze the attached image(s).")
            }
        }
    }
}
