package org.ole.planet.myplanet.base

import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BaseExamFragmentTest {

    private lateinit var fragment: BaseExamFragment
    private lateinit var activity: FragmentActivity
    private lateinit var editText: EditText

    @Before
    fun setUp() {
        mockkStatic(Markwon::class)
        mockkStatic(MarkwonEditor::class)
        mockkStatic(MarkwonEditorTextWatcher::class)

        activity = mockk(relaxed = true)
        editText = mockk(relaxed = true)

        val markwon = mockk<Markwon>(relaxed = true)
        val editor = mockk<MarkwonEditor>(relaxed = true)
        val markwonEditorTextWatcher = mockk<MarkwonEditorTextWatcher>(relaxed = true)

        every { Markwon.create(any<android.content.Context>()) } returns markwon
        every { MarkwonEditor.create(markwon) } returns editor
        every { MarkwonEditorTextWatcher.withProcess(editor) } returns markwonEditorTextWatcher

        // We use a mock fragment to avoid Realm constructor issues but call the real method
        fragment = mockk<BaseExamFragment>()
        every { fragment.requireActivity() } returns activity

        // Setup reflection to make setMarkdownViewAndShowInput call the real method
        every { fragment.setMarkdownViewAndShowInput(any(), any(), any()) } answers { callOriginal() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Markwon::class)
        unmockkStatic(MarkwonEditor::class)
        unmockkStatic(MarkwonEditorTextWatcher::class)
    }

    @Test
    fun testSetMarkdownViewAndShowInput_textarea() {
        // Need to set currentAnswerEditText indirectly if needed, but it starts as null
        fragment.setMarkdownViewAndShowInput(editText, "textarea", "test textarea answer")

        verify { editText.visibility = View.VISIBLE }
        verify { Markwon.create(activity) }
        verify { MarkwonEditorTextWatcher.withProcess(any()) }
        verify { editText.addTextChangedListener(any<MarkwonEditorTextWatcher>()) }
        verify { editText.setText("test textarea answer") }

        // Assert field states via reflection
        val watcherField = BaseExamFragment::class.java.getDeclaredField("answerTextWatcher")
        watcherField.isAccessible = true
        val watcher = watcherField.get(fragment)
        assertTrue(watcher is MarkwonEditorTextWatcher)
    }

    @Test
    fun testSetMarkdownViewAndShowInput_otherType() {
        fragment.setMarkdownViewAndShowInput(editText, "text", "test text answer")

        verify { editText.visibility = View.VISIBLE }
        verify { Markwon.create(activity) }
        verify { editText.addTextChangedListener(any<TextWatcher>()) }
        verify { editText.setText("test text answer") }

        val watcherField = BaseExamFragment::class.java.getDeclaredField("answerTextWatcher")
        watcherField.isAccessible = true
        val watcher = watcherField.get(fragment)
        assertTrue(watcher !is MarkwonEditorTextWatcher)
        assertTrue(watcher is TextWatcher)
    }
}
