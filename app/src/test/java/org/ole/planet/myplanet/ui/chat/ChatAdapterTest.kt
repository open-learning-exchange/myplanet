package org.ole.planet.myplanet.ui.chat

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ChatAdapterTest {

    private lateinit var context: Context
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ChatAdapter(context, recyclerView) { _, _, _ -> {} }
    }

    private fun invokeCopyToClipboard(text: String) {
        val method: Method = ChatAdapter::class.java.getDeclaredMethod("copyToClipboard", String::class.java)
        method.isAccessible = true
        method.invoke(adapter, text)
    }

    @Test
    fun `copyToClipboard writes the text to the clipboard via the cached manager`() {
        val query = "hello world"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        assertEquals(null, clipboard.primaryClip)

        invokeCopyToClipboard(query)
        invokeCopyToClipboard(query)

        val clip = clipboard.primaryClip
        assertEquals(1, clip?.itemCount ?: 0)
        assertEquals(query, clip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `copyToClipboard resolves the clipboard service only once across copies`() {
        val countingContext = CountingClipboardContext(context)
        val cachedAdapter = ChatAdapter(countingContext, recyclerView) { _, _, _ -> {} }
        val copyMethod: Method = ChatAdapter::class.java.getDeclaredMethod("copyToClipboard", String::class.java)
        copyMethod.isAccessible = true

        copyMethod.invoke(cachedAdapter, "first")
        copyMethod.invoke(cachedAdapter, "second")
        copyMethod.invoke(cachedAdapter, "third")

        // The ClipboardManager is resolved lazily and cached, so three long-press
        // copies must result in a single getSystemService lookup, not three.
        assertEquals(1, countingContext.clipboardServiceLookups)
        assertTrue(
            "expected the clipboard to hold the last copied text",
            (countingContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .primaryClip?.getItemAt(0)?.text?.toString() == "third"
        )
    }

    private class CountingClipboardContext(base: Context) : ContextWrapper(base) {
        var clipboardServiceLookups = 0
        private var cached: ClipboardManager? = null

        override fun getSystemService(name: String): Any? {
            if (name == Context.CLIPBOARD_SERVICE) {
                clipboardServiceLookups++
                // Return a stable instance so the adapter's lazy cache and the
                // test's verification share the same ClipboardManager.
                if (cached == null) {
                    cached = super.getSystemService(name) as ClipboardManager
                }
                return cached
            }
            return super.getSystemService(name)
        }
    }
}
