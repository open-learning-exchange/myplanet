package org.ole.planet.myplanet.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class State { IDLE, SPEAKING }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    val isSpeaking get() = _state.value == State.SPEAKING

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = State.SPEAKING
                    }
                    override fun onDone(utteranceId: String?) {
                        _state.value = State.IDLE
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = State.IDLE
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = State.IDLE
                    }
                })
            }
        }
    }

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        tts?.stop()
        _state.value = State.IDLE
    }

    companion object {
        private const val UTTERANCE_ID = "tts_utterance"

        fun stripMarkdown(text: String): String {
            return text
                .replace(Regex("```[\\s\\S]*?```"), "")
                .replace(Regex("`[^`]*`"), "")
                .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
                .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("[*_]{1,3}([^*_]+)[*_]{1,3}"), "$1")
                .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
                .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
                .replace(Regex("^>+\\s?", RegexOption.MULTILINE), "")
                .replace(Regex("[-]{3,}|[*]{3,}|[_]{3,}"), "")
                .replace(Regex("\\|"), " ")
                .trim()
        }

        fun formatCsvForSpeech(rows: List<Array<String>>): String {
            if (rows.isEmpty()) return ""
            val header = rows.first()
            return rows.drop(1).mapIndexed { index, row ->
                val cells = row.mapIndexed { col, value ->
                    val colName = header.getOrElse(col) { "column ${col + 1}" }
                    "$colName: $value"
                }.joinToString(", ")
                "Row ${index + 1}. $cells"
            }.joinToString(". ")
        }
    }
}
