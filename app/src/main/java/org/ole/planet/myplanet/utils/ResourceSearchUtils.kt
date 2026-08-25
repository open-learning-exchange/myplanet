package org.ole.planet.myplanet.utils

import org.ole.planet.myplanet.model.ResourceListModel

object ResourceSearchUtils {
    // Requires normalizedTitleSelector to return a pre-normalized (and lowercased) string
    fun <T> searchList(list: List<T>, query: String, normalizedTitleSelector: (T) -> String?): List<T> {
        if (query.isEmpty()) return list

        val normalizedQueryParts = query.splitToSequence(" ").filter { it.isNotEmpty() }.map { Utilities.normalizeText(it) }.toList()
        val normalizedQuery = Utilities.normalizeText(query)

        val startsWithQuery = mutableListOf<T>()
        val containsQuery = mutableListOf<T>()

        for (item in list) {
            val title = normalizedTitleSelector(item) ?: continue
            if (title.startsWith(normalizedQuery)) {
                startsWithQuery.add(item)
            } else if (normalizedQueryParts.all { title.contains(it) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    fun searchLocalModels(models: List<ResourceListModel>, query: String): List<ResourceListModel> {
        return searchList(models, query) { it.library.titleNormal ?: Utilities.normalizeText(it.item.title ?: "") }
    }
}
