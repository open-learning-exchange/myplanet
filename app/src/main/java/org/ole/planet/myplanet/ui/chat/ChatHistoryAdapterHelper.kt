package org.ole.planet.myplanet.ui.chat

import org.ole.planet.myplanet.model.RealmNews
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement

object ChatHistoryAdapterHelper {
    fun extractSharedViewInIds(sharedNews: List<RealmNews>): Map<String, Set<String>> {
        if (sharedNews.isEmpty()) return emptyMap()
        return sharedNews
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
}
