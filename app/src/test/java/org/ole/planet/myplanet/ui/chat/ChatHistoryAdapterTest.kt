package org.ole.planet.myplanet.ui.chat

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatHistoryAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: ChatHistoryAdapter
    private val testDiffConfig = AsyncDifferConfig.Builder(ChatHistoryAdapter.ITEM_CALLBACK)
        .setBackgroundThreadExecutor(Executor { it.run() })
        .build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = ChatHistoryAdapter(
            context = context,
            chatHistoryList = emptyList(),
            currentUser = UserEntity(),
            newsList = emptyList(),
            cachedSharedViewInIds = emptyMap(),
            shareTargets = ChatShareTargets(null, emptyList(), emptyList()),
            diffConfig = testDiffConfig,
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
        shadowOf(Looper.getMainLooper()).idle()
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

    @Test
    fun `clicking earlier row sets card contentDescription from its own title when another row is bound later`() {
        val item1 = ChatHistory().apply {
            id = "10"
            _id = "10"
            title = "Title A"
        }
        val item2 = ChatHistory().apply {
            id = "20"
            _id = "20"
            title = "Title B"
        }

        adapter.submitList(listOf(item1, item2))
        waitForList(adapter, 2)

        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ChatHistoryAdapterTest.adapter
        }
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, 1080, 1920)

        val holder1 = recyclerView.findViewHolderForAdapterPosition(0) as ChatHistoryAdapter.ViewHolderChat
        assertNotNull(recyclerView.findViewHolderForAdapterPosition(1))

        holder1.rowChatHistoryBinding.root.performClick()

        assertEquals("Title A", holder1.rowChatHistoryBinding.chatCardView.contentDescription)
    }

    @Test
    fun `card label is set on bind without a click`() {
        val item = ChatHistory().apply {
            id = "7"
            _id = "7"
            conversations = listOf(Conversation().apply { query = "never tapped" })
        }
        val holder = bindRow(item)
        assertEquals("never tapped", holder.rowChatHistoryBinding.chatCardView.contentDescription)
    }

    @Test
    fun `recycled row takes the card label of its new item`() {
        val chatA = ChatHistory().apply {
            id = "a"
            _id = "a"
            conversations = listOf(Conversation().apply { query = "chat A" })
        }
        val chatC = ChatHistory().apply {
            id = "c"
            _id = "c"
            title = "chat C"
            conversations = null
        }
        adapter.submitList(listOf(chatA, chatC))
        waitForList(adapter, 2)
        val holder = adapter.onCreateViewHolder(LinearLayout(context), adapter.getItemViewType(0))

        adapter.onBindViewHolder(holder, 0)
        holder.rowChatHistoryBinding.root.performClick()
        adapter.onBindViewHolder(holder, 1)

        assertEquals("chat C", holder.rowChatHistoryBinding.chatTitle.text.toString())
        assertEquals("chat C", holder.rowChatHistoryBinding.chatCardView.contentDescription)
    }
}
