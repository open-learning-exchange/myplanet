package org.ole.planet.myplanet.base

import com.google.gson.JsonArray
import io.realm.RealmModel
import io.realm.Sort
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    var isMyCourseLib: Boolean = false

    @Suppress("UNCHECKED_CAST")
    suspend fun getList(c: Class<*>): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getSurveys() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>)
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
        return when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getSurveys(orderBy ?: "", sort) as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>, orderBy, sort)
            }
            else -> {
                val results = if (orderBy != null) {
                    coursesRepository.getAllCourses(orderBy, sort)
                } else {
                    coursesRepository.getAllCourses()
                }
                RealmMyCourse.getOurCourse(model?.id, results) as List<LI>
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : RealmModel> getMyLibItems(c: Class<T>, orderBy: String? = null, sort: Sort = Sort.ASCENDING): List<LI> {
        return when (c) {
            RealmMyCourse::class.java -> {
                val results = if (orderBy != null) {
                    coursesRepository.getAllCourses(orderBy, sort)
                } else {
                    coursesRepository.getAllCourses()
                }
                RealmMyCourse.getMyCourseByUserId(model?.id, results) as List<LI>
            }
            else -> throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
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
