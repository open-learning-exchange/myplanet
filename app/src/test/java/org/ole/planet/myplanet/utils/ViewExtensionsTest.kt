package org.ole.planet.myplanet.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewExtensionsTest {

    @Test
    fun textChanges_emitsInitialValue() = runTest {
        val editText = mockk<EditText>(relaxed = true)
        val mockEditable = mockk<Editable>(relaxed = true)
        every { mockEditable.toString() } returns "Initial"
        every { editText.text } returns mockEditable

        val initialText = editText.textChanges().first()
        assertEquals("Initial", initialText.toString())
    }

    @Test
    fun textChanges_emitsOnTextChanged() = runTest {
        val editText = mockk<EditText>(relaxed = true)
        val mockEditable = mockk<Editable>(relaxed = true)
        every { mockEditable.toString() } returns "Initial"
        every { editText.text } returns mockEditable

        val listenerSlot = slot<TextWatcher>()
        every { editText.addTextChangedListener(capture(listenerSlot)) } returns Unit

        val emittedValues = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            editText.textChanges().take(3).toList().forEach {
                emittedValues.add(it?.toString() ?: "")
            }
        }

        val listener = listenerSlot.captured
        listener.onTextChanged("First", 0, 0, 5)
        listener.onTextChanged("Second", 0, 0, 6)

        assertEquals(listOf("Initial", "First", "Second"), emittedValues)

        job.cancel()
        verify { editText.removeTextChangedListener(listener) }
    }
}
