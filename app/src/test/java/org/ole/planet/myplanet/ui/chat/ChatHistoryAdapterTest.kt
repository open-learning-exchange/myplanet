package org.ole.planet.myplanet.ui.chat

import android.app.Application
import android.content.Context
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatHistoryAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: ChatHistoryAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = ChatHistoryAdapter(
            context = context,
            chatHistoryList = emptyList(),
            currentUser = UserEntity(),
            newsList = emptyList<News>(),
            cachedSharedViewInIds = emptyMap(),
            shareTargets = ChatShareTargets(null, emptyList(), emptyList()),
            onShareChat = { _, _ -> },
        )
    }

    private fun bindRow(item: ChatHistory): ChatHistoryAdapter.ViewHolderChat {
        adapter.submitList(listOf(item))
        waitForList(adapter, 1)
        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    private fun waitForList(adapter: ChatHistoryAdapter, size: Int) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        var attempts = 0
        while (adapter.currentList.size != size && attempts < 50) {
            Thread.sleep(10)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            attempts++
        }
        assertEquals(size, adapter.currentList.size)
    }

    @Test
    fun `first conversation query is shown as title`() {
        val conversation = Conversation().apply {
            query = "what is photosynthesis"
            response = "a process used by plants"
        }
        val item = ChatHistory().apply {
            id = "1"
            _id = "1"
            title = "fallback title"
            conversations = listOf(conversation)
        }

        val holder = bindRow(item)

        assertEquals("what is photosynthesis", holder.rowChatHistoryBinding.chatTitle.text.toString())
        assertEquals("what is photosynthesis", holder.rowChatHistoryBinding.chatTitle.contentDescription)
    }

    @Test
    fun `title falls back when conversations is null`() {
        val item = ChatHistory().apply {
            id = "2"
            _id = "2"
            title = "fallback title"
            conversations = null
        }

        val holder = bindRow(item)

        assertEquals("fallback title", holder.rowChatHistoryBinding.chatTitle.text.toString())
        assertEquals("fallback title", holder.rowChatHistoryBinding.chatTitle.contentDescription)
    }

    @Test
    fun `title falls back when first conversation query is null`() {
        val item = ChatHistory().apply {
            id = "3"
            _id = "3"
            title = "fallback title"
            conversations = listOf(Conversation().apply {
                query = null
                response = "no query"
            })
        }

        val holder = bindRow(item)

        assertEquals("fallback title", holder.rowChatHistoryBinding.chatTitle.text.toString())
    }

    @Test
    fun `title falls back when conversations list is empty`() {
        val item = ChatHistory().apply {
            id = "4"
            _id = "4"
            title = "fallback title"
            conversations = emptyList()
        }

        val holder = bindRow(item)

        assertEquals("fallback title", holder.rowChatHistoryBinding.chatTitle.text.toString())
    }

    @Test
    fun `chatTitle is set during bind and survives row click`() {
        val conversation = Conversation().apply { query = "hello" }
        val item = ChatHistory().apply {
            id = "5"
            _id = "5"
            conversations = listOf(conversation)
        }
        val holder = bindRow(item)
        holder.rowChatHistoryBinding.root.performClick()
        assertEquals("hello", holder.rowChatHistoryBinding.chatTitle.text.toString())
    }

    @Test
    fun `null title and null conversations produce blank title`() {
        val item = ChatHistory().apply {
            id = "6"
            _id = "6"
            title = null
            conversations = null
        }
        val holder = bindRow(item)
        assertEquals("", holder.rowChatHistoryBinding.chatTitle.text.toString())
        assertNull(holder.rowChatHistoryBinding.chatTitle.contentDescription)
    }
}
