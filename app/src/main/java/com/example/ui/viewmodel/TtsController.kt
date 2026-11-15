package com.example.ui.viewmodel

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsController(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speakingMessageId.value = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    if (_speakingMessageId.value == utteranceId) {
                        _speakingMessageId.value = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (_speakingMessageId.value == utteranceId) {
                        _speakingMessageId.value = null
                    }
                }
            })
            isReady = true
        }
    }

    fun toggleSpeak(messageId: String, markdownText: String) {
        if (!isReady) return
        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }
        val plain = markdownText
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
            .replace(Regex("[#*_`>~]"), "")
            .trim()
        if (plain.isEmpty()) return

        _speakingMessageId.value = messageId
        tts?.speak(plain, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        tts?.stop()
        _speakingMessageId.value = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
