package org.ole.planet.myplanet.base

import com.google.gson.JsonArray
import io.realm.Sort
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    var isMyCourseLib: Boolean = false

    @Suppress("UNCHECKED_CAST")
    suspend fun getList(c: Class<*>): List<LI> {
        val userId = model?.id ?: ""
        return when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getAllSurveys() as List<LI>
            }
            isMyCourseLib -> {
                when (c) {
                    RealmMyLibrary::class.java -> {
                        resourcesRepository.getMyLibraryItems(userId) as List<LI>
                    }
                    RealmMyCourse::class.java -> {
                        coursesRepository.getMyCourses(userId) as List<LI>
                    }
                    else -> throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
                }
            }
            c == RealmMyLibrary::class.java -> {
                resourcesRepository.getOurLibraryItems(userId) as List<LI>
            }
            else -> {
                val myLibItems = coursesRepository.getMyCourses(userId)
                val ourCourseItems = coursesRepository.getOurCourses(userId)

                when (c) {
                    RealmMyCourse::class.java -> {
                        val combinedList = mutableListOf<RealmMyCourse>()
                        myLibItems.forEach { course ->
                            course.isMyCourse = true
                            combinedList.add(course)
                        }
                        ourCourseItems.forEach { course ->
                            if (!combinedList.any { it.id == course.id }) {
                                course.isMyCourse = false
                                combinedList.add(course)
                            }
                        }
                        combinedList as List<LI>
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getList(c: Class<*>, orderBy: String? = null, sort: Sort = Sort.ASCENDING): List<LI> {
        val list = getList(c)
        if (orderBy == null) return list

        val isAscending = sort == Sort.ASCENDING
        return when (c) {
            RealmMyLibrary::class.java -> sortByProperty(list as List<RealmMyLibrary>, orderBy, isAscending) as List<LI>
            RealmMyCourse::class.java -> sortByProperty(list as List<RealmMyCourse>, orderBy, isAscending) as List<LI>
            RealmStepExam::class.java -> sortByProperty(list as List<RealmStepExam>, orderBy, isAscending) as List<LI>
            else -> list
        }
    }

    private fun <T> sortByProperty(list: List<T>, propertyName: String, ascending: Boolean): List<T> {
        val comparator = Comparator<T> { o1, o2 ->
            val v1 = getProperty(o1!!, propertyName)
            val v2 = getProperty(o2!!, propertyName)
            compareValues(v1, v2)
        }
        return if (ascending) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
    }

    private fun getProperty(obj: Any, propertyName: String): Comparable<*>? {
        // Simple reflection or manual mapping to get property value for sorting
        try {
            val field = obj.javaClass.getDeclaredField(propertyName)
            field.isAccessible = true
            return field.get(obj) as? Comparable<*>
        } catch (e: Exception) {
            return null
        }
    }

    fun getJsonArrayFromList(list: Set<String>): JsonArray {
        val array = JsonArray()
        list.forEach { array.add(it) }
        return array
    }

    companion object {
        var isSurvey: Boolean = false
    }
}
