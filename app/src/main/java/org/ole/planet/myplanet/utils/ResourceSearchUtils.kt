package org.ole.planet.myplanet.utils

import org.ole.planet.myplanet.model.ResourceListModel

object ResourceSearchUtils {
    fun <T> searchList(list: List<T>, query: String, titleSelector: (T) -> String?): List<T> {
        if (query.isEmpty()) return list

        val queryParts = query.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }.distinct()
        if (normalizedQueryParts.isEmpty()) return list

        val normalizedQuery = Utilities.normalizeText(query)

        val startsWithQuery = mutableListOf<T>()
        val containsQuery = mutableListOf<T>()

        // Cache normalized titles to avoid repeatedly normalizing the same strings
        val cachedTitles = list.mapNotNull { item ->
            titleSelector(item)?.let { item to Utilities.normalizeText(it) }
        }

        for ((item, title) in cachedTitles) {
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    fun searchLocalModels(models: List<ResourceListModel>, query: String): List<ResourceListModel> {
        return searchList(models, query) { it.item.title }
    }
}
