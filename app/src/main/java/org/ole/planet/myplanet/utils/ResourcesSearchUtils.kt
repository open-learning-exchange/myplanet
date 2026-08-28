package org.ole.planet.myplanet.utils

import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.ResourceListModel

object ResourcesSearchUtils {

    fun <T> searchList(
        list: List<T>,
        query: String,
        primarySelector: (T) -> String?,
        secondarySelectors: List<(T) -> String?> = emptyList()
    ): List<T> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return list

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
            } else {
                val secondaryCombined = secondarySelectors.mapNotNull { it(item) }
                    .joinToString(" ") { Utilities.normalizeText(it) }
                val allText = "$primary $secondaryCombined"
                if (normalizedQueryParts.all { allText.contains(it) }) {
                    secondaryContainsQuery.add(item)
                }
            }
        }
        return startsWithQuery + primaryContainsQuery + secondaryContainsQuery
    }

    // Requires normalizedTitleSelector to return a pre-normalized (and lowercased) string
    fun <T> searchList(list: List<T>, query: String, normalizedTitleSelector: (T) -> String?): List<T> {
        return searchList(list, query, normalizedTitleSelector, emptyList())
    }

    fun searchLocalModels(models: List<ResourceListModel>, query: String): List<ResourceListModel> {
        return searchList(
            list = models,
            query = query,
            primarySelector = { it.library.titleNormal ?: it.item.title?.let { t -> Utilities.normalizeText(t) } },
            secondarySelectors = listOf(
                { it.library.description ?: it.item.description },
                { it.library.author },
                { it.library.publisher },
                { it.tags.joinToString(" ") { tag -> tag.name.orEmpty() } },
                { it.library.subject?.joinToString(" ") }
            )
        )
    }

    fun searchCourseSteps(steps: List<CourseStep>, query: String): List<CourseStep> {
        return searchList(
            list = steps,
            query = query,
            primarySelector = { it.stepTitle?.let { t -> Utilities.normalizeText(t) } },
            secondarySelectors = listOf(
                { it.description }
            )
        )
    }
}
