package org.ole.planet.myplanet.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.utils.ChatHistoryUtils.extractSharedViewInIds

class ChatHistoryAdapterTest {

    @Test
    fun testExtractSharedViewInIds_mergesDuplicateNewsIds() {
        val news1 = News().apply {
            newsId = "chat-1"
            viewIn = "[{\"_id\": \"viewer-1\"}]"
        }
        val news2 = News().apply {
            newsId = "chat-1"
            viewIn = "[{\"_id\": \"viewer-2\"}]"
        }
        val sharedNews = listOf(news1, news2)
        val cachedSharedViewInIds = extractSharedViewInIds(sharedNews)

        assertEquals(setOf("viewer-1", "viewer-2"), cachedSharedViewInIds["chat-1"])
    }

    @Test
    fun testExtractSharedViewInIds_malformedJson() {
        val news = News().apply {
            newsId = "chat-1"
            viewIn = "invalid-json"
        }
        val sharedNews = listOf(news)
        val cachedSharedViewInIds = extractSharedViewInIds(sharedNews)

        assertEquals(emptySet<String>(), cachedSharedViewInIds["chat-1"] ?: emptySet<String>())
    }

    @Test
    fun testExtractSharedViewInIds_nullChatId() {
        val cachedSharedViewInIds = extractSharedViewInIds(emptyList<News>())
        assertEquals(emptyMap<String, Set<String>>(), cachedSharedViewInIds)
    }

    @Test
    fun testExtractSharedViewInIds_emptyListClearsCache() {
        val news = News().apply {
            newsId = "chat-1"
            viewIn = "[{\"_id\": \"viewer-1\"}]"
        }
        val cachedSharedViewInIds = extractSharedViewInIds(listOf(news))
        assertEquals(setOf("viewer-1"), cachedSharedViewInIds["chat-1"])

        val emptyCache = extractSharedViewInIds(emptyList<News>())
        assertEquals(emptyMap<String, Set<String>>(), emptyCache)
    }
}
