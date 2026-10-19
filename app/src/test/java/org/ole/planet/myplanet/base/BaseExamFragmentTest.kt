package org.ole.planet.myplanet.base

import android.content.Context
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.utils.MarkdownUtils

class BaseExamFragmentTest {

    class TestBaseExamFragment : BaseExamFragment() {
        override fun saveCourseProgress(courseId: String?, stepNum: Int, isGraded: Boolean, userId: String?) {}
        override fun startExam(question: ExamQuestion?) {}
    }

    private lateinit var fragment: BaseExamFragment
    private lateinit var activity: FragmentActivity
    private lateinit var editText: EditText

    @Before
    fun setUp() {
        mockkObject(MarkdownUtils)
        mockkStatic(MarkwonEditor::class)
        mockkStatic(MarkwonEditorTextWatcher::class)

        activity = mockk(relaxed = true)
        editText = mockk(relaxed = true)

        val markwon = mockk<Markwon>(relaxed = true)
        val editor = mockk<MarkwonEditor>(relaxed = true)
        val markwonEditorTextWatcher = mockk<MarkwonEditorTextWatcher>(relaxed = true)

        every { MarkdownUtils.create(any<Context>()) } returns markwon
        every { MarkwonEditor.create(markwon) } returns editor
        every { MarkwonEditorTextWatcher.withPreRender(any(), any(), any()) } returns markwonEditorTextWatcher

        // We use a mock fragment to avoid Realm constructor issues but call the real method
        fragment = spyk(TestBaseExamFragment())
        every { fragment.requireActivity() } returns activity

        val markwonField = BaseExamFragment::class.java.getDeclaredField("markwon\$delegate")
        markwonField.isAccessible = true
        markwonField.set(fragment, lazy(LazyThreadSafetyMode.NONE) { MarkdownUtils.create(activity) })

        val editorField = BaseExamFragment::class.java.getDeclaredField("markwonEditor\$delegate")
        editorField.isAccessible = true
        // The real markwon is needed to initialize the editor
        editorField.set(fragment, lazy(LazyThreadSafetyMode.NONE) {
            val getMarkwon = BaseExamFragment::class.java.getDeclaredMethod("getMarkwon")
            getMarkwon.isAccessible = true
            MarkwonEditor.create(getMarkwon.invoke(fragment) as Markwon)
        })
    }

    @After
    fun tearDown() {
        unmockkObject(MarkdownUtils)
        unmockkStatic(MarkwonEditor::class)
        unmockkStatic(MarkwonEditorTextWatcher::class)
    }

    private fun answerTextWatcher(): Any? {
        val watcherField = BaseExamFragment::class.java.getDeclaredField("answerTextWatcher")
        watcherField.isAccessible = true
        return watcherField.get(fragment)
    }

    @Test
    fun testSetMarkdownViewAndShowInput_textarea() {
        fragment.setMarkdownViewAndShowInput(editText, "textarea", "test textarea answer")
        fragment.setMarkdownViewAndShowInput(editText, "textarea", "test textarea answer 2")

        verify { editText.visibility = View.VISIBLE }
        verify(exactly = 1) { MarkdownUtils.create(activity) }
        verify(exactly = 1) { MarkwonEditorTextWatcher.withPreRender(any(), any(), editText) }
        verify(exactly = 1) { editText.addTextChangedListener(any<MarkwonEditorTextWatcher>()) }
        verify(exactly = 0) { editText.removeTextChangedListener(any()) }
        verify { editText.setText("test textarea answer") }
        verify { editText.setText("test textarea answer 2") }
        assertTrue(answerTextWatcher() is MarkwonEditorTextWatcher)
    }

    @Test
    fun testSetMarkdownViewAndShowInput_reusesWatcherAcrossQuestionTypes() {
        fragment.setMarkdownViewAndShowInput(editText, "textarea", "first")
        val watcher = answerTextWatcher() as TextWatcher
        fragment.setMarkdownViewAndShowInput(editText, "text", "second")
        fragment.setMarkdownViewAndShowInput(editText, "textarea", "third")

        verify(exactly = 1) { MarkwonEditorTextWatcher.withPreRender(any(), any(), any()) }
        verify(exactly = 1) { editText.removeTextChangedListener(watcher) }
        verify(exactly = 2) { editText.addTextChangedListener(watcher) }
        assertSame(watcher, answerTextWatcher())

        fragment.onDestroyView()
        verify(exactly = 2) { editText.removeTextChangedListener(watcher) }
        assertNull(answerTextWatcher())
    }

    @Test
    fun testSetMarkdownViewAndShowInput_newEditTextGetsNewWatcher() {
        val otherEditText = mockk<EditText>(relaxed = true)
        fragment.setMarkdownViewAndShowInput(editText, "textarea", "first")
        val watcher = answerTextWatcher() as TextWatcher
        fragment.setMarkdownViewAndShowInput(otherEditText, "textarea", "second")

        verify(exactly = 1) { editText.removeTextChangedListener(watcher) }
        verify(exactly = 1) { MarkwonEditorTextWatcher.withPreRender(any(), any(), editText) }
        verify(exactly = 1) { MarkwonEditorTextWatcher.withPreRender(any(), any(), otherEditText) }
    }

    @Test
    fun testSetMarkdownViewAndShowInput_callingTwiceLeavesOneExecutor() {
        fragment.setMarkdownViewAndShowInput(editText, "textarea", "test 1")
        val executorField = BaseExamFragment::class.java.getDeclaredField("markwonExecutor")
        executorField.isAccessible = true
        val executor1 = executorField.get(fragment) as? ExecutorService

        assertNotNull(executor1)
        assertFalse(executor1!!.isShutdown)

        fragment.setMarkdownViewAndShowInput(editText, "textarea", "test 2")
        val executor2 = executorField.get(fragment) as? ExecutorService

        assertSame(executor1, executor2)
    }

    @Test
    fun testDestroyThenRebindDoesNotThrow() {
        fragment.setMarkdownViewAndShowInput(editText, "textarea", "test 1")
        val executorField = BaseExamFragment::class.java.getDeclaredField("markwonExecutor")
        executorField.isAccessible = true
        val executor1 = executorField.get(fragment) as? ExecutorService
        assertNotNull(executor1)

        fragment.onDestroyView()
        fragment.onDestroy()
        assertTrue(executor1!!.isShutdown)

        fragment.setMarkdownViewAndShowInput(editText, "textarea", "test 2")
        val executor2 = executorField.get(fragment) as? ExecutorService
        assertNotNull(executor2)
        assertFalse(executor2!!.isShutdown)
        verify { MarkwonEditorTextWatcher.withPreRender(any(), any(), any()) }
    }

    @Test
    fun testSetMarkdownViewAndShowInput_otherType() {
        fragment.setMarkdownViewAndShowInput(editText, "text", "test text answer")

        verify { editText.visibility = View.VISIBLE }
        verify(exactly = 0) { MarkdownUtils.create(activity) }
        verify(exactly = 0) { editText.addTextChangedListener(any<TextWatcher>()) }
        verify { editText.setText("test text answer") }
        assertNull(answerTextWatcher())
    }
}
