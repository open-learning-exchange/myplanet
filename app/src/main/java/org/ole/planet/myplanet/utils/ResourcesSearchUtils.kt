package org.ole.planet.myplanet.utils

import org.ole.planet.myplanet.model.ResourceListModel

object ResourcesSearchUtils {

    /**
     * Searches a list of items using multi-part query matching and 3-tier relevance ranking:
     * 1. Prefix matches on primary text
     * 2. Full phrase or all query parts contained in primary text
     * 3. Full phrase or all query parts contained in primary or secondary texts
     *
     * Within each relevance tier, items are sorted alphabetically (case-insensitive) by primary text.
     *
     * @param primarySelector Returns raw or pre-normalized primary text (e.g. title) used for matching and sorting.
     * @param secondarySelectors Returns raw or pre-normalized secondary text fields (e.g. description, author, tags).
     */
    fun <T> searchList(
        list: List<T>,
        query: String,
        primarySelector: (T) -> String?,
        secondarySelectors: List<(T) -> String?> = emptyList()
    ): List<T> {
        val trimmedQuery = query.trim()
        val comparator = compareBy<T, String>(String.CASE_INSENSITIVE_ORDER) { primarySelector(it).orEmpty() }
        if (trimmedQuery.isEmpty()) return list.sortedWith(comparator)

        val normalizedQueryParts = trimmedQuery.splitToSequence(" ")
            .filter { it.isNotEmpty() }
            .map { Utilities.normalizeText(it) }
            .toList()
        val normalizedQuery = Utilities.normalizeText(trimmedQuery)

        val startsWithQuery = mutableListOf<T>()
        val primaryContainsQuery = mutableListOf<T>()
        val secondaryContainsQuery = mutableListOf<T>()

        for (item in list) {
            val primary = primarySelector(item)?.let { Utilities.normalizeText(it) }.orEmpty()
            if (primary.startsWith(normalizedQuery)) {
                startsWithQuery.add(item)
            } else if (normalizedQueryParts.all { primary.contains(it) }) {
                primaryContainsQuery.add(item)
            } else if (secondarySelectors.isNotEmpty()) {
                val secondaryValues = secondarySelectors.mapNotNull { selector ->
                    selector(item)?.let { Utilities.normalizeText(it) }
                }
                val matchesSecondary = normalizedQueryParts.all { part ->
                    primary.contains(part) || secondaryValues.any { it.contains(part) }
                }
                if (matchesSecondary) {
                    secondaryContainsQuery.add(item)
                }
            }
        }
        return startsWithQuery.sortedWith(comparator) +
            primaryContainsQuery.sortedWith(comparator) +
            secondaryContainsQuery.sortedWith(comparator)
    }

    fun searchLocalModels(models: List<ResourceListModel>, query: String): List<ResourceListModel> {
        return searchList(
            list = models,
            query = query,
            primarySelector = { it.item.title?.takeIf { t -> t.isNotEmpty() } ?: it.library.title },
            secondarySelectors = listOf(
                { it.library.description ?: it.item.description },
                { it.library.author },
                { it.library.publisher },
                { it.tags.joinToString(" ") { tag -> tag.name.orEmpty() } },
                { it.library.subject?.joinToString(" ") }
            )
        )
    }
}
