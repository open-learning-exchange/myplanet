package org.ole.planet.myplanet.utils

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.widget.EditText
import io.noties.markwon.editor.MarkwonEditor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * Markwon's `withPreRender` watcher, but one that can stay attached while rendering is switched off.
 *
 * A render result is applied only if no text change has happened since it was started. Markwon's own
 * watcher can only notice a change while it is attached, so once it is removed a render still in flight
 * lands on whatever text replaced it — and throws from `setSpan` when that text is shorter. This one keeps
 * counting changes while [isRenderingEnabled] is false, so such a result is always dropped.
 */
class MarkdownPreRenderer(
    private val editor: MarkwonEditor,
    private val executor: ExecutorService,
    private val editText: EditText,
) : TextWatcher {

    // Touched on the main thread only: afterTextChanged and the posted dispatch both run there.
    private var generation = 0
    private var pending: Future<*>? = null
    private var isDispatching = false

    var isRenderingEnabled = true
        set(value) {
            field = value
            cancel()
        }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable) {
        if (isDispatching) return
        cancel()
        if (!isRenderingEnabled) return
        val key = generation
        val snapshot = SpannableStringBuilder(s)
        pending = executor.submit(Runnable {
            editor.preRender(snapshot) { result ->
                editText.post {
                    if (key != generation) return@post
                    val text = editText.text ?: return@post
                    isDispatching = true
                    try {
                        result.dispatchTo(text)
                    } finally {
                        isDispatching = false
                    }
                }
            }
        })
    }

    /** Drops any render in flight, so its result is never applied. */
    fun cancel() {
        generation++
        pending?.cancel(true)
        pending = null
    }
}
