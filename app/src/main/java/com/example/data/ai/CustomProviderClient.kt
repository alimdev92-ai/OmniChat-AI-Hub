package com.example.data.ai

import android.util.Log
import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "CustomProviderClient"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class CustomProviderClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamChat(
        provider: ProviderEntity,
        modelId: String,
        turns: List<ConversationTurn>,
        params: AiInferenceParams
    ): Flow<AiStreamEvent> = flow {
        val cleanBaseUrl = provider.baseUrl.trim().trimEnd('/')
        if (cleanBaseUrl.isBlank()) {
            throw IllegalArgumentException("Base URL is missing for provider ${provider.name}.")
        }

        when (provider.providerType) {
            ProviderType.OPENAI_COMPATIBLE -> streamOpenAiCompatible(
                baseUrl = cleanBaseUrl,
                apiKey = provider.apiKey.trim(),
                modelId = modelId,
                turns = turns,
                params = params,
                emitEvent = { emit(it) }
            )
            ProviderType.ANTHROPIC -> streamAnthropic(
                baseUrl = cleanBaseUrl,
                apiKey = provider.apiKey.trim(),
                modelId = modelId,
                turns = turns,
                params = params,
                emitEvent = { emit(it) }
            )
            ProviderType.GEMINI_REST -> streamGeminiRest(
                baseUrl = cleanBaseUrl,
                apiKey = provider.apiKey.trim(),
                modelId = modelId,
                turns = turns,
                params = params,
                emitEvent = { emit(it) }
            )
            ProviderType.FIREBASE_AI_LOGIC -> {
                throw IllegalStateException("Built-in Gemini uses FirebaseAiService.")
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun streamOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        turns: List<ConversationTurn>,
        params: AiInferenceParams,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        val messagesJson = JSONArray()
        if (params.systemPrompt.isNotBlank()) {
            messagesJson.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", params.systemPrompt)
            )
        }
        turns.forEach { turn ->
            val combinedText = combinePromptAndFiles(turn)
            if (turn.imageBitmaps.isEmpty() || turn.role == "assistant") {
                messagesJson.put(
                    JSONObject()
                        .put("role", turn.role)
                        .put("content", combinedText)
                )
            } else {
                val parts = JSONArray()
                if (combinedText.isNotBlank()) {
                    parts.put(JSONObject().put("type", "text").put("text", combinedText))
                }
                turn.imageBitmaps.forEach { bmp ->
                    val b64 = MediaUtils.bitmapToBase64Jpeg(bmp)
                    parts.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", "data:image/jpeg;base64,$b64")
                            )
                    )
                }
                messagesJson.put(
                    JSONObject()
                        .put("role", turn.role)
                        .put("content", parts)
                )
            }
        }

        val bodyJson = JSONObject()
            .put("model", modelId)
            .put("messages", messagesJson)
            .put("temperature", params.temperature.toDouble())
            .put("max_tokens", params.maxTokens)
            .put("stream", params.streamingEnabled)

        val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", if (params.streamingEnabled) "text/event-stream" else "application/json")

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IllegalStateException(parseHttpError(response.code, errorBody))
            }

            val contentType = response.header("Content-Type").orEmpty()
            val body = response.body ?: throw IllegalStateException("Empty response body.")

            if (!params.streamingEnabled || !contentType.contains("text/event-stream", ignoreCase = true)) {
                val raw = body.string()
                // Some proxies omit event-stream header even when sending SSE lines
                if (raw.trimStart().startsWith("data:")) {
                    parseOpenAiSseLines(raw.lineSequence(), emitEvent)
                } else {
                    val json = JSONObject(raw)
                    val choices = json.optJSONArray("choices")
                    val firstMsg = choices?.optJSONObject(0)?.optJSONObject("message")
                    val text = firstMsg?.optString("content").orEmpty()
                    val reasoning = firstMsg?.optString("reasoning_content").orEmpty()
                    if (text.isNotEmpty() || reasoning.isNotEmpty()) {
                        emitEvent(AiStreamEvent.Delta(textDelta = text, reasoningDelta = reasoning))
                    }
                    emitEvent(AiStreamEvent.Completed)
                }
                return
            }

            val source = body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue
                parseOpenAiDeltaPayload(payload, emitEvent)
            }
            emitEvent(AiStreamEvent.Completed)
        }
    }

    private suspend fun parseOpenAiSseLines(
        lines: Sequence<String>,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        for (line in lines) {
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            if (payload.isNotEmpty()) {
                parseOpenAiDeltaPayload(payload, emitEvent)
            }
        }
        emitEvent(AiStreamEvent.Completed)
    }

    private suspend fun parseOpenAiDeltaPayload(
        payload: String,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        try {
            val json = JSONObject(payload)
            val choices = json.optJSONArray("choices") ?: return
            if (choices.length() == 0) return
            val choice = choices.optJSONObject(0) ?: return
            val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: return
            val text = delta.optString("content").let { if (it == "null") "" else it }
            val reasoning = delta.optString("reasoning_content")
                .ifEmpty { delta.optString("reasoning") }
                .let { if (it == "null") "" else it }
            if (text.isNotEmpty() || reasoning.isNotEmpty()) {
                emitEvent(AiStreamEvent.Delta(textDelta = text, reasoningDelta = reasoning))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ignore keep-alive or malformed SSE metadata frames
        }
    }

    private suspend fun streamAnthropic(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        turns: List<ConversationTurn>,
        params: AiInferenceParams,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key is required for Anthropic.")
        }

        val messagesJson = JSONArray()
        turns.forEach { turn ->
            val combinedText = combinePromptAndFiles(turn)
            if (turn.imageBitmaps.isEmpty() || turn.role == "assistant") {
                messagesJson.put(
                    JSONObject()
                        .put("role", turn.role)
                        .put("content", combinedText)
                )
            } else {
                val contentBlocks = JSONArray()
                turn.imageBitmaps.forEach { bmp ->
                    val b64 = MediaUtils.bitmapToBase64Jpeg(bmp)
                    contentBlocks.put(
                        JSONObject()
                            .put("type", "image")
                            .put(
                                "source",
                                JSONObject()
                                    .put("type", "base64")
                                    .put("media_type", "image/jpeg")
                                    .put("data", b64)
                            )
                    )
                }
                if (combinedText.isNotBlank()) {
                    contentBlocks.put(JSONObject().put("type", "text").put("text", combinedText))
                }
                messagesJson.put(
                    JSONObject()
                        .put("role", turn.role)
                        .put("content", contentBlocks)
                )
            }
        }

        val bodyJson = JSONObject()
            .put("model", modelId)
            .put("messages", messagesJson)
            .put("max_tokens", params.maxTokens)
            .put("temperature", params.temperature.toDouble())
            .put("stream", params.streamingEnabled)
        if (params.systemPrompt.isNotBlank()) {
            bodyJson.put("system", params.systemPrompt)
        }

        val endpoint = if (baseUrl.endsWith("/messages")) baseUrl else "$baseUrl/messages"
        val request = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IllegalStateException(parseHttpError(response.code, errorBody))
            }
            val body = response.body ?: throw IllegalStateException("Empty response body.")
            if (!params.streamingEnabled) {
                val json = JSONObject(body.string())
                val contentArr = json.optJSONArray("content")
                val textBuilder = StringBuilder()
                if (contentArr != null) {
                    for (i in 0 until contentArr.length()) {
                        val block = contentArr.optJSONObject(i)
                        if (block?.optString("type") == "text") {
                            textBuilder.append(block.optString("text"))
                        }
                    }
                }
                if (textBuilder.isNotEmpty()) {
                    emitEvent(AiStreamEvent.Delta(textDelta = textBuilder.toString()))
                }
                emitEvent(AiStreamEvent.Completed)
                return
            }

            val source = body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                try {
                    val json = JSONObject(payload)
                    when (json.optString("type")) {
                        "content_block_delta" -> {
                            val delta = json.optJSONObject("delta") ?: continue
                            when (delta.optString("type")) {
                                "text_delta" -> {
                                    val text = delta.optString("text")
                                    if (text.isNotEmpty()) {
                                        emitEvent(AiStreamEvent.Delta(textDelta = text))
                                    }
                                }
                                "thinking_delta" -> {
                                    val thinking = delta.optString("thinking")
                                    if (thinking.isNotEmpty()) {
                                        emitEvent(AiStreamEvent.Delta(reasoningDelta = thinking))
                                    }
                                }
                            }
                        }
                        "message_stop" -> break
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            emitEvent(AiStreamEvent.Completed)
        }
    }

    private suspend fun streamGeminiRest(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        turns: List<ConversationTurn>,
        params: AiInferenceParams,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key is required for Gemini REST API.")
        }

        val contentsArr = JSONArray()
        turns.forEach { turn ->
            val parts = JSONArray()
            turn.imageBitmaps.forEach { bmp ->
                val b64 = MediaUtils.bitmapToBase64Jpeg(bmp)
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", b64)
                    )
                )
            }
            val combinedText = combinePromptAndFiles(turn)
            if (combinedText.isNotBlank()) {
                parts.put(JSONObject().put("text", combinedText))
            }
            contentsArr.put(
                JSONObject()
                    .put("role", if (turn.role == "assistant") "model" else "user")
                    .put("parts", parts)
            )
        }

        val bodyJson = JSONObject()
            .put("contents", contentsArr)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", params.temperature.toDouble())
                    .put("maxOutputTokens", params.maxTokens)
            )

        if (params.systemPrompt.isNotBlank()) {
            bodyJson.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", params.systemPrompt))
                )
            )
        }

        val normalizedModel = modelId.removePrefix("models/")
        val action = if (params.streamingEnabled) "streamGenerateContent?alt=sse&key=$apiKey" else "generateContent?key=$apiKey"
        val endpoint = "$baseUrl/models/$normalizedModel:$action"

        val request = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IllegalStateException(parseHttpError(response.code, errorBody))
            }
            val body = response.body ?: throw IllegalStateException("Empty response body.")
            if (!params.streamingEnabled) {
                val json = JSONObject(body.string())
                val text = extractGeminiCandidatesText(json)
                if (text.isNotEmpty()) {
                    emitEvent(AiStreamEvent.Delta(textDelta = text))
                }
                emitEvent(AiStreamEvent.Completed)
                return
            }

            val source = body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                try {
                    val json = JSONObject(payload)
                    val text = extractGeminiCandidatesText(json)
                    if (text.isNotEmpty()) {
                        emitEvent(AiStreamEvent.Delta(textDelta = text))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            emitEvent(AiStreamEvent.Completed)
        }
    }

    private fun extractGeminiCandidatesText(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates") ?: return ""
        val first = candidates.optJSONObject(0) ?: return ""
        val content = first.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val t = part.optString("text")
            if (t.isNotEmpty()) sb.append(t)
        }
        return sb.toString()
    }

    suspend fun fetchAvailableModels(provider: ProviderEntity): Result<List<DiscoveredModel>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanBase = provider.baseUrl.trim().trimEnd('/')
                if (cleanBase.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Base URL cannot be empty."))
                }
                val apiKey = provider.apiKey.trim()

                val requestBuilder = Request.Builder().get()
                val url = when (provider.providerType) {
                    ProviderType.OPENAI_COMPATIBLE -> {
                        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
                        "$cleanBase/models"
                    }
                    ProviderType.ANTHROPIC -> {
                        requestBuilder.header("x-api-key", apiKey)
                        requestBuilder.header("anthropic-version", "2023-06-01")
                        "$cleanBase/models"
                    }
                    ProviderType.GEMINI_REST -> {
                        "$cleanBase/models?key=$apiKey"
                    }
                    ProviderType.FIREBASE_AI_LOGIC -> {
                        return@withContext Result.success(emptyList())
                    }
                }

                client.newCall(requestBuilder.url(url).build()).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException(parseHttpError(response.code, raw))
                        )
                    }
                    val json = JSONObject(raw)
                    val models = mutableListOf<DiscoveredModel>()
                    when (provider.providerType) {
                        ProviderType.OPENAI_COMPATIBLE, ProviderType.ANTHROPIC -> {
                            val data = json.optJSONArray("data") ?: JSONArray()
                            for (i in 0 until data.length()) {
                                val item = data.optJSONObject(i) ?: continue
                                val id = item.optString("id")
                                if (id.isBlank()) continue
                                val display = item.optString("display_name").ifBlank { id }
                                models.add(DiscoveredModel(modelId = id, displayName = display))
                            }
                        }
                        ProviderType.GEMINI_REST -> {
                            val list = json.optJSONArray("models") ?: JSONArray()
                            for (i in 0 until list.length()) {
                                val item = list.optJSONObject(i) ?: continue
                                val rawName = item.optString("name")
                                val id = rawName.removePrefix("models/")
                                if (id.isBlank()) continue
                                val display = item.optString("displayName").ifBlank { id }
                                models.add(DiscoveredModel(modelId = id, displayName = display))
                            }
                        }
                        ProviderType.FIREBASE_AI_LOGIC -> Unit
                    }
                    Result.success(models.sortedBy { it.displayName.lowercase() })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch models for ${provider.name}", e)
                Result.failure(e)
            }
        }

    private fun combinePromptAndFiles(turn: ConversationTurn): String {
        return buildString {
            if (turn.attachmentTextContext.isNotBlank()) {
                append(turn.attachmentTextContext.trim())
                if (turn.text.isNotBlank()) append("\n\n")
            }
            append(turn.text)
            if (isEmpty() && turn.imageBitmaps.isNotEmpty()) {
                append("Please analyze the attached image(s).")
            }
        }
    }

    private fun parseHttpError(code: Int, body: String): String {
        val messageFromJson = try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?.ifBlank { null }
                ?: json.optString("message").ifBlank { null }
        } catch (_: Exception) {
            null
        }
        return if (!messageFromJson.isNullOrBlank()) {
            "HTTP $code: $messageFromJson"
        } else {
            "HTTP $code: ${body.take(240).ifBlank { "Request failed" }}"
        }
    }
}
