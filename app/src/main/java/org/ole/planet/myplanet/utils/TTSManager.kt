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

    @Volatile
    private var isInitialized = false
    @Volatile
    private var pendingText: String? = null

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    val isSpeaking get() = _state.value == State.SPEAKING

    private fun ensureTts(text: String) {
        if (tts != null) {
            if (isInitialized) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            } else {
                pendingText = text
            }
            return
        }

        pendingText = text
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                instance?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = State.SPEAKING
                    }
                    override fun onDone(utteranceId: String?) {
                        _state.value = State.IDLE
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onError(utteranceId, TextToSpeech.ERROR)
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = State.IDLE
                    }
                })
                pendingText?.let { instance?.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) }
                pendingText = null
            } else {
                instance?.shutdown()
                tts = null
                pendingText = null
            }
        }
        tts = instance
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        ensureTts(text)
    }

    fun stop() {
        pendingText = null
        tts?.let {
            it.stop()
            _state.value = State.IDLE
        }
    }

    companion object {
        private const val UTTERANCE_ID = "tts_utterance"

        private val CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```")
        private val INLINE_CODE_REGEX = Regex("`[^`]*`")
        private val HEADER_REGEX = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
        private val LINK_REGEX = Regex("!?\\[([^]]*)]\\([^)]*\\)")
        private val BOLD_ITALIC_REGEX = Regex("[*_]{1,3}([^*_]+)[*_]{1,3}")
        private val LIST_ITEM_REGEX = Regex("^[-*+]\\s+", RegexOption.MULTILINE)
        private val NUMBERED_LIST_REGEX = Regex("^\\d+\\.\\s+", RegexOption.MULTILINE)
        private val BLOCKQUOTE_REGEX = Regex("^>+\\s?", RegexOption.MULTILINE)
        private val HORIZONTAL_RULE_REGEX = Regex("[-]{3,}|[*]{3,}|[_]{3,}")
        private val TABLE_PIPE_REGEX = Regex("\\|")

        fun stripMarkdown(text: String): String {
            return text
                .replace(CODE_BLOCK_REGEX, "")
                .replace(INLINE_CODE_REGEX, "")
                .replace(HEADER_REGEX, "")
                .replace(LINK_REGEX, "$1")
                .replace(BOLD_ITALIC_REGEX, "$1")
                .replace(LIST_ITEM_REGEX, "")
                .replace(NUMBERED_LIST_REGEX, "")
                .replace(BLOCKQUOTE_REGEX, "")
                .replace(HORIZONTAL_RULE_REGEX, "")
                .replace(TABLE_PIPE_REGEX, " ")
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
