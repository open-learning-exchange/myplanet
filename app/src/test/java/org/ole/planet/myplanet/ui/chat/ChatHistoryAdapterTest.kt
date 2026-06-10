package org.ole.planet.myplanet.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.RealmNews
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement

class ChatHistoryAdapterTest {

    @Test
    fun testUpdateCachedData_mergesDuplicateNewsIds() {
        val news1 = RealmNews().apply {
            newsId = "chat-1"
            viewIn = "[{\"_id\": \"viewer-1\"}]"
        }
        val news2 = RealmNews().apply {
            newsId = "chat-1"
            viewIn = "[{\"_id\": \"viewer-2\"}]"
        }
        val sharedNews = listOf(news1, news2)
        val cachedSharedViewInIds = if (sharedNews.isEmpty()) {
            emptyMap<String, Set<String>>()
        } else {
            sharedNews
                .groupBy { it.newsId }
                .mapNotNull { (newsId, newsEntries) ->
                    if (newsId == null) null
                    else {
                        val ids = newsEntries.flatMap { news ->
                            try {
                                val array = Gson().fromJson(news.viewIn, JsonArray::class.java)
                                val list = mutableListOf<String>()
                                for (i in 0 until array.size()) {
                                    val elem = array.get(i) as JsonElement
                                    if (elem.isJsonObject) {
                                        val id = elem.asJsonObject.get("_id")?.asString
                                        if (id != null) list.add(id)
                                    }
                                }
                                list
                            } catch (_: Exception) {
                                emptyList<String>()
                            }
                        }.toSet()
                        newsId to ids
                    }
                }
                .toMap()
        }
        assertEquals(setOf("viewer-1", "viewer-2"), cachedSharedViewInIds["chat-1"])
    }

    @Test
    fun testUpdateCachedData_malformedJson() {
        val news = RealmNews().apply {
            newsId = "chat-1"
            viewIn = "invalid-json"
        }
        val sharedNews = listOf(news)
        val cachedSharedViewInIds = if (sharedNews.isEmpty()) {
            emptyMap<String, Set<String>>()
        } else {
            sharedNews
                .groupBy { it.newsId }
                .mapNotNull { (newsId, newsEntries) ->
                    if (newsId == null) null
                    else {
                        val ids = newsEntries.flatMap { news ->
                            try {
                                val array = Gson().fromJson(news.viewIn, JsonArray::class.java)
                                val list = mutableListOf<String>()
                                for (i in 0 until array.size()) {
                                    val elem = array.get(i) as JsonElement
                                    if (elem.isJsonObject) {
                                        val id = elem.asJsonObject.get("_id")?.asString
                                        if (id != null) list.add(id)
                                    }
                                }
                                list
                            } catch (_: Exception) {
                                emptyList<String>()
                            }
                        }.toSet()
                        newsId to ids
                    }
                }
                .toMap()
        }
        assertEquals(emptySet<String>(), cachedSharedViewInIds["chat-1"] ?: emptySet<String>())
    }
}
