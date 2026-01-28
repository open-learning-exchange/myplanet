package org.ole.planet.myplanet.base

import com.google.gson.JsonArray
import io.realm.RealmModel
import io.realm.Sort
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    var isMyCourseLib: Boolean = false

    @Suppress("UNCHECKED_CAST")
    suspend fun getList(c: Class<*>): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                submissionsRepository.getAllSurveys() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>)
            }
            c == RealmMyLibrary::class.java -> {
                val all = resourcesRepository.getAllLibraryItems()
                val public = all.filter { !it.isPrivate }
                RealmMyLibrary.getOurLibrary(model?.id, public) as List<LI>
            }
            else -> {
                val myLibItems = getMyLibItems(c as Class<out RealmModel>)
                val results = coursesRepository.getAllCourses().filter { !it.courseTitle.isNullOrEmpty() }
                val ourCourseItems = RealmMyCourse.getOurCourse(model?.id, results)

                when (c) {
                    RealmMyCourse::class.java -> {
                        val combinedList = mutableListOf<RealmMyCourse>()
                        (myLibItems as List<RealmMyCourse>).forEach { course ->
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
        return if (orderBy != null) {
            sortList(list, orderBy, sort)
        } else {
            list
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : RealmModel> getMyLibItems(c: Class<T>, orderBy: String? = null): List<LI> {
        val results: List<T> = when (c) {
            RealmMyLibrary::class.java -> resourcesRepository.getAllLibraryItems() as List<T>
            RealmMyCourse::class.java -> coursesRepository.getAllCourses() as List<T>
            else -> emptyList()
        }

        val myItems = when (c) {
            RealmMyLibrary::class.java -> {
                RealmMyLibrary.getMyLibraryByUserId(model?.id, results as? List<RealmMyLibrary> ?: emptyList()) as List<LI>
            }
            RealmMyCourse::class.java -> {
                RealmMyCourse.getMyCourseByUserId(model?.id, results as? List<RealmMyCourse> ?: emptyList()) as List<LI>
            }
            else -> throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
        }

        return if (orderBy != null) sortList(myItems, orderBy, Sort.ASCENDING) else myItems
    }

    private fun sortList(list: List<LI>, orderBy: String, sort: Sort): List<LI> {
        val comparator = Comparator<LI> { o1, o2 ->
            val v1 = getPropertyValue(o1, orderBy)
            val v2 = getPropertyValue(o2, orderBy)
            compareValues(v1, v2)
        }
        return if (sort == Sort.ASCENDING) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
    }

    private fun getPropertyValue(obj: Any?, property: String): Comparable<*>? {
        if (obj == null) return null
        if (obj is RealmMyLibrary) {
            return when (property) {
                "createdDate" -> obj.createdDate
                "title" -> obj.title
                "timesRated" -> obj.timesRated
                "averageRating" -> obj.averageRating
                else -> null
            }
        } else if (obj is RealmMyCourse) {
            return when (property) {
                "courseTitle" -> obj.courseTitle
                "createdDate" -> obj.createdDate
                else -> null
            }
        }
        return null
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
