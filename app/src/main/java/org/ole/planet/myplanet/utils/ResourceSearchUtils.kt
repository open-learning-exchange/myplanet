package org.ole.planet.myplanet.utils

import org.ole.planet.myplanet.model.ResourceListModel

object ResourceSearchUtils {
    fun <T> searchList(list: List<T>, query: String, titleSelector: (T) -> String?): List<T> {
        if (query.isEmpty()) return list

        val queryParts = query.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(query)

        val startsWithQuery = mutableListOf<T>()
        val containsQuery = mutableListOf<T>()

        for (item in list) {
            val title = titleSelector(item) ?: continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    fun searchLocalModels(models: List<ResourceListModel>, query: String): List<ResourceListModel> {
        return searchList(models, query) { it.library.titleNormal ?: it.item.title?.let { t -> Utilities.normalizeText(t) } }
    }
}
