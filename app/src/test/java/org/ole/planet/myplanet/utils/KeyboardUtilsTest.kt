package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.Context
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class KeyboardUtilsTest {

    private lateinit var mockActivity: Activity
    private lateinit var mockInputMethodManager: InputMethodManager
    private lateinit var mockView: View
    private lateinit var mockWindowToken: IBinder

    @Before
    fun setUp() {
        mockActivity = mockk()
        mockInputMethodManager = mockk()
        mockView = mockk()
        mockWindowToken = mockk()

        every { mockActivity.getSystemService(Context.INPUT_METHOD_SERVICE) } returns mockInputMethodManager
        every { mockActivity.currentFocus } returns mockView
        every { mockView.windowToken } returns mockWindowToken
        every { mockInputMethodManager.hideSoftInputFromWindow(any(), any()) } returns true
    }

    @Test
    fun testHideSoftKeyboard() {
        KeyboardUtils.hideSoftKeyboard(mockActivity)

        verify { mockActivity.getSystemService(Context.INPUT_METHOD_SERVICE) }
        verify { mockActivity.currentFocus }
        verify { mockInputMethodManager.hideSoftInputFromWindow(mockWindowToken, 0) }
    }

    @Test
    fun testHideSoftKeyboardWithNullFocus() {
        every { mockActivity.currentFocus } returns null

        KeyboardUtils.hideSoftKeyboard(mockActivity)

        verify { mockInputMethodManager.hideSoftInputFromWindow(null, 0) }
    }

    @Test
    fun testHideSoftKeyboardExceptionHandling() {
        every { mockActivity.getSystemService(Context.INPUT_METHOD_SERVICE) } throws RuntimeException("Test Exception")

        // Should not throw exception
        KeyboardUtils.hideSoftKeyboard(mockActivity)

        verify { mockActivity.getSystemService(Context.INPUT_METHOD_SERVICE) }
    }

    @Test
    fun testSetupUIWithNonEditText() {
        val mockTextView: TextView = mockk(relaxed = true)

        KeyboardUtils.setupUI(mockTextView, mockActivity)

        val touchListenerSlot = slot<View.OnTouchListener>()
        verify { mockTextView.setOnTouchListener(capture(touchListenerSlot)) }

        val listener = touchListenerSlot.captured
        assertNotNull(listener)

        // Trigger touch event
        val mockMotionEvent: MotionEvent = mockk()
        every { mockMotionEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        val result = listener.onTouch(mockTextView, mockMotionEvent)

        assertFalse(result)
        verify { mockActivity.getSystemService(Context.INPUT_METHOD_SERVICE) }
        verify { mockInputMethodManager.hideSoftInputFromWindow(mockWindowToken, 0) }
    }

    @Test
    fun testSetupUIWithNonDownMotionEventDoesNotHideKeyboard() {
        val mockTextView: TextView = mockk(relaxed = true)

        KeyboardUtils.setupUI(mockTextView, mockActivity)

        val touchListenerSlot = slot<View.OnTouchListener>()
        verify { mockTextView.setOnTouchListener(capture(touchListenerSlot)) }

        val listener = touchListenerSlot.captured

        val mockMoveEvent: MotionEvent = mockk()
        every { mockMoveEvent.actionMasked } returns MotionEvent.ACTION_MOVE
        val moveResult = listener.onTouch(mockTextView, mockMoveEvent)
        assertFalse(moveResult)

        val mockUpEvent: MotionEvent = mockk()
        every { mockUpEvent.actionMasked } returns MotionEvent.ACTION_UP
        val upResult = listener.onTouch(mockTextView, mockUpEvent)
        assertFalse(upResult)

        verify(exactly = 0) { mockInputMethodManager.hideSoftInputFromWindow(any(), any()) }
    }

    @Test
    fun testSetupUIWithEditText() {
        val mockEditText: EditText = mockk(relaxed = true)

        KeyboardUtils.setupUI(mockEditText, mockActivity)

        verify(exactly = 0) { mockEditText.setOnTouchListener(any()) }
    }

    @Test
    fun testSetupUIWithViewGroup() {
        val mockViewGroup: ViewGroup = mockk(relaxed = true)
        val mockChildView1: TextView = mockk(relaxed = true)
        val mockChildView2: EditText = mockk(relaxed = true)

        every { mockViewGroup.childCount } returns 2
        every { mockViewGroup.getChildAt(0) } returns mockChildView1
        every { mockViewGroup.getChildAt(1) } returns mockChildView2

        KeyboardUtils.setupUI(mockViewGroup, mockActivity)

        val touchListenerSlot = slot<View.OnTouchListener>()
        // ViewGroup itself gets touch listener
        verify { mockViewGroup.setOnTouchListener(capture(touchListenerSlot)) }

        // Child 1 (TextView) gets touch listener
        verify { mockChildView1.setOnTouchListener(capture(touchListenerSlot)) }

        // Child 2 (EditText) does NOT get touch listener
        verify(exactly = 0) { mockChildView2.setOnTouchListener(any()) }
    }

    @Test
    fun testSetupUIWithViewGroupSharesSameListener() {
        val mockViewGroup: ViewGroup = mockk(relaxed = true)
        val mockChildView1: TextView = mockk(relaxed = true)
        val mockChildView2: TextView = mockk(relaxed = true)

        every { mockViewGroup.childCount } returns 2
        every { mockViewGroup.getChildAt(0) } returns mockChildView1
        every { mockViewGroup.getChildAt(1) } returns mockChildView2

        KeyboardUtils.setupUI(mockViewGroup, mockActivity)

        val slotViewGroup = slot<View.OnTouchListener>()
        val slotChild1 = slot<View.OnTouchListener>()
        val slotChild2 = slot<View.OnTouchListener>()

        verify { mockViewGroup.setOnTouchListener(capture(slotViewGroup)) }
        verify { mockChildView1.setOnTouchListener(capture(slotChild1)) }
        verify { mockChildView2.setOnTouchListener(capture(slotChild2)) }

        assertSame(slotViewGroup.captured, slotChild1.captured)
        assertSame(slotViewGroup.captured, slotChild2.captured)
    }
}
