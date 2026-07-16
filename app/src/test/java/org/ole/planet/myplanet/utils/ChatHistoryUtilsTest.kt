package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.RealmNews

class ChatHistoryUtilsTest {

    @Test
    fun `extractSharedViewInIds returns empty map for empty list`() {
        val result = ChatHistoryUtils.extractSharedViewInIds(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractSharedViewInIds ignores news with null newsId`() {
        val news = RealmNews().apply {
            newsId = null
            viewIn = """[{"_id":"id1"}]"""
        }
        val result = ChatHistoryUtils.extractSharedViewInIds(listOf(news))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractSharedViewInIds extracts unique viewIn ids grouped by newsId`() {
        val news1 = RealmNews().apply {
            newsId = "news_1"
            viewIn = """[{"_id":"id1"}, {"_id":"id2"}]"""
        }
        val news2 = RealmNews().apply {
            newsId = "news_1"
            viewIn = """[{"_id":"id2"}, {"_id":"id3"}]"""
        }
        val news3 = RealmNews().apply {
            newsId = "news_2"
            viewIn = """[{"_id":"id4"}]"""
        }

        val result = ChatHistoryUtils.extractSharedViewInIds(listOf(news1, news2, news3))

        assertEquals(2, result.size)
        assertEquals(setOf("id1", "id2", "id3"), result["news_1"])
        assertEquals(setOf("id4"), result["news_2"])
    }

    @Test
    fun `extractSharedViewInIds handles malformed json gracefully`() {
        val news1 = RealmNews().apply {
            newsId = "news_1"
            viewIn = "malformed json"
        }
        val news2 = RealmNews().apply {
            newsId = "news_2"
            viewIn = """{"not":"an array"}"""
        }
        val news3 = RealmNews().apply {
            newsId = "news_3"
            viewIn = null
        }
        val news4 = RealmNews().apply {
            newsId = "news_4"
            viewIn = """[{"no_id":"present"}]"""
        }

        val result = ChatHistoryUtils.extractSharedViewInIds(listOf(news1, news2, news3, news4))

        assertEquals(4, result.size)
        assertTrue(result["news_1"]!!.isEmpty())
        assertTrue(result["news_2"]!!.isEmpty())
        assertTrue(result["news_3"]!!.isEmpty())
        assertTrue(result["news_4"]!!.isEmpty())
    }
}
