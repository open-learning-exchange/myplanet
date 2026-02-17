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
        val userId = model?.id
        return when {
            c == RealmStepExam::class.java -> {
                mRealm.where(c).equalTo("type", "surveys").findAll().toList() as List<LI>
            }
            isMyCourseLib -> {
                val userId = model?.id ?: ""
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
                resourcesRepository.getOurLibraryItems(userId ?: "") as List<LI>
            }
            else -> {
                val myLibItems = coursesRepository.getMyCourses(userId ?: "")
                val ourCourseItems = coursesRepository.getOurCourses(userId ?: "")

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


    fun getJsonArrayFromList(list: Set<String>): JsonArray {
        val array = JsonArray()
        list.forEach { array.add(it) }
        return array
    }

    companion object {
        var isSurvey: Boolean = false
    }
}
