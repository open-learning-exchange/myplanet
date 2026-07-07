package org.ole.planet.myplanet.utils

import org.ole.planet.myplanet.model.ResourceListModel
import java.text.Normalizer
import java.util.Locale

object ResourceSearchUtils {
    fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
    }

    fun searchLocalModels(models: List<ResourceListModel>, query: String): List<ResourceListModel> {
        if (query.isEmpty()) return models

        val queryParts = query.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { normalizeText(it) }
        val normalizedQuery = normalizeText(query)

        val startsWithQuery = mutableListOf<ResourceListModel>()
        val containsQuery = mutableListOf<ResourceListModel>()

        for (model in models) {
            val title = model.item.title?.let { normalizeText(it) } ?: continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(model)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(model)
            }
        }
        return startsWithQuery + containsQuery
    }
}
