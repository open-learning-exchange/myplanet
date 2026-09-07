package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class UtilitiesTest {

    private val mockContext = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        mockkStatic(Toast::class)
        val mockLooper = mockk<Looper>()
        every { Looper.getMainLooper() } returns mockLooper
        val currentThread = Thread.currentThread()
        every { mockLooper.thread } returns currentThread
        every { mockLooper.getThread() } returns currentThread

        every { Looper.myLooper() } returns mockk<Looper>() // Different from main looper
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `toast dispatches via Handler when not on main thread`() {
        io.mockk.mockkObject(ProcessLifecycleOwner.Companion)
        val mockLifecycleOwner = mockk<LifecycleOwner>()
        val mockLifecycleRegistry = LifecycleRegistry(mockLifecycleOwner)
        mockLifecycleRegistry.currentState = Lifecycle.State.RESUMED
        every { ProcessLifecycleOwner.get() } returns mockLifecycleOwner
        every { mockLifecycleOwner.lifecycle } returns mockLifecycleRegistry

        val mockActivity = mockk<Activity>(relaxed = true)
        every { mockActivity.isFinishing } returns false
        every { mockActivity.isDestroyed } returns false

        val mockToast = mockk<Toast>(relaxed = true)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockToast

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }

        Utilities.toast(mockActivity, "test message", Toast.LENGTH_SHORT)

        verify(exactly = 1) { Toast.makeText(any(), "test message", Toast.LENGTH_SHORT) }
        verify(exactly = 1) { mockToast.show() }
        verify(exactly = 1) { anyConstructed<Handler>().post(any()) }
    }

    @Test
    fun `toHex returns hex representation for valid strings and empty string for null`() {
        org.junit.Assert.assertEquals("68656c6c6f", Utilities.toHex("hello"))
        org.junit.Assert.assertEquals("0", Utilities.toHex(""))
        org.junit.Assert.assertEquals("", Utilities.toHex(null))
    }

    @Test
    fun `normalizeText fast path handles pure ASCII inputs correctly`() {
        // Fast path: mixed case, empty string, digits and punctuation, plain ASCII words
        org.junit.Assert.assertEquals("hello world", Utilities.normalizeText("Hello WORLD"))
        org.junit.Assert.assertEquals("", Utilities.normalizeText(""))
        org.junit.Assert.assertEquals("123!@# $%-=", Utilities.normalizeText("123!@# $%-="))
        org.junit.Assert.assertEquals("simple test", Utilities.normalizeText("simple test"))
    }

    @Test
    fun `normalizeText slow path produces byte-identical output to original NFD normalization`() {
        // Helper function representing the original implementation
        fun legacyNormalizeText(str: String): String {
            val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
            return java.text.Normalizer.normalize(str.lowercase(java.util.Locale.getDefault()), java.text.Normalizer.Form.NFD)
                .replace(DIACRITICS_REGEX, "")
        }

        val testCases = listOf(
            "Café",
            "Niño",
            "áéíóú",
            "مرحبا بك",
            "नमस्ते"
        )

        for (input in testCases) {
            val expected = legacyNormalizeText(input)
            val actual = Utilities.normalizeText(input)
            org.junit.Assert.assertEquals(expected, actual)
            org.junit.Assert.assertArrayEquals(
                "Byte array mismatch for input: $input",
                expected.toByteArray(Charsets.UTF_8),
                actual.toByteArray(Charsets.UTF_8)
            )
        }

        // Specific expected value assertions as requested
        org.junit.Assert.assertEquals("cafe", Utilities.normalizeText("Café"))
        org.junit.Assert.assertEquals("nino", Utilities.normalizeText("Niño"))
        org.junit.Assert.assertEquals("aeiou", Utilities.normalizeText("áéíóú"))
    }
}
