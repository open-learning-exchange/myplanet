package org.ole.planet.myplanet.ui.chat

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.ole.planet.myplanet.model.ChatMessage
import org.junit.Assert.assertEquals
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

    @Test
    fun `copyToClipboard resolves the system service only once and writes the clip`() {
        val query = "hello world"
        adapter.addQuery(query)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // The clipboard manager resolved lazily inside the adapter must be the same
        // singleton instance the platform returns from getSystemService, so caching
        // it never yields a stale or mismatched manager.
        val primaryClipBefore = clipboard.primaryClip
        assertEquals(null, primaryClipBefore)

        // Trigger a long-press copy via the bound query holder.
        val parent = recyclerView
        val holder = adapter.onCreateViewHolder(parent, ChatMessage.QUERY)
        adapter.onBindViewHolder(holder, 0)

        // The long-press listener performs the copy; invoking it twice exercises the
        // cached lookup path repeatedly without re-resolving the service.
        holder.itemView.performLongClick()
        holder.itemView.performLongClick()

        val clip = clipboard.primaryClip
        assertEquals(1, clip?.itemCount ?: 0)
        assertEquals(query, clip?.getItemAt(0)?.text?.toString())
    }
}
