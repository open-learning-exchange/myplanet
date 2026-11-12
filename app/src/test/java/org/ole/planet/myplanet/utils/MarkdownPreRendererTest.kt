package org.ole.planet.myplanet.utils

import android.text.Editable
import android.widget.EditText
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.noties.markwon.editor.MarkwonEditor
import java.util.concurrent.ExecutorService
import org.junit.Before
import org.junit.Test

class MarkdownPreRendererTest {

    private lateinit var editor: MarkwonEditor
    private lateinit var executor: ExecutorService
    private lateinit var editText: EditText
    private lateinit var fieldText: Editable
    private lateinit var result: MarkwonEditor.PreRenderResult
    private val submitted = mutableListOf<Runnable>()
    private val posted = mutableListOf<Runnable>()
    private lateinit var watcher: MarkdownPreRenderer

    @Before
    fun setUp() {
        editor = mockk()
        executor = mockk()
        editText = mockk()
        fieldText = mockk(relaxed = true)
        result = mockk(relaxed = true)

        every { executor.submit(any<Runnable>()) } answers {
            submitted += firstArg<Runnable>()
            mockk(relaxed = true)
        }
        val listener = slot<MarkwonEditor.PreRenderResultListener>()
        every { editor.preRender(any(), capture(listener)) } answers { listener.captured.onPreRenderResult(result) }
        every { editText.post(any()) } answers {
            posted += firstArg<Runnable>()
            true
        }
        every { editText.text } returns fieldText

        watcher = MarkdownPreRenderer(editor, executor, editText)
    }

    /** Runs the background render, then the main-thread dispatch it posted. */
    private fun finishRenders() {
        submitted.toList().also { submitted.clear() }.forEach { it.run() }
        posted.toList().also { posted.clear() }.forEach { it.run() }
    }

    @Test
    fun appliesRenderWhenTextIsUnchanged() {
        watcher.afterTextChanged(mockk(relaxed = true))
        finishRenders()

        verify(exactly = 1) { result.dispatchTo(fieldText) }
    }

    @Test
    fun dropsRenderOfTextareaAnswerOnceFieldSwitchesToPlainText() {
        // Last keystroke on a textarea question: its render is computed but not yet applied...
        watcher.afterTextChanged(mockk(relaxed = true))
        submitted.single().run()
        submitted.clear()

        // ...when Next shows a plain-text question with a shorter answer.
        watcher.isRenderingEnabled = false
        watcher.afterTextChanged(mockk(relaxed = true))
        finishRenders()

        verify(exactly = 0) { result.dispatchTo(any()) }
        verify(exactly = 1) { executor.submit(any<Runnable>()) }
    }

    @Test
    fun dropsRenderThatFinishesAfterTheTextChangedAgain() {
        watcher.afterTextChanged(mockk(relaxed = true))
        val stale = submitted.single()
        submitted.clear()
        watcher.afterTextChanged(mockk(relaxed = true))

        stale.run()
        posted.single().run()
        posted.clear()
        verify(exactly = 0) { result.dispatchTo(any()) }

        finishRenders()
        verify(exactly = 1) { result.dispatchTo(fieldText) }
    }

    @Test
    fun cancelDropsRenderInFlight() {
        watcher.afterTextChanged(mockk(relaxed = true))
        watcher.cancel()
        finishRenders()

        verify(exactly = 0) { result.dispatchTo(any()) }
    }
}
