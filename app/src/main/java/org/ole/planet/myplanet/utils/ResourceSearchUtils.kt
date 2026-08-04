package org.ole.planet.myplanet.utils

import org.ole.planet.myplanet.model.ResourceListModel
import java.lang.ref.WeakReference

object ResourceSearchUtils {
    private var cachedListRef: WeakReference<List<*>>? = null
    private var cachedTitles: List<String?> = emptyList()

    fun <T> searchList(list: List<T>, query: String, titleSelector: (T) -> String?): List<T> {
        if (query.isEmpty()) return list

        val queryParts = query.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(query)

        val startsWithQuery = mutableListOf<T>()
        val containsQuery = mutableListOf<T>()

        if (cachedListRef?.get() !== list) {
            cachedListRef = WeakReference(list)
            cachedTitles = list.map { titleSelector(it)?.let { t -> Utilities.normalizeText(t) } }
        }

        for (i in list.indices) {
            val item = list[i]
            val title = cachedTitles.getOrNull(i) ?: continue
            if (title.startsWith(normalizedQuery)) {
                startsWithQuery.add(item)
            } else if (normalizedQueryParts.all { title.contains(it) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    fun searchLocalModels(models: List<ResourceListModel>, query: String): List<ResourceListModel> {
        return searchList(models, query) { it.item.title }
    }
}
