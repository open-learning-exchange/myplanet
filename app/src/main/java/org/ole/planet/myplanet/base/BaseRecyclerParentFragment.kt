package org.ole.planet.myplanet.base

import com.google.gson.JsonArray
import io.realm.RealmModel
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.repository.SurveysRepository

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    var isMyCourseLib: Boolean = false

    @Inject
    lateinit var surveysRepository: SurveysRepository

    @Suppress("UNCHECKED_CAST")
    suspend fun getList(c: Class<*>): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getAllSurveys() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>)
            }
            c == RealmMyLibrary::class.java -> {
                resourcesRepository.getFilteredPublicLibraryItems(model?.id) as List<LI>
            }
            else -> {
                val myLibItems = getMyLibItems(c as Class<out RealmModel>)
                val ourCourseItems = coursesRepository.getFilteredCourses(model?.id)

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
        var list: List<Any?> = when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getAllSurveys()
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>)
            }
            c == RealmMyLibrary::class.java -> {
                resourcesRepository.getFilteredPublicLibraryItems(model?.id)
            }
            else -> {
                val myLibItems = getMyLibItems(c as Class<out RealmModel>)
                val ourCourseItems = coursesRepository.getFilteredCourses(model?.id)
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
                combinedList
            }
        }

        if (orderBy != null) {
             list = sortList(list, orderBy, sort)
        }

        return list as List<LI>
    }

    private fun sortList(list: List<Any?>, orderBy: String, sort: Sort): List<Any?> {
         return if (sort == Sort.ASCENDING) {
             when (orderBy) {
                 "title" -> list.sortedBy { (it as? RealmMyLibrary)?.title ?: (it as? RealmMyCourse)?.courseTitle ?: "" }
                 "createdDate" -> list.sortedBy { (it as? RealmMyLibrary)?.createdDate ?: (it as? RealmMyCourse)?.createdDate ?: 0L }
                 else -> list
             }
         } else {
             when (orderBy) {
                 "title" -> list.sortedByDescending { (it as? RealmMyLibrary)?.title ?: (it as? RealmMyCourse)?.courseTitle ?: "" }
                 "createdDate" -> list.sortedByDescending { (it as? RealmMyLibrary)?.createdDate ?: (it as? RealmMyCourse)?.createdDate ?: 0L }
                 else -> list
             }
         }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : RealmModel> getMyLibItems(c: Class<T>, orderBy: String? = null): List<LI> {
        val results: List<T> = when (c) {
             RealmMyLibrary::class.java -> {
                 resourcesRepository.getMyLibrary(model?.id) as List<T>
             }
             RealmMyCourse::class.java -> {
                 coursesRepository.getUserCourses(model?.id) as List<T>
             }
             else -> throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
        }

        return results as List<LI>
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
